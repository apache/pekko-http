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

package org.apache.pekko.http.impl.engine.client.pool

import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ThreadFactory

import com.typesafe.config.Config

import org.apache.pekko
import pekko.actor.{ Cancellable, LightArrayRevolverScheduler }
import pekko.event.LoggingAdapter
import pekko.http.impl.engine.client.PoolFlow.{ RequestContext, ResponseContext }
import pekko.http.impl.util._
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model.{ ContentTypes, HttpEntity, HttpRequest, HttpResponse }
import pekko.http.scaladsl.settings.ConnectionPoolSettings
import pekko.stream.scaladsl.{ Flow, Keep, Sink, Source }
import pekko.stream.testkit.{ TestPublisher, TestSubscriber }
import pekko.stream.testkit.scaladsl.{ TestSink, TestSource }
import pekko.util.ByteString

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

object NewHostConnectionPoolSpec {

  /** A delay no other part of the machinery schedules with, so that slot state timeouts can be told apart */
  val SlotStateTimeout = 7331.millis

  val slotStateTimeouts = new CopyOnWriteArrayList[Cancellable]

  /** Records the tasks scheduled for [[SlotStateTimeout]] so that a test can check whether they are cancelled */
  final class TrackingScheduler(config: Config, log: LoggingAdapter, threadFactory: ThreadFactory)
      extends LightArrayRevolverScheduler(config, log, threadFactory) {
    override def scheduleOnce(delay: FiniteDuration, runnable: Runnable)(
        implicit executor: ExecutionContext): Cancellable = {
      val cancellable = super.scheduleOnce(delay, runnable)
      if (delay == SlotStateTimeout) slotStateTimeouts.add(cancellable)
      cancellable
    }
  }
}

class NewHostConnectionPoolSpec extends PekkoSpecWithMaterializer(
      """
    pekko.scheduler.implementation = "org.apache.pekko.http.impl.engine.client.pool.NewHostConnectionPoolSpec$TrackingScheduler"
                                                                 """) {
  import NewHostConnectionPoolSpec._

  "The host connection pool" should {

    "cancel a pending slot state timeout when the pool is shut down" in {
      slotStateTimeouts.clear()

      val settings =
        ConnectionPoolSettings(system)
          .withMaxConnections(1)
          .withMinConnections(0)
          .withResponseEntitySubscriptionTimeout(SlotStateTimeout)

      val connectionRequests = TestSubscriber.probe[HttpRequest]()
      val connectionResponses = TestPublisher.probe[HttpResponse]()
      val connectionFlow =
        Flow.fromSinkAndSource(Sink.fromSubscriber(connectionRequests), Source.fromPublisher(connectionResponses))
          .mapMaterializedValue(_ => Future.successful(Http.OutgoingConnection(address, address)))

      val (requestsIn, responsesOut) =
        TestSource[RequestContext]()
          .via(NewHostConnectionPool(connectionFlow, settings, system.log))
          .toMat(TestSink[ResponseContext]())(Keep.both)
          .run()

      responsesOut.request(1)
      requestsIn.sendNext(RequestContext(HttpRequest(uri = "/"), Promise[HttpResponse](), 0))

      connectionRequests.requestNext()
      // a response whose entity is never subscribed to, which is what puts the slot on a state timeout
      connectionResponses.sendNext(
        HttpResponse(entity = HttpEntity.Chunked.fromData(ContentTypes.`text/plain(UTF-8)`, Source.maybe[ByteString])))
      responsesOut.expectNext()

      awaitCond(slotStateTimeouts.size == 1)

      // tear the pool down while the slot is still waiting for that subscription
      responsesOut.cancel()

      awaitCond(slotStateTimeouts.asScala.forall(_.isCancelled))
    }
  }

  private def address = new InetSocketAddress("127.0.0.1", 1)
}
