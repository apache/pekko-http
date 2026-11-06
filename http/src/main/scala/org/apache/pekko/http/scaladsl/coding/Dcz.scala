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

import java.security.MessageDigest
import java.util.zip.DataFormatException

import scala.annotation.nowarn

import org.apache.pekko
import pekko.NotUsed
import pekko.annotation.InternalApi
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.headers.{ HttpEncoding, HttpEncodings }
import pekko.stream.{ Attributes, ZstdDecompressionSettings }
import pekko.stream.impl.fusing.GraphStages.SimpleLinearGraphStage
import pekko.stream.impl.io.compression.ZstdDictionaryImpl
import pekko.stream.scaladsl.{ Compression, Flow }
import pekko.stream.scaladsl.compression.ZstdDictionary
import pekko.stream.stage.{ GraphStageLogic, InHandler, OutHandler }
import pekko.util.ByteString

/**
 * A coder for the 'dcz' encoding, which compresses with a dictionary that both sides have. Use [[Coders.Dcz]] to get one.
 *
 * Creating one hashes the dictionary, so create one per dictionary and reuse it.
 *
 * @since 2.0.0
 */
sealed trait DictionaryCoder extends Coder {

  /** The SHA-256 hash of the dictionary, which clients send in the `Available-Dictionary` header. */
  def dictionaryHash: ByteString
}

/**
 * INTERNAL API
 *
 * An encoder and decoder for the HTTP 'dcz' encoding (RFC 9842, section 5): a 40 byte header with the hash of the
 * dictionary, followed by a zstd frame that was compressed with the dictionary.
 */
@InternalApi
private[http] final class Dcz private[coding] (
    dictionary: DczDictionary,
    compressionLevel: Int,
    val messageFilter: HttpMessage => Boolean,
    override val maxBytesPerChunk: Int) extends DictionaryCoder {
  require(
    compressionLevel >= Compression.ZstdMinCompressionLevel && compressionLevel <= Compression.ZstdMaxCompressionLevel,
    s"Compression level needs to be between ${Compression.ZstdMinCompressionLevel} and " +
    s"${Compression.ZstdMaxCompressionLevel}, was $compressionLevel")
  private val decompressionSettings =
    ZstdDecompressionSettings()
      .withMaxBytesPerChunk(maxBytesPerChunk)
      .withWindowLogMax(dictionary.decoderWindowLogMax)
      .withDictionary(dictionary.zstdDictionary)

  val encoding: HttpEncoding = HttpEncodings.dcz

  def dictionaryHash: ByteString = dictionary.hash

  @nowarn("msg=deprecated")
  private[http] def newCompressor: Compressor = new DczCompressor(dictionary, compressionLevel)

  def withMaxBytesPerChunk(newMaxBytesPerChunk: Int): Decoder =
    new Dcz(dictionary, compressionLevel, messageFilter, newMaxBytesPerChunk)

  def decoderFlow: Flow[ByteString, ByteString, NotUsed] =
    Flow.fromGraph(new DczHeaderCheck(dictionary.header)).via(Compression.zstdDecompress(decompressionSettings))
}

/** INTERNAL API */
@InternalApi
private[http] object Dcz {
  private[coding] val HashLength = 32

  // a zstd skippable frame with a size of 32 bytes, so a zstd decoder that has the dictionary ignores the header
  private[coding] val HeaderPrefix = ByteString(0x5E, 0x2A, 0x4D, 0x18, 0x20, 0x00, 0x00, 0x00)

  private[coding] val HeaderLength = HeaderPrefix.length + HashLength

  // the little-endian magic number of a dictionary in the zstd dictionary format
  private[coding] val ZstdDictionaryMagic = ByteString(0x37, 0xA4, 0x30, 0xEC)

  private val MinWindow = 8L * 1024 * 1024
  private val MaxWindow = 128L * 1024 * 1024

  // 2 MiB, the window of the default compression level for a large input without a dictionary
  private val BaseEncoderWindowLog = 21

  private def floorLog2(value: Long): Int = 63 - java.lang.Long.numberOfLeadingZeros(value)
  private def ceilLog2(value: Long): Int = 64 - java.lang.Long.numberOfLeadingZeros(value - 1)

  private def enlargedDictionarySize(dictionarySize: Long): Long = dictionarySize + dictionarySize / 4

  // RFC 9842, section 5: decoders must support a window of 8 MB or 1.25 times the dictionary, up to 128 MB
  private def windowLimit(dictionarySize: Long): Long =
    math.min(math.max(MinWindow, enlargedDictionarySize(dictionarySize)), MaxWindow)

  /** The smallest window log that accepts every window up to the limit of RFC 9842. */
  private[coding] def decoderWindowLogMax(dictionarySize: Long): Int = ceilLog2(windowLimit(dictionarySize))

  /**
   * The largest power of 2 within the limit of RFC 9842 is used if needed to reach all of the dictionary and 25% of
   * growth, as content can only refer to the dictionary while they are in the window together. The window is always
   * set, as libzstd would otherwise pick a window for the size of the dictionary, without knowing the size of the input.
   */
  private[coding] def encoderWindowLog(dictionarySize: Long): Int =
    math.min(
      math.max(BaseEncoderWindowLog, ceilLog2(enlargedDictionarySize(dictionarySize))),
      floorLog2(windowLimit(dictionarySize)))

  private[coding] def apply(
      dictionary: ByteString,
      messageFilter: HttpMessage => Boolean,
      compressionLevel: Int): Dcz =
    new Dcz(new DczDictionary(dictionary), compressionLevel, messageFilter, Decoder.MaxBytesPerChunkDefault)
}

