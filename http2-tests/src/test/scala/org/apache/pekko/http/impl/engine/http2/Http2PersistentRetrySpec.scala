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

import java.net.InetSocketAddress
import java.util.concurrent.{ LinkedBlockingQueue, ThreadFactory, TimeUnit }

import scala.concurrent.{ ExecutionContext, Promise }
import scala.concurrent.duration._

import com.typesafe.config.Config

import org.apache.pekko
import pekko.actor.Cancellable
import pekko.event.LoggingAdapter
import pekko.http.impl.engine.http2.client.PersistentConnection
import pekko.http.impl.util.PekkoSpecWithMaterializer
import pekko.http.scaladsl.Http.OutgoingConnection
import pekko.http.scaladsl.model.{ HttpRequest, HttpResponse }
import pekko.http.scaladsl.settings.Http2ClientSettings
import pekko.stream.StreamTcpException
import pekko.stream.scaladsl.{ Flow, Sink, Source }
import pekko.stream.testkit.{ TestPublisher, TestSubscriber }
import pekko.stream.testkit.scaladsl.StreamTestKit
import pekko.testkit.{ ExplicitlyTriggeredScheduler, TestProbe }

object Http2PersistentRetrySpec {
  private val baseBackoff = 1.second

  final class RetryScheduler(config: Config, log: LoggingAdapter, threadFactory: ThreadFactory)
      extends ExplicitlyTriggeredScheduler(config, log, threadFactory) {
    private val scheduledBackoffs = new LinkedBlockingQueue[FiniteDuration]()

    override def scheduleOnce(delay: FiniteDuration, runnable: Runnable)(
        implicit executor: ExecutionContext): Cancellable = {
      val cancellable = super.scheduleOnce(delay, runnable)
      if (delay >= baseBackoff && delay < baseBackoff * 2) scheduledBackoffs.add(delay)
      cancellable
    }

    def expectBackoff(): FiniteDuration =
      Option(scheduledBackoffs.poll(3, TimeUnit.SECONDS)).getOrElse(throw new AssertionError("Retry not scheduled"))
  }
}

class Http2PersistentRetrySpec extends PekkoSpecWithMaterializer("""
    pekko.scheduler.implementation = "org.apache.pekko.http.impl.engine.http2.Http2PersistentRetrySpec$RetryScheduler"
  """) {
  import Http2PersistentRetrySpec._

  private val settings = Http2ClientSettings(system)
    .withBaseConnectionBackoff(Http2PersistentRetrySpec.baseBackoff)
    .withMaxConnectionBackoff(Http2PersistentRetrySpec.baseBackoff * 4)
    .withMaxPersistentAttempts(2)

  private class Setup {
    val input = TestPublisher.probe[HttpRequest]()
    val output = TestSubscriber.probe[HttpResponse]()
    private val endpoint = new InetSocketAddress("localhost", 12345)
    private val attempts = TestProbe()
    private val dispatched = TestProbe()

    // Use an application-level transport stub; request/response association must survive the trip.
    private val flow = Flow[HttpRequest]
      .map { request =>
        dispatched.ref ! request.uri
        HttpResponse().withAttributes(request.attributes)
      }
      .mapMaterializedValue { _ =>
        val establishment = Promise[OutgoingConnection]()
        attempts.ref ! establishment
        establishment.future
      }
    Source.fromPublisher(input)
      .via(PersistentConnection.managedConnection(flow, settings))
      .runWith(Sink.fromSubscriber(output))

    def nextAttempt(): Promise[OutgoingConnection] =
      attempts.expectMsgType[Promise[OutgoingConnection]]

    def establish(attempt: Promise[OutgoingConnection]): Unit = {
      attempt.success(OutgoingConnection(endpoint, endpoint))
      ()
    }

    def start(request: HttpRequest): Promise[OutgoingConnection] = {
      output.request(2)
      input.sendNext(request)
      nextAttempt()
    }

    def expectOnlyOneResponseTo(request: HttpRequest): Unit = {
      dispatched.expectMsg(request.uri)
      output.expectNext() shouldBe HttpResponse()
      output.expectComplete()
    }
  }

  "A persistent HTTP/2 connection" should {
    "dispatch the final buffered request when its source completes during the first connection attempt" in
    StreamTestKit.assertAllStagesStopped {
      val setup = new Setup
      val request = HttpRequest(uri = "/while-connecting")
      val attempt = setup.start(request)
      setup.input.sendComplete()
      setup.output.expectNoMessage(100.millis)
      setup.establish(attempt)
      setup.expectOnlyOneResponseTo(request)
    }

    "dispatch the final buffered request when its source completes during retry backoff" in
    StreamTestKit.assertAllStagesStopped {
      val setup = new Setup
      val request = HttpRequest(uri = "/during-backoff")
      val attempt = setup.start(request)
      val scheduler = system.scheduler.asInstanceOf[RetryScheduler]
      attempt.failure(new StreamTcpException("test connection attempt did not establish"))
      val delay = scheduler.expectBackoff()
      setup.input.sendComplete()
      setup.output.expectNoMessage(100.millis)
      scheduler.timePasses(delay)
      setup.establish(setup.nextAttempt())
      setup.expectOnlyOneResponseTo(request)
    }
  }
}
