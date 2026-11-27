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

package org.apache.pekko.http.scaladsl.server.directives

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

import scala.concurrent.Promise
import scala.concurrent.duration._

import org.apache.pekko
import pekko.http.impl.util._
import pekko.http.scaladsl.coding.Coders
import pekko.http.scaladsl.model.{
  ContentTypes,
  HttpEntity,
  HttpMethods,
  HttpProtocol,
  HttpRequest,
  HttpResponse,
  ResponseEntity,
  StatusCodes
}
import pekko.http.scaladsl.model.headers.{
  `Accept-Encoding`,
  `Content-Encoding`,
  HttpEncoding,
  HttpEncodings,
  RawHeader
}
import pekko.http.scaladsl.server.{ Directives, Route }
import pekko.http.scaladsl.settings.RoutingSettings
import pekko.stream.scaladsl.{ Compression, Flow, Sink, Source }
import pekko.testkit._
import pekko.util.ByteString

class FlowCoderSpec extends PekkoSpecWithMaterializer("") with Directives {
  private val token = HttpEncoding.custom("x-test-gzip")
  private val text = "ordinary flow-coded entity ".repeat(3)
  private val bytes = ByteString(text)

  private def independentGzip(input: ByteString): ByteString = {
    val output = new ByteArrayOutputStream()
    val gzip = new GZIPOutputStream(output)
    try gzip.write(input.toArray)
    finally gzip.close()
    ByteString.fromArray(output.toByteArray)
  }

  private def independentGunzip(input: ByteString): ByteString = {
    val gzip = new GZIPInputStream(new ByteArrayInputStream(input.toArray))
    try ByteString.fromArray(gzip.readAllBytes())
    finally gzip.close()
  }

