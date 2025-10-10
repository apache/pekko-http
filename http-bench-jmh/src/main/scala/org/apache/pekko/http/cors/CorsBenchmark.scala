/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.http.cors

import java.util.concurrent.TimeUnit

import scala.concurrent.{ Await, ExecutionContext }
import scala.concurrent.duration._

import org.openjdk.jmh.annotations._

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.http.cors.scaladsl.CorsDirectives
import pekko.http.cors.scaladsl.settings.CorsSettings
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.Http.ServerBinding
import pekko.http.scaladsl.model.{ HttpMethods, HttpRequest }
import pekko.http.scaladsl.model.headers.{ `Access-Control-Request-Method`, Origin }
import pekko.http.scaladsl.server.Directives
import pekko.http.scaladsl.unmarshalling.Unmarshal

import com.typesafe.config.ConfigFactory

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Array(Mode.Throughput))
class CorsBenchmark extends Directives with CorsDirectives {
  private val config = ConfigFactory.parseString("pekko.loglevel = ERROR").withFallback(ConfigFactory.load())

  implicit private val system: ActorSystem = ActorSystem("CorsBenchmark", config)
  implicit private val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  private val http = Http()
  private val corsSettings = CorsSettings.default

  private var binding: ServerBinding = _
  private var request: HttpRequest = _
  private var requestCors: HttpRequest = _
  private var requestPreflight: HttpRequest = _

  @Setup
  def setup(): Unit = {
    val route = {
      path("baseline") {
        get {
          complete("ok")
        }
      } ~ path("cors") {
        cors(corsSettings) {
          get {
            complete("ok")
          }
        }
      }
    }
    val origin = Origin("http://example.com")

    binding = Await.result(http.newServerAt("127.0.0.1", 0).bind(route), 1.second)
    val base = s"http://${binding.localAddress.getHostString}:${binding.localAddress.getPort}"

    request = HttpRequest(uri = base + "/baseline")
    requestCors = HttpRequest(
      method = HttpMethods.GET,
      uri = base + "/cors",
      headers = List(origin))
    requestPreflight = HttpRequest(
      method = HttpMethods.OPTIONS,
      uri = base + "/cors",
      headers = List(origin, `Access-Control-Request-Method`(HttpMethods.GET)))
  }

  @TearDown
  def shutdown(): Unit = {
    val f = for {
      _ <- http.shutdownAllConnectionPools()
      _ <- binding.terminate(1.second)
      _ <- system.terminate()
    } yield ()
    Await.ready(f, 5.seconds)
  }

  @Benchmark
  def baseline(): Unit = {
    val f = http.singleRequest(request).flatMap(r => Unmarshal(r.entity).to[String])
    assert(Await.result(f, 1.second) == "ok")
  }

  @Benchmark
  def default_cors(): Unit = {
    val f = http.singleRequest(requestCors).flatMap(r => Unmarshal(r.entity).to[String])
    assert(Await.result(f, 1.second) == "ok")
  }

  @Benchmark
  def default_preflight(): Unit = {
    val f = http.singleRequest(requestPreflight).flatMap(r => Unmarshal(r.entity).to[String])
    assert(Await.result(f, 1.second) == "")
  }
}
