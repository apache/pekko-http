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

package org.apache.pekko.http.scaladsl

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.event.Logging
import pekko.http.scaladsl.model._
import pekko.stream.scaladsl._
import pekko.stream.OverflowStrategy
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.BeforeAndAfterAll
import scala.concurrent.duration._
import pekko.testkit._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TightRequestTimeoutSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll with ScalaFutures {
  val testConf: Config = ConfigFactory.parseString("""
    pekko.loggers = ["org.apache.pekko.testkit.TestEventListener"]
    pekko.loglevel = ERROR
    pekko.stdout-loglevel = ERROR
    windows-connection-abort-workaround-enabled = auto
    pekko.log-dead-letters = OFF
    pekko.http.server.request-timeout = 10ms""")

  implicit val system: ActorSystem = ActorSystem(getClass.getSimpleName, testConf)
  implicit val patience: PatienceConfig = PatienceConfig(3.seconds.dilated)

  override def afterAll() = TestKit.shutdownActorSystem(system)

  "Tight request timeout" should {

    "not cause double push error caused by the late response attempting to push" in {
      val slowHandler =
        Flow[HttpRequest].map(_ => HttpResponse()).delay(500.millis.dilated, OverflowStrategy.backpressure)
      val binding = Http().newServerAt("localhost", 0).bindFlow(slowHandler).futureValue
      val (hostname, port) = (binding.localAddress.getHostString, binding.localAddress.getPort)

      val p = TestProbe()
      system.eventStream.subscribe(p.ref, classOf[Logging.Error])

      val response = Http().singleRequest(HttpRequest(uri = s"http://$hostname:$port/")).futureValue
      response.status should ===(StatusCodes.ServiceUnavailable) // the timeout response

      p.expectNoMessage(1.second) // here the double push might happen

      binding.unbind().futureValue
    }

  }
}
