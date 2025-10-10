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

package org.apache.pekko.http.scaladsl.server.util

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TupleOpsSpec extends AnyWordSpec with Matchers {
  import TupleOps._

  "The TupleOps" should {

    "support folding over tuples using a binary poly-function" in {
      object Funky extends BinaryPolyFunc {
        implicit def step1: BinaryPolyFunc.Case[Double, Int, this.type] { type Out = Double } = at[Double, Int](_ + _)
        implicit def step2: BinaryPolyFunc.Case[Double, Symbol, this.type] { type Out = Byte } =
          at[Double, Symbol]((d, s) => (d + s.name.tail.toInt).toByte)
        implicit def step3: BinaryPolyFunc.Case[Byte, String, this.type] { type Out = Long } =
          at[Byte, String]((byte, s) => byte + s.toLong)
      }
      (1, Symbol("X2"), "3").foldLeft(0.0)(Funky) shouldEqual 6L
    }

    "support joining tuples" in {
      (1, Symbol("X2"), "3").join(()) shouldEqual ((1, Symbol("X2"), "3"))
      ().join((1, Symbol("X2"), "3")) shouldEqual ((1, Symbol("X2"), "3"))
      (1, Symbol("X2"), "3").join((4.0, 5L)) shouldEqual ((1, Symbol("X2"), "3", 4.0, 5L))
    }
  }
}
