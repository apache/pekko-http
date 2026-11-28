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

package org.apache.pekko.http

import scala.concurrent.{ ExecutionContext, Future }

import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers.{ Expect, RawHeader }
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ExpectContinuePolicySpec extends AnyWordSpec with Matchers with ScalaFutures {
  import ExpectContinuePolicy._
  import ExpectContinuePolicySupport.{ eligibleContentLength, forRequest }

  private val protocol = HttpProtocols.`HTTP/1.1`
  private val request = HttpRequest(HttpMethods.PUT, "https://example.com/document",
    headers = List(Expect.`100-continue`), entity = "document")

  "An Expect continue policy" should {
    "consider only explicitly expected nonempty strict content when enabled" in {
      eligibleContentLength(request, protocol, disabled) shouldBe None
      eligibleContentLength(request, protocol, retryIdempotentOnce) shouldBe Some(8L)
      eligibleContentLength(request, HttpProtocols.`HTTP/2.0`, retryIdempotentOnce) shouldBe Some(8L)
      eligibleContentLength(request.removeHeader("expect"), protocol, retryIdempotentOnce) shouldBe None
      eligibleContentLength(request.withHeaders(RawHeader("Expect", "example-preference")), protocol,
        retryIdempotentOnce) shouldBe None
      eligibleContentLength(HttpRequest(), protocol, retryIdempotentOnce) shouldBe None
      val streamed =
        request.withEntity(HttpEntity(ContentTypes.`application/octet-stream`, Source.single(ByteString("document"))))
      eligibleContentLength(streamed, protocol, retryIdempotentOnce) shouldBe None
    }

    "decide on ordinary request metadata without receiving either entity" in {
      val put = new ExpectContinueRetryContext(request.method, request.getUri, protocol, 8L)
      put.getMethod shouldBe request.method
      put.getRequestUri.toString shouldBe request.uri.toString
      put.getProtocol shouldBe protocol
      put.getContentLength shouldBe 8L
      disabled.shouldRetry(put) shouldBe false
      retryIdempotentOnce.shouldRetry(put) shouldBe true
      val post = new ExpectContinueRetryContext(HttpMethods.POST, request.getUri, protocol, 8L)
      retryIdempotentOnce.shouldRetry(post) shouldBe false
      val thisDocumentOnly: ExpectContinuePolicy = _.getRequestUri.path() == "/document"
      thisDocumentOnly.shouldRetry(put) shouldBe true
    }

    "allow a request policy to narrow but never widen the client policy" in {
      val context = new ExpectContinueRetryContext(request.method, request.getUri, protocol, 8L)
      val post = new ExpectContinueRetryContext(HttpMethods.POST, request.getUri, protocol, 8L)
      var requestCalls = 0
      val allow: ExpectContinuePolicy = _ => {
        requestCalls += 1
        true
      }
      val deny: ExpectContinuePolicy = _ => false
      val requested = request.addAttribute(attributeKey, allow)

      forRequest(requested, disabled) shouldBe disabled
      forRequest(requested, deny).shouldRetry(context) shouldBe false
      forRequest(requested, retryIdempotentOnce).shouldRetry(post) shouldBe false
      requestCalls shouldBe 0

      forRequest(request.addAttribute(attributeKey, deny), retryIdempotentOnce).shouldRetry(context) shouldBe false
      forRequest(requested, retryIdempotentOnce).shouldRetry(context) shouldBe true
      requestCalls shouldBe 1
      forRequest(requested.removeAttribute(attributeKey), retryIdempotentOnce) shouldBe retryIdempotentOnce
    }

    "compose existing request executors with request vetoes and observe one final response" in {
      implicit val ec: ExecutionContext = ExecutionContext.parasitic
      type Execute = HttpRequest => Future[HttpResponse]
      val observed = Vector.newBuilder[StatusCode]
      val received = Vector.newBuilder[(String, ExpectContinuePolicy)]

      val downstream: Execute = r => {
        received += r.header("x-client").get.value -> forRequest(r, retryIdempotentOnce)
        Future.successful(HttpResponse(StatusCodes.Created))
      }
      def withClientMetadata(next: Execute): Execute = r =>
        next(r.addHeader(RawHeader("X-Client", "documents"))).map { response =>
          observed += response.status
          response
        }
      // The signer knows that changing its Expect field would invalidate this request.
      def withoutRecovery(next: Execute): Execute = r => next(r.addAttribute(attributeKey, disabled))

      val client = withClientMetadata(downstream)
      client(request).futureValue.status shouldBe StatusCodes.Created
      withoutRecovery(client)(request).futureValue.status shouldBe StatusCodes.Created
      received.result() shouldBe Vector("documents" -> retryIdempotentOnce, "documents" -> disabled)
      observed.result() shouldBe Vector(StatusCodes.Created, StatusCodes.Created)
      forRequest(request, disabled) shouldBe disabled
    }
  }
}
