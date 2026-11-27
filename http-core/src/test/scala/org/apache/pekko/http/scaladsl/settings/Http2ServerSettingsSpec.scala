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

  "The server-wide OPTIONS opt-in" should {
    "stay disabled when an existing config does not contain the key" in {
      val config = ConfigFactory.load().withoutPath("pekko.http.server.http2.accept-asterisk-options")
      Http2ServerSettings(config).acceptAsteriskOptions shouldBe false
    }

    "allow Java and Scala callers to opt in or out without changing other settings" in {
      val original = Http2ServerSettings(system)
      val internal = new Http2InternalServerSettings {}
      val javaServer: javadsl.settings.ServerSettings =
        ServerSettings(system).withHttp2Settings(original.withInternalSettings(internal))
      val javaSettings = javaServer.getHttp2Settings
      javaSettings.getAcceptAsteriskOptions shouldBe false

      val enabled = javaServer.withHttp2Settings(javaSettings.withAcceptAsteriskOptions(true))
      enabled.getHttp2Settings.getAcceptAsteriskOptions shouldBe true
      javaSettings.getAcceptAsteriskOptions shouldBe false

      val scalaSettings = original.withAcceptAsteriskOptions(true).withInternalSettings(internal)
      scalaSettings.acceptAsteriskOptions shouldBe true
      val disabled = scalaSettings.withAcceptAsteriskOptions(false).withMaxConcurrentStreams(128)
      disabled.acceptAsteriskOptions shouldBe false
      disabled.internalSettings shouldBe Some(internal)
      disabled.maxConcurrentStreams shouldBe 128
    }

    "preserve the opt-in and other internal settings independently" in {
      val settings = Http2ServerSettings("pekko.http.server.http2.accept-asterisk-options = on")
      settings.internalSettings shouldBe None
      val existingInternal = new Http2InternalServerSettings {}
      val updated = settings.withInternalSettings(existingInternal).withMaxConcurrentStreams(128)
      updated.acceptAsteriskOptions shouldBe true
      updated.internalSettings shouldBe Some(existingInternal)
    }

    "transfer only the legacy value when receiving the internal slot from another settings instance" in {
      val off = Http2ServerSettings(system)
      val on = Http2ServerSettings("pekko.http.server.http2.accept-asterisk-options = on")
      val legacy = new Http2InternalServerSettings {}
      val seed = on.withInternalSettings(legacy)

      val defaultDestination = off.withInternalSettings(seed.internalSettings.get)
      defaultDestination.acceptAsteriskOptions shouldBe false
      (defaultDestination.internalSettings.get should be).theSameInstanceAs(legacy)

      val enabledDestination = on.withInternalSettings(seed.internalSettings.get)
      enabledDestination.acceptAsteriskOptions shouldBe true
      (enabledDestination.internalSettings.get should be).theSameInstanceAs(legacy)
      on.internalSettings shouldBe None
      off.internalSettings shouldBe None
    }

    "preserve the raw copy contract and compose with fluent setters in either order" in {
      val off = Http2ServerSettings(system)
      val legacy = new Http2InternalServerSettings {}
      val initial = off.withInternalSettings(legacy)
      initial.internalSettings shouldBe Some(legacy)
      val scalaFirst = initial.withAcceptAsteriskOptions(true).withMaxConcurrentStreams(128)
      val java: javadsl.settings.Http2ServerSettings = off
      val javaFirst =
        java.withMaxConcurrentStreams(128).withAcceptAsteriskOptions(true).asInstanceOf[Http2ServerSettings]
          .withInternalSettings(legacy)
      javaFirst shouldBe scalaFirst
      (scalaFirst.withAcceptAsteriskOptions(false).internalSettings should be).theSameInstanceAs(
        initial.internalSettings)

      val inferred = scalaFirst.withFrameTypeThrottleFrameTypes(Set.empty[String])
      val replacement = Some(new Http2InternalServerSettings {})
      val replaced = inferred.copy(internalSettings = replacement)
      replaced.acceptAsteriskOptions shouldBe true
      (replaced.internalSettings should be).theSameInstanceAs(replacement)
      val cleared = replaced.copy(internalSettings = None)
      cleared.acceptAsteriskOptions shouldBe true
      cleared.internalSettings shouldBe None
      val restored = cleared.copy(internalSettings = inferred.internalSettings)
      restored.acceptAsteriskOptions shouldBe true
      restored.internalSettings shouldBe Some(legacy)
    }

    "survive copying settings returned by an existing inferred Scala setter" in {
      val configured = Http2ServerSettings(system)
        .withAcceptAsteriskOptions(true)
        .withFrameTypeThrottleFrameTypes(Set.empty[String])
      val copied = configured.copy(maxConcurrentStreams = 64)
      copied.acceptAsteriskOptions shouldBe true
      copied.maxConcurrentStreams shouldBe 64
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
