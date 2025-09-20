/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.model.parser

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import org.parboiled2.UTF8

import org.apache.pekko
import pekko.http.scaladsl.model.Uri

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Array(Mode.Throughput))
class UriParserBenchmark {

  @Param(Array(
    "http://any.hostname?param1=111&amp;param2=222",
    "http://any.hostname?param1=111&amp;param2=222&param3=333&param4=444&param5=555&param6=666&param7=777&param8=888&param9=999"))
  var url = ""

  @Benchmark
  def bench_parse_uri(bh: Blackhole): Unit = {
    bh.consume(Uri(url).query(UTF8, Uri.ParsingMode.Relaxed))
  }

}
