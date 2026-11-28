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

package org.apache.pekko.http.scaladsl.coding

import scala.concurrent.Future

import org.apache.pekko
import pekko.NotUsed
import pekko.annotation.InternalApi
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.headers.{ `Content-Encoding`, HttpEncoding, HttpEncodings }
import pekko.stream.{ FlowShape, Materializer }
import pekko.stream.scaladsl.{ Flow, Sink, Source }
import pekko.stream.stage.GraphStage
import pekko.util.ByteString

trait Decoder {
  def encoding: HttpEncoding

  /**
   * Decodes a message explicitly with this decoder. For responses, built-in decoders remove only a
   * matching outermost content coding; no match leaves the response unchanged.
   */
  def decodeMessage(message: HttpMessage): message.Self =
    Decoder.decodeLegacyMessage(this, message)

  def decodeData[T](t: T)(implicit mapper: DataMapper[T]): T = mapper.transformDataBytes(t, decoderFlow)

  def maxBytesPerChunk: Int
  def withMaxBytesPerChunk(maxBytesPerChunk: Int): Decoder

  def decoderFlow: Flow[ByteString, ByteString, NotUsed]
  def decode(input: ByteString)(implicit mat: Materializer): Future[ByteString] =
    Source.single(input).via(decoderFlow).runWith(Sink.fold(ByteString.empty)(_ ++ _))
}
object Decoder {
  val MaxBytesPerChunkDefault: Int = 65536

  private def decodeLegacyMessage(decoder: Decoder, message: HttpMessage): message.Self =
    if (message.headers.exists(Encoder.isContentEncodingHeader))
      message
        .transformEntityDataBytes(decoder.decoderFlow)
        .withHeaders(message.headers.filterNot(Encoder.isContentEncodingHeader))
    else message.self

  private[coding] def decodeBuiltInMessage(decoder: Decoder, message: HttpMessage): message.Self =
    message match {
      case _: HttpResponse =>
        val headers = message.headers
        // List-valued fields preserve their order across field lines (RFC 9110 sections 5.3 and 8.4).
        val index = headers.lastIndexWhere(Encoder.isContentEncodingHeader)
        if (index < 0) message.self
        else {
          val encodings: Seq[HttpEncoding] = headers(index) match {
            case ce: `Content-Encoding` => ce.encodings
            case raw                    => `Content-Encoding`.parseFromValueString(raw.value).fold(_ => Nil, _.encodings)
          }
          val expected = decoder.encoding
          if (encodings.lastOption.exists { last =>
              last.value.equalsIgnoreCase(expected.value) ||
              (expected == HttpEncodings.gzip && last.value.equalsIgnoreCase("x-gzip"))
            }) {
            val remaining = encodings.dropRight(1)
            val replacement = if (remaining.isEmpty) Nil else List(`Content-Encoding`(remaining))
            message
              .transformEntityDataBytes(decoder.decoderFlow)
              .withHeaders(headers.patch(index, replacement, 1))
          } else message.self
        }
      case _ => decodeLegacyMessage(decoder, message)
    }
}

/** INTERNAL API: opts built-in coders into removing only their response's outermost matching coding. */
@InternalApi
private[coding] trait BuiltInResponseDecoder extends Decoder {
  override def decodeMessage(message: HttpMessage): message.Self =
    Decoder.decodeBuiltInMessage(this, message)
}

/**
 * Internal API
 *
 * A decoder that is implemented in terms of a `Stage`
 */
@InternalApi
@deprecated("StreamDecoder is internal API and will be moved or removed in the future", since = "Akka HTTP 10.2.0")
trait StreamDecoder extends Decoder { outer =>
  protected def newDecompressorStage(maxBytesPerChunk: Int): () => GraphStage[FlowShape[ByteString, ByteString]]

  def maxBytesPerChunk: Int = Decoder.MaxBytesPerChunkDefault
  def withMaxBytesPerChunk(newMaxBytesPerChunk: Int): Decoder = {
    class Configured extends StreamDecoder {
      def encoding: HttpEncoding = outer.encoding
      override def maxBytesPerChunk: Int = newMaxBytesPerChunk

      def newDecompressorStage(maxBytesPerChunk: Int): () => GraphStage[FlowShape[ByteString, ByteString]] =
        outer.newDecompressorStage(maxBytesPerChunk)
    }

    if (outer.isInstanceOf[BuiltInResponseDecoder]) new Configured with BuiltInResponseDecoder
    else new Configured
  }

  def decoderFlow: Flow[ByteString, ByteString, NotUsed] =
    Flow.fromGraph(newDecompressorStage(maxBytesPerChunk)())

}
