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

import java.io.{ InputStream, OutputStream }
import java.security.MessageDigest
import java.util.zip.DataFormatException

import scala.concurrent.duration._

import com.github.luben.zstd.{ ZstdInputStream, ZstdOutputStream }

import org.apache.pekko
import pekko.http.impl.util._
import pekko.http.scaladsl.model.{ HttpEntity, HttpResponse }
import pekko.http.scaladsl.model.headers.{ `Content-Encoding`, HttpEncodings }
import pekko.stream.ZstdCompressionSettings
import pekko.stream.scaladsl.{ Compression, Sink, Source }
import pekko.stream.scaladsl.compression.ZstdDictionary
import pekko.testkit._
import pekko.util.ByteString

class DczSpec extends CoderSpec {
  // written out, instead of using the constants of the implementation
  private val headerPrefix = ByteString(0x5E, 0x2A, 0x4D, 0x18, 0x20, 0x00, 0x00, 0x00)

  private lazy val dictionary = ByteString("Lorem ipsum dolor sit amet, consetetur sadipscing elitr " * 20)
  private lazy val dictionaryHash =
    ByteString(MessageDigest.getInstance("SHA-256").digest(dictionary.toArray))

  protected lazy val Coder: Coder = Coders.Dcz(dictionary)

  protected def newDecodedInputStream(underlying: InputStream): InputStream =
    // the header is a skippable frame, so a plain zstd decoder that has the dictionary has to accept the output
    new ZstdInputStream(underlying).setDict(dictionary.toArray)

  protected def newEncodedOutputStream(underlying: OutputStream): OutputStream = {
    underlying.write(headerPrefix.toArray)
    underlying.write(dictionaryHash.toArray)
    new ZstdOutputStream(underlying).setDict(dictionary.toArray)
  }

  private def frameWithDictionary(windowLog: Int, content: ByteString = smallTextBytes): ByteString =
    Source(content.grouped(100).toVector)
      .via(Compression.zstd(
        ZstdCompressionSettings().withWindowLog(windowLog).withDictionary(ZstdDictionary(dictionary))))
      .runWith(Sink.fold(ByteString.empty)(_ ++ _))
      .awaitResult(3.seconds.dilated)

  /** The window size declared by the Window_Descriptor of a zstd frame header (RFC 8878, section 3.1.1.1.2) */
  private def windowSizeOf(encoded: ByteString): Long = {
    val frame = encoded.drop(40)
    (frame(4) & 0x20) shouldEqual 0 // not a single segment frame
    val windowDescriptor = frame(5) & 0xFF
    val windowBase = 1L << (10 + (windowDescriptor >> 3))
    windowBase + (windowBase / 8) * (windowDescriptor & 0x7)
  }

