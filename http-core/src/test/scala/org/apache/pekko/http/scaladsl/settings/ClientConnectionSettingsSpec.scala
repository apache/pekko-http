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
    "enable websocket compression only from an explicit client config override" in {
      import pekko.http.impl.settings.WebSocketSettingsImpl

      val section = system.settings.config.getConfig("pekko.http.client.websocket")
      val older = section.withoutPath("compression")
      WebSocketSettingsImpl.client(older).compression.clientEnabled shouldBe false
      WebSocketSettingsImpl.client(section).compression.clientEnabled shouldBe false

      val config = com.typesafe.config.ConfigFactory.parseString("compression.enabled = on").withFallback(section)
      val javaSettings = pekko.http.javadsl.settings.WebSocketSettings.client(config)
      javaSettings.asInstanceOf[WebSocketSettingsImpl].compression.clientEnabled shouldBe true
      val scalaSettings = ClientConnectionSettings("pekko.http.client.websocket.compression.enabled = on")
      scalaSettings.websocketSettings.clientCompressionEnabled shouldBe true
      val enabledServer = WebSocketSettingsImpl.server(
        config.withFallback(system.settings.config.getConfig("pekko.http.server.websocket")))
      enabledServer.compression.enabled shouldBe true
      enabledServer.compression.clientEnabled shouldBe false
    }

    "load client websocket compression limits and level independently of the opening timeout" in {
      import pekko.http.impl.settings.WebSocketSettingsImpl

      val defaults = system.settings.config.getConfig("pekko.http.client.websocket")
      val overrides = com.typesafe.config.ConfigFactory.parseString("""
        compression.enabled = on
        compression.max-allocation = 512k
        compression.permessage-deflate.compression-level = 3
        opening-timeout = 10s
        """)
      val javaSettings = pekko.http.javadsl.settings.WebSocketSettings.client(overrides.withFallback(defaults))
      val scalaSettings = ClientConnectionSettings("""
        pekko.http.client.websocket {
          compression.enabled = on
          compression.max-allocation = 512k
          compression.permessage-deflate.compression-level = 3
          opening-timeout = 10s
        }
        """).websocketSettings
      for (settings <- List(javaSettings, scalaSettings)) {
        val impl = settings.asInstanceOf[WebSocketSettingsImpl]
        impl.compression.clientEnabled shouldBe true
        impl.compression.maxAllocation shouldBe 512L * 1024
        impl.compression.compressionLevel shouldBe 3
        impl.openingTimeout shouldBe 10.seconds
      }
    }

    "set managed client websocket compression from Java and Scala without changing the other settings" in {
      import pekko.http.impl.settings.WebSocketSettingsImpl

      val original = ClientConnectionSettings("""
        pekko.http.client.websocket {
          compression.max-allocation = 512k
          compression.permessage-deflate.compression-level = 3
          opening-timeout = 10s
        }
        """)
      val javaClient: pekko.http.javadsl.settings.ClientConnectionSettings = original
      val javaEnabled =
        javaClient.withWebsocketSettings(javaClient.getWebsocketSettings.withClientCompressionEnabled(true))
      val scalaEnabled =
        original.withWebsocketSettings(original.websocketSettings.withClientCompressionEnabled(true))
      scalaEnabled.websocketSettings.clientCompressionEnabled shouldBe true
      for (settings <- List(javaEnabled.getWebsocketSettings, scalaEnabled.websocketSettings)) {
        val impl = settings.asInstanceOf[WebSocketSettingsImpl]
        settings.getClientCompressionEnabled shouldBe true
        impl.compression.enabled shouldBe false
        impl.compression.maxAllocation shouldBe 512L * 1024
        impl.compression.compressionLevel shouldBe 3
        impl.openingTimeout shouldBe 10.seconds
        settings.withClientCompressionEnabled(false).getClientCompressionEnabled shouldBe false
      }
      original.websocketSettings.clientCompressionEnabled shouldBe false
    }

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
    "configure the client WebSocket opening timeout" in {
      val original = ClientConnectionSettings(system).websocketSettings
      original.openingTimeout shouldBe Duration.Inf
      original.getOpeningTimeout shouldBe ChronoUnit.FOREVER.getDuration
      val updated = original.withOpeningTimeout(750.millis)
      updated.openingTimeout shouldBe 750.millis
      updated.getOpeningTimeout shouldBe java.time.Duration.ofMillis(750)
      updated.withCloseTimeout(1.second).openingTimeout shouldBe 750.millis
      val javaSettings = updated: pekko.http.javadsl.settings.WebSocketSettings
      javaSettings.withOpeningTimeout(ChronoUnit.FOREVER.getDuration).openingTimeout shouldBe Duration.Inf
      intercept[IllegalArgumentException](original.withOpeningTimeout(Duration.Zero))
      intercept[IllegalArgumentException](original.withOpeningTimeout(1.hour + 1.millis))
    }

    "read only the client WebSocket opening timeout from config" in {
      val clientConfig = system.settings.config.getConfig("pekko.http.client.websocket")
      org.apache.pekko.http.javadsl.settings.WebSocketSettings.client(clientConfig.withoutPath("opening-timeout"))
        .openingTimeout shouldBe Duration.Inf
      val finite = com.typesafe.config.ConfigFactory.parseString("opening-timeout = 750ms")
      org.apache.pekko.http.javadsl.settings.WebSocketSettings.client(finite.withFallback(clientConfig))
        .openingTimeout shouldBe 750.millis
      org.apache.pekko.http.javadsl.settings.WebSocketSettings
        .server(finite.withFallback(system.settings.config.getConfig("pekko.http.server.websocket")))
        .openingTimeout shouldBe Duration.Inf
      val invalid = com.typesafe.config.ConfigFactory.parseString("opening-timeout = 0ms").withFallback(clientConfig)
      intercept[ConfigurationException] {
        org.apache.pekko.http.javadsl.settings.WebSocketSettings.client(invalid)
      }.getMessage should include("opening-timeout")
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
