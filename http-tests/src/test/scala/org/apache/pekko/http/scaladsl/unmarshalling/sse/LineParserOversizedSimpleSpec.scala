/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.http
package scaladsl
package unmarshalling
package sse

import org.apache.pekko
import pekko.http.scaladsl.settings.OversizedSseStrategy
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.util.ByteString
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

final class LineParserOversizedSimpleSpec extends AsyncWordSpec with Matchers with BaseUnmarshallingSpec {

  "LineParser with oversized message handling" should {

    "fail the stream with FailStream strategy" in {
      val input = ByteString("before\n" + "x" * 100 + "\nafter\n")

      recoverToExceptionIf[IllegalStateException] {
        Source.single(input)
          .via(new LineParser(50, OversizedSseStrategy.FailStream))
          .runWith(Sink.seq)
      }.map { exception =>
        exception.getMessage should include("SSE line size")
        exception.getMessage should include("exceeds max-line-size: 50")
      }
    }

    "skip oversized messages with LogAndSkip strategy" in {
      val input = ByteString("before\n" + "x" * 100 + "\nafter\n")

      Source.single(input)
        .via(new LineParser(50, OversizedSseStrategy.LogAndSkip))
        .runWith(Sink.seq)
        .map { result =>
          result shouldBe Vector("before", "after")
        }
    }

    "truncate oversized messages with Truncate strategy" in {
      val input = ByteString("before\n" + "x" * 100 + "\nafter\n")

      Source.single(input)
        .via(new LineParser(50, OversizedSseStrategy.Truncate))
        .runWith(Sink.seq)
        .map { result =>
          result should have size 3
          result(0) shouldBe "before"
          result(1) shouldBe "x" * 50 // truncated
          result(2) shouldBe "after"
        }
    }

    "send oversized messages to dead letters with DeadLetter strategy" in {
      val input = ByteString("before\n" + "x" * 100 + "\nafter\n")

      Source.single(input)
        .via(new LineParser(50, OversizedSseStrategy.DeadLetter))
        .runWith(Sink.seq)
        .map { result =>
          result shouldBe Vector("before", "after")
        }
    }
  }
}
