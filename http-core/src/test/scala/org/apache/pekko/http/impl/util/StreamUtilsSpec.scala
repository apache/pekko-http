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

package org.apache.pekko.http.impl.util

import org.scalatest.concurrent.ScalaFutures

import org.apache.pekko
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.stream.{ ActorAttributes, Attributes, Supervision }
import pekko.testkit._
import pekko.util.ByteString

import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.util.Failure

class StreamUtilsSpec extends PekkoSpec with ScalaFutures {

  "captureTermination" should {
    "signal completion" when {
      "upstream terminates" in {
        val (newSource, whenCompleted) = StreamUtils.captureTermination(Source(List(1, 2, 3)))

        newSource.runWith(Sink.ignore)

        Await.result(whenCompleted, 3.seconds.dilated) shouldBe (())
      }

      "upstream fails" in {
        val ex = new RuntimeException("ex")
        val (newSource, whenCompleted) = StreamUtils.captureTermination(Source.failed[Int](ex))
        (intercept[RuntimeException] {
          Await.result(newSource.runWith(Sink.head), 3.second.dilated)
        } should be).theSameInstanceAs(ex)

        Await.ready(whenCompleted, 3.seconds.dilated).value shouldBe Some(Failure(ex))
      }

      "downstream cancels" in {
        val (newSource, whenCompleted) = StreamUtils.captureTermination(Source(List(1, 2, 3)))

        newSource.runWith(Sink.head)

        Await.result(whenCompleted, 3.seconds.dilated) shouldBe (())
      }
    }
  }

  "exposeAttributes" should {
    "expose attrs" in {
      val element = "hello"
      val nameAttr = Attributes.name("Amazing")

      val res =
        Source.single(element)
          .via(StreamUtils.statefulAttrsMap(attrs => el => attrs -> el))
          .addAttributes(nameAttr)
          .runWith(Sink.head)

      val (attrs, `element`) = res.futureValue
      attrs.attributeList should contain(nameAttr.attributeList.head)
    }
  }

  "simple statefulMap" should {

    "be able to handle state" in {
      val flow = StreamUtils.statefulMap[Int, Int](() => {
        var state = 0

        { delta =>
          state += delta
          state
        }
      })

      implicit val ec: ExecutionContext = system.dispatcher
      val list =
        for {
          _ <- 1 to 10
        } yield Source(1 to 10)
          .via(flow)
          .runWith(Sink.last)

      Future.reduceLeft(list)(_ + _)
        .futureValue shouldBe 550
    }

    "be able to stop" in {
      val flow = StreamUtils.statefulMap[Int, Int](() => {
        var state = 0

        { input =>
          if (input == 2) {
            throw new RuntimeException("stop")
          }
          state += input
          state
        }
      })

      Source(1 to 10)
        .via(flow)
        .runWith(Sink.ignore)
        .failed
        .futureValue shouldBe a[RuntimeException]
    }

    "be able to restart" in {
      val flow = StreamUtils.statefulMap[Int, Int](() => {
        var state = 0

        { input =>
          if (input % 2 == 0) {
            throw new RuntimeException("stop")
          }
          state += input
          state
        }
      })

      Source(1 to 10)
        .via(flow.withAttributes(ActorAttributes.supervisionStrategy(Supervision.restartingDecider)))
        .runWith(Sink.seq)
        .futureValue shouldBe List(1, 3, 5, 7, 9)
    }

    "be able to resume" in {
      val flow = StreamUtils.statefulMap[Int, Int](() => {
        var state = 0

        { input =>
          if (input % 2 == 0) {
            throw new RuntimeException("stop")
          }
          state += input
          state
        }
      })

      Source(1 to 10)
        .via(flow.withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider)))
        .runWith(Sink.seq)
        .futureValue shouldBe List(1, 4, 9, 16, 25)
    }
  }

  "sliceBytesTransformer" should {
    "not discard data when slicing more that Int.MaxValue" in {
      val start = Int.MaxValue + 10L
      val length = Int.MaxValue * 10L
      val elementSize = 100 * 1024 * 1024
      val pack = ByteString(new Array[Byte](elementSize))
      val totalElements = (start + length) / elementSize

      val whenCompleted =
        Source(0L to totalElements)
          .map { _ => pack }
          .via(StreamUtils.sliceBytesTransformer(start, length))
          .fold(0L) {
            case (sum, element) =>
              sum + element.length
          }
          .runWith(Sink.head)

      Await.result(whenCompleted, 3.seconds.dilated) should be(length)
    }
  }
}
