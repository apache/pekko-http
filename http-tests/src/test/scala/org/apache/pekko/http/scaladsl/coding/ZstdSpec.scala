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

import scala.annotation.nowarn
import scala.concurrent.duration._

import com.github.luben.zstd.{ ZstdInputStream, ZstdOutputStream }

import org.apache.pekko
import pekko.http.impl.util._
import pekko.http.scaladsl.model.{ HttpEntity, HttpRequest }
import pekko.http.scaladsl.model.HttpMethods.POST
import pekko.http.scaladsl.model.headers.{ `Content-Encoding`, HttpEncoding, HttpEncodings }
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.testkit._
import pekko.util.ByteString

class ZstdSpec extends CoderSpec {
  protected def Coder: Coder = Coders.Zstd

  protected def newDecodedInputStream(underlying: InputStream): InputStream =
    new ZstdInputStream(underlying)

  protected def newEncodedOutputStream(underlying: OutputStream): OutputStream =
    new ZstdOutputStream(underlying)

  // zstd-jni reports corrupt input with a ZstdException instead of a DataFormatException
  override protected def corruptInputCheck: Boolean = false

  override def extraTests(): Unit = {
    "use the 'zstd' encoding" in {
      Coder.encoding shouldEqual HttpEncodings.zstd
      HttpEncoding.custom("zstd") shouldEqual HttpEncodings.zstd
    }
    "set the Content-Encoding header when encoding a message" in {
      val request = HttpRequest(POST, entity = HttpEntity(largeText))
      Coder.encodeMessage(request).header[`Content-Encoding`] shouldEqual Some(`Content-Encoding`(HttpEncodings.zstd))
    }
    "throw an error on corrupt input" in {
      an[Exception] should be thrownBy ourDecode(corruptContent)
    }
    "properly round-trip encode/decode a large string with a custom compression level" in {
      val coder = Coders.Zstd(compressionLevel = 19)
      coder.decode(coder.encodeAsync(largeTextBytes).awaitResult(3.seconds.dilated))
        .awaitResult(3.seconds.dilated) should readAs(largeText)
    }
    "reject a compression level outside the zstd range" in {
      an[IllegalArgumentException] should be thrownBy Coders.Zstd(compressionLevel = 23)
    }
    "close the compressor when encoding completes" in {
      val compressor = new TrackingZstdCompressor
      Source.single(smallTextBytes)
        .via(encoderWith(compressor).encoderFlow)
        .runWith(Sink.ignore)
        .awaitResult(3.seconds.dilated)
      // postStop() is dispatched to the stage actor after the Sink.ignore future completes
      awaitCond(compressor.cleanupCalls == 1, 3.seconds.dilated)
    }
    "close the compressor when encoding is cancelled early" in {
      val compressor = new TrackingZstdCompressor
      Source.repeat(largeTextBytes).take(10)
        .via(encoderWith(compressor).encoderFlow)
        .take(1)
        .runWith(Sink.ignore)
        .awaitResult(3.seconds.dilated)
      awaitCond(compressor.cleanupCalls == 1, 3.seconds.dilated)
    }
  }

  @nowarn("msg=deprecated")
  private def encoderWith(compressor: Compressor): Encoder =
    new Encoder {
      override def encoding: HttpEncoding = HttpEncodings.zstd
      override def messageFilter = Encoder.DefaultFilter
      override private[http] def newCompressor: Compressor = compressor
    }

  private class TrackingZstdCompressor extends ZstdCompressor(Zstd.DefaultCompressionLevel) {
    @volatile var cleanupCalls = 0

    private[coding] override def cleanup(): Unit = {
      cleanupCalls += 1
      super.cleanup()
    }
  }

  private def awaitCond(p: => Boolean, max: FiniteDuration): Unit = {
    val deadline = max.fromNow
    while (!p && deadline.hasTimeLeft()) Thread.sleep(10)
    assert(p, s"condition not met within $max")
  }
}
