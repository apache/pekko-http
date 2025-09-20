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

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.event.NoLogging
import pekko.http.CommonBenchmark
import pekko.http.impl.engine.server.HttpServerBluePrint
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model.ContentTypes
import pekko.http.scaladsl.model.HttpEntity
import pekko.http.scaladsl.model.HttpRequest
import pekko.http.scaladsl.model.HttpResponse
import pekko.http.scaladsl.model.headers
import pekko.http.scaladsl.settings.ServerSettings
import pekko.stream.scaladsl.Flow
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source
import pekko.stream.scaladsl.TLSPlacebo
import pekko.util.ByteString

import com.typesafe.config.ConfigFactory

@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
class StreamServerProcessingBenchmark extends CommonBenchmark {
  val request = ByteString("GET / HTTP/1.1\r\nHost: localhost\r\nUser-Agent: test\r\n\r\n")

  // @Param - currently not a param
  val totalBytes: String = "1000000"

  @Param(Array("10", "100", "1000"))
  var numChunks: String = _

  var totalExpectedBytes: Long = _

  // @Param(Array("100")) -- not a param any more
  var numRequestsPerConnection: String = "100"

  @Param(Array("strict", "default", "chunked"))
  var entityType: String = _

  var httpFlow: Flow[ByteString, ByteString, Any] = _

  implicit var system: ActorSystem = _

  @Benchmark
  def benchRequestProcessing(): Unit = {
    val latch = new CountDownLatch(1)
    Source.repeat(request)
      .take(numRequestsPerConnection.toInt)
      .via(httpFlow)
      .runWith(Sink.fold(0L)(_ + _.size))
      .onComplete { res =>
        latch.countDown()
        require(res.filter(_ >= totalExpectedBytes).isSuccess,
          s"Expected at least $totalExpectedBytes but only got $res")
      }(system.dispatcher)

    latch.await()
  }

  @Setup
  def setup(): Unit = {
    val config =
      ConfigFactory.parseString(
        """
           pekko.actor.default-dispatcher.fork-join-executor.parallelism-max = 1
        """)
        .withFallback(ConfigFactory.load())
    system = ActorSystem("PekkoHttpBenchmarkSystem", config)

    val bytesPerChunk = totalBytes.toInt / numChunks.toInt
    totalExpectedBytes = numRequestsPerConnection.toInt * bytesPerChunk * numChunks.toInt

    val byteChunk = ByteString(new Array[Byte](bytesPerChunk))
    val streamedBytes = Source.repeat(byteChunk).take(numChunks.toInt)

    val entity = entityType match {
      case "strict" =>
        HttpEntity.Strict(ContentTypes.`application/octet-stream`,
          ByteString(new Array[Byte](bytesPerChunk.toInt * numChunks.toInt)))
      case "chunked" =>
        HttpEntity.Chunked.fromData(
          ContentTypes.`application/octet-stream`,
          streamedBytes)
      case "default" =>
        HttpEntity.Default(
          ContentTypes.`application/octet-stream`,
          bytesPerChunk.toInt * numChunks.toInt,
          streamedBytes)
    }

    val response = HttpResponse(
      headers = headers.Server("pekko-http-bench") :: Nil,
      entity = entity)

    httpFlow =
      Flow[HttpRequest].map(_ => response).join(
        HttpServerBluePrint(ServerSettings(system), NoLogging, false, Http().dateHeaderRendering).atop(
          TLSPlacebo()))
  }

  @TearDown
  def tearDown(): Unit = {
    system.terminate()
  }
}
