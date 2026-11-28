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
import pekko.http.javadsl
import pekko.testkit.PekkoSpec

import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory

class Http2ServerSettingsSpec extends PekkoSpec {

  "Http2ServerSettings max-concurrent-streams" should {
    "reject zero with a configuration error" in {
      val fromSetter = intercept[IllegalArgumentException] {
        Http2ServerSettings(system).withMaxConcurrentStreams(0)
      }
      fromSetter.getMessage should include("max-concurrent-streams must be > 0")

      val fromConfig = intercept[IllegalArgumentException] {
        Http2ServerSettings("pekko.http.server.http2.max-concurrent-streams = 0")
      }
      fromConfig.getMessage should include("max-concurrent-streams must be > 0")
    }

    "accept one and retain the configured value" in {
      val settings = Http2ServerSettings(system).withMaxConcurrentStreams(1)
      settings.maxConcurrentStreams shouldBe 1
      settings.getMaxConcurrentStreams shouldBe 1
    }
  }

  "Http2ServerSettings max-connection-age" should {

    "be disabled by default" in {
      val settings = Http2ServerSettings(system)
      settings.maxConnectionAge should ===(Duration.Inf)
      settings.getMaxConnectionAge should ===(ChronoUnit.FOREVER.getDuration)
    }

    "round-trip an infinite value through the Java API" in {
      val settings = Http2ServerSettings(system).withMaxConnectionAge(Duration.Inf)
      settings.getMaxConnectionAge should ===(ChronoUnit.FOREVER.getDuration)
      val roundTripped = settings.withMaxConnectionAge(settings.getMaxConnectionAge)
      roundTripped.getMaxConnectionAge should ===(ChronoUnit.FOREVER.getDuration)
    }

    "round-trip a finite value through the Java API" in {
      val settings = Http2ServerSettings(system).withMaxConnectionAge(2.minutes)
      settings.getMaxConnectionAge should ===(java.time.Duration.ofMinutes(2))
      val roundTripped = settings.withMaxConnectionAge(settings.getMaxConnectionAge)
      roundTripped.getMaxConnectionAge should ===(java.time.Duration.ofMinutes(2))
    }
  }

  "Http2ServerSettings max-connection-age-grace" should {

    "default to 30 seconds" in {
      Http2ServerSettings(system).maxConnectionAgeGrace should ===(30.seconds)
    }

    "accept 'infinite' from config" in {
      val settings = Http2ServerSettings("pekko.http.server.http2.max-connection-age-grace = infinite")
      settings.maxConnectionAgeGrace should ===(Duration.Inf)
    }

    "round-trip an infinite value through the Java API" in {
      val settings = Http2ServerSettings(system).withMaxConnectionAgeGrace(Duration.Inf)
      settings.getMaxConnectionAgeGrace should ===(ChronoUnit.FOREVER.getDuration)
      val roundTripped = settings.withMaxConnectionAgeGrace(settings.getMaxConnectionAgeGrace)
      roundTripped.getMaxConnectionAgeGrace should ===(ChronoUnit.FOREVER.getDuration)
    }
  }

  "Http2ServerSettings graceful-shutdown-advisory-timeout" should {
    val settingKey = "pekko.http.server.http2.graceful-shutdown-advisory-timeout"

    "disable the advisory by default and for a pre-existing config without the key" in {
      Http2ServerSettings(system).gracefulShutdownAdvisoryTimeout shouldBe Duration.Zero
      Http2ServerSettings(ConfigFactory.load().withoutPath(settingKey))
        .gracefulShutdownAdvisoryTimeout shouldBe Duration.Zero
    }

    "preserve independent legacy and programmatic settings through fluent setters and copy" in {
      val marker = new Http2InternalServerSettings {}
      val configured = Http2ServerSettings(s"$settingKey = 2 seconds").withInternalSettings(marker)
      val markerValue = configured.internalSettings
      configured.gracefulShutdownAdvisoryTimeout shouldBe 2.seconds

      val enabled = configured.withGracefulShutdownAdvisoryTimeout(3.seconds)
        .withMaxConnectionAgeGrace(30.seconds)
        .withFrameTypeThrottleFrameTypes(Set.empty[String])
      enabled.gracefulShutdownAdvisoryTimeout shouldBe 3.seconds
      enabled.maxConnectionAgeGrace shouldBe 30.seconds
      (enabled.internalSettings should be).theSameInstanceAs(markerValue)

      val replacement = Some(new Http2InternalServerSettings {})
      val copied = enabled.copy(internalSettings = replacement)
      copied.gracefulShutdownAdvisoryTimeout shouldBe 3.seconds
      (copied.internalSettings should be).theSameInstanceAs(replacement)

      val disabled = copied.withGracefulShutdownAdvisoryTimeout(Duration.Zero)
      disabled.gracefulShutdownAdvisoryTimeout shouldBe Duration.Zero
      (disabled.internalSettings should be).theSameInstanceAs(replacement)
      configured.gracefulShutdownAdvisoryTimeout shouldBe 2.seconds
    }

    "not change the client settings when configured on the server" in {
      Http2ClientSettings(s"$settingKey = 2 seconds") shouldBe Http2ClientSettings("")
    }

    "reject a negative advisory timeout through both configuration and the Scala setter" in {
      intercept[IllegalArgumentException] {
        Http2ServerSettings(s"$settingKey = -1 second")
      }.getMessage should include("graceful-shutdown-advisory-timeout must be >= 0")
      intercept[IllegalArgumentException] {
        Http2ServerSettings(system).withGracefulShutdownAdvisoryTimeout(Duration.Zero - 1.second)
      }.getMessage should include("graceful-shutdown-advisory-timeout must be >= 0")
    }
  }

  "Http2ServerSettings frame-type-throttle" should {

    "set the frame types, cost and burst through the Java API" in {
      val frameTypes = java.util.Collections.singleton("reset")
      val settings: javadsl.settings.Http2ServerSettings = Http2ServerSettings(system)
      val updated = settings.withFrameTypeThrottleFrameTypes(frameTypes)
        .withFrameTypeThrottleCost(5)
        .withFrameTypeThrottleBurst(7)
      updated.getFrameTypeThrottleFrameTypes() should ===(frameTypes)
      updated.getFrameTypeThrottleCost() should ===(5)
      updated.getFrameTypeThrottleBurst() should ===(7)
    }

    "accept a burst of zero" in {
      Http2ServerSettings("pekko.http.server.http2.frame-type-throttle.burst = 0").frameTypeThrottleBurst should ===(0)
    }

    "reject a zero or negative cost" in {
      intercept[IllegalArgumentException] {
        Http2ServerSettings("pekko.http.server.http2.frame-type-throttle.cost = 0")
      }
      intercept[IllegalArgumentException] {
        Http2ServerSettings("pekko.http.server.http2.frame-type-throttle.cost = -1")
      }
    }

    "reject a negative burst" in {
      intercept[IllegalArgumentException] {
        Http2ServerSettings("pekko.http.server.http2.frame-type-throttle.burst = -1")
      }
    }

    "accept an invalid cost and burst when no frame types are throttled" in {
      val settings = Http2ServerSettings("""
        pekko.http.server.http2.frame-type-throttle {
          frame-types = []
          cost = 0
          burst = -1
        }
      """)
      settings.frameTypeThrottleCost should ===(0)
      settings.frameTypeThrottleBurst should ===(-1)
    }
  }
}
