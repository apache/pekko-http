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
import pekko.http.scaladsl.model.{ ErrorInfo, ParsingException }
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
 *
 * @param maxHeaderListSize the maximum size of a decoded header list. The same limit is applied to the accumulated
 *                          header block fragments of a HEADERS frame and its CONTINUATION frames, so that the memory
 *                          used for a header block that the peer never completes stays bounded
 *                          (see RFC 9113, section 10.5).
 */
@InternalApi
private[http2] final class HeaderDecompression(masterHeaderParser: HttpHeaderParser, parserSettings: ParserSettings,
    maxHeaderListSize: Int)
    extends GraphStage[FlowShape[FrameEvent, FrameEvent]] {
  val UTF8 = StandardCharsets.UTF_8
  val US_ASCII = StandardCharsets.US_ASCII

  // Each fragment is accounted with the size of its frame header on top of its payload size. Without that, empty
  // CONTINUATION frames would never add to the accumulated header block and their number would be unbounded.
  private val FrameHeaderSize = 9

  val eventsIn = Inlet[FrameEvent]("HeaderDecompression.eventsIn")
  val eventsOut = Outlet[FrameEvent]("HeaderDecompression.eventsOut")

  val shape = FlowShape(eventsIn, eventsOut)

  def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new HandleOrPassOnStage[FrameEvent, FrameEvent](shape) {
      val httpHeaderParser = masterHeaderParser.createShallowCopy()
      val decoder =
        new pekko.http.shaded.com.twitter.hpack.Decoder(maxHeaderListSize, Http2Protocol.InitialMaxHeaderTableSize)

      become(Idle)

      // simple state machine
      // Idle: no ongoing HEADERS parsing
      // Receiving headers: waiting for CONTINUATION frame

      def parseAndEmit(
          streamId: Int, endStream: Boolean, payload: ByteString, prioInfo: Option[PriorityFrame]): Unit = {
        val headers = new VectorBuilder[(String, AnyRef)]
        // A header that fails to parse must not unwind out of the decoder. Decoding has to run to the end of
        // the block so that the HPACK dynamic table keeps tracking the peer's - insertHeader calls this
        // listener before adding to the table, and the representations after this one would not be read at
        // all - and so that endHeaderBlock resets the state machine. Both would otherwise stay wrong for
        // every later HEADERS frame on the connection. Remember the first failure and report it afterwards.
        var parsingError: Option[ErrorInfo] = None
        object Receiver extends HeaderListener {
          def addHeader(name: String, value: String, parsed: AnyRef, sensitive: Boolean): AnyRef = try {
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
                case "content-type" => handle(ContentType.parse(name, value, parserSettings))
                case ":authority"   => handle(Authority.parse(name, value, parserSettings))
                case ":path"        =>
                  if (value.isEmpty)
                    throw new Http2ProtocolException("Malformed request: ':path' must not be empty")
                  handle(PathAndQuery.parse(name, value, parserSettings))
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
          } catch {
            case ex: ParsingException =>
              if (parsingError.isEmpty) parsingError = Some(ex.info)
              // nothing usable to cache against the table entry, so the value is parsed again if it is
              // referenced again - and fails again, consistently
              null
          }
        }
        // no compact() needed: the decoder only reads forward, so the SequenceInputStream that a
        // multi-fragment ByteString hands out is enough and the header block is not copied
        val stream = payload.asInputStream
        try {
          decoder.decode(stream, Receiver)
          // the decoder stops emitting headers as soon as the limit is exceeded and reports that here
          val truncated = decoder.endHeaderBlock()

          if (truncated) headerListSizeExceeded(streamId)
          else
            parsingError match {
              // push details further and let RequestErrorFlow handle responding with bad request
              case Some(info) =>
                push(eventsOut, ParsedHeadersFrame(streamId, endStream, Seq.empty, prioInfo, Some(info)))
              case None =>
                push(eventsOut, ParsedHeadersFrame(streamId, endStream, headers.result(), prioInfo, None))
            }
        } catch {
          case ex: ParsingException =>
            // not expected any more now that the listener catches them, kept so that one thrown from
            // somewhere else still answers with a bad request rather than tearing down the connection
            push(eventsOut, ParsedHeadersFrame(streamId, endStream, Seq.empty, prioInfo, Some(ex.info)))
          case _: IOException =>
            // this is signalled by the decoder when it failed, we want to react to this by rendering a GOAWAY frame
            fail(eventsOut,
              new Http2Compliance.Http2ProtocolException(ErrorCode.COMPRESSION_ERROR, "Decompression failed."))
        } finally {
          // endHeaderBlock is what resets the decoder for the next block, so it has to run even when decode
          // unwound anyway - a malformed pseudo header raises an Http2ProtocolException, for one. Running it
          // a second time after the call above is a no-op.
          decoder.endHeaderBlock()
          stream.close()
        }
      }

      object Idle extends State {
        val handleEvent: PartialFunction[FrameEvent, Unit] = {
          case HeadersFrame(streamId, endStream, endHeaders, fragment, prioInfo) =>
            if (endHeaders) parseAndEmit(streamId, endStream, fragment, prioInfo)
            else if (exceedsMaxHeaderListSize(0, fragment)) headerListSizeExceeded(streamId)
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
        // includes the frame headers of the fragments received so far, see `FrameHeaderSize`
        var accountedSize: Long = FrameHeaderSize + initiallyReceivedData.size

        val handleEvent: PartialFunction[FrameEvent, Unit] = {
          case ContinuationFrame(`streamId`, endHeaders, payload) =>
            if (exceedsMaxHeaderListSize(accountedSize, payload))
              // Neither the HPACK decoder nor any of the checks further down the line run before the header block
              // is complete, so this is the only place where the size of an unfinished header block is bounded.
              headerListSizeExceeded(streamId)
            else if (endHeaders) {
              parseAndEmit(streamId, endStream, receivedData ++ payload, priorityInfo)
              become(Idle)
            } else {
              receivedData ++= payload
              accountedSize += FrameHeaderSize + payload.size
              pull(eventsIn)
            }
          case x =>
            protocolError(s"While waiting for CONTINUATION frame on stream $streamId received unexpected frame $x")
        }
      }

      def exceedsMaxHeaderListSize(accountedSize: Long, payload: ByteString): Boolean =
        accountedSize + FrameHeaderSize + payload.size > maxHeaderListSize

      def headerListSizeExceeded(streamId: Int): Unit =
        fail(eventsOut,
          new Http2ProtocolException(
            ErrorCode.ENHANCE_YOUR_CALM,
            s"Header block of stream $streamId exceeded the configured max-header-list-size of $maxHeaderListSize bytes"))

      def protocolError(msg: String): Unit = failStage(new Http2ProtocolException(msg))
    }
}
