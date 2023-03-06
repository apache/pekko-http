/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.settings

import org.apache.pekko
import pekko.testkit.PekkoSpec
import pekko.http.scaladsl.model.headers.`User-Agent`
import com.typesafe.config.ConfigFactory

class ConnectionPoolSettingsSpec extends PekkoSpec {
  "ConnectionPoolSettings" should {
    "use pekko.http.client settings by default" in {
      val settings = config(
        """
          pekko.http.client.user-agent-header = "serva/0.0"
        """)

      settings.connectionSettings.userAgentHeader shouldEqual Some(
        `User-Agent`.parseFromValueString("serva/0.0").right.get)
    }
    "allow overriding client settings with pekko.http.host-connection-pool.client" in {
      val settings = config(
        """
          pekko.http.client.request-header-size-hint = 1024
          pekko.http.client.user-agent-header = "serva/0.0"
          pekko.http.host-connection-pool.client.user-agent-header = "serva/5.7"
        """)

      settings.connectionSettings.userAgentHeader shouldEqual Some(
        `User-Agent`.parseFromValueString("serva/5.7").right.get)
      settings.connectionSettings.requestHeaderSizeHint shouldEqual 1024 // still fall back
    }
    "allow max-open-requests = 1" in {
      config("pekko.http.host-connection-pool.max-open-requests = 1").maxOpenRequests should be(1)
    }
    "allow max-open-requests = 42" in {
      config("pekko.http.host-connection-pool.max-open-requests = 42").maxOpenRequests should be(42)
    }
    "allow per host overrides" in {

      val settingsString =
        """
          |pekko.http.host-connection-pool {
          |  max-connections = 7
          |
          |  per-host-override : [
          |    {
          |      host-pattern = "pekko.apache.org"
          |      # can use same things as in global `host-connection-pool` section
          |      max-connections = 47
          |    },
          |   {
          |     host-pattern = "*.example.com"
          |     # allow `*` to apply overrides for all subdomains
          |     max-connections = 34
          |   },
          |   {
          |     host-pattern = "glob:*example2.com"
          |     max-connections = 39
          |   },
          |   {
          |     host-pattern = "regex:((w{3})?\\.)?scala-lang\\.(com|org)"
          |     max-connections = 36
          |   }
          |  ]
          |}
        """.stripMargin

      val settings = ConnectionPoolSettings(
        ConfigFactory.parseString(settingsString)
          .withFallback(ConfigFactory.defaultReference(getClass.getClassLoader)))

      settings.forHost("pekko.apache.org").maxConnections shouldEqual 47
      settings.forHost("test.pekko.apache.org").maxConnections shouldEqual 7
      settings.forHost("example.com").maxConnections shouldEqual 34
      settings.forHost("www.example.com").maxConnections shouldEqual 34
      settings.forHost("example2.com").maxConnections shouldEqual 39
      settings.forHost("www.example2.com").maxConnections shouldEqual 39
      settings.forHost("www.someexample2.com").maxConnections shouldEqual 39
      settings.forHost("test.example.com").maxConnections shouldEqual 34
      settings.forHost("lightbend.com").maxConnections shouldEqual 7
      settings.forHost("www.scala-lang.org").maxConnections shouldEqual 36
      settings.forHost("scala-lang.org").maxConnections shouldEqual 36
      settings.forHost("ww.scala-lang.org").maxConnections shouldEqual 7
      settings.forHost("scala-lang.com").maxConnections shouldEqual 36
    }

    "allow overriding values from code" in {
      val settingsString =
        """
          |pekko.http.host-connection-pool {
          |  max-connections = 7
          |
          |  per-host-override = [
          |    {
          |      host-pattern = "pekko.apache.org"
          |      # can use same things as in global `host-connection-pool` section
          |      max-connections = 47
          |    }
          |  ]
          |}
        """.stripMargin

      val settings = ConnectionPoolSettings(
        ConfigFactory.parseString(settingsString).withFallback(ConfigFactory.defaultReference(getClass.getClassLoader)))
      settings.forHost("pekko.apache.org").maxConnections shouldEqual 47
      settings.maxConnections shouldEqual 7

      val settingsWithCodeOverrides = settings.withMaxConnections(42)
      settingsWithCodeOverrides.forHost("pekko.apache.org").maxConnections shouldEqual 42
      settingsWithCodeOverrides.maxConnections shouldEqual 42
    }

    "choose the first matching override when there are multiple" in {
      val settingsString =
        """
          |pekko.http.host-connection-pool {
          |  min-connections = 2
          |  max-connections = 7
          |
          |  per-host-override = [
          |    {
          |      host-pattern = "pekko.apache.org"
          |      # can use same things as in global `host-connection-pool` section
          |      max-connections = 27
          |    },
          |    {
          |      host-pattern = "*.io"
          |      # can use same things as in global `host-connection-pool` section
          |      min-connections = 22
          |      max-connections = 47
          |    }
          |  ]
          |}
        """.stripMargin

      val settings = ConnectionPoolSettings(
        ConfigFactory.parseString(settingsString).withFallback(ConfigFactory.defaultReference(getClass.getClassLoader)))
      settings.forHost("pekko.apache.org").maxConnections shouldEqual 27
      settings.forHost("other.io").maxConnections shouldEqual 47
      settings.forHost("akka.com").maxConnections shouldEqual 7
      settings.maxConnections shouldEqual 7

      // the '*.io' overrides are not selected, because pekko.apache.org occurs earlier:
      settings.forHost("pekko.apache.org").minConnections shouldEqual 2
      settings.forHost("other.io").minConnections shouldEqual 22
      settings.forHost("akka.com").minConnections shouldEqual 2
      settings.minConnections shouldEqual 2
    }
  }

  def config(configString: String): ConnectionPoolSettings =
    ConnectionPoolSettings(configString)
}
