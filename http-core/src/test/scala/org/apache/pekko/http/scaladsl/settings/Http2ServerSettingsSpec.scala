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

  "Http2ServerSettings accept-connect-requests" should {
    "default off and support explicit config and JVM APIs" in {
      val defaults = Http2ServerSettings(system)
      defaults.acceptConnectRequests shouldBe false

      val configured = Http2ServerSettings("pekko.http.server.http2.accept-connect-requests = true")
      configured.acceptConnectRequests shouldBe true

      val javaSettings: javadsl.settings.Http2ServerSettings = defaults
      javaSettings.getAcceptConnectRequests shouldBe false
      javaSettings.withAcceptConnectRequests(true).getAcceptConnectRequests shouldBe true
      configured.withAcceptConnectRequests(false).getAcceptConnectRequests shouldBe false
    }

    "preserve independent settings when applying Scala or Java withers in either order" in {
      val defaults = Http2ServerSettings(system)
      val enabled = defaults.withAcceptConnectRequests(true)
      val updated = enabled.withRequestEntityChunkSize(8192)
      updated shouldBe defaults.withRequestEntityChunkSize(8192).withAcceptConnectRequests(true)
      updated.acceptConnectRequests shouldBe true
      updated.requestEntityChunkSize shouldBe 8192

      val javaSettings: javadsl.settings.Http2ServerSettings = defaults
      val javaUpdated = javaSettings.withAcceptConnectRequests(true).withRequestEntityChunkSize(8192)
      javaUpdated shouldBe javaSettings.withRequestEntityChunkSize(8192).withAcceptConnectRequests(true)
      javaUpdated.getAcceptConnectRequests shouldBe true
      javaUpdated.getRequestEntityChunkSize shouldBe 8192
    }

    "keep the flag independent of internal settings" in {
      val extra = new Http2InternalServerSettings {}
      val defaults = Http2ServerSettings(system)
      val configured = defaults.withAcceptConnectRequests(true)
      val withExtra = configured.withInternalSettings(extra)
      withExtra shouldBe defaults.withInternalSettings(extra).withAcceptConnectRequests(true)
      withExtra.acceptConnectRequests shouldBe true
      withExtra.internalSettings shouldBe Some(extra)
      withExtra.withAcceptConnectRequests(false).internalSettings shouldBe Some(extra)
      configured.internalSettings shouldBe None
    }

    "keep enabled and disabled settings in the same implementation and preserve the flag in a copy" in {
      val defaults = Http2ServerSettings(system)
      val enabled = defaults.withAcceptConnectRequests(true)
      enabled.getClass shouldBe defaults.getClass
      enabled should not be defaults
      defaults should not be enabled
      enabled shouldBe enabled.withRequestEntityChunkSize(enabled.requestEntityChunkSize)
      enabled.withAcceptConnectRequests(false) shouldBe defaults
      enabled.withFrameTypeThrottleFrameTypes(Set.empty[String]).copy().acceptConnectRequests shouldBe true
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
