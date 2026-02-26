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

package org.apache.pekko.http.scaladsl

import java.nio.file.Paths

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.Random

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.http.impl.util.ExampleHttpContexts
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.HttpMethods._
import pekko.http.scaladsl.unmarshalling.Unmarshal
import pekko.stream.scaladsl.FileIO

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

/**
 * App to manually test an HTTP2 server
 */
object Http2ServerTest extends App {
  val testConf: Config = ConfigFactory.parseString("""
    pekko.log-dead-letters = off
    pekko.stream.materializer.debug.fuzzing-mode = off
    pekko.actor.serialize-creators = off
    pekko.actor.serialize-messages = off
    #pekko.actor.default-dispatcher.throughput = 1000
    pekko.actor.default-dispatcher.fork-join-executor.parallelism-max=8""")
  implicit val system: ActorSystem = ActorSystem("ServerTest", testConf)
  implicit val ec: ExecutionContext = system.dispatcher

  def slowDown[T](millis: Int): T => Future[T] = { t =>
    pekko.pattern.after(millis.millis, system.scheduler)(Future.successful(t))
  }

  val syncHandler: HttpRequest => HttpResponse = {
    case HttpRequest(GET, Uri.Path("/"), _, _, _)                                          => index
    case HttpRequest(GET, Uri.Path("/ping"), _, _, _)                                      => HttpResponse(entity = "PONG!")
    case HttpRequest(GET, Uri.Path("/image-page"), _, _, _)                                => imagePage
    case HttpRequest(GET, Uri(_, _, p, _, _), _, _, _) if p.toString.startsWith("/image1") =>
      HttpResponse(entity = HttpEntity(MediaTypes.`image/jpeg`,
        FileIO.fromPath(Paths.get("bigimage.jpg"), 100000).mapAsync(1)(slowDown(1))))
    case HttpRequest(GET, Uri(_, _, p, _, _), _, _, _) if p.toString.startsWith("/image2") =>
      HttpResponse(entity = HttpEntity(MediaTypes.`image/jpeg`,
        FileIO.fromPath(Paths.get("bigimage2.jpg"), 150000).mapAsync(1)(slowDown(2))))
    case HttpRequest(GET, Uri.Path("/crash"), _, _, _) => sys.error("BOOM!")
    case _: HttpRequest                                => HttpResponse(404, entity = "Unknown resource!")
  }

  val asyncHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(POST, Uri.Path("/upload"), _, entity, _) =>
      Unmarshal(entity).to[Multipart.FormData]
        .flatMap { formData =>
          formData.parts.runFoldAsync("") { (msg, part) =>
            part.entity.dataBytes.runFold(0)(_ + _.size)
              .map(dataSize =>
                msg +
                s"${part.name} ${part.filename} $dataSize ${part.entity.contentType} ${part.additionalDispositionParams}\n")
          }
        }
        .map { msg =>
          HttpResponse(entity = s"Got upload: $msg")
        }
    case req => Future.successful(syncHandler(req))
  }

  try {
    val bindings =
      for {
        httpsBinding <- Http().newServerAt(interface = "localhost", port = 9000).enableHttps(
          ExampleHttpContexts.exampleServerContext).bind(asyncHandler)
        plainBinding <- Http().newServerAt(interface = "localhost", port = 9002).bind(asyncHandler)
      } yield (httpsBinding, plainBinding)

    Await.result(bindings, 1.second) // throws if binding fails
    println(Console.BOLD + "Server (HTTP/1.1 and HTTP/2) online at https://localhost:9000" + Console.RESET)
    println(Console.BOLD + "Server (HTTP/1/1 and HTTP/2) online at http://localhost:9002" + Console.RESET)
    println("Press RETURN to stop...")
    StdIn.readLine()
  } finally {
    system.terminate()
  }

  ////////////// helpers //////////////

  lazy val index = HttpResponse(
    entity = HttpEntity(
      ContentTypes.`text/html(UTF-8)`,
      """|<html>
        | <body>
        |    <h1>Say hello to <i>pekko-http-core</i>!</h1>
        |    <p>Defined resources:</p>
        |    <ul>
        |      <li><a href="/ping">/ping</a></li>
        |      <li><a href="/image-page">/image-page</a></li>
        |      <li><a href="/crash">/crash</a></li>
        |    </ul>
        |    <div>
        |      <form method="post" enctype="multipart/form-data" action="upload">
        |        <label for="file">File</label><input type="file" name="file" multiple="true"/><br/>
        |        <input type="submit" />
        |      </form>
        |    </div>
        |  </body>
        |</html>""".stripMargin))

  def imagesBlock = {
    def one(): String =
      s"""<img width="80" height="60" src="/image1?cachebuster=${Random.nextInt()}"></img>
         |<img width="80" height="60" src="/image2?cachebuster=${Random.nextInt()}"></img>
         |""".stripMargin

    Seq.fill(20)(one()).mkString
  }

  lazy val imagePage = HttpResponse(
    entity = HttpEntity(
      ContentTypes.`text/html(UTF-8)`,
      s"""|<html>
          | <body>
          |    <h1>Image Page</h1>
          |    $imagesBlock
          |  </body>
          |</html>""".stripMargin))
}
