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

package org.apache.pekko.http.impl

import java.lang.reflect.Method

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Guards the pekko-http internals that the OpenTelemetry Java agent attaches bytecode advice to.
 *
 * The agent matches these by name and signature. When a match stops applying the instrumentation is
 * silently disabled, so this spec fails loudly instead. Keep it in sync with
 * https://github.com/apache/pekko-http/issues/1241
 */
class InstrumentationPointsSpec extends AnyWordSpec with Matchers {

  private def methods(className: String): Array[Method] =
    Class.forName(className, false, getClass.getClassLoader).getMethods

  private def declaredMethods(className: String): Array[Method] =
    Class.forName(className, false, getClass.getClassLoader).getDeclaredMethods

  "The methods instrumented by the OpenTelemetry Java agent" should {

    "include HttpExt.singleRequest taking an HttpRequest" in {
      methods("org.apache.pekko.http.scaladsl.HttpExt").filter(_.getName == "singleRequest").exists(
        _.getParameterTypes.headOption.exists(
          _.getName == "org.apache.pekko.http.scaladsl.model.HttpRequest")) shouldBe true
    }

    "include Http.IncomingConnection.handleWith taking a Flow" in {
      methods("org.apache.pekko.http.scaladsl.Http$IncomingConnection").filter(_.getName == "handleWith").exists(
        _.getParameterTypes.headOption.exists(
          _.getName == "org.apache.pekko.stream.scaladsl.Flow")) shouldBe true
    }

    "include HttpServerBluePrint.requestPreparation returning a BidiFlow" in {
      methods("org.apache.pekko.http.impl.engine.server.HttpServerBluePrint$").filter(
        _.getName == "requestPreparation").exists(
        _.getReturnType.getName == "org.apache.pekko.stream.scaladsl.BidiFlow") shouldBe true
    }

    // the agent matches the mangled name, which only exists because a closure in the method body captures it
    "include the mangled accessor for PoolMasterActor.startPoolInterface" in {
      declaredMethods("org.apache.pekko.http.impl.engine.client.PoolMasterActor").exists(
        _.getName == "org$apache$pekko$http$impl$engine$client$PoolMasterActor$$startPoolInterface") shouldBe true
    }

    "include Http2Ext.bindAndHandleAsync and Http2Ext.system" in {
      val http2Ext = methods("org.apache.pekko.http.impl.engine.http2.Http2Ext")
      http2Ext.exists(_.getName == "bindAndHandleAsync") shouldBe true
      http2Ext.filter(_.getName == "system").exists(
        _.getReturnType.getName == "org.apache.pekko.actor.ActorSystem") shouldBe true
    }

    "include Http2.streamId" in {
      methods("org.apache.pekko.http.impl.engine.http2.Http2$").filter(_.getName == "streamId").exists(
        _.getReturnType.getName == "org.apache.pekko.http.scaladsl.model.AttributeKey") shouldBe true
    }
  }
}
