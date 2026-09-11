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

package org.apache.pekko.http.impl.engine.client

import org.apache.pekko
import pekko.http.impl.engine.client.PoolFlow.{ RequestContext, ResponseContext }
import pekko.http.impl.settings.{ ConnectionPoolSetup, HostConnectionPoolSetup }
import pekko.http.impl.util._
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model.{ HttpRequest, HttpResponse }
import pekko.http.scaladsl.settings.ConnectionPoolSettings
import pekko.stream.scaladsl.{ Flow, Sink, Source }
import pekko.stream.testkit.{ TestPublisher, TestSubscriber }
import pekko.testkit._

import scala.concurrent.{ Await, Promise }
import scala.concurrent.duration._

class PoolInterfaceSpec extends PekkoSpecWithMaterializer {

  "The pool interface" should {

    "fail requests that are still queued when the pool stops" in {
      val settings = ConnectionPoolSettings(system).withMaxConnections(1).withMaxOpenRequests(4)
      // nothing is ever dispatched to this endpoint, it only identifies the pool
      val poolId = new PoolId(
        HostConnectionPoolSetup("127.0.0.1", 1, ConnectionPoolSetup(settings, log = system.log)),
        PoolId.newUniquePool())

      val requestsToPool = TestSubscriber.probe[RequestContext]()
      val responsesFromPool = TestPublisher.probe[ResponseContext]()
      val poolInterface =
        Flow.fromGraph(
          new PoolInterface.PoolInterfaceStage(poolId, Http().poolMaster, settings.maxOpenRequests, system.log))
          .join(Flow.fromSinkAndSource(Sink.fromSubscriber(requestsToPool), Source.fromPublisher(responsesFromPool)))
          .run()

      // the pool never signals demand, so these requests only ever reach the interface's buffer
      val queuedRequests = Vector.fill(3)(Promise[HttpResponse]())
      queuedRequests.foreach(poolInterface.request(HttpRequest(uri = "/"), _))

      // the pool flow going away takes the interface with it
      responsesFromPool.sendComplete()
      requestsToPool.expectSubscriptionAndComplete()

      queuedRequests.foreach { responsePromise =>
        Await.result(responsePromise.future.failed, 3.seconds.dilated) shouldBe an[IllegalStateException]
      }
    }
  }
}
