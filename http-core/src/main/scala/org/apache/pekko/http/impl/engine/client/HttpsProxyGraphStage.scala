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

package org.apache.pekko.http.impl.engine.client

import org.apache.pekko
import pekko.NotUsed
import pekko.annotation.InternalApi
import pekko.http.impl.engine.parsing.HttpMessageParser.StateResult
import pekko.http.impl.engine.parsing.ParserOutput.{ NeedMoreData, RemainingBytes, ResponseStart }
import pekko.http.impl.engine.parsing.{ HttpHeaderParser, HttpResponseParser, ParserOutput }
import pekko.http.impl.util.ByteStringRendering
import pekko.http.impl.util.Rendering.CrLf
import pekko.http.scaladsl.model.headers.{ `Proxy-Authorization`, HttpCredentials }
import pekko.http.scaladsl.model.{ HttpMethods, StatusCodes }
import pekko.http.scaladsl.settings.ClientConnectionSettings
import pekko.stream.scaladsl.BidiFlow
import pekko.stream.stage._
import pekko.stream.{ Attributes, BidiShape, Inlet, Outlet }
import pekko.util.ByteString

/** INTERNAL API */
@InternalApi
private[http] object HttpsProxyGraphStage {
  sealed trait State
  // Entry state
  case object Starting extends State

  // State after CONNECT messages has been sent to Proxy and before Proxy responded back
  case object Connecting extends State

  // State after Proxy responded  back
  case object Connected extends State

  def apply(targetHostName: String, targetPort: Int, settings: ClientConnectionSettings,
      proxyAuth: Option[HttpCredentials]): BidiFlow[ByteString, ByteString, ByteString, ByteString, NotUsed] =
    BidiFlow.fromGraph(new HttpsProxyGraphStage(targetHostName, targetPort, settings, proxyAuth))
}

/** INTERNAL API */
@InternalApi
private final class HttpsProxyGraphStage(
    targetHostName: String, targetPort: Int,
    settings: ClientConnectionSettings,
    proxyAuthorization: Option[HttpCredentials])
    extends GraphStage[BidiShape[ByteString, ByteString, ByteString, ByteString]] {

  import HttpsProxyGraphStage._

  val bytesIn: Inlet[ByteString] = Inlet("OutgoingTCP.in")
  val bytesOut: Outlet[ByteString] = Outlet("OutgoingTCP.out")

  val sslIn: Inlet[ByteString] = Inlet("OutgoingSSL.in")
  val sslOut: Outlet[ByteString] = Outlet("OutgoingSSL.out")

  override def shape: BidiShape[ByteString, ByteString, ByteString, ByteString] =
    BidiShape.apply(sslIn, bytesOut, bytesIn, sslOut)

  private val connectMsg = {
    val r = new ByteStringRendering(256)

    r ~~ "CONNECT " ~~ targetHostName ~~ ':' ~~ targetPort ~~ " HTTP/1.1" ~~ CrLf
    r ~~ "Host: " ~~ targetHostName ~~ CrLf
    proxyAuthorization.foreach { creds =>
      r ~~ `Proxy-Authorization`(creds)
    }
    r ~~ CrLf
    r.get
  }

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with StageLogging {
      private var state: State = Starting

      lazy val parser = {
        val p = new HttpResponseParser(settings.parserSettings, HttpHeaderParser(settings.parserSettings, log)) {
          override def handleInformationalResponses = false

          override protected def parseMessage(input: ByteString, offset: Int): StateResult = {
            // hacky, we want in the first branch *all fragments* of the first response
            if (offset == 0) {
              super.parseMessage(input, offset)
            } else {
              if (input.size > offset) {
                emit(RemainingBytes(input.drop(offset)))
              } else {
                emit(NeedMoreData)
              }
              terminate()
            }
          }
        }
        p.setContextForNextResponse(HttpResponseParser.ResponseContext(HttpMethods.CONNECT, None))
        p
      }

      setHandler(sslIn,
        new InHandler {
          override def onPush() = {
            state match {
              case Starting =>
                throw new IllegalStateException("inlet OutgoingSSL.in unexpectedly pushed in Starting state")
              case Connecting =>
                throw new IllegalStateException("inlet OutgoingSSL.in unexpectedly pushed in Connecting state")
              case Connected =>
                push(bytesOut, grab(sslIn))
            }
          }

          override def onUpstreamFinish(): Unit = {
            complete(bytesOut)
          }

        })

      setHandler(bytesIn,
        new InHandler {
          override def onPush() = {
            state match {
              case Starting =>
              // that means that proxy had sent us something even before CONNECT to proxy was sent, therefore we just ignore it
              case Connecting =>
                val proxyResponse = grab(bytesIn)
                parser.parseBytes(proxyResponse) match {
                  case NeedMoreData =>
                    pull(bytesIn)
                  case ResponseStart(_: StatusCodes.Success, _, _, _, _, _) =>
                    var pushed = false
                    val parseResult = parser.onPull()
                    require(parseResult == ParserOutput.MessageEnd,
                      s"parseResult should be MessageEnd but was $parseResult")
                    parser.onPull() match {
                      // NeedMoreData is what we emit in overridden `parseMessage` in case input.size == offset
                      case NeedMoreData =>
                      case RemainingBytes(bytes) =>
                        push(sslOut, bytes) // parser already read more than expected, forward that data directly
                        pushed = true
                      case other =>
                        throw new IllegalStateException(s"unexpected element of type ${other.getClass}")
                    }
                    parser.onUpstreamFinish()

                    log.debug(s"HTTP(S) proxy connection to {}:{} established. Now forwarding data.", targetHostName,
                      targetPort)

                    state = Connected
                    if (isAvailable(bytesOut)) pull(sslIn)
                    if (isAvailable(sslOut)) pull(bytesIn)
                  case ResponseStart(statusCode, _, _, _, _, _) =>
                    failStage(new ProxyConnectionFailedException(
                      s"The HTTP(S) proxy rejected to open a connection to $targetHostName:$targetPort with status code: $statusCode"))
                  case other =>
                    throw new IllegalStateException(s"unexpected element of type $other")
                }

              case Connected =>
                push(sslOut, grab(bytesIn))
            }
          }

          override def onUpstreamFinish(): Unit = complete(sslOut)

        })

      setHandler(bytesOut,
        new OutHandler {
          override def onPull() = {
            state match {
              case Starting =>
                log.debug(
                  s"TCP connection to HTTP(S) proxy connection established. Sending CONNECT {}:{} to HTTP(S) proxy",
                  targetHostName, targetPort)
                push(bytesOut, connectMsg)
                state = Connecting
              case Connecting =>
              // don't need to do anything
              case Connected =>
                pull(sslIn)
            }
          }

          override def onDownstreamFinish(cause: Throwable): Unit = cancel(sslIn)

        })

      setHandler(sslOut,
        new OutHandler {
          override def onPull() = {
            pull(bytesIn)
          }

          override def onDownstreamFinish(cause: Throwable): Unit = cancel(bytesIn)

        })

    }

}

final case class ProxyConnectionFailedException(msg: String) extends RuntimeException(msg)
