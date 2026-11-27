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

package org.apache.pekko.http.impl.engine.ws

import java.util.concurrent.TimeoutException

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.concurrent.duration.FiniteDuration

import org.apache.pekko
import pekko.NotUsed
import pekko.annotation.InternalApi
import pekko.event.LoggingAdapter
import pekko.http.impl.engine.parsing.HttpMessageParser.StateResult
import pekko.http.impl.engine.parsing.ParserOutput.{ MessageStartError, NeedMoreData, RemainingBytes, ResponseStart }
import pekko.http.impl.engine.parsing.{ HttpHeaderParser, HttpResponseParser, ParserOutput }
import pekko.http.impl.engine.rendering.{ HttpRequestRendererFactory, RequestRenderingContext }
import pekko.http.impl.settings.{ WebSocketCompressionSettingsImpl, WebSocketSettingsImpl }
import pekko.http.impl.util.LogByteStringTools
import pekko.http.impl.util.{ SingletonException, StreamUtils }
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model.headers.{ Host, `Sec-WebSocket-Extensions` }
import pekko.http.scaladsl.model.ws._
import pekko.http.scaladsl.model.{ HttpEntity, HttpMethods, HttpResponse }
import pekko.http.scaladsl.settings.ClientConnectionSettings
import pekko.stream.TLSProtocol._
import pekko.stream._
import pekko.stream.impl.fusing.GraphStages.SimpleLinearGraphStage
import pekko.stream.scaladsl._
import pekko.stream.stage._
import pekko.util.ByteString

