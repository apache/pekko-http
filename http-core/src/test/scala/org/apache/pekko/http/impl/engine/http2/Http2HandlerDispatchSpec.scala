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

package org.apache.pekko.http.impl.engine.http2

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.MILLISECONDS

import scala.concurrent.{ Await, ExecutionContext, Future, Promise }

import org.apache.pekko
import pekko.event.NoLogging
import pekko.http.scaladsl.model.{ HttpRequest, HttpResponse, StatusCodes }
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.testkit.PekkoSpec

class Http2HandlerDispatchSpec extends PekkoSpec {
  "HTTP/2 handler error processing" should {
    "preserve an already successful application future" in {
      val completed = Future.successful(HttpResponse(StatusCodes.Accepted))
      val handler = Http2(system).withErrorHandling(NoLogging, _ => completed)
      (handler(HttpRequest()) should be).theSameInstanceAs(completed)
    }

    "keep the same error response for synchronous, completed and later failures" in {
      val exception = new IllegalStateException("handler failure")
      val promise = Promise[HttpResponse]()
      val sync = Http2(system).withErrorHandling(NoLogging, _ => throw exception)
      val failed = Http2(system).withErrorHandling(NoLogging, _ => Future.failed(exception))
      val pending = Http2(system).withErrorHandling(NoLogging, _ => promise.future)
      val response = pending(HttpRequest())
      response.isCompleted shouldBe false
      promise.failure(exception)
      Seq(sync(HttpRequest()), failed(HttpRequest()), response).foreach { result =>
        Await.result(result, remainingOrDefault).status shouldBe StatusCodes.InternalServerError
      }
    }
  }

  "HTTP/2 handler dispatch" should {
    for (completeBeforeDispatch <- List(true, false)) {
      val timing = if (completeBeforeDispatch) "already completed" else "pending"
      s"dispatch application code and attach the stream ID on the handler dispatcher ($timing response)" in {
        val tasks = new LinkedBlockingQueue[Runnable]()
        val handlerContext = new ExecutionContext {
          def execute(runnable: Runnable): Unit = { tasks.add(runnable); () }
          def reportFailure(cause: Throwable): Unit = system.dispatcher.reportFailure(cause)
        }
        val handlerThread = Promise[Thread]()
        val response = Promise[HttpResponse]()
        if (completeBeforeDispatch) response.success(HttpResponse())
        val flow = Http2Blueprint.handleWithStreamIdHeader(1) { _ =>
          handlerThread.success(Thread.currentThread())
          response.future
        }(handlerContext)
        val result = Source.single(HttpRequest().addAttribute(Http2.streamId, 3)).via(flow).runWith(Sink.head)

        // Only the supplied execution context may start the handler, not the stream interpreter.
        val runHandler = tasks.poll(remainingOrDefault.toMillis, MILLISECONDS)
        runHandler should not be null
        handlerThread.isCompleted shouldBe false
        runHandler.run()
        Await.result(handlerThread.future, remainingOrDefault) shouldBe Thread.currentThread()
        if (!completeBeforeDispatch) {
          response.success(HttpResponse())
          // The pending future may finish on a different thread: use the handler dispatcher for its callback.
          val attachStreamId = tasks.poll(remainingOrDefault.toMillis, MILLISECONDS)
          attachStreamId should not be null
          attachStreamId.run()
        }

        // A completed response needs no second dispatch.
        Await.result(result, remainingOrDefault).attribute(Http2.streamId) shouldBe Some(3)
      }
    }

    "leave a response unchanged when the request has no stream ID" in {
      val response = HttpResponse()
      val flow = Http2Blueprint.handleWithStreamIdHeader(1)(_ => Future.successful(response))(system.dispatcher)
      val result = Source.single(HttpRequest()).via(flow).runWith(Sink.head)
      (Await.result(result, remainingOrDefault) should be).theSameInstanceAs(response)
    }

    "preserve a failed handler response" in {
      val error = new IllegalStateException("handler failure")
      val flow = Http2Blueprint.handleWithStreamIdHeader(1)(_ => Future.failed(error))(system.dispatcher)
      val result = Source.single(HttpRequest().addAttribute(Http2.streamId, 3)).via(flow).runWith(Sink.head)
      (Await.result(result.failed, remainingOrDefault) should be).theSameInstanceAs(error)
    }
  }
}
