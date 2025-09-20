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

package org.apache.pekko.http.scaladsl.settings

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import com.typesafe.config.ConfigFactory

class RoutingSettingsEqualitySpec extends AnyWordSpec with Matchers {

  val config = ConfigFactory.load.resolve

  "equality" should {

    "hold for RoutingSettings" in {
      val s1 = RoutingSettings(config)
      val s2 = RoutingSettings(config)

      s1 shouldBe s2
      s1.toString should startWith("RoutingSettings(")
    }

  }

}
