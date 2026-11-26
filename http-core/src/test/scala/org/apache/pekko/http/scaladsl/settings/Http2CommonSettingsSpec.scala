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

    "preserve default receive flow control when isolation is explicitly disabled" in {
      Http2ServerSettings(system) shouldBe
      Http2ServerSettings("pekko.http.server.http2.incoming-flow-control-isolated-streams = 0")
      Http2ClientSettings(system) shouldBe
      Http2ClientSettings("pekko.http.client.http2.incoming-flow-control-isolated-streams = 0")
    }

    "validate startup window capacity for isolated receive flow control" in {
      val serverConfig = "pekko.http.server.http2.incoming-flow-control-isolated-streams = 2"
      val clientConfig = "pekko.http.client.http2.incoming-flow-control-isolated-streams = 2"
      val server = Http2ServerSettings(serverConfig)
      val client = Http2ClientSettings(clientConfig)
      org.apache.pekko.http.javadsl.settings.Http2ServerSettings.create(serverConfig) shouldBe server
      org.apache.pekko.http.javadsl.settings.ClientConnectionSettings.create(clientConfig) shouldBe
      ClientConnectionSettings(clientConfig)
      ClientConnectionSettings(clientConfig).http2Settings shouldBe client
      server.withLogFrames(true).incomingFlowControlIsolatedStreams shouldBe 2
      client.withRequestEntityChunkSize(4096).incomingFlowControlIsolatedStreams shouldBe 2
      Http2CommonSettings.isolatedReceiveStreamBudget(server) shouldBe server.incomingStreamLevelBufferSize
      Http2CommonSettings.isolatedReceiveStreamBudget(client.withIncomingConnectionLevelBufferSize(196605)) shouldBe
      65535

      intercept[IllegalArgumentException](server.withIncomingConnectionLevelBufferSize(196604))
        .getMessage should include("initial 65535-byte stream window")
      intercept[IllegalArgumentException](client.withIncomingStreamLevelBufferSize(65534))
        .getMessage should include("initial 65535-byte stream window")
      intercept[IllegalArgumentException](
        Http2ServerSettings("pekko.http.server.http2.incoming-flow-control-isolated-streams = -1"))
        .getMessage should include("must be >= 0")
      intercept[IllegalArgumentException](
        Http2ClientSettings("pekko.http.client.http2.incoming-flow-control-isolated-streams = 2147483647"))
        .getMessage should include("initial 65535-byte stream window")
      intercept[IllegalArgumentException](Http2ServerSettings(
        """pekko.http.server.http2 {
          |  incoming-flow-control-isolated-streams = 1
          |  max-concurrent-streams = 1
          |  min-collect-strict-entity-size = 65536
          |}""".stripMargin))
        .getMessage should include("min-collect-strict-entity-size <= 65535")
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
