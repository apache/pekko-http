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

package org.apache.pekko.http.scaladsl.model

import org.apache.pekko
import pekko.Done
import pekko.http.impl.util.PekkoSpecWithMaterializer
import pekko.http.scaladsl.Http
import pekko.stream.scaladsl._
import pekko.testkit._

import scala.concurrent.duration._
import pekko.util.ByteString

import scala.concurrent.{ Await, Promise }

class EntityDiscardingSpec extends PekkoSpecWithMaterializer {
  val testData = Vector.tabulate(200)(i => ByteString(s"row-$i"))

  "HttpRequest" should {

    "discard entity stream after .discardEntityBytes() call" in {

      val p = Promise[Done]()
      val s = Source
        .fromIterator[ByteString](() => testData.iterator)
        .alsoTo(Sink.onComplete(t => p.complete(t)))

      val req = HttpRequest(entity = HttpEntity(ContentTypes.`text/csv(UTF-8)`, s))
      val de = req.discardEntityBytes()

      p.future.futureValue should ===(Done)
      de.future.futureValue should ===(Done)
    }
  }

  "HttpResponse" should {

    "discard entity stream after .discardEntityBytes() call" in {

      val p = Promise[Done]()
      val s = Source
        .fromIterator[ByteString](() => testData.iterator)
        .alsoTo(Sink.onComplete(t => p.complete(t)))

      val resp = HttpResponse(entity = HttpEntity(ContentTypes.`text/csv(UTF-8)`, s))
      val de = resp.discardEntityBytes()

      p.future.futureValue should ===(Done)
      de.future.futureValue should ===(Done)
    }

    // TODO consider improving this by storing a mutable "already materialized" flag somewhere
    // TODO likely this is going to inter-op with the auto-draining as described in #18716
    "should not allow draining a second time" in {
      val bound = Http().newServerAt("localhost", 0).bindSync(req =>
        HttpResponse(entity = HttpEntity(
          ContentTypes.`text/csv(UTF-8)`, Source.fromIterator[ByteString](() => testData.iterator)))).futureValue

      try {

        val response =
          Http().singleRequest(HttpRequest(uri = s"http://localhost:${bound.localAddress.getPort}/")).futureValue

        val de = response.discardEntityBytes()
        de.future.futureValue should ===(Done)

        val de2 = response.discardEntityBytes()
        val secondRunException = intercept[IllegalStateException] { Await.result(de2.future, 3.seconds.dilated) }
        secondRunException.getMessage should include("cannot be materialized more than once")
      } finally bound.unbind().futureValue
    }
  }

}
