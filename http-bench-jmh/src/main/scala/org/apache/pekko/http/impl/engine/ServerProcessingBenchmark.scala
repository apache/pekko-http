/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.engine

import java.util.concurrent.CountDownLatch
import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.event.NoLogging
import pekko.http.CommonBenchmark
import pekko.http.impl.engine.server.HttpServerBluePrint
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model.HttpRequest
import pekko.http.scaladsl.model.HttpResponse
import pekko.http.scaladsl.settings.ServerSettings
import pekko.stream.ActorMaterializer
import pekko.stream.scaladsl.Flow
import pekko.stream.scaladsl.Source
import pekko.stream.scaladsl.TLSPlacebo
import pekko.util.ByteString
import com.typesafe.config.ConfigFactory
import org.openjdk.jmh.annotations._

class ServerProcessingBenchmark extends CommonBenchmark {
  val request = ByteString("GET / HTTP/1.1\r\nHost: localhost\r\nUser-Agent: test\r\n\r\n")
  val response = HttpResponse()

  var httpFlow: Flow[ByteString, ByteString, Any] = _
  implicit var system: ActorSystem = _
  implicit var mat: ActorMaterializer = _

  @Benchmark
  @OperationsPerInvocation(10000)
  def benchRequestProcessing(): Unit = {
    val numRequests = 10000
    val latch = new CountDownLatch(numRequests)
    Source.repeat(request)
      .take(numRequests)
      .via(httpFlow)
      .runForeach(_ => latch.countDown())

    latch.await()
  }

  @Setup
  def setup(): Unit = {
    val config =
      ConfigFactory.parseString(
        """
           pekko.actor.default-dispatcher.fork-join-executor.parallelism-max = 1
           akka.http.server.server-header = "akka-http-bench"
        """)
        .withFallback(ConfigFactory.load())
    system = ActorSystem("AkkaHttpBenchmarkSystem", config)
    mat = ActorMaterializer()
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
