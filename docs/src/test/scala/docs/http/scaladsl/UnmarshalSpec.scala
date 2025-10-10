/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.http.scaladsl

import org.apache.pekko
import pekko.testkit.PekkoSpec

class UnmarshalSpec extends PekkoSpec {

  "use unmarshal" in {
    // #use-unmarshal
    import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
    import system.dispatcher // Optional ExecutionContext (default from Materializer)

    import scala.concurrent.Await
    import scala.concurrent.duration._

    val intFuture = Unmarshal("42").to[Int]
    val int = Await.result(intFuture, 1.second) // don't block in non-test code!
    int shouldEqual 42

    val boolFuture = Unmarshal("off").to[Boolean]
    val bool = Await.result(boolFuture, 1.second) // don't block in non-test code!
    bool shouldBe false
    // #use-unmarshal
  }

  "use unmarshal without execution context" in {
    import pekko.http.scaladsl.unmarshalling.Unmarshal

    import scala.concurrent.Await
    import scala.concurrent.duration._

    val intFuture = Unmarshal("42").to[Int]
    val int = Await.result(intFuture, 1.second) // don't block in non-test code!
    int shouldEqual 42

    val boolFuture = Unmarshal("off").to[Boolean]
    val bool = Await.result(boolFuture, 1.second) // don't block in non-test code!
    bool shouldBe false
  }
}
