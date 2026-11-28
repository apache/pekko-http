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
import scala.concurrent.duration._

import org.apache.pekko
import pekko.http.impl.util._
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.headers._
import pekko.stream.scaladsl.Source
import pekko.testkit._
import pekko.util.ByteString

import org.scalatest.wordspec.AnyWordSpec

class CodersSpec extends AnyWordSpec with CodecSpecSupport {

  private val coders = Seq(Coders.Zstd, Coders.Gzip, Coders.Deflate, Coders.NoCoding)

  private def encoded(coder: Coder): HttpResponse =
    coder.encodeMessage(HttpResponse(entity = HttpEntity(smallText)))

  private def body(response: HttpResponse): String =
    response.entity.toStrict(3.seconds.dilated).awaitResult(3.seconds.dilated).data.utf8String

  "Encoder.encodeMessage" should {
    for (coder <- Seq(Coders.Gzip, Coders.Deflate)) {
      s"preserve a pre-encoded request with a raw ${coder.encoding} Content-Encoding header" in {
        val bytes = coder.encodeAsync(ByteString(smallText)).awaitResult(3.seconds.dilated)
        val header = RawHeader("cOnTeNt-EnCoDiNg", coder.encoding.value)
        val entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, bytes)
        val request = HttpRequest(method = HttpMethods.POST, headers = List(header), entity = entity)

        val encoded = coder.encodeMessage(request)

        (encoded should be).theSameInstanceAs(request)
        encoded.headers shouldEqual List(header)
        encoded.entity.toStrict(3.seconds.dilated).awaitResult(3.seconds.dilated) shouldEqual entity
      }
    }
  }

  "Decoder.decodeMessage" should {
    for (coder <- Seq(Coders.Gzip, Coders.Deflate)) {
      s"decode a request with a raw ${coder.encoding} Content-Encoding header and remove the header" in {
        val bytes = coder.encodeAsync(ByteString(smallText)).awaitResult(3.seconds.dilated)
        val header = RawHeader("cOnTeNt-EnCoDiNg", coder.encoding.value)
        val other = RawHeader("X-Other", "kept")
        val request = HttpRequest(method = HttpMethods.POST, headers = List(header, other),
          entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, bytes))

        val decoded = coder.decodeMessage(request)

        decoded.headers shouldEqual List(other)
        decoded.entity.toStrict(3.seconds.dilated).awaitResult(3.seconds.dilated).data.utf8String shouldEqual smallText
      }
    }
  }

  "explicit response decoding with a built-in coder" should {
    val gzip = `Content-Encoding`(HttpEncodings.gzip)
    val other = RawHeader("X-Other", "kept")

    for ((label, outer, headers, remaining) <- List(
        ("one field", Coders.Deflate.withMaxBytesPerChunk(16),
          List(other, `Content-Encoding`(HttpEncodings.gzip, HttpEncodings.deflate)), List(other, gzip)),
        ("separate fields", Coders.Deflate,
          List(gzip, other, `Content-Encoding`(HttpEncodings.deflate)), List(gzip, other)),
        ("zstd as the outer coding", Coders.Zstd,
          List(other, `Content-Encoding`(HttpEncodings.gzip, HttpEncodings.zstd)), List(other, gzip)))) {
      s"decode only the outer coding and retain the inner one for $label" in {
        val original = ByteString(smallText)
        val inner = Coders.Gzip.encodeAsync(original).awaitResult(3.seconds.dilated)
        // The explicitly selected decoder can also be a configured Decoder, not a Coder.
        val outerEncoder = if (outer.encoding == HttpEncodings.zstd) Coders.Zstd else Coders.Deflate
        val encoded = outerEncoder.encodeAsync(inner).awaitResult(3.seconds.dilated)
        val contentType = ContentTypes.`text/plain(UTF-8)`
        val response = HttpResponse(StatusCodes.Accepted, headers = headers, entity = HttpEntity(contentType, encoded))

        (Coders.Gzip.decodeMessage(response) should be).theSameInstanceAs(response)

        val partial = outer.decodeMessage(response)
        partial.headers shouldEqual remaining
        val partialEntity = partial.entity.toStrict(3.seconds.dilated).awaitResult(3.seconds.dilated)
        partialEntity shouldEqual HttpEntity(contentType, inner)

        val decoded = Coders.Gzip.decodeMessage(partial.withEntity(partialEntity))
        decoded.headers shouldEqual List(other)
        decoded.status shouldEqual response.status
        decoded.entity.toStrict(3.seconds.dilated).awaitResult(3.seconds.dilated) shouldEqual
        HttpEntity(contentType, original)
      }
    }

    "remove one matching coding at a time when two field lines name the same coding" in {
      val original = ByteString(smallText)
      val inner = Coders.Gzip.encodeAsync(original).awaitResult(3.seconds.dilated)
      val outer = Coders.Gzip.encodeAsync(inner).awaitResult(3.seconds.dilated)
      val response = HttpResponse(headers = List(gzip, other, gzip), entity = HttpEntity(outer))
      val decoder = Coders.Gzip.withMaxBytesPerChunk(32).withMaxBytesPerChunk(16)

      (Coders.decodeMessage(response) should be).theSameInstanceAs(response)
      val partial = decoder.decodeMessage(response)
      partial.headers shouldEqual List(gzip, other)
      val partialEntity = partial.entity.toStrict(3.seconds.dilated).awaitResult(3.seconds.dilated)
      partialEntity.data shouldEqual inner

      val decoded = decoder.decodeMessage(partial.withEntity(partialEntity))
      decoded.headers shouldEqual List(other)
      body(decoded) shouldEqual smallText
    }

    "preserve custom stream decoders' explicit choice of a compatible codec" in {
      @nowarn("msg=deprecated")
      val custom = new StreamDecoder {
        val encoding = HttpEncoding.custom("x-application-gzip")
        protected def newDecompressorStage(maxBytesPerChunk: Int) = () => new GzipDecompressor(maxBytesPerChunk)
      }
      for (decoder <- Seq(custom, custom.withMaxBytesPerChunk(16))) {
        val decoded = decoder.decodeMessage(encoded(Coders.Gzip))
        decoded.headers shouldEqual Nil
        body(decoded) shouldEqual smallText
      }
    }

    "leave a response without a coding or with a different coding unchanged" in {
      val plain = HttpResponse(entity = HttpEntity(smallText))
      val compressed = encoded(Coders.Deflate)
      (Coders.Gzip.decodeMessage(plain) should be).theSameInstanceAs(plain)
      (Coders.Gzip.decodeMessage(compressed) should be).theSameInstanceAs(compressed)
    }

    "decode one gzip layer with a case-insensitive raw x-gzip field" in {
      val response = encoded(Coders.Gzip).withHeaders(RawHeader("cOnTeNt-EnCoDiNg", "X-GZip"), other)
      val decoded = Coders.Gzip.decodeMessage(response)
      decoded.headers shouldEqual List(other)
      body(decoded) shouldEqual smallText
    }
  }

  "Coders.decodeMessage" should {
    "decode a message with the decoder matching its Content-Encoding" in {
      for (coder <- Seq(Coders.Zstd, Coders.Gzip, Coders.Deflate)) {
        val response = encoded(coder)
        response.encoding shouldEqual coder.encoding
        val decoded = Coders.decodeMessage(response, coders)
        decoded.header[`Content-Encoding`] shouldEqual None
        body(decoded) shouldEqual smallText
      }
    }
    "decode a response when a custom coding differs only in case" in {
      class ExampleDecoder(val maxBytesPerChunk: Int = Decoder.MaxBytesPerChunkDefault) extends Decoder {
        override val encoding = HttpEncoding.custom("example-codec")
        override def withMaxBytesPerChunk(n: Int): Decoder = new ExampleDecoder(n)
        override def decoderFlow = Coders.Gzip.withMaxBytesPerChunk(maxBytesPerChunk).decoderFlow
      }
      val response = encoded(Coders.Gzip).withHeaders(`Content-Encoding`(HttpEncoding.custom("Example-Codec")))

      val decoded = Coders.decodeMessage(response, Seq(new ExampleDecoder))
      decoded.header[`Content-Encoding`] shouldEqual None
      body(decoded) shouldEqual smallText
    }
    "decode a gzip close-delimited response and preserve its entity type and content type" in {
      val original = ByteString(smallText)
      val compressed = Coders.Gzip.encodeAsync(original).awaitResult(3.seconds.dilated)
      val contentType = ContentTypes.`text/plain(UTF-8)`
      val response = HttpResponse(
        entity = HttpEntity.CloseDelimited(contentType, Source.single(compressed)),
        headers = List(`Content-Encoding`(HttpEncodings.gzip)))

      val decoded = Coders.decodeMessage(response)
      decoded.header[`Content-Encoding`] shouldEqual None
      decoded.entity shouldBe a[HttpEntity.CloseDelimited]
      decoded.entity.contentType shouldEqual contentType
      decoded.entity.toStrict(3.seconds.dilated).awaitResult(3.seconds.dilated).data shouldEqual original
    }
    "decode with the default coders if none are given" in {
      body(Coders.decodeMessage(encoded(Coders.Gzip))) shouldEqual smallText
    }
    "leave a message without Content-Encoding unchanged" in {
      val response = HttpResponse(entity = HttpEntity(smallText))
      Coders.decodeMessage(response, coders) shouldEqual response
    }
    "leave a message with an encoding that none of the decoders supports unchanged" in {
      val response = encoded(Coders.Zstd)
      val decoded = Coders.decodeMessage(response)
      decoded shouldEqual response
      decoded.encoding shouldEqual HttpEncodings.zstd
    }
    "leave a message with more than one encoding unchanged" in {
      val response = HttpResponse(
        entity = HttpEntity(ByteString("abc")),
        headers = List(`Content-Encoding`(HttpEncodings.gzip, HttpEncodings.zstd)))
      Coders.decodeMessage(response, coders) shouldEqual response
    }
  }
}
