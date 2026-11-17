/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.engine.http2

import java.net.InetSocketAddress

import scala.concurrent.Future

import org.apache.pekko
import pekko.http.impl.util.PekkoSpecWithMaterializer
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model.{
  AttributeKey,
  HttpEntity,
  HttpRequest,
  HttpResponse,
  RequestResponseAssociation,
  StatusCodes
}
import pekko.http.scaladsl.settings.ClientConnectionSettings
import pekko.stream.scaladsl.{ Flow, Sink, Source }
import pekko.stream.testkit.scaladsl.StreamTestKit
import pekko.stream.testkit.{ TestPublisher, TestSubscriber }
import pekko.testkit.TestProbe
import pekko.util.ByteString

import org.scalatest.concurrent.ScalaFutures

class Http2PersistentNoContentSpec extends PekkoSpecWithMaterializer with ScalaFutures {
  import pekko.http.impl.engine.http2.client.PersistentConnection
  import pekko.http.scaladsl.model.headers.RawHeader

  case class RequestId(id: Int) extends RequestResponseAssociation
  val requestId = AttributeKey[RequestId]("no-content-request-id")
  implicit val executionContext: scala.concurrent.ExecutionContext = system.dispatcher

  "A managed HTTP/2 connection receiving a content-free response" should {
    for (status <- Seq(StatusCodes.NoContent, StatusCodes.NotModified)) {
      s"keep ${status.intValue} correlation and reuse the connection while its wire end is pending" in
      StreamTestKit.assertAllStagesStopped {
        class Network extends Http2FrameHpackSupport {
          val fromNet = TestPublisher.probe[ByteString]()
          val frameProbeDelegate = Http2FrameProbe()
          def sendBytes(bytes: ByteString): Unit = fromNet.sendNext(bytes)
        }
        val connections = TestProbe()
        val settings = ClientConnectionSettings(system)
        val address = new InetSocketAddress("localhost", 12345)
        val connectionFlow = Flow.fromMaterializer { (_, _) =>
          val network = new Network
          connections.ref ! network
          val transport = Flow.fromSinkAndSource(network.sink, Source.fromPublisher(network.fromNet))
          Http2Blueprint.clientStack(settings, system.log, NoOpTelemetry).join(transport)
            .mapMaterializedValue(_ => Future.successful(Http.OutgoingConnection(address, address)))
        }.mapMaterializedValue(_.flatten)
        val input = TestPublisher.probe[HttpRequest]()
        val output = TestSubscriber.probe[HttpResponse]()
        Source.fromPublisher(input)
          .via(PersistentConnection.managedConnection(connectionFlow, settings.http2Settings))
          .runWith(Sink.fromSubscriber(output))
        output.request(3)
        input.sendNext(HttpRequest(uri = "https://example.com/one",
          headers = List(RawHeader("if-none-match", "\"cached\""))).addAttribute(requestId, RequestId(1)))
        val network = connections.expectMsgType[Network]
        network.expectBytes(Http2Protocol.ClientConnectionPreface)
        network.expectSETTINGS()
        network.expectDecodedResponseHEADERSPairs(1)
        network.sendFrame(FrameEvent.SettingsFrame(Nil))
        network.expectSettingsAck()
        network.sendHEADERS(1, endStream = false, Seq(RawHeader(":status", status.intValue.toString)))
        val first = output.expectNext()
        first.status shouldBe status
        first.attribute(requestId) shouldBe Some(RequestId(1))
        first.entity shouldBe HttpEntity.Empty
        input.sendNext(HttpRequest(uri = "https://example.com/two").addAttribute(requestId, RequestId(2)))
        network.expectDecodedResponseHEADERSPairs(3)
        network.sendHEADERS(3, endStream = true, Seq(RawHeader(":status", "200")))
        val second = output.expectNext()
        second.status shouldBe StatusCodes.OK
        second.attribute(requestId) shouldBe Some(RequestId(2))
        network.sendDATA(1, endStream = true, ByteString.empty)
        network.expect[FrameEvent.WindowUpdateFrame]().streamId shouldBe 0
        input.sendNext(HttpRequest(uri = "https://example.com/three").addAttribute(requestId, RequestId(3)))
        network.expectDecodedResponseHEADERSPairs(5)
        network.sendHEADERS(5, endStream = true, Seq(RawHeader(":status", "200")))
        output.expectNext().attribute(requestId) shouldBe Some(RequestId(3))
        input.sendComplete()
        network.fromNet.sendComplete()
        output.expectComplete()
      }
    }
  }
}
