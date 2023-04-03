/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl

import org.apache.pekko
import pekko.http.scaladsl.model.HttpRequest
import pekko.util.ByteString
import com.typesafe.config.{ Config, ConfigFactory }
import pekko.actor.ActorSystem
import pekko.stream._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.StdIn

object TestSingleRequest extends App {
  val testConf: Config = ConfigFactory.parseString("""
    pekko.loglevel = INFO
    pekko.log-dead-letters = off
    pekko.stream.materializer.debug.fuzzing-mode = off
    """)
  implicit val system: ActorSystem = ActorSystem("ServerTest", testConf)
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  import system.dispatcher

  val url = StdIn.readLine("url? ")

  val x = Http().singleRequest(HttpRequest(uri = url))

  val res = Await.result(x, 10.seconds)

  val response = res.entity.dataBytes.runFold(ByteString.empty)(_ ++ _).map(_.utf8String)

  println(" ------------ RESPONSE ------------")
  println(Await.result(response, 10.seconds))
  println(" -------- END OF RESPONSE ---------")

  system.terminate()
}
