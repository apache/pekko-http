/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.engine.ws

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

import org.apache.pekko
import pekko.Done
import pekko.actor.Cancellable
import pekko.http.impl.util.PekkoSpecWithMaterializer
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model.AttributeKeys.webSocketUpgrade
import pekko.http.scaladsl.model.ws._
import pekko.http.scaladsl.settings.{ ClientConnectionSettings, ServerSettings, WebSocketSettings }
import pekko.stream.scaladsl._
import pekko.stream.testkit.Utils
import pekko.testkit._

class WebSocketIdleTimeoutSpec extends PekkoSpecWithMaterializer(
      """
     pekko {
       stream.materializer.debug.fuzzing-mode=off
       http.server.websocket.log-frames = on
       http.client.websocket.log-frames = on
     }
  """) {

  private val idleTimeout = 500.millis
  private def ticks(text: String): Source[Message, Cancellable] =
    Source.tick[Message](Duration.Zero, 100.millis, TextMessage(text))

  /** Binds a WebSocket server whose handler flow reports its termination to the given probe */
  private def bindServer(
      probe: TestProbe,
      outgoing: Source[Message, Any],
      adapt: WebSocketSettings => WebSocketSettings = identity): Http.ServerBinding = {
    val handler = Flow.fromSinkAndSourceCoupled(Sink.ignore, outgoing)
      .watchTermination((_, termination) => termination.onComplete(probe.ref ! _)(system.dispatcher))
    Http().newServerAt("localhost", 0)
      .adaptSettings(s => s.withWebsocketSettings(adapt(s.websocketSettings)))
      .bindSync(_.attribute(webSocketUpgrade).get.handleMessages(handler, None))
      .futureValue
  }

  private def runClient[M](
      binding: Http.ServerBinding,
      outgoing: Source[Message, M],
      adapt: WebSocketSettings => WebSocketSettings = identity): (M, Future[Done]) = {
    val clientSettings = ClientConnectionSettings(system)
    outgoing
      .viaMat(Http().webSocketClientFlow(
        WebSocketRequest("ws://127.0.0.1:" + binding.localAddress.getPort),
        settings = clientSettings.withWebsocketSettings(adapt(clientSettings.websocketSettings))))(Keep.left)
      .toMat(Sink.ignore)(Keep.both)
      .run()
  }

  private def expectIdleTimeoutFailure(probe: TestProbe): Unit =
    probe.expectMsgType[Try[Done]](3.seconds.dilated) match {
      case Failure(ex) => ex shouldBe a[WebSocketIdleTimeoutException]
      case Success(_)  => fail("Expected the handler flow to fail with a WebSocketIdleTimeoutException")
    }

  "WebSocket settings" should {
    "disable the idle timeouts by default and allow them to be set programmatically" in {
      for (settings <-
          Seq(ServerSettings(system).websocketSettings, ClientConnectionSettings(system).websocketSettings)) {
        settings.receiveIdleTimeout should ===(Duration.Inf)
        settings.sendIdleTimeout should ===(Duration.Inf)

        val updated = settings.withReceiveIdleTimeout(1.second).withSendIdleTimeout(java.time.Duration.ofSeconds(2))
        updated.receiveIdleTimeout should ===(1.second)
        updated.getReceiveIdleTimeout should ===(java.time.Duration.ofSeconds(1))
        updated.sendIdleTimeout should ===(2.seconds)
        updated.getSendIdleTimeout should ===(java.time.Duration.ofSeconds(2))
      }
    }
  }

  "A WebSocket server" should {
    "fail the handler flow when no frame is received within receive-idle-timeout" in Utils.assertAllStagesStopped {
      val probe = TestProbe()
      val binding = bindServer(probe, Source.maybe, _.withReceiveIdleTimeout(idleTimeout))
      runClient(binding, Source.maybe)

      expectIdleTimeoutFailure(probe)
      binding.unbind().futureValue
    }

    "fail the handler flow when no frame is sent within send-idle-timeout" in Utils.assertAllStagesStopped {
      val probe = TestProbe()
      val binding = bindServer(probe, Source.maybe, _.withSendIdleTimeout(idleTimeout))
      val (cancellable, _) = runClient(binding, ticks("client"))

      expectIdleTimeoutFailure(probe)
      cancellable.cancel()
      binding.unbind().futureValue
    }

    "not fail the handler flow when a silent client answers keep-alive Pings within receive-idle-timeout" in
    Utils.assertAllStagesStopped {
      val probe = TestProbe()
      val binding = bindServer(probe, Source.maybe,
        _.withReceiveIdleTimeout(idleTimeout).withPeriodicKeepAliveMaxIdle(100.millis))
      val (clientOut, _) = runClient(binding, Source.maybe[Message])

      // the Pong replies to the server's Pings are the only frames the server receives
      probe.expectNoMessage(idleTimeout * 3)

      clientOut.success(None)
      probe.expectMsg(Success(Done))
      binding.unbind().futureValue
    }
  }

  "A WebSocket client" should {
    "fail when no frame is received within receive-idle-timeout" in Utils.assertAllStagesStopped {
      val binding = bindServer(TestProbe(), Source.maybe)
      val (_, result) = runClient(binding, Source.maybe, _.withReceiveIdleTimeout(idleTimeout))

      result.failed.futureValue shouldBe a[WebSocketIdleTimeoutException]
      binding.unbind().futureValue
    }

    "fail when no frame is sent within send-idle-timeout" in Utils.assertAllStagesStopped {
      val binding = bindServer(TestProbe(), ticks("server"))
      val (_, result) = runClient(binding, Source.maybe, _.withSendIdleTimeout(idleTimeout))

      // must not be reported as a TCP idle timeout by the client connection
      val ex = result.failed.futureValue
      ex shouldBe a[WebSocketIdleTimeoutException]
      ex.getMessage should include("send-idle-timeout")
      binding.unbind().futureValue
    }
  }
}
