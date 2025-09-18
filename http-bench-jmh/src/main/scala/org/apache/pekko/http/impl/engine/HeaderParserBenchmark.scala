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

package org.apache.pekko.http.impl.engine

import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.duration._

import org.openjdk.jmh.annotations._

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.http.impl.engine.parsing.HttpHeaderParser
import pekko.http.impl.settings.ParserSettingsImpl
import pekko.http.scaladsl.model.MediaType
import pekko.util.ByteString

import com.typesafe.config.ConfigFactory

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Array(Mode.Throughput))
private[engine] class HeaderParserBenchmark {
  implicit val system: ActorSystem = ActorSystem("header-parser-benchmark")

  @Param(Array("no", "yes"))
  var withCustomMediaTypes = "no"

  var parser: HttpHeaderParser = _

  val request = """GET / HTTP/1.1
      |
      |Content-Type: application/json
      |Accept: application/json, text/plain
      |Content-Length: 0
    """.stripMargin

  val firstHeaderStart = request.indexOf('\n') + 2

  val requestBytes = ByteString(request)

  @Setup
  def setup(): Unit = {
    parser = HttpHeaderParser.prime(HttpHeaderParser.unprimed(settings(), system.log, _ => ()))
  }

  private def settings() = {
    val root = ConfigFactory.load()
    val settings = ParserSettingsImpl.fromSubConfig(root, root.getConfig("pekko.http.server.parsing"))
    if (withCustomMediaTypes == "no") settings
    else settings.withCustomMediaTypes(
      MediaType.customWithOpenCharset("application", "json"))
  }

  @TearDown
  def tearDown(): Unit = {
    Await.result(system.terminate(), 5.seconds)
  }

  @Benchmark
  def bench_parse_headers(): Int = {
    val next = parser.parseHeaderLine(requestBytes, firstHeaderStart)()
    parser.parseHeaderLine(requestBytes, next)()
  }
}
