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

package org.apache.pekko.http

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.testkit.PekkoSpec
import org.scalatest.Assertions
import com.typesafe.config.ConfigFactory
import pekko.http.scaladsl.model.headers.`User-Agent`
import pekko.http.scaladsl.model.headers.Server
import pekko.http.scaladsl.settings.{ ClientConnectionSettings, ServerSettings }

class ConfigSpec extends PekkoSpec(ConfigFactory.defaultReference(ActorSystem.findClassLoader())) with Assertions {

  "The default configuration file (i.e. reference.conf)" must {
    "include the generated version file (i.e. pekko-http-version.conf)" in {
      val settings = system.settings
      val config = settings.config

      config.getString("pekko.http.version") should ===(Version.current)

      val versionString = "pekko-http/" + Version.current
      val serverSettings = ServerSettings(system)
      serverSettings.serverHeader should ===(Some(Server(versionString)))

      val clientConnectionSettings = ClientConnectionSettings(system)
      clientConnectionSettings.userAgentHeader should ===(Some(`User-Agent`(versionString)))
    }
  }
}
