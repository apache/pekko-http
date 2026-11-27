/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.engine.http2

import java.util.Base64
import java.util.concurrent.CompletionStage

import javax.net.ssl.SSLEngine

import scala.concurrent.{ Future, Promise }
import scala.concurrent.duration._
import scala.jdk.FutureConverters._

import org.apache.pekko
import pekko.http.impl.engine.http2.FrameEvent.{ DataFrame, HeadersFrame, SettingsAckFrame, SettingsFrame }
import pekko.http.impl.engine.http2.framing.FrameRenderer
import pekko.http.impl.util.{ ExampleHttpContexts, PekkoSpecWithMaterializer }
import pekko.http.javadsl
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model.{
  AttributeKeys,
  ContentTypes,
  HttpEntity,
  HttpProtocols,
  HttpRequest,
  HttpResponse,
  StatusCodes
}
import pekko.http.scaladsl.settings.ServerSettings
import pekko.japi.function.Function
import pekko.stream.{ OverflowStrategy, TLSClosing }
import pekko.stream.TLSProtocol._
import pekko.stream.scaladsl.{ BidiFlow, Flow, Keep, Sink, Source, TLS, Tcp }
import pekko.util.ByteString

class WithPriorKnowledgeSpec extends PekkoSpecWithMaterializer("""
    pekko.http.server.http2.log-frames = on
  """) {

  "An HTTP server with PriorKnowledge" should {
    val binding = Http().newServerAt("127.0.0.1", 0).bind(_ =>
      Future.successful(HttpResponse(status = StatusCodes.ImATeapot))).futureValue

    "respond to cleartext HTTP/1.1 requests with cleartext HTTP/1.1" in {
      val (host, port) = (binding.localAddress.getHostName, binding.localAddress.getPort)
      val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = s"http://$host:$port"))
      val response = responseFuture.futureValue
      response.protocol should be(HttpProtocols.`HTTP/1.1`)
      response.status should be(StatusCodes.ImATeapot)
    }

    "respond to cleartext HTTP/2 requests with cleartext HTTP/2" in {
      val (host, port) = (binding.localAddress.getHostName, binding.localAddress.getPort)

      val fromServer = Http2FrameProbe()

      val source =
        Source.queue[String](1000, OverflowStrategy.fail)
          .map(str => ByteString(Base64.getDecoder.decode(str)))
          .via(Tcp(system).outgoingConnection(host, port))
          .toMat(fromServer.sink)(Keep.left)
          .run()

      // Obtained by converting the input request bytes from curl with --http2-prior-knowledge
      // This includes port 9009 as 'authority', which our server accepts.
      source.offer(
        "UFJJICogSFRUUC8yLjANCg0KU00NCg0KAAASBAAAAAAAAAMAAABkAARAAAAAAAIAAAAAAAAECAAAAAAAP/8AAQAAHgEFAAAAAYKEhkGKCJ1cC4Fw3HwAf3qIJbZQw6u20uBTAyovKg==").futureValue

      fromServer.expectFrameFlagsAndPayload(Http2Protocol.FrameType.SETTINGS, 0) // don't check data
      fromServer.expectSettingsAck()

      // ack settings
      source.offer("AAAABAEAAAAA")

      fromServer.expectHeaderBlock(1, true)

      source.complete()
      fromServer.plainDataProbe.expectComplete()
    }

    "respond to cleartext HTTP/2 requests with cleartext HTTP/2 (connection level client API)" in {
      val connectionFlow = Http().connectionTo(binding.localAddress.getHostName).toPort(
        binding.localAddress.getPort).http2WithPriorKnowledge()

      val (queue, headFuture) = Source.queue(1000, OverflowStrategy.fail)
        .via(connectionFlow)
        .toMat(Sink.headOption)(Keep.both)
        .run()
      queue.offer(HttpRequest())
      val head = headFuture.futureValue
      head.isEmpty should be(false)
      val response = head.get
      response.entity.discardBytes()
      response.status should ===(StatusCodes.ImATeapot)
      queue.complete()
    }

    for ((javaApi, https) <- Seq(false -> false, true -> false, true -> true)) {
      val api = if (javaApi) "Java" else "Scala"
      val transport = if (https) "HTTPS with h2 ALPN" else "cleartext"
      s"serve application-handled HTTP/2 CONNECT through the $api ServerBuilder.bind over $transport" in {
        val (port, unbind) =
          if (javaApi) {
            val settings = Http2ConnectJavaHandler.settings(system, Http2Protocol.InitialWindowSize)
            val builder = pekko.http.javadsl.Http.get(system).newServerAt("127.0.0.1", 0).withSettings(settings)
            val secured = if (https) builder.enableHttps(ExampleHttpContexts.getExampleServerContext()) else builder
            val handler: Function[javadsl.model.HttpRequest, CompletionStage[javadsl.model.HttpResponse]] =
              Http2ConnectJavaHandler.echo(_)
            val server = secured.bind(handler).asScala.futureValue
            (server.localAddress.getPort, () => server.unbind().asScala)
          } else {
            val settings = ServerSettings(system).mapHttp2Settings(_.withAcceptConnectRequests(true)
              .withIncomingConnectionLevelBufferSize(Http2Protocol.InitialWindowSize))
            val server = Http().newServerAt("127.0.0.1", 0).withSettings(settings).bind { request =>
              val tunnel = request.attribute(AttributeKeys.http2ConnectData).get
              Future.successful(tunnel.accept(HttpEntity.Chunked.fromData(ContentTypes.`application/octet-stream`,
                tunnel.data.map(bytes => ByteString("local:") ++ bytes))))
            }.futureValue
            (server.localAddress.getPort, () => server.unbind())
          }

        val fromServer = Http2FrameProbe()
        val tcp = Tcp(system).outgoingConnection("127.0.0.1", port)
        val clientEngine = Promise[SSLEngine]()
        val connection =
          if (https) {
            val commands = BidiFlow.fromFlows(
              Flow[ByteString].map(SendBytes(_)),
              Flow[SslTlsInbound].collect { case SessionBytes(_, bytes) => bytes })
            val tls = TLS(() => {
                // Use the example CA and the certificate's name without doing a DNS lookup.
                val engine = ExampleHttpContexts.exampleClientContext.engineCreator(Some("pekko.example.org" -> port))
                val params = engine.getSSLParameters
                params.setApplicationProtocols(Array("h2"))
                engine.setSSLParameters(params)
                clientEngine.success(engine)
                engine
              }, TLSClosing.eagerClose)
            commands.atop(tls).join(tcp)
          } else tcp
        val queue = Source.queue[ByteString](8, OverflowStrategy.fail)
          .via(connection)
          .toMat(fromServer.sink)(Keep.left)
          .run()
        try {
          val codec = new HPackEncodingSupport {}
          queue.offer(Http2Protocol.ClientConnectionPreface ++ FrameRenderer.render(SettingsFrame(Nil))).futureValue
          fromServer.expectSETTINGS()
          fromServer.expectSettingsAck()
          if (https) clientEngine.future.futureValue.getApplicationProtocol shouldBe "h2"
          queue.offer(FrameRenderer.render(SettingsAckFrame(Nil))).futureValue

          val headers = codec.encodeHeaderPairs(Seq(":method" -> "CONNECT", ":authority" -> "example.com:443"))
          queue.offer(FrameRenderer.render(HeadersFrame(
            1, endStream = false, endHeaders = true, headers, None))).futureValue
          fromServer.expectHeaderBlock(1, endStream = false) should not be empty

          // Normal tunnel traffic addressed only to this loopback handler, not the request's authority.
          queue.offer(FrameRenderer.render(DataFrame(1, endStream = true, ByteString("hello")))).futureValue
          fromServer.expectDATA(1, endStream = true, ByteString("local:hello"))
        } finally {
          queue.complete()
          unbind().futureValue
        }
      }
    }

    "send ':scheme' and ':authority' for a relative URI from the connection (connection level client API)" in {
      val echoBinding = Http().newServerAt("127.0.0.1", 0).bind(request =>
        Future.successful(HttpResponse(entity = request.uri.toString))).futureValue
      val (host, port) = (echoBinding.localAddress.getHostName, echoBinding.localAddress.getPort)
      val connectionFlow = Http().connectionTo(host).toPort(port).http2WithPriorKnowledge()

      val response = Source.single(HttpRequest(uri = "/foo")).via(connectionFlow).runWith(Sink.head).futureValue
      response.status should ===(StatusCodes.OK)
      response.entity.toStrict(3.seconds).futureValue.data.utf8String should ===(s"http://$host:$port/foo")
      echoBinding.unbind().futureValue
    }
  }
}
