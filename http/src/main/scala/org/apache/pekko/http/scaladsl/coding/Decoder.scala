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

import headers.HttpEncoding

import org.apache.pekko
import pekko.NotUsed
import pekko.annotation.InternalApi
import pekko.http.scaladsl.model._
import pekko.stream.{ FlowShape, Materializer }
import pekko.stream.scaladsl.{ Flow, Sink, Source }
import pekko.stream.stage.GraphStage
import pekko.util.ByteString

trait Decoder {
  def encoding: HttpEncoding

  def decodeMessage(message: HttpMessage): message.Self =
    if (message.headers.exists(Encoder.isContentEncodingHeader))
      message
        .transformEntityDataBytes(decoderFlow)
        .withHeaders(message.headers.filterNot(Encoder.isContentEncodingHeader))
    else message.self

  def decodeData[T](t: T)(implicit mapper: DataMapper[T]): T = mapper.transformDataBytes(t, decoderFlow)

  def maxBytesPerChunk: Int
  def withMaxBytesPerChunk(maxBytesPerChunk: Int): Decoder

  def decoderFlow: Flow[ByteString, ByteString, NotUsed]
  def decode(input: ByteString)(implicit mat: Materializer): Future[ByteString] =
    Source.single(input).via(decoderFlow).runWith(Sink.fold(ByteString.empty)(_ ++ _))
}
object Decoder {
  val MaxBytesPerChunkDefault: Int = 65536
}

/**
 * Internal API
 *
 * A decoder that is implemented in terms of a [[Stage]]
 */
@InternalApi
@deprecated("StreamDecoder is internal API and will be moved or removed in the future", since = "Akka HTTP 10.2.0")
trait StreamDecoder extends Decoder { outer =>
  protected def newDecompressorStage(maxBytesPerChunk: Int): () => GraphStage[FlowShape[ByteString, ByteString]]

  def maxBytesPerChunk: Int = Decoder.MaxBytesPerChunkDefault
  def withMaxBytesPerChunk(newMaxBytesPerChunk: Int): Decoder =
    new StreamDecoder {
      def encoding: HttpEncoding = outer.encoding
      override def maxBytesPerChunk: Int = newMaxBytesPerChunk

      def newDecompressorStage(maxBytesPerChunk: Int): () => GraphStage[FlowShape[ByteString, ByteString]] =
        outer.newDecompressorStage(maxBytesPerChunk)
    }

  def decoderFlow: Flow[ByteString, ByteString, NotUsed] =
    Flow.fromGraph(newDecompressorStage(maxBytesPerChunk)())

}
