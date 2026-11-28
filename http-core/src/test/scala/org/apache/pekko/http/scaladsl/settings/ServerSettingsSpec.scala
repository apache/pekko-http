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

import com.typesafe.config.ConfigFactory
import org.apache.pekko.testkit.PekkoSpec

class ServerSettingsSpec extends PekkoSpec {
  "ServerSettings" should {
    "read and copy an independent HTTP/1 field-section limit for each parser settings scope" in {
      val cfg = ConfigFactory.parseString("""
        pekko.http.parsing.max-http1-field-section-size = 24
        pekko.http.server.parsing.max-http1-field-section-size = 20
        pekko.http.host-connection-pool.client.parsing.max-http1-field-section-size = 32
      """).withFallback(system.settings.config).resolve()
      val server = ServerSettings(cfg).parserSettings
      server.maxHttp1FieldSectionSize shouldBe 20L
      ClientConnectionSettings(cfg).parserSettings.maxHttp1FieldSectionSize shouldBe 24L
      ConnectionPoolSettings(cfg).connectionSettings.parserSettings.maxHttp1FieldSectionSize shouldBe 32L
      server.withMaxHttp1FieldSectionSize(21L).withMaxHeaderCount(8).maxHttp1FieldSectionSize shouldBe 21L
      server.withMaxHttp1FieldSectionSize(Long.MaxValue).maxHttp1FieldSectionSize shouldBe Long.MaxValue
      intercept[IllegalArgumentException](server.withMaxHttp1FieldSectionSize(0L))
      server.maxHttp1FieldSectionSize shouldBe 20L
      ServerSettings(system).parserSettings.maxHttp1FieldSectionSize shouldBe Long.MaxValue
    }

    "default enableHttp2 to true" in {
      val serverSettings = ServerSettings(system)
      serverSettings.enableHttp2 should ===(true)
    }
    "set enableHttp2 to false if enable-http2 is off" in {
      val cfg = ConfigFactory.parseString("""
        pekko.http.server {
          enable-http2 = off
        }
      """).withFallback(system.settings.config)
      val serverSettings = ServerSettings(cfg)
      serverSettings.enableHttp2 should ===(false)
    }
    "set enableHttp2 to true if enable-http2 is on and preview.enable-http2 is off" in {
      val cfg = ConfigFactory.parseString("""
        pekko.http.server {
          enable-http2 = on
          preview.enable-http2 = off
        }
      """).withFallback(system.settings.config)
      val serverSettings = ServerSettings(cfg)
      serverSettings.enableHttp2 should ===(true)
    }
    "set enableHttp2 to false if enable-http2 is off and preview.enable-http2 is off" in {
      val cfg = ConfigFactory.parseString("""
        pekko.http.server {
          enable-http2 = off
          preview.enable-http2 = off
        }
      """).withFallback(system.settings.config)
      val serverSettings = ServerSettings(cfg)
      serverSettings.enableHttp2 should ===(false)
    }
    "set enableHttp2 to true if enable-http2 is off and preview.enable-http2 is on" in {
      val cfg = ConfigFactory.parseString("""
        pekko.http.server {
          enable-http2 = off
          preview.enable-http2 = on
        }
      """).withFallback(system.settings.config)
      val serverSettings = ServerSettings(cfg)
      serverSettings.enableHttp2 should ===(true)
    }
  }
}
