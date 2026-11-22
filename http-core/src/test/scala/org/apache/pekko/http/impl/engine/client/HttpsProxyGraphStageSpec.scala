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

package org.apache.pekko.http.impl.engine.client

import org.apache.pekko
import pekko.http.impl.util.PekkoSpecWithMaterializer
import pekko.http.impl.util.EnhancedString
import pekko.http.scaladsl.model.headers.{ BasicHttpCredentials, HttpCredentials }
import pekko.http.scaladsl.settings.ClientConnectionSettings
import pekko.stream.scaladsl.{ Flow, Keep, Sink, Source }
import pekko.stream.testkit.scaladsl.{ TestSink, TestSource }
import pekko.stream.testkit.{ TestPublisher, TestSubscriber, Utils }
import pekko.util.ByteString

import scala.concurrent.duration._

class HttpsProxyGraphStageSpec extends PekkoSpecWithMaterializer {

  "A ProxyGraphStage" should {
    "reject target host names that would change the structure of the CONNECT request" in {
      val settings = ClientConnectionSettings(system)
      for (host <- Seq(
          "",
          "example.test\r\nProxy-Authorization: Basic injected",
          "example.test\nX-Injected: yes",
          "example.test:443 HTTP/1.1",
          "example.test\tinjected",
          "example.test\u0000",
          "example.test\u007f",
          // the renderer writes one byte per char, so these would come out as CR LF, a space and 0xE4
          "example.test\u010d\u010aX-Injected: yes",
          "example.test\u0120injected",
          "ex\u00e4mple.test")) {
        withClue(s"host with chars ${host.map(_.toInt).mkString(",")}: ") {
          an[IllegalArgumentException] should be thrownBy HttpsProxyGraphStage(host, 443, settings, None)
        }
      }
    }

    "accept host names and IP literals as target host names" in {
      val settings = ClientConnectionSettings(system)
      for (host <- Seq("example.test", "xn--exmple-cua.test", "192.0.2.1", "2001:db8::1", "[2001:db8::1]"))
        HttpsProxyGraphStage(host, 443, settings, None)
    }

    for ((inputHost, port, wireHost) <- Seq(
        ("example.test", 80, "example.test"),
        ("example.test", 443, "example.test"),
        ("example.test", 8443, "example.test"),
        ("2001:db8::1", 443, "[2001:db8::1]"),
        ("2001:db8::1", 8443, "[2001:db8::1]"),
        ("[2001:db8::1]", 8443, "[2001:db8::1]"))) {
      s"render a CONNECT request for $inputHost at port $port" in new Context {
        override val targetHostName = inputHost
        override val targetPort = port

        testCase() { (source, flowInProbe, flowOutProbe, sink) =>
          source.sendNext(ByteString("request"))
          sink.request(1)

          val expected = s"CONNECT $wireHost:$port HTTP/1.1\r\nHost: $wireHost:$port\r\n\r\n"
          flowInProbe.requestNext().utf8String shouldBe expected
          flowOutProbe.sendNext(ByteString("HTTP/1.1 200 Connection established\r\n\r\n"))
          flowInProbe.requestNext(ByteString("request"))
          flowOutProbe.sendNext(ByteString("response"))
          sink.expectNext(ByteString("response"))

          source.sendComplete()
          flowOutProbe.sendComplete()
          sink.expectComplete()
        }
      }
    }

    "send CONNECT message and then forward incoming messages" in new Context {
      testCase() { (source, flowInProbe, flowOutProbe, sink) =>
        source.sendNext(ByteString("anything"))
        sink.request(1)

        val connectMsg = flowInProbe.requestNext()
        connectMsg.utf8String should startWith(s"CONNECT $targetHostName:$targetPort HTTP/1.1")
        flowOutProbe.sendNext(ByteString("HTTP/1.0 200 Connection established\r\n\r\n"))

        // we deliberately wait here some time to see if ProxyGraphStage properly handles slow demand from HTTP(S) proxy
        flowInProbe.expectNoMessage(300.millis)

        source.sendNext(ByteString("something"))
        flowInProbe.requestNext(ByteString("anything"))
        flowOutProbe.sendNext(ByteString("anything"))
        sink.expectNext(ByteString("anything"))

        flowInProbe.requestNext(ByteString("something"))
        flowOutProbe.sendNext(ByteString("something"))
        sink.requestNext(ByteString("something"))

        source.sendComplete()
        flowOutProbe.sendComplete()
        sink.expectComplete()
      }
    }

    "send CONNECT message with ProxyAuth header and then forward incoming messages" in new Context {
      testCase(Some(basicProxyAuth)) { (source, flowInProbe, flowOutProbe, sink) =>
        source.sendNext(ByteString("anything"))
        sink.request(1)

        val connectMsg = flowInProbe.requestNext()
        connectMsg.utf8String should startWith(
          new EnhancedString(s"""CONNECT $targetHostName:$targetPort HTTP/1.1
            |Host: $targetHostName:$targetPort
            |Proxy-Authorization: Basic dGVzdFVzZXJuYW1lOnRlc3RQYXNzd29yZA==
            |
            |""").stripMarginWithNewline("\r\n"))
        flowOutProbe.sendNext(ByteString("HTTP/1.0 200 Connection established\r\n\r\n"))

        // we deliberately wait here some time to see if ProxyGraphStage properly handles slow demand from HTTP(S) proxy
        flowInProbe.expectNoMessage(300.millis)

        source.sendNext(ByteString("something"))
        flowInProbe.requestNext(ByteString("anything"))
        flowOutProbe.sendNext(ByteString("anything"))
        sink.expectNext(ByteString("anything"))

        flowInProbe.requestNext(ByteString("something"))
        flowOutProbe.sendNext(ByteString("something"))
        sink.requestNext(ByteString("something"))

        source.sendComplete()
        flowOutProbe.sendComplete()
        sink.expectComplete()
      }
    }

    "treat any 2xx response for CONNECT message as successful" in new Context {
      testCase() { (source, flowInProbe, flowOutProbe, sink) =>
        source.sendNext(ByteString("anything"))
        sink.request(1)

        flowInProbe.requestNext()
        flowOutProbe.sendNext(ByteString("HTTP/1.0 201 Connection established\r\n\r\n"))

        source.sendComplete()
        flowInProbe.requestNext(ByteString("anything"))
        flowOutProbe.sendComplete()
        sink.expectComplete()
      }
    }

    "treat fragmented 200 response for CONNECT message as successful" in new Context {
      testCase() { (source, flowInProbe, flowOutProbe, sink) =>
        source.sendNext(ByteString("anything"))
        sink.request(100)

        flowInProbe.request(100)

        flowInProbe.expectNext()
        flowOutProbe.sendNext(ByteString("HTTP/1.0"))
        flowOutProbe.sendNext(ByteString(" 200"))
        flowOutProbe.sendNext(ByteString(" Connection established\r\n\r\n"))

        val receivedByteString = flowInProbe.expectNext()
        flowOutProbe.sendNext(receivedByteString)

        sink.expectNext(ByteString("anything"))

        source.sendComplete()
        flowOutProbe.sendComplete()
        sink.expectComplete()
      }
    }

    "report the rendered IPv6 authority if the proxy rejects CONNECT" in new Context {
      override val targetHostName = "2001:db8::1"

      testCase() { (source, flowInProbe, flowOutProbe, sink) =>
        source.sendNext(ByteString("anything"))
        sink.request(100)

        flowInProbe.request(100)

        flowOutProbe.sendNext(ByteString("HTTP/1.0 501 Some Error\r\n\r\n"))

        sink.expectError() match {
          case e: ProxyConnectionFailedException =>
            e.getMessage should include(s"[2001:db8::1]:$targetPort")
          case e =>
            fail(s"should be ProxyConnectionFailedException, caught ${e.getClass.getName} instead")
        }
      }
    }

    "fail in case of unexpected Proxy response for CONNECT message" in new Context {
      testCase() { (source, flowInProbe, flowOutProbe, sink) =>
        source.sendNext(ByteString("anything"))
        sink.request(100)

        flowInProbe.request(100)

        flowOutProbe.sendNext(ByteString("something unexpected"))

        source.expectCancellation()
        sink.expectError()
      }
    }

    "forward additional data sent by Proxy" in new Context {
      testCase() { (source, flowInProbe, flowOutProbe, sink) =>
        source.sendNext(ByteString("anything"))
        sink.request(100)

        flowInProbe.request(100)

        flowInProbe.expectNext()
        flowOutProbe.sendNext(ByteString("HTTP/1.0"))
        flowOutProbe.sendNext(ByteString(" 200"))
        flowOutProbe.sendNext(ByteString(" Connection established\r\n"))
        flowOutProbe.sendNext(ByteString("Server: Apache/2.2.22\r\n\r\nsomething"))

        val receivedByteString = flowInProbe.expectNext()
        flowOutProbe.sendNext(receivedByteString)

        sink.expectNext(ByteString("something"))
        sink.expectNext(ByteString("anything"))

        source.sendComplete()
        flowOutProbe.sendComplete()
        sink.expectComplete()
      }
    }

  }