  override def extraTests(): Unit = {
    "use the 'dcz' encoding" in {
      Coder.encoding shouldEqual HttpEncodings.dcz
    }
    "write the header of RFC 9842" in {
      val abc = Coders.Dcz(ByteString("abc"))
      // a test vector of FIPS 180-2
      val expectedHash = ByteString(
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad".grouped(2)
          .map(Integer.parseInt(_, 16).toByte).toArray)
      abc.dictionaryHash shouldEqual expectedHash
      abc.encodeAsync(smallTextBytes).awaitResult(3.seconds.dilated).take(40) shouldEqual headerPrefix ++ expectedHash
    }
    "write the header once, also if there is no content" in {
      val encoded = Source.empty[ByteString].via(Coder.encoderFlow).runWith(Sink.fold(ByteString.empty)(_ ++ _))
        .awaitResult(3.seconds.dilated)
      encoded.take(40) shouldEqual headerPrefix ++ dictionaryHash
      encoded.drop(40).indexOfSlice(headerPrefix) shouldEqual -1
      streamDecode(encoded) should readAs(emptyText)
    }
    "compress with the dictionary" in {
      val encoded = ourEncode(dictionary)
      encoded.length - 40 should be < Coders.Zstd.encodeAsync(dictionary).awaitResult(3.seconds.dilated).length / 2

      an[Exception] should be thrownBy
      Coders.Zstd.decode(encoded).awaitResult(3.seconds.dilated)
    }
    "throw an error on a corrupt frame" in {
      val encoded = encode(largeText)
      val corruptFrame = encoded.take(50) ++ ByteString(encoded(50) ^ 0xFF) ++ encoded.drop(51)
      an[Exception] should be thrownBy ourDecode(corruptFrame)
    }
    "reject input that was compressed with another dictionary" in {
      val encoded = Coders.Dcz(ByteString("another dictionary")).encodeAsync(smallTextBytes)
        .awaitResult(3.seconds.dilated)
      (the[RuntimeException] thrownBy ourDecode(encoded)).ultimateCause should be(a[DataFormatException])
    }
    "reject a plain zstd frame" in {
      val frame = Source.single(largeTextBytes)
        .via(Compression.zstd(ZstdCompressionSettings().withDictionary(ZstdDictionary(dictionary))))
        .runWith(Sink.fold(ByteString.empty)(_ ++ _))
        .awaitResult(3.seconds.dilated)
      (the[RuntimeException] thrownBy ourDecode(frame)).ultimateCause should be(a[DataFormatException])
    }
    "reject a truncated header" in {
      (the[RuntimeException] thrownBy ourDecode(headerPrefix ++ dictionaryHash.take(10))).ultimateCause should
      be(a[DataFormatException])
    }
    "reject a truncated frame" in {
      an[Exception] should be thrownBy ourDecode(ourEncode(largeTextBytes).dropRight(10))
    }
    "pass on input without any bytes" in {
      ourDecode(ByteString.empty) shouldEqual ByteString.empty
    }
    "accept a header that arrives byte by byte" in {
      val encoded = ourEncode(smallTextBytes)
      decodeChunks(Source(encoded.grouped(1).toVector)) should readAs(smallText)
    }
    "reject a dictionary that cannot be loaded as raw content" in {
      an[IllegalArgumentException] should be thrownBy Coders.Dcz(ByteString(0x37, 0xA4, 0x30, 0xEC, 1, 2, 3, 4, 5))
    }
    "reject an empty dictionary" in {
      an[IllegalArgumentException] should be thrownBy Coders.Dcz(ByteString.empty)
    }
    "reject a compression level outside the zstd range" in {
      an[IllegalArgumentException] should be thrownBy Coders.Dcz(dictionary, compressionLevel = 23)
    }
    "be selected by Coders.decodeMessage" in {
      val encoded = Coder.encodeMessage(HttpResponse(entity = HttpEntity(smallText)))
      encoded.header[`Content-Encoding`] shouldEqual Some(`Content-Encoding`(HttpEncodings.dcz))
      val decoded = Coders.decodeMessage(encoded, Seq(Coders.Gzip, Coder))
      decoded.header[`Content-Encoding`] shouldEqual None
      decoded.entity.toStrict(3.seconds.dilated).awaitResult(3.seconds.dilated).data should readAs(smallText)
    }
    "use the window sizes of RFC 9842" in {
      val MiB = 1024 * 1024L
      // at least 8 MB, or the next power of 2 that allows every window up to 1.25 times the dictionary
      Dcz.decoderWindowLogMax(1) shouldEqual 23
      Dcz.decoderWindowLogMax(6 * MiB) shouldEqual 23
      Dcz.decoderWindowLogMax(7 * MiB) shouldEqual 24
      Dcz.decoderWindowLogMax(100 * MiB) shouldEqual 27
      Dcz.decoderWindowLogMax(1024 * MiB) shouldEqual 27

      // what reaches the dictionary, as long as it is a power of 2 within the limit
      Dcz.encoderWindowLog(1) shouldEqual 21
      Dcz.encoderWindowLog(1 * MiB) shouldEqual 21
      Dcz.encoderWindowLog(2 * MiB) shouldEqual 22
      Dcz.encoderWindowLog(6 * MiB) shouldEqual 23
      Dcz.encoderWindowLog(10 * MiB) shouldEqual 23 // the limit is 12.5 MiB
      Dcz.encoderWindowLog(20 * MiB) shouldEqual 24 // the limit is 25 MiB
      Dcz.encoderWindowLog(1024 * MiB) shouldEqual 27
    }
    "declare the window for the size of the dictionary at the highest compression level" in {
      val coder = Coders.Dcz(dictionary, compressionLevel = Compression.ZstdMaxCompressionLevel)
      val encoded = Source(largeTextBytes.grouped(1000).toVector)
        .via(coder.encoderFlow)
        .runWith(Sink.fold(ByteString.empty)(_ ++ _))
        .awaitResult(3.seconds.dilated)
      windowSizeOf(encoded) shouldEqual (1L << 21)
      coder.decode(encoded).awaitResult(3.seconds.dilated) should readAs(largeText)
    }
    "reject a frame that needs a window larger than the limit" in {
      val encoded = headerPrefix ++ dictionaryHash ++ frameWithDictionary(windowLog = 24)
      (the[RuntimeException] thrownBy ourDecode(encoded)).ultimateCause.getMessage should include("memory")
    }
    "accept a frame that needs the window of the limit" in {
      ourDecode(headerPrefix ++ dictionaryHash ++ frameWithDictionary(windowLog = 23)) should readAs(smallText)
    }
    "use a large dictionary, and allow the larger window that it needs" in {
      val largeDictionary = ByteString(Array.tabulate(10 * 1024 * 1024)(i => (i * 31 >> 7).toByte))
      val coder = Coders.Dcz(largeDictionary)
      val content = largeDictionary.take(1024 * 1024)
      val encoded = Source(content.grouped(1024 * 64).toVector)
        .via(coder.encoderFlow)
        .runWith(Sink.fold(ByteString.empty)(_ ++ _))
        .awaitResult(20.seconds.dilated)

      windowSizeOf(encoded) shouldEqual (1L << 23)
      encoded.length should be < content.length / 10
      coder.decode(encoded).awaitResult(20.seconds.dilated) shouldEqual content

      val frame = Source.single(content)
        .via(Compression.zstd(ZstdCompressionSettings().withWindowLog(24)
          .withDictionary(ZstdDictionary(largeDictionary))))
        .runWith(Sink.fold(ByteString.empty)(_ ++ _))
        .awaitResult(20.seconds.dilated)
      val decodedLargeWindow = coder.decode(headerPrefix ++ coder.dictionaryHash ++ frame)
      decodedLargeWindow.awaitResult(20.seconds.dilated) shouldEqual content
    }
  }
}
