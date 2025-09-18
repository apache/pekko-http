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

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.io.StdIn

import spray.json.RootJsonFormat

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.common.EntityStreamingSupport
import pekko.http.scaladsl.common.JsonEntityStreamingSupport
import pekko.http.scaladsl.marshallers.xml.ScalaXmlSupport
import pekko.http.scaladsl.model.{ HttpResponse, StatusCodes }
import pekko.http.scaladsl.server.directives.Credentials
import pekko.stream.scaladsl._

import com.typesafe.config.{ Config, ConfigFactory }

object TestServer extends App {
  val testConf: Config = ConfigFactory.parseString("""
    pekko.loglevel = INFO
    pekko.log-dead-letters = off
    pekko.stream.materializer.debug.fuzzing-mode = off
    """)

  implicit val system: ActorSystem = ActorSystem("ServerTest", testConf)
  implicit val ec: ExecutionContext = system.dispatcher

  import spray.json.DefaultJsonProtocol._

  import pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  final case class Tweet(message: String)
  implicit val tweetFormat: RootJsonFormat[Tweet] = jsonFormat1(Tweet.apply)

  implicit val jsonStreaming: JsonEntityStreamingSupport = EntityStreamingSupport.json()

  import Directives._
  import ScalaXmlSupport._

  def auth: AuthenticatorPF[String] = {
    case p @ Credentials.Provided(name) if p.verify(name + "-password") => name
  }

  // format: OFF
  val routes = {
    get {
      path("") {
        withRequestTimeout(1.milli, _ => HttpResponse(
          StatusCodes.EnhanceYourCalm,
          entity = "Unable to serve response within time limit, please enhance your calm.")) {
          Thread.sleep(1000)
          complete(index)
        }
      } ~
      path("secure") {
        authenticateBasicPF("My very secure site", auth) { user =>
          complete(<html> <body> Hello <b>{user}</b>. Access has been granted! </body> </html>)
        }
      } ~
      path("ping") {
        complete("PONG!")
      } ~
      path("crash") {
        complete(sys.error("BOOM!"))
      } ~
      path("tweet") {
        complete(Tweet("Hello, world!"))
      } ~
      (path("tweets") & parameter("n".as[Int])) { n =>
        get {
          val tweets = Source.repeat(Tweet("Hello, world!")).take(n)
          complete(tweets)
        } ~
        post {
          entity(asSourceOf[Tweet]) { tweets =>
            onComplete(tweets.runFold(0)({ case (acc, t) => acc + 1 })) { count =>
              complete(s"Total tweets received: " + count)
            }
          }
        } ~
        put {
          // checking the alternative syntax also works:
          entity(as[Source[Tweet, NotUsed]]) { tweets =>
            onComplete(tweets.runFold(0)({ case (acc, t) => acc + 1 })) { count =>
              complete(s"Total tweets received: " + count)
            }
          }
        }
      }
    } ~
    pathPrefix("inner")(getFromResourceDirectory("someDir"))
  }
  // format: ON

  val bindingFuture = Http().newServerAt(interface = "0.0.0.0", port = 8080).bind(routes)

  println(s"Server online at http://0.0.0.0:8080/\nPress RETURN to stop...")
  StdIn.readLine()

  bindingFuture.flatMap(_.unbind()).onComplete(_ => system.terminate())

  lazy val index =
    <html>
      <body>
        <h1>Say hello to <i>pekko-http-core</i>!</h1>
        <p>Defined resources:</p>
        <ul>
          <li><a href="/ping">/ping</a></li>
          <li><a href="/secure">/secure</a> Use any username and '&lt;username&gt;-password' as credentials</li>
          <li><a href="/crash">/crash</a></li>
        </ul>
      </body>
    </html>
}
