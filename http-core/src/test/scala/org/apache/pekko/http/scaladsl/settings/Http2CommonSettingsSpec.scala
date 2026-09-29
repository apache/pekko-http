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

import org.apache.pekko.testkit.PekkoSpec

import scala.concurrent.duration._

class Http2CommonSettingsSpec extends PekkoSpec {

  "HTTP2 persistent client settings" should {
    "disable connection age retirement by default and parse overrides" in {
      val default = Http2ClientSettings(system)
      default.internalSettings shouldBe None
      default.persistentConnectionMaxAge shouldBe Duration.Zero
      default.getPersistentConnectionMaxAge shouldBe java.time.Duration.ZERO
      default.persistentConnectionMaxAgeJitter shouldBe 0.1
      default.getPersistentConnectionMaxAgeJitter shouldBe 0.1

      val configured = Http2ClientSettings(
        """
          pekko.http.client.http2.persistent-connection-max-age = 2s
          pekko.http.client.http2.persistent-connection-max-age-jitter = 0.25
        """)
      configured.persistentConnectionMaxAge shouldBe 2.seconds
      configured.getPersistentConnectionMaxAge shouldBe java.time.Duration.ofSeconds(2)
      configured.persistentConnectionMaxAgeJitter shouldBe 0.25

      default.withPersistentConnectionMaxAge(3.seconds).persistentConnectionMaxAge shouldBe 3.seconds
      default.withPersistentConnectionMaxAge(java.time.Duration.ofSeconds(4)).getPersistentConnectionMaxAge shouldBe
      java.time.Duration.ofSeconds(4)
      val subMillisecondAge = java.time.Duration.ofNanos(1)
      default.withPersistentConnectionMaxAge(subMillisecondAge).getPersistentConnectionMaxAge shouldBe subMillisecondAge
      default.withPersistentConnectionMaxAgeJitter(0.2).persistentConnectionMaxAgeJitter shouldBe 0.2
    }

    "validate persistent connection age and jitter" in {
      intercept[IllegalArgumentException] {
        Http2ClientSettings("pekko.http.client.http2.persistent-connection-max-age = -1s")
      }
      intercept[IllegalArgumentException] {
        Http2ClientSettings("pekko.http.client.http2.persistent-connection-max-age-jitter = -0.1")
      }
      intercept[IllegalArgumentException] {
        Http2ClientSettings("pekko.http.client.http2.persistent-connection-max-age-jitter = 1")
      }
    }
  }

  "Validation of HTTP2 settings" should {

    "require ping-timeout to be evenly divisable by ping-interval" in {
      import Http2CommonSettings.validate
      val default = Http2ClientSettings(system)
      validate(default) // default is disabled, should be ok
      validate(default.withPingInterval(4.seconds)) // undefined timeout means same as interval
      validate(default.withPingTimeout(2.seconds)) // if ping not enabled (interval 0) it does not matter
      validate(default.withPingInterval(4.seconds).withPingTimeout(0.seconds)) // undefined timeout means same as interval
      validate(default.withPingInterval(4.seconds).withPingTimeout(2.seconds)) // evenly divisible is ok
      intercept[IllegalArgumentException] {
        validate(default.withPingInterval(4.seconds).withPingTimeout(502.millis)) // not evenly divisible should throw
      }
      intercept[IllegalArgumentException] {
        validate(default.withPingInterval(4.seconds).withPingTimeout(5.seconds)) // larger than interval should throw
      }
    }

  }

}
