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

package org.apache.pekko.http.scaladsl.coding

import scala.annotation.nowarn
import scala.concurrent.Future

import org.apache.pekko
import pekko.NotUsed
import pekko.http.scaladsl.model.HttpMessage
import pekko.http.scaladsl.model.headers.{ `Content-Encoding`, HttpEncoding }
import pekko.stream.Materializer
import pekko.stream.scaladsl.{ Flow, Sink, Source }
import pekko.util.ByteString

private[http] final class FlowCoder(
    val encoding: HttpEncoding,
    val messageFilter: HttpMessage => Boolean,
    encodingFlow: Flow[ByteString, ByteString, ?],
    decodeWithChunkSize: Int => Flow[ByteString, ByteString, ?],
    val maxBytesPerChunk: Int) extends Coder {
  require(maxBytesPerChunk > 0, "maxBytesPerChunk must be positive")

  def matchesEncoding(actual: HttpEncoding): Boolean =
    encoding.value.equalsIgnoreCase(actual.value)

  def matchesSingleContentEncoding(message: HttpMessage): Boolean =
    Coders.singleContentEncodingHeader(message) match {
      case Some(`Content-Encoding`(Seq(actual))) => matchesEncoding(actual)
      case Some(_: `Content-Encoding`)           => false
      case Some(header)                          =>
        `Content-Encoding`.parseFromValueString(header.value) match {
          case Right(`Content-Encoding`(Seq(actual))) => matchesEncoding(actual)
          case _                                      => false
        }
      case _ => false
    }

  override val encoderFlow: Flow[ByteString, ByteString, NotUsed] =
    encodingFlow.mapMaterializedValue(_ => NotUsed)

  override def encodeMessage(message: HttpMessage): message.Self =
    Encoder.encodeMessageWithFlow(message, encoding, messageFilter)(encoderFlow)

  override def encodeData[T](t: T)(implicit mapper: DataMapper[T]): T =
    mapper.transformDataBytes(t, encoderFlow)

  override def encodeAsync(input: ByteString)(implicit mat: Materializer): Future[ByteString] =
    Source.single(input).via(encoderFlow).runWith(Sink.fold(ByteString.empty)(_ ++ _))

  override def decodeMessage(message: HttpMessage): message.Self =
    if (matchesSingleContentEncoding(message)) super.decodeMessage(message)
    else message.self

  override def decoderFlow: Flow[ByteString, ByteString, NotUsed] =
    Flow.fromMaterializer { (_, _) =>
      decodeWithChunkSize(maxBytesPerChunk)
    }.mapMaterializedValue(_ => NotUsed)

  override def withMaxBytesPerChunk(size: Int): Decoder =
    new FlowCoder(encoding, messageFilter, encodingFlow, decodeWithChunkSize, size)

  // All encoding entry points above delegate to the supplied graph, not the legacy synchronous compressor.
  @nowarn("msg=deprecated")
  private[http] override def newCompressor: Compressor =
    throw new UnsupportedOperationException("Flow-based coders do not provide a legacy compressor")
}