  "A flow-based coder" should {
    "use its reusable graph for encoderFlow, encodeMessage, encodeData and encodeAsync" in {
      val materializations = new AtomicInteger()
      val flow = Flow.fromMaterializer { (_, _) =>
        materializations.incrementAndGet()
        Compression.gzip
      }
      val coder = Coders.fromFlows(token, flow, chunks => Compression.gzipDecompress(chunks))
      materializations.get() shouldBe 0

      val fragments = Source(List(bytes.take(11), bytes.drop(11)))
      val encodedFlow = fragments.via(coder.encoderFlow).runFold(ByteString.empty)(_ ++ _)
      independentGunzip(encodedFlow.awaitResult(3.seconds.dilated)) shouldBe bytes

      val strict = coder.encodeMessage(HttpResponse(entity = HttpEntity(text)))
      strict.header[`Content-Encoding`] shouldBe Some(`Content-Encoding`(token))
      independentGunzip(strict.entity.toStrict(3.seconds.dilated).awaitResult(3.seconds.dilated).data) shouldBe bytes

      val stream: ResponseEntity = HttpEntity.Chunked.fromData(ContentTypes.`text/plain(UTF-8)`, fragments)
      independentGunzip(
        coder.encodeData(stream).toStrict(3.seconds.dilated).awaitResult(3.seconds.dilated).data) shouldBe
      bytes
      independentGunzip(coder.encodeAsync(bytes).awaitResult(3.seconds.dilated)) shouldBe bytes
      independentGunzip(coder.encodeAsync(bytes).awaitResult(3.seconds.dilated)) shouldBe bytes
      materializations.get() shouldBe 5
    }

    "respect a custom message filter and keep an explicitly encoded response unchanged" in {
      val unencoded = HttpResponse(entity = HttpEntity(text))
      val coder = Coders.fromFlows(token, Compression.gzip, n => Compression.gzipDecompress(n))
      val filtered = Coders.fromFlows(
        token,
        Compression.gzip,
        n => Compression.gzipDecompress(n),
        messageFilter = _ => false)
      filtered.encodeMessage(unencoded) shouldBe unencoded
      for (header <- Seq(`Content-Encoding`(token), RawHeader("Content-Encoding", token.value))) {
        val alreadyEncoded = unencoded.withHeaders(header)
        coder.encodeMessage(alreadyEncoded) shouldBe alreadyEncoded
      }
    }

    "reject reserved content-coding names" in {
      for (name <- List("identity", "IDENTITY", "*")) {
        intercept[IllegalArgumentException] {
          Coders.fromFlows(HttpEncoding.custom(name), Compression.gzip, limit => Compression.gzipDecompress(limit))
        }
      }
    }

    "create a fresh decoder only when its graph materializes" in {
      val allocations = new AtomicInteger()
      val coder = Coders.fromFlows(token, Compression.gzip,
        maxBytesPerChunk => {
          allocations.incrementAndGet()
          Compression.gzipDecompress(maxBytesPerChunk)
        })

      val encoded = HttpResponse(entity = HttpEntity(independentGzip(bytes))).withHeaders(`Content-Encoding`(token))
      val mixedCaseEncoding = HttpEncoding.custom("X-Test-GZip")
      val decoded = Coders.decodeMessage(encoded.withHeaders(`Content-Encoding`(mixedCaseEncoding)), Seq(coder))
      val decodingFlow = coder.decoderFlow
      val compressed = Source.single(independentGzip(bytes)).via(decodingFlow)
      allocations.get() shouldBe 0

      decoded.entity.toStrict(3.seconds.dilated).awaitResult(3.seconds.dilated).data shouldBe bytes
      allocations.get() shouldBe 1
      compressed.runFold(ByteString.empty)(_ ++ _).awaitResult(3.seconds.dilated) shouldBe bytes
      compressed.runFold(ByteString.empty)(_ ++ _).awaitResult(3.seconds.dilated) shouldBe bytes
      allocations.get() shouldBe 3
    }

    "select a single coding from mixed decoders without decoding a coding stack" in {
      val customDecoderMaterializations = new AtomicInteger()
      val coder = Coders.fromFlows(token, Compression.gzip,
        maxBytesPerChunk => {
          customDecoderMaterializations.incrementAndGet()
          Compression.gzipDecompress(maxBytesPerChunk)
        })
      val decoders = Seq(Coders.Gzip, coder)
      val compressed = independentGzip(bytes)

      def decode(encoding: HttpEncoding): Unit = {
        val message = HttpResponse(headers = List(`Content-Encoding`(encoding)), entity = HttpEntity(compressed))
        val decoded = Coders.decodeMessage(message, decoders)
        decoded.header[`Content-Encoding`] shouldBe None
        decoded.entity.toStrict(3.seconds.dilated).awaitResult(3.seconds.dilated).data shouldBe bytes
      }

      decode(HttpEncodings.gzip)
      customDecoderMaterializations.get() shouldBe 0

      decode(token)
      customDecoderMaterializations.get() shouldBe 1

      val stacked = HttpResponse(
        headers = List(`Content-Encoding`(HttpEncodings.gzip, token)),
        entity = HttpEntity(independentGzip(compressed)))
      (Coders.decodeMessage(stacked, decoders) should be).theSameInstanceAs(stacked)
      customDecoderMaterializations.get() shouldBe 1
    }

    "reject a stack of content codings without materializing its single decoder" in {
      val allocations = new AtomicInteger()
      val coder = Coders.fromFlows(token, Compression.gzip,
        limit => {
          allocations.incrementAndGet()
          Compression.gzipDecompress(limit)
        })
      val route = Route.toFunction(decodeRequestWith(coder) { entity(as[String]) { decoded => complete(decoded) } })
      val encoded = independentGzip(independentGzip(bytes))
      val request = HttpRequest(HttpMethods.POST, entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, encoded))
      for (headers <- List(
          List(`Content-Encoding`(token, HttpEncodings.gzip)),
          List(RawHeader("Content-Encoding", "x-test-gzip, gzip")),
          List(`Content-Encoding`(token), `Content-Encoding`(HttpEncodings.gzip)),
          List(`Content-Encoding`(token), RawHeader("Content-Encoding", "gzip")))) {
        val stacked = request.withHeaders(headers)
        coder.decodeMessage(stacked) shouldBe stacked
        Coders.decodeMessage(stacked, Seq(coder)) shouldBe stacked
        val response = route(stacked).awaitResult(3.seconds.dilated)
        // The coding stack is unsupported, not malformed, so the sealed route returns 415.
        response.status shouldBe StatusCodes.UnsupportedMediaType
        response.header[`Accept-Encoding`].map(_.value) shouldBe Some(token.value)
      }
      allocations.get() shouldBe 0

      val rawSingle = request
        .withEntity(HttpEntity(ContentTypes.`text/plain(UTF-8)`, independentGzip(bytes)))
        .withHeaders(RawHeader("Content-Encoding", "X-Test-GZip"))
      for (decoded <- List(coder.decodeMessage(rawSingle), Coders.decodeMessage(rawSingle, Seq(coder)))) {
        decoded.headers.exists(_.is("content-encoding")) shouldBe false
        decoded.entity.toStrict(3.seconds.dilated).awaitResult(3.seconds.dilated).data shouldBe bytes
      }
      allocations.get() shouldBe 2
    }

