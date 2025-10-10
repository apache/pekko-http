/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.util

private[pekko] object BenchUtils {

  /**
   * Races f1 against f2 for attempts times (after 10 warmup runs) and returns the minimum factor
   * between f1 time and f2 time.
   *
   * Multiple attempts are more resilient against one time disruptions.
   */
  def nanoRace(f1: => Unit, f2: => Unit, attempts: Int = 3): Double = {
    // warmup to remove some of the JIT-related variance
    (1 to 10).foreach { _ =>
      f1
      f2
    }

    def nanoTime(f: () => Unit): Long = {
      val start = System.nanoTime()
      f()
      val end = System.nanoTime()
      end - start
    }

    def oneAttempt(): Double = {
      val f1Time = nanoTime(() => f1)
      val f2Time = nanoTime(() => f2)
      val factor = f1Time.toDouble / f2Time
      factor
    }

    Seq.fill(attempts)(oneAttempt()).min
  }
}
