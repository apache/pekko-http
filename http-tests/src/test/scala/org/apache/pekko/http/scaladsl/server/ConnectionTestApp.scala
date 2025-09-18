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

import scala.concurrent.Future
import scala.io.StdIn
import scala.util.{ Failure, Success, Try }

import org.apache.pekko
import pekko.actor._
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model.{ HttpRequest, HttpResponse, Uri }
import pekko.stream.OverflowStrategy
import pekko.stream.scaladsl.{ Flow, Sink, Source }

import com.typesafe.config.{ Config, ConfigFactory }

object ConnectionTestApp {
  val testConf: Config = ConfigFactory.parseString("""
    pekko.loglevel = debug
    pekko.log-dead-letters = off

    pekko.http {
      client {
        idle-timeout = 10s
      }
    }
    """)

  implicit val system: ActorSystem = ActorSystem("ConnectionTest", testConf)
  import system.dispatcher

  val clientFlow = Http().superPool[Int]()

  val sourceActor = {
    // Our superPool expects (HttpRequest, Int) as input
    val source =
      Source.actorRef[(HttpRequest, Int)](10000, OverflowStrategy.dropNew).buffer(20000, OverflowStrategy.fail)
    val sink = Sink.foreach[(Try[HttpResponse], Int)] {
      case (resp, id) => handleResponse(resp, id)
    }

    source.via(clientFlow).to(sink).run()
  }

  def sendPoolFlow(uri: Uri, id: Int): Unit = {
    sourceActor ! ((buildRequest(uri), id))
  }

  def sendPoolFuture(uri: Uri, id: Int): Unit = {
    val responseFuture: Future[HttpResponse] =
      Http().singleRequest(buildRequest(uri))

    responseFuture.onComplete(r => handleResponse(r, id))
  }

  def sendSingle(uri: Uri, id: Int): Unit = {
    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().connectionTo(uri.authority.host.address).toPort(uri.effectivePort).http()
    val responseFuture: Future[HttpResponse] =
      Source.single(buildRequest(uri))
        .via(connectionFlow)
        .runWith(Sink.head)

    responseFuture.onComplete(r => handleResponse(r, id))
  }

  private def buildRequest(uri: Uri): HttpRequest =
    HttpRequest(uri = uri)

  private def handleResponse(httpResp: Try[HttpResponse], id: Int): Unit = {
    httpResp match {
      case Success(httpRes) =>
        println(s"$id: OK (${httpRes.status.intValue})")
        httpRes.entity.dataBytes.runWith(Sink.ignore)

      case Failure(ex) =>
        println(s"$id: $ex")
    }
  }

  def main(args: Array[String]): Unit = {
    for (i <- 1 to 1000) {
      val u = s"http://127.0.0.1:6666/test/$i"
      println("u =>" + u)
      sendPoolFlow(Uri(u), i)
      // sendPoolFuture(uri, i)
      // sendSingle(uri, i)
    }

    StdIn.readLine()
    println(
      "===================== \n\n" + system.asInstanceOf[ActorSystemImpl].printTree + "\n\n========================")
    StdIn.readLine()
    system.terminate()
  }

}
