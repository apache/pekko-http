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

package docs.http.scaladsl

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model.ResponsePromise
import pekko.http.scaladsl.model.headers.HttpEncodings
import pekko.http.scaladsl.model.HttpRequest
import pekko.http.scaladsl.model.HttpResponse
import pekko.http.scaladsl.model.headers
import pekko.stream.scaladsl.Flow
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source
import pekko.stream.QueueOfferResult

import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise

/** A small example app that shows how to use the HTTP/2 client API currently against actual internet servers */
object Http2ClientApp extends App {
  val config =
    ConfigFactory.parseString(
      """
         # pekko.loglevel = debug
         pekko.http.client.http2.log-frames = true
         pekko.http.client.parsing.max-content-length = 20m
      """).withFallback(ConfigFactory.defaultApplication())

  implicit val system: ActorSystem = ActorSystem("Http2ClientApp", config)
  implicit val ec: ExecutionContext = system.dispatcher

  // #response-future-association
  val dispatch = singleRequest(Http().connectionTo("pekko.apache.org").http2())

  dispatch(
    HttpRequest(
      uri = "https://pekko.apache.org/api/pekko/current/org/apache/pekko/actor/typed/scaladsl/index.html",
      headers = headers.`Accept-Encoding`(HttpEncodings.gzip) :: Nil)).onComplete { res =>
    println(s"[1] Got index.html: $res")
    res.get.entity.dataBytes.runWith(Sink.ignore).onComplete(res => println(s"Finished reading [1] $res"))
  }

  // #response-future-association

  dispatch(
    HttpRequest(
      uri = "https://pekko.apache.org/api/pekko/current/index.js",
      headers = /*headers.`Accept-Encoding`(HttpEncodings.gzip) ::*/ Nil)).onComplete { res =>
    println(s"[2] Got index.js: $res")
    res.get.entity.dataBytes.runWith(Sink.ignore).onComplete(res => println(s"Finished reading [2] $res"))
  }

  dispatch(HttpRequest(uri = "https://pekko.apache.org/api/pekko/current/lib/MaterialIcons-Regular.woff"))
    .flatMap(_.toStrict(1.second))
    .onComplete(res => println(s"[3] Got font: $res"))

  dispatch(HttpRequest(uri = "https://pekko.apache.org/favicon.ico"))
    .flatMap(_.toStrict(1.second))
    .onComplete(res => println(s"[4] Got favicon: $res"))

  // #response-future-association
  def singleRequest(
      connection: Flow[HttpRequest, HttpResponse, Any], bufferSize: Int = 100): HttpRequest => Future[HttpResponse] = {
    val queue =
      Source.queue(bufferSize)
        .via(connection)
        .to(Sink.foreach { response =>
          // complete the response promise with the response when it arrives
          val responseAssociation = response.attribute(ResponsePromise.Key).get
          responseAssociation.promise.trySuccess(response)
        })
        .run()

    req => {
      // create a promise of the response for each request and set it as an attribute on the request
      val p = Promise[HttpResponse]()
      queue.offer(req.addAttribute(ResponsePromise.Key, ResponsePromise(p))) match {
        // return the future response
        case QueueOfferResult.Enqueued    => p.future
        case QueueOfferResult.Dropped     => Future.failed(new RuntimeException("Queue overflowed. Try again later."))
        case QueueOfferResult.Failure(ex) => Future.failed(ex)
        case QueueOfferResult.QueueClosed => Future.failed(
            new RuntimeException("Queue was closed (pool shut down) while running the request. Try again later."))
      }
    }
  }
  // #response-future-association

}
