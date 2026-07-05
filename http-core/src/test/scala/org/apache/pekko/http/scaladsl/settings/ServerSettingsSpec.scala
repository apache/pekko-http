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
