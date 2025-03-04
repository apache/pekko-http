/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.engine.http2

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.impl.engine.http2.Http2.HttpImplementation
import pekko.http.impl.engine.server.{ HttpAttributes, ServerTerminator }
import pekko.stream.ActorAttributes.Dispatcher
import pekko.stream.TLSProtocol.{ SessionBytes, SessionTruncated, SslTlsInbound, SslTlsOutbound }
import pekko.stream._
import pekko.stream.scaladsl.Flow
import pekko.stream.stage.{ GraphStageLogic, GraphStageWithMaterializedValue, InHandler, OutHandler }

import javax.net.ssl.SSLException
import scala.concurrent.{ Future, Promise }

/** INTERNAL API */
@InternalApi
private[http] object ProtocolSwitch {
  def apply(
      chosenProtocolAccessor: SessionBytes => String,
      http1Stack: HttpImplementation,
      http2Stack: HttpImplementation): Flow[SslTlsInbound, SslTlsOutbound, Future[ServerTerminator]] =
    Flow.fromGraph(
      new GraphStageWithMaterializedValue[FlowShape[SslTlsInbound, SslTlsOutbound], Future[ServerTerminator]] {

        // --- outer ports ---
        val netIn = Inlet[SslTlsInbound]("AlpnSwitch.netIn")
        val netOut = Outlet[SslTlsOutbound]("AlpnSwitch.netOut")
        // --- end of outer ports ---

        val shape: FlowShape[SslTlsInbound, SslTlsOutbound] =
          FlowShape(netIn, netOut)

        override def createLogicAndMaterializedValue(
            inheritedAttributes: Attributes): (GraphStageLogic, Future[ServerTerminator]) = {
          val terminatorPromise = Promise[ServerTerminator]()

          object Logic extends GraphStageLogic(shape) {

            // --- inner ports, bound to actual server in install call ---
            val serverDataIn = new SubSinkInlet[SslTlsOutbound]("ServerImpl.netIn")
            val serverDataOut = new SubSourceOutlet[SslTlsInbound]("ServerImpl.netOut")
            // --- end of inner ports ---

            override def preStart(): Unit = pull(netIn)

            setHandler(netIn,
              new InHandler {
                def onPush(): Unit =
                  grab(netIn) match {
                    case first: SessionBytes =>
                      val chosen = chosenProtocolAccessor(first)
                      chosen match {
                        case "h2" =>
                          install(http2Stack.addAttributes(HttpAttributes.tlsSessionInfo(first.session)), first)
                        case _ => install(http1Stack, first)
                      }
                    case SessionTruncated =>
                      failStage(new SSLException("TLS session was truncated (probably missing a close_notify packet)."))
                  }
              })

            private val ignorePull = new OutHandler {
              def onPull(): Unit = ()
            }

            setHandler(netOut, ignorePull)

            def install(serverImplementation: HttpImplementation, firstElement: SslTlsInbound): Unit = {
              val networkSide = Flow.fromSinkAndSource(serverDataIn.sink, serverDataOut.source)

              connect(netIn, serverDataOut, Some(firstElement))

              connect(serverDataIn, netOut)

              val attrs =
                Attributes(
                  // don't (re)set dispatcher attribute to avoid adding an explicit async boundary
                  // between low-level and high-level stages
                  inheritedAttributes.attributeList.filterNot(_.isInstanceOf[Dispatcher]))

              val serverTerminator =
                serverImplementation
                  .addAttributes(attrs) // propagate attributes to "real" server (such as HttpAttributes)
                  .join(networkSide)
                  .run()(interpreter.subFusingMaterializer)
              terminatorPromise.success(serverTerminator)
            }

            // helpers to connect inlets and outlets also binding completion signals of given ports
            def connect[T](in: Inlet[T], out: SubSourceOutlet[T], initialElement: Option[T]): Unit = {
              val propagatePull =
                new OutHandler {
                  override def onPull(): Unit = pull(in)

                  override def onDownstreamFinish(cause: Throwable): Unit = cancel(in)
                }

              val firstHandler =
                initialElement match {
                  case Some(initial) if out.isAvailable =>
                    out.push(initial)
                    propagatePull
                  case Some(initial) =>
                    new OutHandler {
                      override def onPull(): Unit = {
                        out.push(initial)
                        out.setHandler(propagatePull)
                      }
                    }
                  case None => propagatePull
                }

              out.setHandler(firstHandler)
              setHandler(in,
                new InHandler {
                  override def onPush(): Unit = out.push(grab(in))

                  override def onUpstreamFinish(): Unit = {
                    out.complete()
                    super.onUpstreamFinish()
                  }

                  override def onUpstreamFailure(ex: Throwable): Unit = {
                    out.fail(ex)
                    super.onUpstreamFailure(ex)
                  }
                })

              if (out.isAvailable) pull(in) // to account for lost pulls during initialization
            }

            def connect[T](in: SubSinkInlet[T], out: Outlet[T]): Unit = {
              val handler = new InHandler {
                override def onPush(): Unit = push(out, in.grab())
              }

              val outHandler = new OutHandler {
                override def onPull(): Unit = in.pull()

                override def onDownstreamFinish(cause: Throwable): Unit = {
                  in.cancel()
                  super.onDownstreamFinish(cause)
                }
              }
              in.setHandler(handler)
              setHandler(out, outHandler)

              if (isAvailable(out)) in.pull() // to account for lost pulls during initialization
            }
          }

          (Logic, terminatorPromise.future)
        }
      })

  def byPreface(http1Stack: HttpImplementation, http2Stack: HttpImplementation)
      : Flow[SslTlsInbound, SslTlsOutbound, Future[ServerTerminator]] = {
    def chooseProtocol(sessionBytes: SessionBytes): String =
      if (sessionBytes.bytes.startsWith(Http2Protocol.ClientConnectionPreface)) "h2" else "http/1.1"
    ProtocolSwitch(chooseProtocol, http1Stack, http2Stack)
  }
}
