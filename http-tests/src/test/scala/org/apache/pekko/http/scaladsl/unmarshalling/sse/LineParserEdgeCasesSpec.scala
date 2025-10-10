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

final class LineParserEdgeCasesSpec extends AsyncWordSpec with Matchers with BaseUnmarshallingSpec {

  "LineParser edge cases" should {

    "handle lines exactly at the size limit" in {
      val exactSizeLine = "x" * 50
      val input = ByteString(s"before\n$exactSizeLine\nafter\n")

      Source.single(input)
        .via(new LineParser(50, OversizedSseStrategy.FailStream))
        .runWith(Sink.seq)
        .map { result =>
          result shouldBe Vector("before", exactSizeLine, "after")
        }
    }

    "handle lines one byte over the limit" in {
      val oversizedLine = "x" * 51
      val input = ByteString(s"before\n$oversizedLine\nafter\n")

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

    "handle oversized content spanning multiple chunks" in {
      val part1 = ByteString("before\n" + "x" * 30)
      val part2 = ByteString("y" * 30) // total line = 60 chars, exceeds limit of 50
      val part3 = ByteString("\nafter\n")

      Source(Vector(part1, part2, part3))
        .via(new LineParser(50, OversizedSseStrategy.LogAndSkip))
        .runWith(Sink.seq)
        .map { result =>
          result shouldBe Vector("before", "after")
        }
    }

    "handle mixed line endings with oversized content" in {
      val oversizedLine = "x" * 100
      val crInput = ByteString(s"before\r$oversizedLine\rafter\r")
      val lfInput = ByteString(s"before\n$oversizedLine\nafter\n")
      val crlfInput = ByteString(s"before\r\n$oversizedLine\r\nafter\r\n")

      for {
        crResult <- Source.single(crInput)
          .via(new LineParser(50, OversizedSseStrategy.LogAndSkip))
          .runWith(Sink.seq)
        lfResult <- Source.single(lfInput)
          .via(new LineParser(50, OversizedSseStrategy.LogAndSkip))
          .runWith(Sink.seq)
        crlfResult <- Source.single(crlfInput)
          .via(new LineParser(50, OversizedSseStrategy.LogAndSkip))
          .runWith(Sink.seq)
      } yield {
        crResult shouldBe Vector("before", "after")
        lfResult shouldBe Vector("before", "after")
        crlfResult shouldBe Vector("before", "after")
      }
    }

    "handle empty and whitespace lines with size limits" in {
      val input = ByteString("before\n\n   \n" + "x" * 100 + "\n\t\nafter\n")

      Source.single(input)
        .via(new LineParser(50, OversizedSseStrategy.LogAndSkip))
        .runWith(Sink.seq)
        .map { result =>
          result shouldBe Vector("before", "", "   ", "\t", "after")
        }
    }

    "handle chunk boundaries at line endings" in {
      val chunk1 = ByteString("before")
      val chunk2 = ByteString("\n" + "x" * 100) // oversized line starts in chunk2
      val chunk3 = ByteString("\nafter\n")

      Source(Vector(chunk1, chunk2, chunk3))
        .via(new LineParser(50, OversizedSseStrategy.Truncate))
        .runWith(Sink.seq)
        .map { result =>
          result should have size 3
          result(0) shouldBe "before"
          result(1) shouldBe "x" * 50 // truncated
          result(2) shouldBe "after"
        }
    }

    "handle consecutive oversized messages" in {
      val input = ByteString("normal\n" + "x" * 100 + "\n" + "y" * 200 + "\n" + "z" * 75 + "\nnormal2\n")

      for {
        logSkipResult <- Source.single(input)
          .via(new LineParser(50, OversizedSseStrategy.LogAndSkip))
          .runWith(Sink.seq)
        truncateResult <- Source.single(input)
          .via(new LineParser(50, OversizedSseStrategy.Truncate))
          .runWith(Sink.seq)
      } yield {
        // LogAndSkip: only normal messages
        logSkipResult shouldBe Vector("normal", "normal2")

        // Truncate: normal + truncated messages
        truncateResult should have size 5
        truncateResult(0) shouldBe "normal"
        truncateResult(1) shouldBe "x" * 50
        truncateResult(2) shouldBe "y" * 50
        truncateResult(3) shouldBe "z" * 50
        truncateResult(4) shouldBe "normal2"
      }
    }

    "work with unlimited line size (maxLineSize = 0)" in {
      val veryLongLine = "x" * 50000
      val input = ByteString(s"before\n$veryLongLine\nafter\n")

      Source.single(input)
        .via(new LineParser(0, OversizedSseStrategy.FailStream))
        .runWith(Sink.seq)
        .map { result =>
          result should have size 3
          result(0) shouldBe "before"
          result(1) shouldBe veryLongLine
          result(2) shouldBe "after"
        }
    }

    "handle line ending edge cases" in {
      // CR followed by non-LF
      val input1 = ByteString("line1\rline2\nline3\r\nline4\n")

      Source.single(input1)
        .via(new LineParser(100, OversizedSseStrategy.FailStream))
        .runWith(Sink.seq)
        .map { result =>
          result shouldBe Vector("line1", "line2", "line3", "line4")
        }
    }
  }
}
