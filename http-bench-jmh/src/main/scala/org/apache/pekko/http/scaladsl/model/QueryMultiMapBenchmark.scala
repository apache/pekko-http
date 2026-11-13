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

import java.util.concurrent.TimeUnit

import scala.annotation.tailrec

import org.openjdk.jmh.annotations._

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
@Fork(2)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
class QueryMultiMapBenchmark {
  @Param(Array("0", "1", "4", "16", "64", "256"))
  var size: Int = 0

  @Param(Array("unique", "repeated", "interleaved", "mostlyUnique"))
  var distribution: String = "unique"

  var query: Uri.Query = _

  @Setup
  def setup(): Unit = {
    val pairs = Vector.tabulate(size) { i =>
      val key = distribution match {
        case "unique" => s"k$i"
        case "repeated" => "k"
        case "interleaved" => s"k${i % 4}"
        case "mostlyUnique" => if (i % 8 == 0) "repeated" else s"k$i"
      }
      key -> (i % 10).toString
    }
    query = Uri.Query(pairs: _*)
    require(baseline() == optimized())
  }

  // Pre-change Uri.Query.toMultiMap implementation, retained verbatim as the baseline. Parsing is outside timing.
  @Benchmark
  def baseline(): Map[String, List[String]] = {
    @tailrec def append(map: Map[String, List[String]], q: Uri.Query): Map[String, List[String]] =
      if (q.isEmpty) map else append(map.updated(q.key, map.getOrElse(q.key, Nil) :+ q.value), q.tail)
    append(Map.empty, query)
  }

  @Benchmark
  def optimized(): Map[String, List[String]] = query.toMultiMap
}
