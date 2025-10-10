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

package docs.http.scaladsl.server.directives

import org.scalatest.wordspec.AnyWordSpec

class JsonStreamingFullExamples extends AnyWordSpec {

  "compile only spec" in {}

  // #custom-content-type
  import org.apache.pekko
  import pekko.NotUsed
  import pekko.actor.ActorSystem
  import pekko.http.scaladsl.Http
  import pekko.http.scaladsl.common.{ EntityStreamingSupport, JsonEntityStreamingSupport }
  import pekko.http.scaladsl.model.{ HttpEntity, _ }
  import pekko.http.scaladsl.server.Directives._
  import pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import pekko.http.scaladsl.marshalling.{ Marshaller, ToEntityMarshaller }
  import pekko.stream.scaladsl.Source
  import spray.json.DefaultJsonProtocol

  import scala.concurrent.ExecutionContext
  import scala.io.StdIn
  import scala.util.Random

  final case class User(name: String, id: String)

  trait UserProtocol extends DefaultJsonProtocol {

    import spray.json._

    implicit val userFormat: JsonFormat[User] = jsonFormat2(User.apply)

    val `vnd.example.api.v1+json` =
      MediaType.applicationWithFixedCharset("vnd.example.api.v1+json", HttpCharsets.`UTF-8`)
    val ct = ContentType.apply(`vnd.example.api.v1+json`)

    implicit def userMarshaller: ToEntityMarshaller[User] = Marshaller.oneOf(
      Marshaller.withFixedContentType(`vnd.example.api.v1+json`) { (user: User) =>
        HttpEntity(`vnd.example.api.v1+json`, user.toJson.compactPrint)
      })
  }

  object ApiServer extends App with UserProtocol {
    implicit val system: ActorSystem = ActorSystem("api")
    implicit val executionContext: ExecutionContext = system.dispatcher

    implicit val jsonStreamingSupport: JsonEntityStreamingSupport = EntityStreamingSupport.json()
      .withContentType(ct)
      .withParallelMarshalling(parallelism = 10, unordered = false)

    // (fake) async database query api
    def dummyUser(id: String) = User(s"User $id", id.toString)

    def fetchUsers(): Source[User, NotUsed] = Source.fromIterator(() =>
      Iterator.fill(10000) {
        val id = Random.nextInt()
        dummyUser(id.toString)
      })

    val route =
      pathPrefix("users") {
        get {
          complete(fetchUsers())
        }
      }

    val bindingFuture = Http().newServerAt("localhost", 8080).bind(route)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine()
    bindingFuture.flatMap(_.unbind()).onComplete(_ => system.terminate())
  }

  // #custom-content-type
}
