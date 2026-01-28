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

package org.apache.pekko.http.impl.engine.client

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.http.scaladsl.settings.{
  ClientConnectionSettings,
  ConnectionPoolSettings,
  HttpsProxySettings,
  ParserSettings,
  ServerSettings
}
import pekko.testkit.PekkoSpec
import com.typesafe.config.ConfigFactory

class HttpConfigurationSpec extends PekkoSpec {

  val On = true
  val Off = false

  "Reference configurations" should {
    "have default server `parsing` settings" in {
      // max-content-length defined specially for server
      ServerSettings(system).parserSettings.toString shouldEqual ParserSettings(system).withMaxContentLength(
        8 * 1024 * 1024).toString
    }
    "have default client `parsing` settings" in {
      // max-content-length defined specially for client
      ClientConnectionSettings(system).parserSettings.toString shouldEqual ParserSettings(system).withMaxContentLength(
        Long.MaxValue).toString
    }
    "have default client and pool `client` settings" in {
      ClientConnectionSettings(system).toString should ===(ConnectionPoolSettings(system).connectionSettings.toString)
    }
    "have empty string host  default client https proxy settings" in {
      assertThrows[IllegalArgumentException] {
        HttpsProxySettings(system)
      }
    }

    "override value from `pekko.http.parsing` by setting `pekko.http.client.parsing`" in {
      configuredSystem("""pekko.http.client.parsing.illegal-header-warnings = off""") { sys =>
        val client = ClientConnectionSettings(sys)
        client.parserSettings.illegalHeaderWarnings should ===(Off)

        val pool = ConnectionPoolSettings(sys)
        pool.connectionSettings.parserSettings.illegalHeaderWarnings should ===(Off)

        val server = ServerSettings(sys)
        server.parserSettings.illegalHeaderWarnings should ===(On)
      }
    }

    "override `pekko.http.parsing` by setting `pekko.http.host-connection-pool.client.parsing` setting" in {
      configuredSystem("""pekko.http.host-connection-pool.client.parsing.illegal-header-warnings = off""") { sys =>
        val client = ClientConnectionSettings(sys)
        client.parserSettings.illegalHeaderWarnings should ===(On)

        val pool = ConnectionPoolSettings(sys)
        pool.connectionSettings.parserSettings.illegalHeaderWarnings should ===(Off)

        val server = ServerSettings(sys)
        server.parserSettings.illegalHeaderWarnings should ===(On)
      }
    }

    "set `pekko.http.host-connection-pool.client.idle-timeout` only" in {
      configuredSystem("""pekko.http.host-connection-pool.client.idle-timeout = 1337s""") { sys =>
        import scala.concurrent.duration._

        val client = ClientConnectionSettings(sys)
        client.idleTimeout should ===(60.seconds)

        val pool = ConnectionPoolSettings(sys)
        pool.connectionSettings.idleTimeout should ===(1337.seconds)

        val server = ServerSettings(sys)
        server.idleTimeout should ===(60.seconds) // no change, default pekko.http.server.idle-timeout
      }
    }
    "set `pekko.http.server.idle-timeout` only" in {
      configuredSystem("""pekko.http.server.idle-timeout = 1337s""") { sys =>
        import scala.concurrent.duration._

        val client = ClientConnectionSettings(sys)
        client.idleTimeout should ===(60.seconds)

        val pool = ConnectionPoolSettings(sys)
        pool.connectionSettings.idleTimeout should ===(60.seconds)

        val server = ServerSettings(sys)
        server.idleTimeout should ===(1337.seconds)
      }
    }

    "change parser settings for all by setting `pekko.http.parsing`" in {
      configuredSystem("""pekko.http.parsing.illegal-header-warnings = off""") { sys =>
        val client = ClientConnectionSettings(sys)
        client.parserSettings.illegalHeaderWarnings should ===(Off)

        val pool = ConnectionPoolSettings(sys)
        pool.connectionSettings.parserSettings.illegalHeaderWarnings should ===(Off)

        val server = ServerSettings(sys)
        server.parserSettings.illegalHeaderWarnings should ===(Off)
      }
    }

    "change parser settings for all by setting `pekko.http.parsing`, unless client/server override it" in {
      configuredSystem("""
        pekko.http {
          parsing.illegal-header-warnings = off
          server.parsing.illegal-header-warnings = on
          client.parsing.illegal-header-warnings = on // also affects host-connection-pool.client
        }""") { sys =>
        val client = ClientConnectionSettings(sys)
        client.parserSettings.illegalHeaderWarnings should ===(On)

        val pool = ConnectionPoolSettings(sys)
        pool.connectionSettings.parserSettings.illegalHeaderWarnings should ===(On)

        val server = ServerSettings(sys)
        server.parserSettings.illegalHeaderWarnings should ===(On)
      }
    }

    "change parser settings for all by setting `pekko.http.parsing`, unless all override it" in {
      configuredSystem("""
        pekko.http {
          parsing.illegal-header-warnings = off
          server.parsing.illegal-header-warnings = on
          client.parsing.illegal-header-warnings = on
          host-connection-pool.client.parsing.illegal-header-warnings = off
        }""") { sys =>
        val client = ClientConnectionSettings(sys)
        client.parserSettings.illegalHeaderWarnings should ===(On)

        val pool = ConnectionPoolSettings(sys)
        pool.connectionSettings.parserSettings.illegalHeaderWarnings should ===(Off)

        val server = ServerSettings(sys)
        server.parserSettings.illegalHeaderWarnings should ===(On)
      }
    }

    "set `pekko.http.host-connection-pool.min-connections` only" in {
      configuredSystem(
        """
          pekko.http.host-connection-pool.min-connections = 42
          pekko.http.host-connection-pool.max-connections = 43
        """.stripMargin) { sys =>
        val pool = ConnectionPoolSettings(sys)
        pool.getMinConnections should ===(42)
        pool.getMaxConnections should ===(43)
      }

      configuredSystem(""" """) { sys =>
        val pool = ConnectionPoolSettings(sys)
        pool.minConnections should ===(0)
      }

      configuredSystem(
        """
          pekko.http.host-connection-pool.min-connections = 101
          pekko.http.host-connection-pool.max-connections = 1
        """.stripMargin) { sys =>
        intercept[IllegalArgumentException] { ConnectionPoolSettings(sys) }
      }
    }

    "set `pekko.http.client.proxy.https.host` only in" in {
      configuredSystem(
        """
          pekko.http.client.proxy.https.host = ""
        """) { sys =>
        assertThrows[IllegalArgumentException] {
          HttpsProxySettings(sys)
        }
      }
    }

    "set `pekko.http.client.proxy.https.port` only in" in {
      configuredSystem(
        """
          pekko.http.client.proxy.https.port = 8080
        """) { sys =>
        assertThrows[IllegalArgumentException] {
          HttpsProxySettings(sys)
        }
      }
    }

    "set `pekko.http.client.proxy.https.port` and `pekko.http.client.proxy.https.host` in" in {
      configuredSystem(
        """
          pekko.http.client.proxy.https.host = localhost
          pekko.http.client.proxy.https.port = 8080
        """.stripMargin) { sys =>
        val settings = HttpsProxySettings(sys)
        settings.host should ===("localhost")
        settings.port should ===(8080)
      }
    }
  }

  def configuredSystem(overrides: String)(block: ActorSystem => Unit) = {
    val config = ConfigFactory.parseString(overrides).withFallback(ConfigFactory.load())
    // we go via ActorSystem in order to hit the settings caching infrastructure
    val sys = ActorSystem("config-testing", config)
    try block(sys)
    finally sys.terminate()
  }

}
