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

package org.apache.pekko.http.scaladsl.server

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.client.RequestBuilding
import pekko.http.scaladsl.coding.{ Coders, Decoder }
import pekko.http.scaladsl.model.HttpEntity.Chunk
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.headers.{ `Content-Encoding`, HttpEncoding, HttpEncodings }
import pekko.http.scaladsl.server.Directives._
import pekko.stream.scaladsl.{ Flow, Source }
import pekko.testkit.TestKit
import pekko.util.ByteString
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{ Millis, Seconds, Span }
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.immutable
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class SizeLimitSpec extends AnyWordSpec with Matchers with RequestBuilding with BeforeAndAfterAll with ScalaFutures {

  import pekko.http.ccompat.ImplicitUtils._

  val maxContentLength = 800
  // Protect network more than memory:
  val decodeMaxSize = 1600

  val testConf: Config = ConfigFactory.parseString(s"""
    pekko.loggers = ["org.apache.pekko.testkit.TestEventListener"]
    pekko.loglevel = ERROR
    pekko.stdout-loglevel = ERROR
    pekko.http.server.parsing.max-content-length = $maxContentLength
    pekko.http.routing.decode-max-size = $decodeMaxSize
    """)
  implicit val system: ActorSystem = ActorSystem(getClass.getSimpleName, testConf)
  import system.dispatcher
  val random = new scala.util.Random(42)

  implicit val defaultPatience: PatienceConfig = PatienceConfig(timeout = Span(2, Seconds), interval = Span(5, Millis))

  "a normal route" should {
    val route = path("noDirective") {
      post {
        entity(as[String]) { _ =>
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to Pekko HTTP</h1>"))
        }
      }
    }

    val binding = Http().newServerAt("localhost", port = 0).bind(route).futureValue

    "accept small POST requests" in {
      Http().singleRequest(Post(s"http:/${binding.localAddress}/noDirective", entityOfSize(maxContentLength)))
        .futureValue.status shouldEqual StatusCodes.OK
    }

    "not accept entities bigger than configured with pekko.http.parsing.max-content-length" in {
      Http().singleRequest(Post(s"http:/${binding.localAddress}/noDirective", entityOfSize(maxContentLength + 1)))
        .futureValue.status shouldEqual StatusCodes.ContentTooLarge
    }
  }

  "a route with decodeRequest" should {
    val route = path("noDirective") {
      decodeRequest {
        post {
          entity(as[String]) { e =>
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"Got request with entity of ${e.length} characters"))
          }
        }
      }
    }

    val binding = Http().newServerAt("localhost", port = 0).bind(route).futureValue

    "accept a small request" in {
      val response = Http().singleRequest(Post(s"http:/${binding.localAddress}/noDirective",
        entityOfSize(maxContentLength))).futureValue
      response.status shouldEqual StatusCodes.OK
      response.entity.dataBytes.runReduce(
        _ ++ _).futureValue.utf8String shouldEqual s"Got request with entity of $maxContentLength characters"
    }

    "reject a small request that decodes into a large entity" in {
      val data = ByteString.fromString("0" * (decodeMaxSize + 1))
      val zippedData = Await.result(Coders.Gzip.encodeAsync(data), 10.seconds)
      val request = HttpRequest(
        HttpMethods.POST,
        s"http:/${binding.localAddress}/noDirective",
        immutable.Seq(`Content-Encoding`(HttpEncodings.gzip)),
        HttpEntity(ContentTypes.`text/plain(UTF-8)`, zippedData))

      zippedData.size should be <= maxContentLength
      data.size should be > decodeMaxSize

      Http().singleRequest(request)
        .futureValue.status shouldEqual StatusCodes.ContentTooLarge
    }
  }

  "a route with decodeRequest that results in a large chunked entity" should {
    val decoder = decodeTo(chunkedEntityOfSize(decodeMaxSize + 1))

    val route = path("noDirective") {
      decodeRequestWith(decoder) {
        post {
          entity(as[String]) { e =>
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"Got request with entity of ${e.length} characters"))
          }
        }
      }
    }

    val binding = Http().newServerAt("localhost", port = 0).bind(route).futureValue

    "reject a small request that decodes into a large chunked entity" in {
      val request =
        Post(s"http:/${binding.localAddress}/noDirective", "x").withHeaders(`Content-Encoding`(HttpEncoding("custom")))
      val response = Http().singleRequest(request).futureValue
      response.status shouldEqual StatusCodes.ContentTooLarge
    }
  }

  "a route with decodeRequest that results in a large non-chunked streaming entity" should {
    val decoder = decodeTo(nonChunkedEntityOfSize(decodeMaxSize + 1))

    val route = path("noDirective") {
      decodeRequestWith(decoder) {
        post {
          entity(as[String]) { e =>
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"Got request with entity of ${e.length} characters"))
          }
        }
      }
    }

    val binding = Http().newServerAt("localhost", port = 0).bind(route).futureValue

    "reject a small request that decodes into a large non-chunked streaming entity" in {
      val request =
        Post(s"http:/${binding.localAddress}/noDirective", "x").withHeaders(`Content-Encoding`(HttpEncoding("custom")))
      val response = Http().singleRequest(request).futureValue
      response.status shouldEqual StatusCodes.ContentTooLarge
    }
  }

  "a route with decodeRequest followed by withoutSizeLimit" should {
    val route = path("noDirective") {
      decodeRequest {
        withoutSizeLimit {
          post {
            entity(as[String]) { e =>
              complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
                s"Got request with entity of ${e.length} characters"))
            }
          }
        }
      }
    }

    val binding = Http().newServerAt("localhost", port = 0).bind(route).futureValue

    "accept a small request" in {
      Http().singleRequest(Post(s"http:/${binding.localAddress}/noDirective", entityOfSize(maxContentLength)))
        .futureValue.status shouldEqual StatusCodes.OK
    }

    "accept a small request that decodes into a large entity" in {
      val data = ByteString.fromString("0" * (decodeMaxSize + 1))
      val zippedData = Await.result(Coders.Gzip.encodeAsync(data), 10.seconds)
      val request = HttpRequest(
        HttpMethods.POST,
        s"http:/${binding.localAddress}/noDirective",
        immutable.Seq(`Content-Encoding`(HttpEncodings.gzip)),
        HttpEntity(ContentTypes.`text/plain(UTF-8)`, zippedData))

      zippedData.size should be <= maxContentLength
      data.size should be > decodeMaxSize

      val response = Http().singleRequest(request).futureValue
      response.status shouldEqual StatusCodes.OK
      response.entity.dataBytes.runReduce(
        _ ++ _).futureValue.utf8String shouldEqual s"Got request with entity of ${decodeMaxSize + 1} characters"
    }

    "accept a large request that decodes into a large entity" in {
      val data = new Array[Byte](decodeMaxSize)
      random.nextBytes(data)
      val zippedData = Await.result(Coders.Gzip.encodeAsync(ByteString(data)), 3.seconds)
      val request = HttpRequest(
        HttpMethods.POST,
        s"http:/${binding.localAddress}/noDirective",
        immutable.Seq(`Content-Encoding`(HttpEncodings.gzip)),
        HttpEntity(ContentTypes.`text/plain(UTF-8)`, zippedData))

      zippedData.size should be > maxContentLength
      data.length should be <= decodeMaxSize

      Http().singleRequest(request)
        .futureValue.status shouldEqual StatusCodes.OK
    }
  }

  "the withoutSizeLimit directive" should {
    val route = path("withoutSizeLimit") {
      post {
        withoutSizeLimit {
          entity(as[String]) { _ =>
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to Pekko HTTP</h1>"))
          }
        }
      }
    }

    val binding = Http().newServerAt("localhost", port = 0).bind(route).futureValue

    "accept entities bigger than configured with pekko.http.parsing.max-content-length" in {
      Http().singleRequest(Post(s"http:/${binding.localAddress}/withoutSizeLimit", entityOfSize(maxContentLength + 1)))
        .futureValue.status shouldEqual StatusCodes.OK
    }
  }

  override def afterAll() = TestKit.shutdownActorSystem(system)

  private def byteSource(size: Int): Source[ByteString, Any] =
    Source(Array.fill[ByteString](size)(ByteString("0")).toVector)

  private def chunkedEntityOfSize(size: Int) =
    HttpEntity.Chunked(ContentTypes.`text/plain(UTF-8)`, byteSource(size).map(Chunk(_)))
  private def nonChunkedEntityOfSize(size: Int): MessageEntity =
    HttpEntity.Default(ContentTypes.`text/plain(UTF-8)`, size, byteSource(size))
  private def entityOfSize(size: Int) = HttpEntity(ContentTypes.`text/plain(UTF-8)`, "0" * size)

  private def decodeTo(result: MessageEntity): Decoder = new Decoder {
    override def encoding: HttpEncoding = HttpEncoding("custom")

    override def maxBytesPerChunk: Int = 1000
    override def withMaxBytesPerChunk(maxBytesPerChunk: Int): Decoder = this

    override def decoderFlow: Flow[ByteString, ByteString, NotUsed] = ???

    override def decodeMessage(message: HttpMessage) = message.withEntity(result)
  }
}