/** INTERNAL API */
@InternalApi
private[http] object WebSocketClientBlueprint {
  private final class ManagedCompression(val settings: WebSocketCompressionSettingsImpl) {
    val selected = Promise[Option[PerMessageDeflate.Negotiated]]()
  }

  /**
   * Returns a WebSocketClientLayer that can be materialized once.
   */
  def apply(
      request: WebSocketRequest,
      settings: ClientConnectionSettings,
      log: LoggingAdapter): Http.WebSocketClientLayer = {
    val compression = settings.websocketSettings.asInstanceOf[WebSocketSettingsImpl].compression
    val managedCompression =
      if (compression.clientEnabled &&
        !Handshake.Client.extensionNames(request.extraHeaders).contains(PerMessageDeflate.ExtensionName))
        Some(new ManagedCompression(compression))
      else None
    val stack = managedCompression match {
      case Some(managed) => WebSocket.clientStack(settings.websocketSettings, managed.selected.future, log)
      case None          => WebSocket.stack(serverSide = false, settings.websocketSettings, log = log)
    }
    LogByteStringTools.logTLSBidiBySetting("client-plain-text", settings.logUnencryptedNetworkBytes).reversed
      .atop(simpleTls)
      .atopMat(handshake(request, settings, log, managedCompression))(Keep.right)
      .atop(WebSocket.framing)
      .atop(stack)
      .reversed
  }

  /**
   * A bidi flow that injects and inspects the WS handshake and then goes out of the way. This BidiFlow
   * can only be materialized once.
   */
  def handshake(
      request: WebSocketRequest,
      settings: ClientConnectionSettings,
      log: LoggingAdapter)
      : BidiFlow[ByteString, ByteString, ByteString, ByteString, Future[WebSocketUpgradeResponse]] =
    handshake(request, settings, log, None)

  private def handshake(
      request: WebSocketRequest,
      settings: ClientConnectionSettings,
      log: LoggingAdapter,
      managedCompression: Option[ManagedCompression])
      : BidiFlow[ByteString, ByteString, ByteString, ByteString, Future[WebSocketUpgradeResponse]] = {
    import request._
    val result = Promise[WebSocketUpgradeResponse]()

    val valve = StreamUtils.OneTimeValve()
    val openingDeadline = settings.websocketSettings.openingTimeout match {
      case timeout: FiniteDuration => Some(timeout -> Promise[Unit]())
      case _                       => None
    }

    val subprotocols: Seq[String] = subprotocol.toList.flatMap(_.split(",")).map(_.trim)
    val requestHeaders = if (managedCompression.isDefined)
      extraHeaders :+ `Sec-WebSocket-Extensions`(Seq(PerMessageDeflate.ClientOffer))
    else extraHeaders
    val (initialRequest, key) =
      Handshake.Client.buildRequest(uri, requestHeaders, subprotocols, settings.websocketRandomFactory())
    val offeredExtensions = Handshake.Client.extensionNames(requestHeaders)
    val hostHeader = Host(uri.authority.normalizedFor(uri.scheme))
    val renderedInitialRequest =
      HttpRequestRendererFactory.renderStrict(RequestRenderingContext(initialRequest, hostHeader), settings, log)

    class UpgradeStage extends SimpleLinearGraphStage[ByteString] {

      override def createLogic(attributes: Attributes): GraphStageLogic =
        new TimerGraphStageLogic(shape) with InHandler with OutHandler {
          private val OpeningDeadline = "websocket-opening"
          override def preStart(): Unit = {
            // The managed frame stages wait for negotiation before pulling. Drive the HTTP
            // handshake independently of that demand, while leaving any coalesced frames buffered.
            if (managedCompression.isDefined) pull(in)
            openingDeadline.foreach { case (timeout, dispatched) =>
              val startTimer = getAsyncCallback[Unit] { _ =>
                if (!result.isCompleted) scheduleOnce(OpeningDeadline, timeout)
              }
              dispatched.future.foreach(startTimer.invoke)(ExecutionContext.parasitic)
            }
          }

          override protected def onTimer(timerKey: Any): Unit =
            openingDeadline.foreach { case (timeout, _) =>
              val failure = new TimeoutException(s"WebSocket opening handshake did not complete within $timeout.")
              if (result.tryFailure(failure)) failStage(failure)
            }

          // a special version of the parser which only parses one message and then reports the remaining data
          // if some is available
          val parser: HttpResponseParser =
            new HttpResponseParser(settings.parserSettings, HttpHeaderParser(settings.parserSettings, log)) {
              var first = true
              override def handleInformationalResponses = false
              override protected def parseMessage(input: ByteString, offset: Int): StateResult = {
                if (first) {
                  try {
                    // If we're called recursively then that's a next message
                    first = false
                    super.parseMessage(input, offset)
                  } catch {
                    // Specifically NotEnoughDataException, but that's not visible here
                    case t: SingletonException => {
                      // If parsing the first message fails, retry and treat it like the first message again.
                      first = true
                      throw t
                    }
                  }
                } else {
                  emit(RemainingBytes(input.drop(offset)))
                  terminate()
                }
              }
            }
          parser.setContextForNextResponse(HttpResponseParser.ResponseContext(HttpMethods.GET, None))

          override def onPush(): Unit = {
            parser.parseBytes(grab(in)) match {
              case NeedMoreData                                                        => pull(in)
              case ResponseStart(status, protocol, attributes, headers, entity, close) =>
                val response = new HttpResponse(status, headers, attributes, HttpEntity.Empty, protocol)
                val validated =
                  Handshake.Client.validateResponse(response, subprotocols, key, offeredExtensions).flatMap {
                    negotiated =>
                      managedCompression match {
                        case Some(managed) =>
                          PerMessageDeflate.negotiateClient(response.headers, managed.settings)
                            .map(pmd => (negotiated.subprotocol, pmd))
                        case None => Right((negotiated.subprotocol, None))
                      }
                  }
                validated match {
                  case Right((protocol, pmd)) =>
                    if (openingDeadline.isDefined) cancelTimer(OpeningDeadline)
                    managedCompression.foreach(_.selected.success(pmd))
                    result.success(ValidUpgrade(response, protocol))

                    setHandler(in,
                      new InHandler {
                        override def onPush(): Unit = push(out, grab(in))
                      })
                    valve.open()

                    val parseResult = parser.onPull()
                    require(parseResult == ParserOutput.MessageEnd,
                      s"parseResult should be MessageEnd but was $parseResult")
                    parser.onPull() match {
                      case NeedMoreData          => if (isAvailable(out)) tryPull(in)
                      case RemainingBytes(bytes) => emit(out, bytes)
                      case other                 =>
                        throw new IllegalStateException(s"unexpected element of type ${other.getClass}")
                    }
                  case Left(problem) =>
                    result.success(InvalidUpgradeResponse(response, s"WebSocket server at $uri returned $problem"))
                    failStage(new IllegalArgumentException(s"WebSocket upgrade did not finish because of '$problem'"))
                }
              case MessageStartError(statusCode, errorInfo, _) =>
                throw new IllegalStateException(s"Message failed with status code $statusCode; Error info: $errorInfo")
              case other =>
                throw new IllegalStateException(s"unexpected element of type ${other.getClass}")
            }
          }

          override def onPull(): Unit = tryPull(in)

          setHandlers(in, out, this)

          override def onDownstreamFinish(cause: Throwable): Unit = {
            if (openingDeadline.isDefined && !result.isCompleted)
              result.tryFailure(new RuntimeException("WebSocket opening handshake was cancelled before completion.",
                cause))
            super.onDownstreamFinish(cause)
          }

          override def postStop(): Unit = {
            managedCompression.foreach(_.selected.trySuccess(None))
            // TimerGraphStageLogic cancels the deadline on stop, so an unexpected terminal path must settle the result.
            if (openingDeadline.isDefined && !result.isCompleted)
              result.tryFailure(new RuntimeException("WebSocket opening handshake stopped before completion."))
            super.postStop()
          }

          override def onUpstreamFinish(): Unit = {
            result.tryFailure(new RuntimeException("Connection closed before WebSocket upgrade completed."))
            super.onUpstreamFinish()
          }

          override def onUpstreamFailure(ex: Throwable): Unit = {
            result.tryFailure(new RuntimeException("Connection failed.", ex))
            super.onUpstreamFailure(ex)
          }
        }

      override def toString = "UpgradeStage"
    }

    BidiFlow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val networkIn = b.add(Flow[ByteString].via(new UpgradeStage))
      val wsIn = b.add(Flow[ByteString])

      val handshakeRequestSource = b.add(Source.single(renderedInitialRequest) ++ valve.source)
      val httpRequestBytesAndThenWSBytes = b.add(Concat[ByteString]())

      handshakeRequestSource ~> httpRequestBytesAndThenWSBytes
      wsIn.outlet            ~> httpRequestBytesAndThenWSBytes

      val networkOut = openingDeadline match {
        case Some((_, dispatched)) =>
          // Observe dispatch from the WebSocket layer, not TCP write completion or TLS negotiation.
          var first = true
          val dispatch = b.add(Flow[ByteString].map { bytes =>
            if (first) {
              first = false
              dispatched.success(())
            }
            bytes
          })
          httpRequestBytesAndThenWSBytes.out ~> dispatch.in
          dispatch.out
        case None => httpRequestBytesAndThenWSBytes.out
      }

      BidiShape(networkIn.in, networkIn.out, wsIn.in, networkOut)
    }).mapMaterializedValue(_ => result.future)
  }

  def simpleTls: BidiFlow[SslTlsInbound, ByteString, ByteString, SendBytes, NotUsed] =
    BidiFlow.fromFlowsMat(
      Flow[SslTlsInbound].collect { case SessionBytes(_, bytes) => bytes },
      Flow[ByteString].map(SendBytes(_)))(Keep.none)
}
