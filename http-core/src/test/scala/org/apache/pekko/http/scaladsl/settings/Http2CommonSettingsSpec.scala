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

  "Validation of HTTP2 settings" should {

    "read separate outbound HPACK insertion policies and preserve them across settings copies" in {
      val server = Http2ServerSettings(
        """pekko.http.server.http2.hpack-no-index-headers = ["X-Synthetic-Trace", "x-synthetic-trace"]""")
      val client = Http2ClientSettings(
        """pekko.http.client.http2.hpack-no-index-headers = ["X-Local-Label"]""")
      server.withMaxConcurrentStreams(50).hpackPolicyOptions.get.names shouldBe Set("x-synthetic-trace")
      client.withMaxConcurrentStreams(50).hpackPolicyOptions.get.names shouldBe Set("x-local-label")
      Http2ServerSettings(system).hpackPolicyOptions shouldBe empty
      Http2ClientSettings(system).hpackPolicyOptions shouldBe empty
    }

    "preserve internal settings and programmatic policies independently" in {
      val internalClient = new Http2InternalClientSettings {}
      val internalServer = new Http2InternalServerSettings {}
      val policy: org.apache.pekko.http.javadsl.settings.HpackIndexingPolicy = (_, _) => true
      val client = Http2ClientSettings(system)
      val server = Http2ServerSettings(system)
      val clientWithBoth = client.withHpackIndexingPolicy(policy).withInternalSettings(internalClient)
      val serverWithBoth = server.withInternalSettings(internalServer).withHpackIndexingPolicy(policy)
      clientWithBoth shouldBe client.withInternalSettings(internalClient).withHpackIndexingPolicy(policy)
      serverWithBoth shouldBe server.withHpackIndexingPolicy(policy).withInternalSettings(internalServer)
      clientWithBoth.internalSettings shouldBe Some(internalClient)
      serverWithBoth.internalSettings shouldBe Some(internalServer)
      clientWithBoth.hpackPolicyOptions.get.policy shouldBe theSameInstanceAs(policy)
      serverWithBoth.hpackPolicyOptions.get.policy shouldBe theSameInstanceAs(policy)
    }

    "preserve configured policies across ordinary case class copies" in {
      val default = Http2ServerSettings(system).withFrameTypeThrottleInterval(2.seconds)
      val configured = Http2ServerSettings(
        """pekko.http.server.http2.hpack-no-index-headers = ["x-synthetic-trace"]""")
        .withFrameTypeThrottleInterval(2.seconds)

      (configured should not).equal(default)
      configured.copy() shouldBe configured
      configured.copy().hashCode shouldBe configured.hashCode
      configured.copy(logFrames = true).hpackPolicyOptions shouldBe configured.hpackPolicyOptions
      configured.internalSettings shouldBe empty
      configured.toString should include("HpackPolicyOptions(<configured>)")

      val client = Http2ClientSettings(
        """pekko.http.client.http2.hpack-no-index-headers = ["x-client-label"]""")
        .asInstanceOf[Http2ClientSettings.Http2ClientSettingsImpl]
      client.copy() shouldBe client
      client.copy(logFrames = true).hpackPolicyOptions shouldBe client.hpackPolicyOptions
      client.internalSettings shouldBe empty
    }

    "apply a value-dependent policy configured through Java HTTP/2 settings" in {
      import org.apache.pekko.http.javadsl.{ settings => javaSettings }

      val policy: javaSettings.HpackIndexingPolicy = (name, value) => name == "x-label" && value == "test"
      val client = javaSettings.ClientConnectionSettings.create("")
      val clientHttp2 = client.getHttp2Settings.withHpackIndexingPolicy(policy)
      val server = javaSettings.ServerSettings.create("")
      val serverHttp2 = server.getHttp2Settings.withHpackIndexingPolicy(policy)

      val clientOptions = client.withHttp2Settings(clientHttp2).getHttp2Settings
        .asInstanceOf[Http2ClientSettings].hpackPolicyOptions
      val serverOptions = server.withHttp2Settings(serverHttp2).getHttp2Settings
        .asInstanceOf[Http2ServerSettings].hpackPolicyOptions
      for (options <- Seq(clientOptions, serverOptions)) {
        val effective = options.get.policy
        effective.skipInsertion("x-label", "test") shouldBe true
        effective.skipInsertion("x-label", "other") shouldBe false
        effective.skipInsertion("x-other", "test") shouldBe false
      }
    }

    "allow standard pseudo-header names as outbound indexing policy selectors" in {
      val settings = Http2ClientSettings(
        """pekko.http.client.http2.hpack-no-index-headers = [":Path"]""")
      val policy = settings.hpackPolicyOptions.get.policy
      policy.skipInsertion(":path", "/ordinary") shouldBe true
      policy.skipInsertion(":method", "GET") shouldBe false
    }

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