    "decode an HTTP/2 streamed request with a route-local flow coder" in {
      val timeout = 3.seconds.dilated
      val http = pekko.http.scaladsl.Http()
      val http2 = pekko.http.scaladsl.model.HttpProtocols.`HTTP/2.0`
      val coder = Coders.fromFlows(token, Compression.gzip, n => Compression.gzipDecompress(n))
      val contentType = ContentTypes.`application/octet-stream`
      val requestShape = Promise[(HttpProtocol, Boolean)]()
      val route = post {
        extractRequest { request =>
          requestShape.trySuccess((request.protocol, request.entity.isStrict))
          decodeRequestWith(coder) {
            entity(as[ByteString]) { decoded => complete(HttpEntity(contentType, decoded)) }
          }
        }
      }
      val settings = pekko.http.scaladsl.settings.ServerSettings(system).withEnableHttp2(true)
      val binding = http.newServerAt("127.0.0.1", 0).withSettings(settings)
        .bind(Route.toFunction(route)).awaitResult(timeout)
      try {
        val encoded = independentGzip(bytes)
        val (first, second) = encoded.splitAt(encoded.length / 2)
        val request = HttpRequest(
          method = HttpMethods.POST,
          headers = List(`Content-Encoding`(token)),
          entity = HttpEntity(contentType, Source(List(first, second))))
        val connection = http.connectionTo("127.0.0.1").toPort(binding.localAddress.getPort)
          .http2WithPriorKnowledge()
        val response = Source.single(request).via(connection)
          .mapAsync(1)(_.toStrict(timeout)(system.dispatcher, materializer))
          .runWith(Sink.head).awaitResult(timeout)

        val (incomingProtocol, incomingStrict) = requestShape.future.awaitResult(timeout)
        incomingProtocol shouldBe http2
        incomingStrict shouldBe false
        response.protocol shouldBe http2
        response.status shouldBe StatusCodes.OK
        response.entity shouldBe HttpEntity(contentType, bytes)
      } finally binding.terminate(1.second.dilated).awaitResult(timeout)
    }

    "retain per-route selection and supply the configured decoding output chunk size" in {
      val usedChunkSize = new AtomicInteger()
      val coder = Coders.fromFlows(token, Compression.gzip,
        limit => {
          usedChunkSize.set(limit)
          Compression.gzipDecompress(limit)
        })
      val chunks = coder.withMaxBytesPerChunk(7).decoderFlow
      val decoded = Source.single(independentGzip(bytes)).via(chunks).runWith(Sink.seq)
        .awaitResult(3.seconds.dilated)
      decoded.foldLeft(ByteString.empty)(_ ++ _) shouldBe bytes
      decoded.forall(_.size <= 7) shouldBe true
      usedChunkSize.get() shouldBe 7
      val routingSettings = RoutingSettings(system).withDecodeMaxBytesPerChunk(17)
      val requestRoute = Route.toFunction(withSettings(routingSettings) {
        decodeRequestWith(coder) {
          entity(as[String]) { decodedText => complete(decodedText) }
        }
      })
      val post = HttpRequest(
        method = HttpMethods.POST,
        headers = List(`Content-Encoding`(HttpEncoding.custom("X-Test-GZip"))),
        entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, independentGzip(bytes)))
      val decodedResponse = requestRoute(post).awaitResult(3.seconds.dilated)
      decodedResponse.entity.toStrict(3.seconds.dilated).awaitResult(3.seconds.dilated).data shouldBe bytes
      usedChunkSize.get() shouldBe 17

      val request = HttpRequest(headers = List(`Accept-Encoding`(HttpEncoding.custom("X-Test-GZip"))))
      val explicitRoute = Route.toFunction(encodeResponseWith(coder) { complete(text) })
      val coded = explicitRoute(request).awaitResult(3.seconds.dilated)
      coded.header[`Content-Encoding`] shouldBe Some(`Content-Encoding`(token))
      independentGunzip(coded.entity.toStrict(3.seconds.dilated).awaitResult(3.seconds.dilated).data) shouldBe bytes

      val defaultRoute = Route.toFunction(encodeResponse { complete(text) })
      val plain = defaultRoute(request).awaitResult(3.seconds.dilated)
      plain.header[`Content-Encoding`] shouldBe None
      plain.entity.toStrict(3.seconds.dilated).awaitResult(3.seconds.dilated).data shouldBe bytes
    }
  }
}
