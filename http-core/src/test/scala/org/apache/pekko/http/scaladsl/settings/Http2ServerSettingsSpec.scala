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

import org.apache.pekko.testkit.PekkoSpec

import scala.concurrent.duration._

class Http2ServerSettingsSpec extends PekkoSpec {

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
}
