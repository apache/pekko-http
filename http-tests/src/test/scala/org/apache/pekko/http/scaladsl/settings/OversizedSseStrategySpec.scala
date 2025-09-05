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
package settings

import org.apache.pekko.http.scaladsl.settings.OversizedSseStrategy
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class OversizedSseStrategySpec extends AnyWordSpec with Matchers {

  "OversizedSseStrategy" should {
    "parse valid string values correctly" in {
      OversizedSseStrategy.fromString("fail-stream") shouldBe OversizedSseStrategy.FailStream
      OversizedSseStrategy.fromString("log-and-skip") shouldBe OversizedSseStrategy.LogAndSkip
      OversizedSseStrategy.fromString("truncate") shouldBe OversizedSseStrategy.Truncate
      OversizedSseStrategy.fromString("dead-letter") shouldBe OversizedSseStrategy.DeadLetter
    }

    "throw IllegalArgumentException for invalid string values" in {
      val exception = intercept[IllegalArgumentException] {
        OversizedSseStrategy.fromString("invalid-strategy")
      }
      exception.getMessage should include("Invalid oversized-message-handling: 'invalid-strategy'")
      exception.getMessage should include("Valid options are: fail-stream, log-and-skip, truncate, dead-letter")
    }

    "convert strategy objects back to strings correctly" in {
      OversizedSseStrategy.toString(OversizedSseStrategy.FailStream) shouldBe "fail-stream"
      OversizedSseStrategy.toString(OversizedSseStrategy.LogAndSkip) shouldBe "log-and-skip"
      OversizedSseStrategy.toString(OversizedSseStrategy.Truncate) shouldBe "truncate"
      OversizedSseStrategy.toString(OversizedSseStrategy.DeadLetter) shouldBe "dead-letter"
    }

    "handle case-sensitive strings" in {
      intercept[IllegalArgumentException] {
        OversizedSseStrategy.fromString("FAIL-STREAM")
      }
      intercept[IllegalArgumentException] {
        OversizedSseStrategy.fromString("Fail-Stream")
      }
    }

    "handle empty and null strings" in {
      intercept[IllegalArgumentException] {
        OversizedSseStrategy.fromString("")
      }
      intercept[IllegalArgumentException] {
        OversizedSseStrategy.fromString(null)
      }
    }
  }
}