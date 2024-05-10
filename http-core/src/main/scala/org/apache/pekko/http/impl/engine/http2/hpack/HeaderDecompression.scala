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

package org.apache.pekko.http.impl.engine.http2.hpack

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.impl.engine.http2.FrameEvent._
import pekko.http.impl.engine.http2.Http2Compliance.Http2ProtocolException
import pekko.http.impl.engine.http2.Http2Protocol.ErrorCode
import pekko.http.impl.engine.http2.RequestParsing.parseHeaderPair
import pekko.http.impl.engine.http2._
import pekko.http.impl.engine.parsing.HttpHeaderParser
import pekko.http.scaladsl.settings.ParserSettings
import pekko.http.shaded.com.twitter.hpack.HeaderListener
import pekko.stream._
import pekko.stream.stage.{ GraphStage, GraphStageLogic }
import pekko.util.ByteString

import java.io.IOException
import java.nio.charset.StandardCharsets
import scala.collection.immutable.VectorBuilder

/**
 * INTERNAL API
 *
 * Can be used on server and client side.
 */
@InternalApi
private[http2] final class HeaderDecompression(masterHeaderParser: HttpHeaderParser, parserSettings: ParserSettings)
    extends GraphStage[FlowShape[FrameEvent, FrameEvent]] {
  val UTF8 = StandardCharsets.UTF_8
  val US_ASCII = StandardCharsets.US_ASCII

  val eventsIn = Inlet[FrameEvent]("HeaderDecompression.eventsIn")
  val eventsOut = Outlet[FrameEvent]("HeaderDecompression.eventsOut")

  val shape = FlowShape(eventsIn, eventsOut)

  def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new HandleOrPassOnStage[FrameEvent, FrameEvent](shape) {
      val httpHeaderParser = masterHeaderParser.createShallowCopy()
      val decoder = new pekko.http.shaded.com.twitter.hpack.Decoder(Http2Protocol.InitialMaxHeaderListSize,
        Http2Protocol.InitialMaxHeaderTableSize)

      become(Idle)

      // simple state machine
      // Idle: no ongoing HEADERS parsing
      // Receiving headers: waiting for CONTINUATION frame

      def parseAndEmit(
          streamId: Int, endStream: Boolean, payload: ByteString, prioInfo: Option[PriorityFrame]): Unit = {
        val headers = new VectorBuilder[(String, AnyRef)]
        object Receiver extends HeaderListener {
          def addHeader(name: String, value: String, parsed: AnyRef, sensitive: Boolean): AnyRef = {
            if (parsed ne null) {
              headers += name -> parsed
              parsed
            } else {
              import Http2HeaderParsing._
              def handle(parsed: AnyRef): AnyRef = {
                headers += name -> parsed
                parsed
              }

              name match {
                case "content-type"   => handle(ContentType.parse(name, value, parserSettings))
                case ":authority"     => handle(Authority.parse(name, value, parserSettings))
                case ":path"          => handle(PathAndQuery.parse(name, value, parserSettings))
                case ":method"        => handle(Method.parse(name, value, parserSettings))
                case ":scheme"        => handle(Scheme.parse(name, value, parserSettings))
                case "content-length" => handle(ContentLength.parse(name, value, parserSettings))
                case "cookie"         => handle(Cookie.parse(name, value, parserSettings))
                case x if x(0) == ':' => handle(value)
                case _                =>
                  // cannot use OtherHeader.parse because that doesn't has access to header parser
                  val header = parseHeaderPair(httpHeaderParser, name, value)
                  RequestParsing.validateHeader(header)
                  handle(header)
              }
            }
          }
        }
        try {
          decoder.decode(ByteStringInputStream.asByteArrayInputStream(payload), Receiver)
          decoder.endHeaderBlock() // TODO: do we have to check the result here?

          push(eventsOut, ParsedHeadersFrame(streamId, endStream, headers.result(), prioInfo))
        } catch {
          case ex: IOException =>
            // this is signalled by the decoder when it failed, we want to react to this by rendering a GOAWAY frame
            fail(eventsOut,
              new Http2Compliance.Http2ProtocolException(ErrorCode.COMPRESSION_ERROR, "Decompression failed."))
        }
      }

      object Idle extends State {
        val handleEvent: PartialFunction[FrameEvent, Unit] = {
          case HeadersFrame(streamId, endStream, endHeaders, fragment, prioInfo) =>
            if (endHeaders) parseAndEmit(streamId, endStream, fragment, prioInfo)
            else {
              become(new ReceivingHeaders(streamId, endStream, fragment, prioInfo))
              pull(eventsIn)
            }
          case c: ContinuationFrame =>
            protocolError(s"Received unexpected continuation frame: $c")

          // FIXME: handle SETTINGS frames that change decompression parameters
        }
      }
      class ReceivingHeaders(streamId: Int, endStream: Boolean, initiallyReceivedData: ByteString,
          priorityInfo: Option[PriorityFrame]) extends State {
        var receivedData = initiallyReceivedData

        val handleEvent: PartialFunction[FrameEvent, Unit] = {
          case ContinuationFrame(`streamId`, endHeaders, payload) =>
            if (endHeaders) {
              parseAndEmit(streamId, endStream, receivedData ++ payload, priorityInfo)
              become(Idle)
            } else {
              receivedData ++= payload
              pull(eventsIn)
            }
          case x =>
            protocolError(s"While waiting for CONTINUATION frame on stream $streamId received unexpected frame $x")
        }
      }

      def protocolError(msg: String): Unit = failStage(new Http2ProtocolException(msg))
    }
}