  trait Context {
    val clientSettings = ClientConnectionSettings(system)

    val targetHostName = "pekko.apache.org"
    val targetPort = 443
    val basicProxyAuth: BasicHttpCredentials = BasicHttpCredentials("testUsername", "testPassword")

    type PublisherProbe = TestPublisher.Probe[ByteString]
    type SubscriberProbe = TestSubscriber.Probe[ByteString]
    type ProxyAuth = BasicHttpCredentials

    def testCase(proxyAuth: Option[HttpCredentials] = None)(
        fn: (PublisherProbe, SubscriberProbe, PublisherProbe, SubscriberProbe) => Unit): Unit = {
      Utils.assertAllStagesStopped {
        val proxyGraphStage = HttpsProxyGraphStage(targetHostName, targetPort, clientSettings, proxyAuth)

        val flowInProbe = TestSubscriber.probe[ByteString]()
        val flowOutProbe = TestPublisher.probe[ByteString]()

        val proxyFlow = Flow.fromSinkAndSource(
          Sink.fromSubscriber(flowInProbe),
          Source.fromPublisher(flowOutProbe))

        val flowUnderTest = proxyGraphStage.join(proxyFlow)

        val (source, sink) = TestSource[ByteString]()
          .via(flowUnderTest)
          .toMat(TestSink())(Keep.both)
          .run()

        fn(source, flowInProbe, flowOutProbe, sink)
      }
    }
  }
}
