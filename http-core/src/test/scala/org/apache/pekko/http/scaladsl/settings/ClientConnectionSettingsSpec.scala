/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.settings

import java.time.temporal.ChronoUnit

import scala.concurrent.duration._

import org.apache.pekko
import pekko.ConfigurationException
import pekko.testkit.PekkoSpec

class ClientConnectionSettingsSpec extends PekkoSpec {
  "ClientConnectionSettings" should {
    "round-trip an infinite idle-timeout through the Java API" in {
      val settings = ClientConnectionSettings(system).withIdleTimeout(Duration.Inf)
      settings.getIdleTimeout should ===(ChronoUnit.FOREVER.getDuration)
      val roundTripped = settings.withIdleTimeout(settings.getIdleTimeout)
      roundTripped.getIdleTimeout should ===(ChronoUnit.FOREVER.getDuration)
    }

    "round-trip an infinite websocket periodic-keep-alive-max-idle through the Java API" in {
      val settings = ClientConnectionSettings(system).websocketSettings.withPeriodicKeepAliveMaxIdle(Duration.Inf)
      settings.getPeriodicKeepAliveMaxIdle should ===(ChronoUnit.FOREVER.getDuration)
      val roundTripped = settings.withPeriodicKeepAliveMaxIdle(settings.getPeriodicKeepAliveMaxIdle)
      roundTripped.getPeriodicKeepAliveMaxIdle should ===(ChronoUnit.FOREVER.getDuration)
    }
    for ((side, settings) <- Seq(
        "client" -> ClientConnectionSettings(system).websocketSettings,
        "server" -> ServerSettings(system).websocketSettings)) {
      s"configure $side websocket graceful-close timing without changing other settings" in {
        settings.closeTimeout shouldBe 3.seconds
        settings.getCloseTimeout shouldBe java.time.Duration.ofSeconds(3)
        val updated = settings.withCloseTimeout(750.millis)
        updated.closeTimeout shouldBe 750.millis
        updated.getCloseTimeout shouldBe java.time.Duration.ofMillis(750)
        updated.withLogFrames(!settings.logFrames).closeTimeout shouldBe 750.millis
        settings.closeTimeout shouldBe 3.seconds
        updated should not be settings
        val javaSettings: org.apache.pekko.http.javadsl.settings.WebSocketSettings = updated
        javaSettings.withCloseTimeout(java.time.Duration.ofSeconds(2)).closeTimeout shouldBe 2.seconds
      }

      s"require a positive $side websocket graceful-close wait of at most one hour" in {
        intercept[IllegalArgumentException](settings.withCloseTimeout(Duration.Zero))
        intercept[IllegalArgumentException](settings.withCloseTimeout((-1).millis))
        settings.withCloseTimeout(1.hour).closeTimeout shouldBe 1.hour
        intercept[IllegalArgumentException](settings.withCloseTimeout(1.hour + 1.milli))
          .getMessage should include("at most 1 hour")
        val javaSettings: pekko.http.javadsl.settings.WebSocketSettings = settings
        intercept[IllegalArgumentException](javaSettings.withCloseTimeout(ChronoUnit.FOREVER.getDuration))
          .getMessage should include("must be finite")
      }
    }

    for (side <- Seq("client", "server")) {
      s"read $side websocket close-timeout and preserve defaults for an older subsection" in {
        val section = system.settings.config.getConfig(s"pekko.http.$side.websocket")
        def read(config: com.typesafe.config.Config): org.apache.pekko.http.javadsl.settings.WebSocketSettings =
          if (side == "client") org.apache.pekko.http.javadsl.settings.WebSocketSettings.client(config)
          else org.apache.pekko.http.javadsl.settings.WebSocketSettings.server(config)
        read(section.withoutPath("close-timeout")).closeTimeout shouldBe 3.seconds
        val custom = com.typesafe.config.ConfigFactory.parseString("close-timeout = 750ms").withFallback(section)
        read(custom).closeTimeout shouldBe 750.millis
        for (spelling <- Seq("infinite", "Inf")) {
          val infinite =
            com.typesafe.config.ConfigFactory.parseString(s"close-timeout = $spelling").withFallback(section)
          intercept[ConfigurationException](read(infinite)).getMessage shouldBe
          "Config setting 'close-timeout' must be a finite duration"
        }
        for (outOfRange <- Seq("0s", "300d")) {
          val invalid =
            com.typesafe.config.ConfigFactory.parseString(s"close-timeout = $outOfRange").withFallback(section)
          intercept[ConfigurationException](read(invalid)).getMessage shouldBe
          "Config setting 'close-timeout' must be > 0 and at most 1 hour"
        }
      }
    }

  }
}