/** INTERNAL API: what is derived from a dictionary, shared by the coders of that dictionary as it is costly to create */
@InternalApi
private[coding] final class DczDictionary(bytes: ByteString) {
  require(bytes.nonEmpty, "The dictionary must not be empty")
  // zstd-jni cannot force libzstd to load a dictionary as raw content, which RFC 9842 requires, and libzstd would
  // parse this as a formatted dictionary, so the output would not be decodable by other implementations
  require(
    bytes.take(Dcz.ZstdDictionaryMagic.length) != Dcz.ZstdDictionaryMagic,
    "A dictionary that starts with the zstd dictionary magic number is not supported")

  val zstdDictionary: ZstdDictionary = ZstdDictionary(bytes)
  val zstdDictionaryImpl: ZstdDictionaryImpl = zstdDictionary.toImpl

  val hash: ByteString = {
    val digest = MessageDigest.getInstance("SHA-256")
    bytes.asByteBuffers.foreach(digest.update)
    ByteString.fromArrayUnsafe(digest.digest())
  }

  val header: ByteString = (Dcz.HeaderPrefix ++ hash).compact

  val encoderWindowLog: Int = Dcz.encoderWindowLog(bytes.length)
  val decoderWindowLogMax: Int = Dcz.decoderWindowLogMax(bytes.length)
}

/** INTERNAL API */
@InternalApi
private[coding] final class DczCompressor(dictionary: DczDictionary, compressionLevel: Int)
    extends ZstdCompressor(compressionLevel, dictionary.encoderWindowLog, Some(dictionary.zstdDictionaryImpl)) {
  private var headerPending = true

  private def withHeader(output: ByteString): ByteString =
    if (headerPending) {
      headerPending = false
      dictionary.header ++ output
    } else output

  override def compress(input: ByteString): ByteString = withHeader(super.compress(input))
  override def flush(): ByteString = withHeader(super.flush())
  override def finish(): ByteString = withHeader(super.finish())
  override def compressAndFlush(input: ByteString): ByteString = withHeader(super.compressAndFlush(input))
  override def compressAndFinish(input: ByteString): ByteString = withHeader(super.compressAndFinish(input))
}

/**
 * INTERNAL API
 *
 * Checks and removes the header. Input without any bytes is passed on, as it is for the other coders, because a message
 * that has no entity like a response to a HEAD request can have a `Content-Encoding` header.
 */
@InternalApi
private[coding] final class DczHeaderCheck(header: ByteString) extends SimpleLinearGraphStage[ByteString] {
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      private var checked = false
      private var buffered = ByteString.empty

      override def onPush(): Unit =
        if (checked) push(out, grab(in))
        else {
          buffered ++= grab(in)
          if (buffered.length < header.length) pull(in)
          else if (buffered.take(Dcz.HeaderPrefix.length) != Dcz.HeaderPrefix)
            failStage(new DataFormatException("The input does not start with the 'dcz' header"))
          else if (buffered.take(header.length) != header)
            failStage(new DataFormatException("The input was compressed with a different dictionary"))
          else {
            val remaining = buffered.drop(header.length)
            checked = true
            buffered = ByteString.empty
            if (remaining.nonEmpty) push(out, remaining) else pull(in)
          }
        }

      override def onPull(): Unit = pull(in)

      override def onUpstreamFinish(): Unit =
        if (buffered.isEmpty) completeStage()
        else failStage(new DataFormatException("Truncated 'dcz' header"))

      setHandlers(in, out, this)
    }
}
