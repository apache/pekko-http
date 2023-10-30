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

package org.apache.pekko.http.scaladsl

import org.apache.pekko
import pekko.http.impl.util.PekkoSpecWithMaterializer
import pekko.stream.scaladsl.{ Keep, Sink, Source }
import example.HostConnectionPoolCompat

class HostConnectionPoolCompatSpec extends PekkoSpecWithMaterializer {
  "HostConnectionPool" should {
    "be compatible" in {
      val hcp0 =
        Source.empty
          .viaMat(Http().cachedHostConnectionPool("localhost", 8080))(Keep.right)
          .to(Sink.ignore)
          .run()

      val hcp1 =
        Source.empty
          .viaMat(Http().cachedHostConnectionPool("localhost", 8080))(Keep.right)
          .to(Sink.ignore)
          .run()

      val hcpOther =
        Source.empty
          .viaMat(Http().newHostConnectionPool("localhost", 8080))(Keep.right)
          .to(Sink.ignore)
          .run()

      hcp0 shouldEqual hcp1
      (hcp0 should not).equal(hcpOther)

      HostConnectionPoolCompat.access(hcp0)
    }
  }
}
