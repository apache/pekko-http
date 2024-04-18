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

package docs.http.scaladsl

import docs.CompileOnlySpec
import org.apache.pekko
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import pekko.http.scaladsl.model.HttpRequest
import pekko.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings}

import scala.concurrent.ExecutionContext

class HttpClientExampleSpec extends AnyWordSpec with Matchers with CompileOnlySpec {

  "manual-entity-consume-example-1" in compileOnlySpec {
    // #manual-entity-consume-example-1
    import org.apache.pekko
    import pekko.actor.ActorSystem
    import pekko.http.scaladsl.model._
    import pekko.stream.scaladsl.{FileIO, Framing}
    import pekko.util.ByteString

    import java.io.File

    implicit val system: ActorSystem = ActorSystem()

    val response: HttpResponse = ???

    response.entity.dataBytes
      .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256))
      .map(transformEachLine)
      .runWith(FileIO.toPath(new File("/tmp/example.out").toPath))

    def transformEachLine(line: ByteString): ByteString = ???

    // #manual-entity-consume-example-1
  }

  "manual-entity-consume-example-2" in compileOnlySpec {
    // #manual-entity-consume-example-2
    import org.apache.pekko
    import pekko.actor.ActorSystem
    import pekko.http.scaladsl.model._
    import pekko.util.ByteString

    import scala.concurrent.{ExecutionContext, Future}
    import scala.concurrent.duration._

    implicit val system: ActorSystem = ActorSystem()
    implicit val dispatcher: ExecutionContext = system.dispatcher

    case class ExamplePerson(name: String)
    def parse(line: ByteString): ExamplePerson = ???

    val response: HttpResponse = ???

    // toStrict to enforce all data be loaded into memory from the connection
    val strictEntity: Future[HttpEntity.Strict] = response.entity.toStrict(3.seconds)

    // You can now use the `data` directly...
    val person1: Future[ExamplePerson] = strictEntity.map(e => parse(e.data))

    // Though it is also still possible to use the streaming API to consume dataBytes,
    // even though now they're in memory:
    val person2: Future[ExamplePerson] =
      strictEntity.flatMap { e =>
        e.dataBytes
          .runFold(ByteString.empty) { case (acc, b) => acc ++ b }
          .map(parse)
      }

    // #manual-entity-consume-example-2
  }

  "manual-entity-consume-example-3" in compileOnlySpec {
    // #manual-entity-consume-example-3
    import org.apache.pekko
    import pekko.NotUsed
    import pekko.actor.ActorSystem
    import pekko.http.scaladsl.Http
    import pekko.http.scaladsl.model._
    import pekko.stream.scaladsl.{Flow, Sink, Source}
    import pekko.util.ByteString

    import scala.concurrent.{ExecutionContext, Future}

    implicit val system: ActorSystem = ActorSystem()
    implicit val dispatcher: ExecutionContext = system.dispatcher

    case class ExamplePerson(name: String)

    def parse(line: ByteString): Option[ExamplePerson] =
      line.utf8String.split(" ").headOption.map(ExamplePerson.apply)

    val requests: Source[HttpRequest, NotUsed] = Source
      .fromIterator(() =>
        Range(0, 10).map(i => HttpRequest(uri = Uri(s"https://localhost/people/$i"))).iterator)

    val processorFlow: Flow[Option[ExamplePerson], Int, NotUsed] =
      Flow[Option[ExamplePerson]].map(_.map(_.name.length).getOrElse(0))

    // Run and completely consume a single pekko http request
    def runRequest(req: HttpRequest): Future[Option[ExamplePerson]] =
      Http()
        .singleRequest(req)
        .flatMap { response =>
          response.entity.dataBytes
            .runReduce(_ ++ _)
            .map(parse)
        }

    // Run each pekko http flow to completion, then continue processing. You'll want to tune the `parallelism`
    // parameter to mapAsync -- higher values will create more cpu and memory load which may or may not positively
    // impact performance.
    requests
      .mapAsync(2)(runRequest)
      .via(processorFlow)
      .runWith(Sink.ignore)

    // #manual-entity-consume-example-3
  }

  "manual-entity-discard-example-1" in compileOnlySpec {
    // #manual-entity-discard-example-1
    import org.apache.pekko
    import pekko.actor.ActorSystem
    import pekko.http.scaladsl.model._
    import pekko.http.scaladsl.model.HttpMessage.DiscardedEntity

    import scala.concurrent.ExecutionContext

    implicit val system: ActorSystem = ActorSystem()
    implicit val dispatcher: ExecutionContext = system.dispatcher

    val response1: HttpResponse = ??? // obtained from an HTTP call (see examples below)

    val discarded: DiscardedEntity = response1.discardEntityBytes()
    discarded.future.onComplete { done => println("Entity discarded completely!") }

    // #manual-entity-discard-example-1
  }
  "manual-entity-discard-example-2" in compileOnlySpec {
    import pekko.Done
    import pekko.actor.ActorSystem
    import pekko.http.scaladsl.model._
    import pekko.stream.scaladsl.Sink

    import scala.concurrent.{ExecutionContext, Future}

    implicit val system: ActorSystem = ActorSystem()
    implicit val dispatcher: ExecutionContext = system.dispatcher

    // #manual-entity-discard-example-2
    val response1: HttpResponse = ??? // obtained from an HTTP call (see examples below)

    val discardingComplete: Future[Done] = response1.entity.dataBytes.runWith(Sink.ignore)
    discardingComplete.onComplete(done => println("Entity discarded completely!"))
    // #manual-entity-discard-example-2
  }

  "host-level-queue-example" in compileOnlySpec {
    // #host-level-queue-example
    import org.apache.pekko
    import pekko.actor.ActorSystem
    import pekko.http.scaladsl.Http
    import pekko.http.scaladsl.model._
    import pekko.stream.scaladsl._
    import pekko.stream.QueueOfferResult

    import scala.concurrent.{Future, Promise}
    import scala.util.{Failure, Success}

    implicit val system: ActorSystem = ActorSystem() // to get an implicit ExecutionContext into scope

    val QueueSize = 10

    // This idea came initially from this blog post (link broken):
    // http://kazuhiro.github.io/scala/akka/akka-http/akka-streams/2016/01/31/connection-pooling-with-akka-http-and-source-queue.html
    val poolClientFlow = Http().cachedHostConnectionPool[Promise[HttpResponse]]("pekko.apache.org")
    val queue =
      Source.queue[(HttpRequest, Promise[HttpResponse])](QueueSize)
        .via(poolClientFlow)
        .to(Sink.foreach {
          case ((Success(resp), p)) => p.success(resp)
          case ((Failure(e), p))    => p.failure(e)
        })
        .run()

    def queueRequest(request: HttpRequest): Future[HttpResponse] = {
      val responsePromise = Promise[HttpResponse]()
      queue.offer(request -> responsePromise) match {
        case QueueOfferResult.Enqueued    => responsePromise.future
        case QueueOfferResult.Dropped     => Future.failed(new RuntimeException("Queue overflowed. Try again later."))
        case QueueOfferResult.Failure(ex) => Future.failed(ex)
        case QueueOfferResult.QueueClosed => Future.failed(
            new RuntimeException("Queue was closed (pool shut down) while running the request. Try again later."))
      }
    }

    val responseFuture: Future[HttpResponse] = queueRequest(HttpRequest(uri = "/"))
    // #host-level-queue-example
  }

  "host-level-streamed-example" in compileOnlySpec {
    // #host-level-streamed-example
    import org.apache.pekko
    import pekko.NotUsed
    import pekko.actor.ActorSystem
    import pekko.http.scaladsl.Http
    import pekko.http.scaladsl.marshalling.Marshal
    import pekko.http.scaladsl.model._
    import pekko.http.scaladsl.model.Multipart.FormData
    import pekko.stream.scaladsl._

    import java.nio.file.{Path, Paths}
    import scala.concurrent.Future
    import scala.util.{Failure, Success}

    implicit val system: ActorSystem = ActorSystem()
    import system.dispatcher // to get an implicit ExecutionContext into scope

    case class FileToUpload(name: String, location: Path)

    def filesToUpload(): Source[FileToUpload, NotUsed] =
      // This could even be a lazy/infinite stream. For this example we have a finite one:
      Source(List(
        FileToUpload("foo.txt", Paths.get("./foo.txt")),
        FileToUpload("bar.txt", Paths.get("./bar.txt")),
        FileToUpload("baz.txt", Paths.get("./baz.txt"))))

    val poolClientFlow =
      Http().cachedHostConnectionPool[FileToUpload]("pekko.apache.org")

    def createUploadRequest(fileToUpload: FileToUpload): Future[(HttpRequest, FileToUpload)] = {
      val bodyPart =
        // fromPath will use FileIO.fromPath to stream the data from the file directly
        FormData.BodyPart.fromPath(fileToUpload.name, ContentTypes.`application/octet-stream`, fileToUpload.location)

      val body = FormData(bodyPart) // only one file per upload
      Marshal(body).to[RequestEntity].map { entity => // use marshalling to create multipart/formdata entity
        // build the request and annotate it with the original metadata
        HttpRequest(method = HttpMethods.POST, uri = "http://example.com/uploader", entity = entity) -> fileToUpload
      }
    }

    // you need to supply the list of files to upload as a Source[...]
    filesToUpload()
      // The stream will "pull out" these requests when capacity is available.
      // When that is the case we create one request concurrently
      // (the pipeline will still allow multiple requests running at the same time)
      .mapAsync(1)(createUploadRequest)
      // then dispatch the request to the connection pool
      .via(poolClientFlow)
      // report each response
      // Note: responses will not come in in the same order as requests. The requests will be run on one of the
      // multiple pooled connections and may thus "overtake" each other.
      .runForeach {
        case (Success(response), fileToUpload) =>
          // TODO: also check for response status code
          println(s"Result for file: $fileToUpload was successful: $response")
          response.discardEntityBytes() // don't forget this
        case (Failure(ex), fileToUpload) =>
          println(s"Uploading file $fileToUpload failed with $ex")
      }
    // #host-level-streamed-example
  }

  "single-request-example" in compileOnlySpec {
    import pekko.actor.ActorSystem
    import pekko.http.scaladsl.model._

    import scala.concurrent.Future
    // #create-simple-request
    HttpRequest(uri = "https://pekko.apache.org")

    // or:
    import org.apache.pekko.http.scaladsl.client.RequestBuilding.Get
    Get("https://pekko.apache.org")

    // with query params
    Get("https://pekko.apache.org?foo=bar")

    // #create-simple-request

    implicit val ec: ExecutionContext = null
    // #create-post-request
    HttpRequest(
      method = HttpMethods.POST,
      uri = "https://userservice.example/users",
      entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, "data"))

    // or:
    import org.apache.pekko.http.scaladsl.client.RequestBuilding.Post
    Post("https://userservice.example/users", "data")
    // #create-post-request

    implicit val system: ActorSystem = null
    val response: HttpResponse = null
    // #unmarshal-response-body
    import org.apache.pekko
    import pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
    import pekko.http.scaladsl.unmarshalling.Unmarshal
    import spray.json.DefaultJsonProtocol._
    import spray.json.RootJsonFormat

    case class Pet(name: String)
    implicit val petFormat: RootJsonFormat[Pet] = jsonFormat1(Pet.apply)

    val pet: Future[Pet] = Unmarshal(response).to[Pet]
    // #unmarshal-response-body
  }

  "single-request-in-actor-example" in compileOnlySpec {
    // #single-request-in-actor-example
    import org.apache.pekko
    import pekko.actor.{Actor, ActorLogging, ActorSystem}
    import pekko.http.scaladsl.Http
    import pekko.http.scaladsl.model._
    import pekko.util.ByteString

    class Myself extends Actor
        with ActorLogging {

      import context.dispatcher
      import pekko.pattern.pipe

      implicit val system: ActorSystem = context.system
      val http = Http(system)

      override def preStart() = {
        http.singleRequest(HttpRequest(uri = "http://pekko.apache.org"))
          .pipeTo(self)
      }

      def receive = {
        case HttpResponse(StatusCodes.OK, headers, entity, _) =>
          entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
            log.info("Got response, body: " + body.utf8String)
          }
        case resp @ HttpResponse(code, _, _, _) =>
          log.info("Request failed, response code: " + code)
          resp.discardEntityBytes()
      }

    }
    // #single-request-in-actor-example
  }

  "https-proxy-example-single-request" in compileOnlySpec {
    // #https-proxy-example-single-request
    import org.apache.pekko
    import pekko.actor.ActorSystem
    import pekko.http.scaladsl.{ClientTransport, Http}

    import java.net.InetSocketAddress

    implicit val system = ActorSystem()

    val proxyHost = "localhost"
    val proxyPort = 8888

    val httpsProxyTransport = ClientTransport.httpsProxy(InetSocketAddress.createUnresolved(proxyHost, proxyPort))

    val settings = ConnectionPoolSettings(system)
      .withConnectionSettings(ClientConnectionSettings(system)
        .withTransport(httpsProxyTransport))
    Http().singleRequest(HttpRequest(uri = "https://google.com"), settings = settings)
    // #https-proxy-example-single-request
  }

  "https-proxy-example-single-request with auth" in compileOnlySpec {
    import org.apache.pekko
    import pekko.actor.ActorSystem
    import pekko.http.scaladsl.{ClientTransport, Http}

    import java.net.InetSocketAddress

    implicit val system = ActorSystem()

    val proxyHost = "localhost"
    val proxyPort = 8888

    // #auth-https-proxy-example-single-request
    import org.apache.pekko.http.scaladsl.model.headers

    val proxyAddress = InetSocketAddress.createUnresolved(proxyHost, proxyPort)
    val auth = headers.BasicHttpCredentials("proxy-user", "secret-proxy-pass-dont-tell-anyone")

    val httpsProxyTransport = ClientTransport.httpsProxy(proxyAddress, auth)

    val settings = ConnectionPoolSettings(system)
      .withConnectionSettings(ClientConnectionSettings(system)
        .withTransport(httpsProxyTransport))
    Http().singleRequest(HttpRequest(uri = "http://pekko.apache.org"), settings = settings)
    // #auth-https-proxy-example-single-request
  }

}
