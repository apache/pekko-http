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

    "treat a missing request-order setting like an explicit opt-out" in {
      val key = "pekko.http.routing.preserve-range-request-order"
      val missing = RoutingSettings(config.withoutPath(key))
      val disabled = RoutingSettings(ConfigFactory.parseString(s"$key = off").withFallback(config))

      missing.preserveRangeRequestOrder shouldBe false
      disabled.preserveRangeRequestOrder shouldBe false
      missing shouldBe disabled
      missing.hashCode() shouldBe disabled.hashCode()
    }

    "retain request ordering in Scala and Java settings copies" in {
      val enabledConfig =
        ConfigFactory.parseString("pekko.http.routing.preserve-range-request-order = on").withFallback(config)
      val scalaSettings = RoutingSettings(enabledConfig).withRangeCountLimit(8).withRangeCoalescingThreshold(64L)
      val javaSettings = org.apache.pekko.http.javadsl.settings.RoutingSettings
        .create(enabledConfig)
        .withRangeCountLimit(8)
        .withRangeCoalescingThreshold(64L)
      val disabled = RoutingSettings(config.withoutPath("pekko.http.routing.preserve-range-request-order"))
        .withRangeCountLimit(8)
        .withRangeCoalescingThreshold(64L)

      scalaSettings.preserveRangeRequestOrder shouldBe true
      javaSettings shouldBe scalaSettings
      javaSettings.hashCode() shouldBe scalaSettings.hashCode()
      scalaSettings shouldNot be(disabled)
      scalaSettings.hashCode() shouldNot be(disabled.hashCode())
    }

  }

}
