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
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
class QueryMultiMapBenchmark {
  // Fixed cases keep the default short and avoid pairing meaningless sizes/distributions.
  @Param(Array(
    "smallUnique", "smallOnePair", "mediumUnique", "mediumOnePair",
    "largeOnePair", "lateThird", "fivePerKey", "manyValues"))
  var workload: String = "smallUnique"

  var query: Uri.Query = Uri.Query.Empty

  @Setup
  def setup(): Unit = {
    val size = workload match {
      case "smallUnique" | "smallOnePair"                 => 4
      case "mediumUnique" | "mediumOnePair" | "lateThird" => 16
      case "fivePerKey"                                   => 30
      case "largeOnePair" | "manyValues"                  => 256
    }
    val pairs = Vector.tabulate(size) { i =>
      val key = workload match {
        case "smallOnePair" | "mediumOnePair" | "largeOnePair" => if (i == size - 1) "k0" else s"k$i"
        case "lateThird"                                       => if (i == size - 1) "k0" else s"k${i % (size / 2)}"
        case "fivePerKey"                                      => s"k${i % (size / 5)}"
        case "manyValues"                                      => "k0"
        case _                                                 => s"k$i"
      }
      key -> i.toString
    }
    if (workload.endsWith("OnePair"))
      require(pairs.groupMap(_._1)(_._2).valuesIterator.map(_.size).filter(_ > 1).toList == List(2))
    query = Uri.Query(pairs: _*)
    require(baseline().iterator.toList == optimized().iterator.toList)
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
