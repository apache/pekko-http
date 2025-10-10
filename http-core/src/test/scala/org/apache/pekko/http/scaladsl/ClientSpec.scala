/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.HttpMethods._
import com.typesafe.config.{ Config, ConfigFactory }
import scala.concurrent.duration._
import scala.concurrent.Await
import org.scalatest.BeforeAndAfterAll
import pekko.testkit._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ClientSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {
  val testConf: Config = ConfigFactory.parseString("""
    pekko.loggers = ["org.apache.pekko.testkit.TestEventListener"]
    pekko.loglevel = ERROR
    pekko.stdout-loglevel = ERROR
    windows-connection-abort-workaround-enabled = auto
    pekko.log-dead-letters = OFF
    pekko.http.server.request-timeout = infinite""")
  implicit val system: ActorSystem = ActorSystem(getClass.getSimpleName, testConf)

  override def afterAll() = TestKit.shutdownActorSystem(system)

  "HTTP Client" should {

    "reuse connection pool" in {
      val bindingFuture = Http().newServerAt("localhost", 0).bindSync(_ => HttpResponse())
      val binding = Await.result(bindingFuture, 3.seconds.dilated)
      val port = binding.localAddress.getPort

      val respFuture = Http().singleRequest(HttpRequest(POST, s"http://localhost:$port/"))
      val resp = Await.result(respFuture, 3.seconds.dilated)
      resp.status shouldBe StatusCodes.OK

      Await.result(Http().poolSize, 1.second.dilated) shouldEqual 1

      Http().singleRequest(HttpRequest(POST, s"http://localhost:$port/"))
      val resp2 = Await.result(respFuture, 3.seconds.dilated)
      resp2.status shouldBe StatusCodes.OK

      Await.result(Http().poolSize, 1.second.dilated) shouldEqual 1

      Await.ready(binding.unbind(), 1.second.dilated)
    }
  }
}
