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

import org.apache.pekko
import pekko.NotUsed
import pekko.annotation.InternalApi
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.headers.{ HttpEncoding, HttpEncodings }
import pekko.stream.scaladsl.{ Compression, Flow }
import pekko.util.ByteString

/**
 * INTERNAL API
 *
 * An encoder and decoder for the HTTP 'zstd' encoding (RFC 8878), use [[Coders.Zstd]] to get one.
 *
 * zstd is implemented by pekko-stream, which needs the optional `com.github.luben:zstd-jni` dependency
 * on the classpath.
 */
@InternalApi
private[http] final class Zstd private[http] (
    compressionLevel: Int,
    val messageFilter: HttpMessage => Boolean,
    override val maxBytesPerChunk: Int) extends Coder {
  require(
    compressionLevel >= Compression.ZstdMinCompressionLevel && compressionLevel <= Compression.ZstdMaxCompressionLevel,
    s"Compression level needs to be between ${Compression.ZstdMinCompressionLevel} and " +
    s"${Compression.ZstdMaxCompressionLevel}, was $compressionLevel")
  require(maxBytesPerChunk > 0, s"maxBytesPerChunk must be greater than 0, was $maxBytesPerChunk")

  val encoding: HttpEncoding = HttpEncodings.zstd

  @nowarn("msg=deprecated")
  private[http] def newCompressor: Compressor = new ZstdCompressor(compressionLevel)

  def withMaxBytesPerChunk(newMaxBytesPerChunk: Int): Decoder =
    new Zstd(compressionLevel, messageFilter, newMaxBytesPerChunk)

  def decoderFlow: Flow[ByteString, ByteString, NotUsed] = Compression.zstdDecompress(maxBytesPerChunk)

  private[http] def withLevel(level: Int): Zstd = new Zstd(level, messageFilter, maxBytesPerChunk)
}

/** INTERNAL API */
@InternalApi
private[http] object Zstd {
  val DefaultCompressionLevel: Int = Compression.ZstdDefaultCompressionLevel

  val Default: Zstd = new Zstd(DefaultCompressionLevel, Encoder.DefaultFilter, Decoder.MaxBytesPerChunkDefault)
}

/**
 * INTERNAL API
 *
 * Adapts the zstd compressor of pekko-stream. It is created lazily, as it holds a native context that is
 * only released by `cleanup`, and an encoder flow that is never materialized is never cleaned up.
 */
@InternalApi
@nowarn("msg=deprecated")
private[coding] class ZstdCompressor(compressionLevel: Int) extends Compressor {
  private var underlyingCreated = false
  private lazy val underlying = {
    val compressor = new pekko.stream.impl.io.compression.ZstdCompressor(compressionLevel)
    underlyingCreated = true
    compressor
  }
  private var closed = false

  override def compress(input: ByteString): ByteString = underlying.compress(input)
  override def flush(): ByteString = underlying.flush()
  override def finish(): ByteString = underlying.finish()
  override def compressAndFlush(input: ByteString): ByteString = underlying.compressAndFlush(input)
  override def compressAndFinish(input: ByteString): ByteString = underlying.compressAndFinish(input)

  private[coding] override def cleanup(): Unit =
    if (underlyingCreated && !closed) {
      closed = true
      underlying.close()
    }
}
