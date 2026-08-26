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

package org.apache.pekko.http.scaladsl.server

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Guards the route building blocks that the OpenTelemetry Java agent attaches bytecode advice to in order
 * to derive the `http.route` attribute. See https://github.com/apache/pekko-http/issues/1241
 */
class RouteInstrumentationPointsSpec extends AnyWordSpec with Matchers {

  "The route methods instrumented by the OpenTelemetry Java agent" should {

    "include the single argument Directive.tapply" in {
      classOf[Directive[Unit]].getMethods.filter(_.getName == "tapply").exists(
        _.getParameterTypes.length == 1) shouldBe true
    }

    "include PathMatcher.apply taking a Uri.Path on concrete matchers" in {
      PathMatchers.Segment.getClass.getMethods.filter(_.getName == "apply").exists(
        _.getParameterTypes.headOption.exists(
          _.getName == "org.apache.pekko.http.scaladsl.model.Uri$Path")) shouldBe true
    }

    "include PathMatcher.apply taking a Uri.Path and returning a PathMatcher" in {
      PathMatcher.getClass.getMethods.filter(_.getName == "apply").exists { m =>
        m.getParameterTypes.headOption.exists(_.getName == "org.apache.pekko.http.scaladsl.model.Uri$Path") &&
        m.getReturnType.getName == "org.apache.pekko.http.scaladsl.server.PathMatcher"
      } shouldBe true
    }
  }
}
