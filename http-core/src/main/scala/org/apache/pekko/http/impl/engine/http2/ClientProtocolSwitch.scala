/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.http.impl.engine.http2

import org.apache.pekko
import pekko.NotUsed
import pekko.annotation.InternalApi
import pekko.http.scaladsl.model.{ HttpRequest, HttpResponse }
import pekko.stream.ActorAttributes.Dispatcher
import pekko.stream.TLSProtocol.{ SslTlsInbound, SslTlsOutbound }
import pekko.stream.scaladsl.{ BidiFlow, Flow, Sink, Source }
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import pekko.stream.{ Attributes, BidiShape, Inlet, Outlet }

import javax.net.ssl.SSLException
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

/**
 * INTERNAL API
 *
 * Client-side counterpart of [[ProtocolSwitch]]: installs either the HTTP/1.1 or the HTTP/2 client layer once ALPN
 * has settled which protocol the server speaks.
 *
 * [[ProtocolSwitch]] can decide on the first inbound `SessionBytes`, because server-side the client always speaks
 * first. That trigger deadlocks here: when negotiation lands on HTTP/1.1 the server stays silent until it receives a
 * request, and the switch would be holding that request while waiting for inbound bytes. So the decision is instead
 * driven by `negotiatedProtocol`, which [[AlpnObservingSSLEngine]] completes from the TLS handshake itself.
 *
 * Because that future is completed on the TLS stage's thread and delivered through an `AsyncCallback`, inbound
 * elements can overtake it — an HTTP/2 server sends its SETTINGS frame immediately after the handshake. Those are
 * buffered and replayed into the installed layer.
 */
