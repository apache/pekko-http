/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.engine.http2

import scala.concurrent.duration._

/**
 * This tests the http2 server throttle support for rapid resets when explicitly disabled.
 */
class Http2ServerDisableFrameTypeThrottleSpec extends Http2SpecWithMaterializer("""
    pekko.http.server.http2.log-frames = on
    pekko.http.server.http2.frame-type-throttle.frame-types = []
  """) {
  import Http2ServerEnableFrameTypeThrottleSpec._

  override def failOnSevereMessages: Boolean = true

  "The Http/2 server implementation" should {
    "not close the connection during a rapid reset attack (throttle disabled)".inAssertAllStagesStopped(
      new TestSetup {
        override def handlerFlow = neverRespond

        // more than the default burst of 100, fewer than max-concurrent-streams
        network.sendBytes(rapidResets(200, network.encodeRequestHeaders))
        network.toNet.expectNoBytes(200.millis)
      })
  }
}
