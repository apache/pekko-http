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

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

import scala.concurrent.duration._

import org.apache.pekko
import pekko.http.impl.util._
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.HttpEntity.{ Chunk, Chunked }
import pekko.http.scaladsl.model.headers._
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.stream.testkit.{ TestPublisher, TestSubscriber }
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
    "finish a chunked gzip response before reading an unencoded response on the same HTTP/1.1 connection" in {
      val firstBody = ByteString("compressed report")
      val secondBody = ByteString("unencoded report")
      val target = new ByteArrayOutputStream()
      val encoder = new GZIPOutputStream(target)
      try encoder.write(firstBody.toArray)
      finally encoder.close()
      val gzipMember = ByteString.fromArray(target.toByteArray)
      // RFC 1952 puts CRC32 and ISIZE in the final eight octets. Send them in their own HTTP data chunk.
      val (compressedData, footer) = gzipMember.splitAt(gzipMember.length - 8)
      val contentType = ContentTypes.`text/plain(UTF-8)`
      val coding = `Content-Encoding`(HttpEncodings.gzip)
      val compressed = HttpResponse(headers = List(coding),
        entity = Chunked(contentType, Source(List(Chunk(compressedData), Chunk(footer)))))
      val unencoded = HttpResponse(entity = HttpEntity(contentType, secondBody))
      val http = Http()
      val host = "127.0.0.1"
      val timeout = 3.seconds.dilated
      val binding = http.newServerAt(host, 0).bindSync { request =>
        request.uri.path.toString match {
          case "/gzip"  => compressed
          case "/plain" => unencoded
          case _        => HttpResponse(StatusCodes.NotFound)
        }
      }.awaitResult(timeout)
      try {
        val requests = TestPublisher.probe[HttpRequest]()
        val responses = TestSubscriber.probe[HttpResponse]()
        Source.fromPublisher(requests)
          .via(http.connectionTo(host).toPort(binding.localAddress.getPort).http())
          .runWith(Sink.fromSubscriber(responses))
        try {
          responses.request(1)
          requests.sendNext(HttpRequest(uri = "/gzip", headers = List(`Accept-Encoding`(HttpEncodings.gzip))))
          val first = responses.expectNext()
          first.entity shouldBe a[Chunked]
          val decodedFirst = Coders.decodeMessage(first)
          decodedFirst.entity.toStrict(timeout, maxBytes = 1024L).awaitResult(timeout).data shouldBe firstBody
          first.status shouldBe StatusCodes.OK
          first.protocol shouldBe HttpProtocols.`HTTP/1.1`
          first.header[`Content-Encoding`] shouldBe Some(coding)
          decodedFirst.header[`Content-Encoding`] shouldBe None

          // Do not pipeline: the first decoded entity has completed before the second request is sent.
          responses.request(1)
          requests.sendNext(HttpRequest(uri = "/plain"))
          val second = responses.expectNext()
          val decodedSecond = Coders.decodeMessage(second)
          decodedSecond.entity.toStrict(timeout, maxBytes = 1024L).awaitResult(timeout).data shouldBe secondBody
          second.status shouldBe StatusCodes.OK
          second.protocol shouldBe HttpProtocols.`HTTP/1.1`
          second.header[`Content-Encoding`] shouldBe None
          decodedSecond.header[`Content-Encoding`] shouldBe None

          requests.sendComplete()
          responses.expectComplete()
        } finally responses.cancel()
      } finally binding.terminate(1.second.dilated).awaitResult(timeout)
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
