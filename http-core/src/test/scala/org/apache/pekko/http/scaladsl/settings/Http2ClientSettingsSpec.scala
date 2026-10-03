/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.http.scaladsl.settings

import java.time.temporal.ChronoUnit

import org.apache.pekko
import pekko.testkit.PekkoSpec

import scala.concurrent.duration._

class Http2ClientSettingsSpec extends PekkoSpec {

  "Http2ClientSettings persistent-connection-max-age" should {

    "be disabled by default" in {
      val settings = Http2ClientSettings(system)
      settings.persistentConnectionMaxAge should ===(Duration.Inf)
      settings.getPersistentConnectionMaxAge should ===(ChronoUnit.FOREVER.getDuration)
      settings.internalSettings should ===(None)
    }

    "accept a finite value from config" in {
      val settings = Http2ClientSettings("pekko.http.client.http2.persistent-connection-max-age = 2s")
      settings.persistentConnectionMaxAge should ===(2.seconds)
      settings.getPersistentConnectionMaxAge should ===(java.time.Duration.ofSeconds(2))
    }

    "round-trip an infinite value through the Java API" in {
      val settings = Http2ClientSettings(system).withPersistentConnectionMaxAge(Duration.Inf)
      settings.getPersistentConnectionMaxAge should ===(ChronoUnit.FOREVER.getDuration)
      val roundTripped = settings.withPersistentConnectionMaxAge(settings.getPersistentConnectionMaxAge)
      roundTripped.getPersistentConnectionMaxAge should ===(ChronoUnit.FOREVER.getDuration)
    }

    "round-trip a finite value through the Java API" in {
      val settings = Http2ClientSettings(system).withPersistentConnectionMaxAge(2.minutes)
      settings.getPersistentConnectionMaxAge should ===(java.time.Duration.ofMinutes(2))
      val roundTripped = settings.withPersistentConnectionMaxAge(settings.getPersistentConnectionMaxAge)
      roundTripped.getPersistentConnectionMaxAge should ===(java.time.Duration.ofMinutes(2))
    }

    "keep sub-millisecond precision through the Java API" in {
      val age = java.time.Duration.ofNanos(1)
      Http2ClientSettings(system).withPersistentConnectionMaxAge(age).getPersistentConnectionMaxAge should ===(age)
    }

    "reject a zero or negative value" in {
      intercept[IllegalArgumentException] {
        Http2ClientSettings("pekko.http.client.http2.persistent-connection-max-age = 0s")
      }
      intercept[IllegalArgumentException] {
        Http2ClientSettings("pekko.http.client.http2.persistent-connection-max-age = -1s")
      }
    }
  }

  "Http2ClientSettings persistent-connection-max-age-jitter" should {

    "default to 0.1" in {
      val settings = Http2ClientSettings(system)
      settings.persistentConnectionMaxAgeJitter should ===(0.1)
      settings.getPersistentConnectionMaxAgeJitter should ===(0.1)
    }

    "accept a value from config and programmatically" in {
      Http2ClientSettings("pekko.http.client.http2.persistent-connection-max-age-jitter = 0.25")
        .persistentConnectionMaxAgeJitter should ===(0.25)
      Http2ClientSettings(system).withPersistentConnectionMaxAgeJitter(0.2)
        .persistentConnectionMaxAgeJitter should ===(0.2)
    }

    "reject a value outside of [0, 1)" in {
      intercept[IllegalArgumentException] {
        Http2ClientSettings("pekko.http.client.http2.persistent-connection-max-age-jitter = -0.1")
      }
      intercept[IllegalArgumentException] {
        Http2ClientSettings("pekko.http.client.http2.persistent-connection-max-age-jitter = 1")
      }
    }
  }
}
