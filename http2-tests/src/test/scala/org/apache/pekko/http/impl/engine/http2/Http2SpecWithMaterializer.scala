/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.engine.http2

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future }

import org.apache.pekko
import pekko.NotUsed
import pekko.event.Logging
import pekko.http.impl.engine.http2.FrameEvent._
import pekko.http.impl.engine.server.ServerTerminator
import pekko.http.impl.engine.ws.ByteStringSinkProbe
import pekko.http.impl.util.LogByteStringTools
import pekko.http.impl.util.PekkoSpecWithMaterializer
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.settings.ServerSettings
import pekko.stream.Attributes
import pekko.stream.Attributes.LogLevels
import pekko.stream.scaladsl.{ BidiFlow, Flow, Keep, Sink, Source }
import pekko.stream.testkit.{ TestPublisher, TestSubscriber }
import pekko.stream.testkit.TestPublisher.Probe
import pekko.stream.testkit.scaladsl.StreamTestKit
import pekko.util.ByteString

abstract class Http2SpecWithMaterializer(configOverrides: String) extends PekkoSpecWithMaterializer(configOverrides) {
  implicit class InWithStoppedStages(name: String) {
    def inAssertAllStagesStopped(runTest: => TestSetup) =
      name in StreamTestKit.assertAllStagesStopped {
        val setup = runTest

        // force connection to shutdown (in case it is an invalid state)
        setup.network.fromNet.sendError(new RuntimeException)
        setup.network.toNet.cancel()

        // and then assert that all stages, substreams in particular, are stopped
      }
  }

  protected /* To make ByteFlag warnings go away */ abstract class TestSetupWithoutHandshake {
    implicit def ec: ExecutionContext = system.dispatcher

    private val framesOut: Http2FrameProbe = Http2FrameProbe()
    private val toNet = framesOut.plainDataProbe
    private val fromNet = TestPublisher.probe[ByteString]()

    def handlerFlow: Flow[HttpRequest, HttpResponse, NotUsed]

    // hook to modify server, for example add attributes
    def modifyServer(server: BidiFlow[HttpResponse, ByteString, ByteString, HttpRequest, ServerTerminator]) = server

    // hook to modify server settings
    def settings: ServerSettings = ServerSettings(system).withServerHeader(None)

    final def theServer: BidiFlow[HttpResponse, ByteString, ByteString, HttpRequest, ServerTerminator] =
      modifyServer(Http2Blueprint.serverStack(settings, system.log, telemetry = NoOpTelemetry,
        dateHeaderRendering = Http().dateHeaderRendering))
        .atop(LogByteStringTools.logByteStringBidi("network-plain-text").addAttributes(
          Attributes(LogLevels(Logging.DebugLevel, Logging.DebugLevel, Logging.DebugLevel))))

    val serverTerminator =
      handlerFlow
        .joinMat(theServer)(Keep.right)
        .join(Flow.fromSinkAndSource(toNet.sink, Source.fromPublisher(fromNet)))
        .withAttributes(Attributes.inputBuffer(1, 1))
        .run()

    val network = new NetworkSide(fromNet, toNet, framesOut) with Http2FrameHpackSupport
  }

  class NetworkSide(val fromNet: Probe[ByteString], val toNet: ByteStringSinkProbe, val framesOut: Http2FrameProbe)
      extends WindowTracking {
    override def frameProbeDelegate = framesOut

    def sendBytes(bytes: ByteString): Unit = fromNet.sendNext(bytes)

  }

  /** Basic TestSetup that has already passed the exchange of the connection preface */
  abstract class TestSetup(initialClientSettings: Setting*) extends TestSetupWithoutHandshake {
    network.sendBytes(Http2Protocol.ClientConnectionPreface)
    network.expectSETTINGS()

    network.sendFrame(SettingsFrame(immutable.Seq.empty ++ initialClientSettings))
    network.expectSettingsAck()
  }

  /** Provides the user handler flow as `requestIn` and `responseOut` probes for manual stream interaction */
  trait RequestResponseProbes extends TestSetupWithoutHandshake {
    private lazy val requestIn = TestSubscriber.probe[HttpRequest]()
    private lazy val responseOut = TestPublisher.probe[HttpResponse]()

    def handlerFlow: Flow[HttpRequest, HttpResponse, NotUsed] =
      Flow.fromSinkAndSource(Sink.fromSubscriber(requestIn), Source.fromPublisher(responseOut))

    lazy val user = new UserSide(requestIn, responseOut)

    def expectGracefulCompletion(): Unit = {
      network.toNet.expectComplete()
      user.requestIn.expectComplete()
    }
  }

  class UserSide(val requestIn: TestSubscriber.Probe[HttpRequest], val responseOut: TestPublisher.Probe[HttpResponse]) {
    def expectRequest(): HttpRequest = requestIn.requestNext().removeAttribute(Http2.streamId)

    def expectRequestRaw(): HttpRequest = requestIn.requestNext() // TODO, make it so that internal headers are not listed in `headers` etc?

    def emitResponse(streamId: Int, response: HttpResponse): Unit =
      responseOut.sendNext(response.addAttribute(Http2.streamId, streamId))

  }

  /** Provides the user handler flow as a handler function */
  trait HandlerFunctionSupport extends TestSetupWithoutHandshake {
    def parallelism: Int = 2

    def handler: HttpRequest => Future[HttpResponse] =
      _ => Future.successful(HttpResponse())

    def handlerFlow: Flow[HttpRequest, HttpResponse, NotUsed] =
      Http2Blueprint.handleWithStreamIdHeader(parallelism)(handler)
  }

  def bytes(num: Int, byte: Byte): ByteString = ByteString(Array.fill[Byte](num)(byte))
}
