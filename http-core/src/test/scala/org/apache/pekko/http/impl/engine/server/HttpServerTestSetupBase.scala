/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.engine.server

import org.apache.pekko
import pekko.http.impl.engine.ws.ByteStringSinkProbe
import pekko.http.scaladsl.settings.ServerSettings
import pekko.stream.TLSProtocol._

import scala.concurrent.duration.FiniteDuration
import pekko.actor.ActorSystem
import pekko.util.ByteString
import pekko.stream._
import pekko.stream.scaladsl._
import pekko.stream.testkit.{ TestPublisher, TestSubscriber }
import pekko.http.impl.util._
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model.headers.{ ProductVersion, Server }
import pekko.http.scaladsl.model.{ HttpRequest, HttpResponse }

abstract class HttpServerTestSetupBase {
  implicit def system: ActorSystem
  implicit def materializer: Materializer

  val requests = TestSubscriber.probe[HttpRequest]()
  val responses = TestPublisher.probe[HttpResponse]()

  def settings = ServerSettings(system)
    .withServerHeader(Some(Server(List(ProductVersion("pekko-http", "test")))))

  // hook to modify server, for example add attributes
  def modifyServer(server: Http.ServerLayer): Http.ServerLayer = server

  val (netIn, netOut) = {
    val netIn = TestPublisher.probe[ByteString]()
    val netOut = ByteStringSinkProbe()

    RunnableGraph.fromGraph(GraphDSL.createGraph(modifyServer(Http().serverLayer(settings))) { implicit b => server =>
      import GraphDSL.Implicits._
      Source.fromPublisher(netIn) ~> Flow[ByteString].map(SessionBytes(null, _)) ~> server.in2
      server.out1                      ~> Flow[SslTlsOutbound].collect { case SendBytes(x) => x }.buffer(1,
        OverflowStrategy.backpressure) ~> netOut.sink
      server.out2                      ~> Sink.fromSubscriber(requests)
      Source.fromPublisher(responses)  ~> server.in1
      ClosedShape
    }).run()

    netIn -> netOut
  }

  def expectResponseWithWipedDate(expected: String): Unit = {
    val trimmed = expected.stripMarginWithNewline("\r\n")
    // XXXX = 4 bytes, ISO Date Time String = 29 bytes => need to request 25 bytes more than expected string
    val expectedSize = ByteString(trimmed, "utf8").length + 25
    val received = wipeDate(netOut.expectBytes(expectedSize).utf8String)
    assert(received == trimmed, s"Expected request '$trimmed' but got '$received'")
  }

  def wipeDate(string: String) =
    string.fastSplit('\n').map {
      case s if s.startsWith("Date:") => "Date: XXXX\r"
      case s                          => s
    }.mkString("\n")

  def expectRequest(): HttpRequest = requests.requestNext()
  def expectNoRequest(max: FiniteDuration): Unit = requests.expectNoMessage(max)
  def expectSubscribe(): Unit = netOut.expectComplete()
  def expectSubscribeAndNetworkClose(): Unit = netOut.expectSubscriptionAndComplete()
  def expectNetworkClose(): Unit = netOut.expectComplete()

  def send(data: ByteString): Unit = netIn.sendNext(data)
  def send(string: String): Unit = send(ByteString(string.stripMarginWithNewline("\r\n"), "UTF8"))

  def closeNetworkInput(): Unit = netIn.sendComplete()

  def simpleResponse(): Unit = {
    responses.sendNext(HttpResponse())
    expectResponseWithWipedDate(
      """HTTP/1.1 200 OK
        |Server: pekko-http/test
        |Date: XXXX
        |Content-Length: 0
        |
        |""")
  }

  def shutdownBlueprint(): Unit = {
    netIn.sendComplete()
    requests.expectComplete()

    responses.sendComplete()
    netOut.cancel()
  }
}
