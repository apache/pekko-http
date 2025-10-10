/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.server

import java.util.concurrent.{ CountDownLatch, TimeUnit }

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

import org.apache.pekko
import org.apache.pekko.stream.Materializer
import pekko.actor.ActorSystem
import pekko.event.{ LogSource, Logging }
import pekko.http.impl.util.WithLogCapturing
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model.{ HttpRequest, HttpResponse, Uri }
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.stream.testkit.Utils.assertAllStagesStopped
import pekko.testkit.TestKit

import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import com.typesafe.config.ConfigFactory

abstract class DontLeakActorsOnFailingConnectionSpecs(poolImplementation: String)
    extends AnyWordSpecLike with Matchers with BeforeAndAfterAll with WithLogCapturing {

  val config = ConfigFactory.parseString(s"""
    pekko {
      # disable logs (very noisy tests - 100 expected errors)
      loglevel = DEBUG
      loggers = ["org.apache.pekko.http.impl.util.SilenceAllTestEventListener"]

      http.host-connection-pool.pool-implementation = $poolImplementation

      http.host-connection-pool.base-connection-backoff = 0 ms
    }""").withFallback(ConfigFactory.load())
  implicit val system: ActorSystem = ActorSystem("DontLeakActorsOnFailingConnectionSpecs-" + poolImplementation, config)
  implicit val materializer: Materializer = Materializer.createMaterializer(system)

  val log = Logging(system, getClass)(LogSource.fromClass)

  "Http.superPool" should {

    "not leak connection Actors when hitting non-existing endpoint" in {
      assertAllStagesStopped {
        val reqsCount = 100
        val clientFlow = Http().superPool[Int]()
        // host that will reply, important because if it is a host not replying it will
        // take too long to fail
        val host = "127.0.0.1"
        val port = 86 // (Micro Focus Cobol) unlikely to be used port in the "system ports" range
        val source = Source(1 to reqsCount)
          .map(i => HttpRequest(uri = Uri(s"http://$host:$port/test/$i")) -> i)

        val countDown = new CountDownLatch(reqsCount)
        val sink = Sink.foreach[(Try[HttpResponse], Int)] {
          case (resp, id) =>
            countDown.countDown()
            handleResponse(resp, id)
        }

        val running = source.via(clientFlow).runWith(sink)
        countDown.await(10, TimeUnit.SECONDS) should be(true)
        Await.result(running, 10.seconds)
      }
    }
  }

  private def handleResponse(httpResp: Try[HttpResponse], id: Int): Unit = {
    httpResp match {
      case Success(httpRes) =>
        system.log.error(s"$id: OK: (${httpRes.status.intValue}")
        httpRes.entity.dataBytes.runWith(Sink.ignore)

      case Failure(ex) =>
        system.log.debug(s"$id: FAIL $ex") // this is what we expect
    }
  }

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)
}

class LegacyPoolDontLeakActorsOnFailingConnectionSpecs extends DontLeakActorsOnFailingConnectionSpecs("legacy")
class NewPoolDontLeakActorsOnFailingConnectionSpecs extends DontLeakActorsOnFailingConnectionSpecs("new")