@InternalApi
private[http] object ClientProtocolSwitch {
  type ClientLayer = BidiFlow[HttpRequest, SslTlsOutbound, SslTlsInbound, HttpResponse, NotUsed]

  def apply(negotiatedProtocol: Future[String], http1: ClientLayer, http2: ClientLayer): ClientLayer =
    BidiFlow.fromGraph(new GraphStage[BidiShape[HttpRequest, SslTlsOutbound, SslTlsInbound, HttpResponse]] {
      val appIn = Inlet[HttpRequest]("ClientProtocolSwitch.appIn")
      val netOut = Outlet[SslTlsOutbound]("ClientProtocolSwitch.netOut")
      val netIn = Inlet[SslTlsInbound]("ClientProtocolSwitch.netIn")
      val appOut = Outlet[HttpResponse]("ClientProtocolSwitch.appOut")

      override val shape: BidiShape[HttpRequest, SslTlsOutbound, SslTlsInbound, HttpResponse] =
        BidiShape(appIn, netOut, netIn, appOut)

      override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
        private var installed = false
        private var appInFinished = false
        private var pendingInbound: Vector[SslTlsInbound] = Vector.empty

        override def preStart(): Unit = {
          // give TLS somewhere to put decrypted bytes; nothing is emitted until the handshake is through
          pull(netIn)

          val callback = getAsyncCallback[Try[String]] {
            case Success(protocol) =>
              if (!installed) install(if (protocol == Http2AlpnSupport.H2) http2 else http1)
            case Failure(cause) => failStage(cause)
          }
          negotiatedProtocol.onComplete(callback.invoke)(ExecutionContext.parasitic)
        }

        // -- handlers used until a layer is installed --

        setHandler(appIn,
          new InHandler {
            // appIn is never pulled before a layer is installed, so onPush cannot fire
            override def onPush(): Unit = ()
            override def onUpstreamFinish(): Unit = appInFinished = true
            override def onUpstreamFailure(cause: Throwable): Unit = failStage(cause)
          })

        setHandler(netIn,
          new InHandler {
            // buffer and stop pulling: whatever arrives here is already past the handshake
            override def onPush(): Unit = pendingInbound :+= grab(netIn)
            override def onUpstreamFinish(): Unit =
              failStage(new SSLException("Connection closed before the ALPN protocol was negotiated"))
            override def onUpstreamFailure(cause: Throwable): Unit = failStage(cause)
          })

        setHandler(netOut, GraphStageLogic.EagerTerminateOutput)
        setHandler(appOut, GraphStageLogic.EagerTerminateOutput)

        def install(layer: ClientLayer): Unit = {
          installed = true

          val appDataOut = new SubSourceOutlet[HttpRequest]("ClientProtocolSwitch.appDataOut")
          val appDataIn = new SubSinkInlet[HttpResponse]("ClientProtocolSwitch.appDataIn")
          val netDataOut = new SubSourceOutlet[SslTlsInbound]("ClientProtocolSwitch.netDataOut")
          val netDataIn = new SubSinkInlet[SslTlsOutbound]("ClientProtocolSwitch.netDataIn")

          val replay = pendingInbound
          pendingInbound = Vector.empty

          connectIn(appIn, appDataOut, Vector.empty, appInFinished)
          connectIn(netIn, netDataOut, replay, upstreamFinished = false)
          connectOut(appDataIn, appOut)
          connectOut(netDataIn, netOut)

          val attrs =
            Attributes(
              // don't (re)set dispatcher attribute to avoid adding an explicit async boundary
              // between low-level and high-level stages
              inheritedAttributes.attributeList.filterNot(_.isInstanceOf[Dispatcher]))

          Source.fromGraph(appDataOut.source)
            .via(layer.addAttributes(attrs).join(Flow.fromSinkAndSource(netDataIn.sink, netDataOut.source)))
            .runWith(Sink.fromGraph(appDataIn.sink))(interpreter.subFusingMaterializer)
        }

        /**
         * Feeds an outer inlet into the installed layer, replaying anything buffered while waiting for the
         * negotiation result.
         *
         * `netIn` may still have the pull from `preStart` outstanding here, and an element may arrive for it before
         * the sub-stream has any demand, so this both guards against pulling twice and buffers what it cannot push
         * yet. Upstream completion is forwarded to the sub-stream only: the connection has to keep running so that
         * in-flight responses can still be delivered.
         */
        def connectIn[T](in: Inlet[T], out: SubSourceOutlet[T], buffered: Vector[T], upstreamFinished: Boolean)
            : Unit = {
          var pending = buffered
          var finished = upstreamFinished

          def pump(): Unit = {
            while (pending.nonEmpty && out.isAvailable) {
              out.push(pending.head)
              pending = pending.tail
            }
            if (pending.isEmpty)
              if (finished) out.complete()
              else if (out.isAvailable && !hasBeenPulled(in) && !isClosed(in)) pull(in)
          }

          out.setHandler(new OutHandler {
            override def onPull(): Unit = pump()
            override def onDownstreamFinish(cause: Throwable): Unit = if (!isClosed(in)) cancel(in)
          })

          setHandler(in,
            new InHandler {
              override def onPush(): Unit = {
                pending :+= grab(in)
                pump()
              }
              override def onUpstreamFinish(): Unit = {
                finished = true
                if (pending.isEmpty) out.complete()
              }
              override def onUpstreamFailure(cause: Throwable): Unit = out.fail(cause)
            })

          pump()
        }

        /** Drains the installed layer into an outer outlet. */
        def connectOut[T](in: SubSinkInlet[T], out: Outlet[T]): Unit = {
          in.setHandler(new InHandler {
            override def onPush(): Unit = push(out, in.grab())
            override def onUpstreamFinish(): Unit = complete(out)
            override def onUpstreamFailure(cause: Throwable): Unit = fail(out, cause)
          })

          setHandler(out,
            new OutHandler {
              override def onPull(): Unit = in.pull()
              override def onDownstreamFinish(cause: Throwable): Unit = in.cancel()
            })

          // demand may already have arrived on the outer port while we were waiting for the handshake
          if (isAvailable(out)) in.pull()
        }
      }
    })
}
