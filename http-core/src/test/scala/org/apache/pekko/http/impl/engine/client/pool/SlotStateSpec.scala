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

package org.apache.pekko.http.impl.engine.client.pool

import java.net.InetSocketAddress
import org.apache.pekko
import pekko.event.LoggingAdapter
import pekko.http.impl.engine.client.PoolFlow
import pekko.http.impl.engine.client.PoolFlow.RequestContext
import pekko.http.impl.engine.client.pool.SlotState._
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model.ContentTypes
import pekko.http.scaladsl.model.HttpEntity
import pekko.http.scaladsl.model.{ headers, HttpRequest, HttpResponse, StatusCodes }
import pekko.http.scaladsl.settings.ConnectionPoolSettings
import pekko.stream.scaladsl.Source
import pekko.testkit.PekkoSpec
import pekko.util.ByteString

import scala.concurrent.Promise
import scala.concurrent.duration.Duration
import scala.util.Try

class SlotStateSpec extends PekkoSpec {
  val outgoingConnection = Http.OutgoingConnection(
    InetSocketAddress.createUnresolved("127.0.0.1", 1234),
    InetSocketAddress.createUnresolved("127.0.0.1", 5678))

  val TheRequestContext =
    RequestContext(
      HttpRequest(
        entity = HttpEntity(ContentTypes.`application/octet-stream`, Source.single(ByteString("test")))),
      Promise[HttpResponse](), 0)

  "The new connection pool slot state machine" should {
    "successfully complete a 'happy path' request" in {
      var state: SlotState = Unconnected
      val context = new MockSlotContext(system.log)
      state = context.expectOpenConnection {
        state.onPreConnect(context)
      }
      state = state.onNewRequest(context, TheRequestContext)

      state = state.onConnectionAttemptSucceeded(context, outgoingConnection)

      state should be(PushingRequestToConnection(TheRequestContext))
      state = state.onRequestDispatched(context)

      state = state.onRequestEntityCompleted(context)
      state = state.onResponseReceived(context, HttpResponse())
      state = state.onResponseDispatchable(context)
      state = state.onResponseEntitySubscribed(context)
      state = state.onResponseEntityCompleted(context)
      state should be(Idle(Duration.Inf))

      state = state.onConnectionCompleted(context)
      state should be(ToBeClosed)
    }

    "allow postponing completing the request until just after the response was received" in {
      var state: SlotState = Unconnected
      val context = new MockSlotContext(system.log)
      state = context.expectOpenConnection {
        state.onPreConnect(context)
      }
      state = state.onNewRequest(context, TheRequestContext)

      state = state.onConnectionAttemptSucceeded(context, outgoingConnection)

      state should be(PushingRequestToConnection(TheRequestContext))
      state = state.onRequestDispatched(context)

      state = state.onResponseReceived(context, HttpResponse())

      state = state.onRequestEntityCompleted(context)

      state = state.onResponseDispatchable(context)
      state = state.onResponseEntitySubscribed(context)
      state = state.onResponseEntityCompleted(context)
      state should be(Idle(Duration.Inf))
    }

    "allow postponing completing the request until just after the response was dispatchable" in {
      var state: SlotState = Unconnected
      val context = new MockSlotContext(system.log)
      state = context.expectOpenConnection {
        state.onPreConnect(context)
      }
      state = state.onNewRequest(context, TheRequestContext)

      state = state.onConnectionAttemptSucceeded(context, outgoingConnection)

      state should be(PushingRequestToConnection(TheRequestContext))
      state = state.onRequestDispatched(context)

      state = state.onResponseReceived(context, HttpResponse())
      state = state.onResponseDispatchable(context)

      state = state.onRequestEntityCompleted(context)

      state = state.onResponseEntitySubscribed(context)
      state = state.onResponseEntityCompleted(context)
      state should be(Idle(Duration.Inf))
    }

    "allow postponing completing the request until just after the response was subscribed" in {
      var state: SlotState = Unconnected
      val context = new MockSlotContext(system.log)
      state = context.expectOpenConnection {
        state.onPreConnect(context)
      }
      state = state.onNewRequest(context, TheRequestContext)

      state = state.onConnectionAttemptSucceeded(context, outgoingConnection)

      state should be(PushingRequestToConnection(TheRequestContext))
      state = state.onRequestDispatched(context)

      state = state.onResponseReceived(context, HttpResponse())
      state = state.onResponseDispatchable(context)
      state = state.onResponseEntitySubscribed(context)

      state = state.onRequestEntityCompleted(context)

      state = state.onResponseEntityCompleted(context)
      state should be(Idle(Duration.Inf))
    }

    "finish sending the request and then close the connection after an early successful response" in {
      val context = new MockSlotContext(system.log)
      var state = earlyResponseCompleted(context, HttpResponse())
      // The response was completed before the request was sent completely (an "early" response). The server may
      // still be reading the request entity, so it is sent completely, but the connection is not reused.
      state should be(WaitingForEndOfRequestEntity)

      state = state.onRequestEntityCompleted(context)
      state should be(ToBeClosed)
    }

    "close the connection when the request entity fails after an early successful response" in {
      val context = new MockSlotContext(system.log)
      var state = earlyResponseCompleted(context, HttpResponse())
      state should be(WaitingForEndOfRequestEntity)

      state = state.onRequestEntityFailed(context, new RuntimeException("request entity failed"))
      state should be(ToBeClosed)
    }

    "close the connection right away after an early error response" in {
      earlyResponseCompleted(new MockSlotContext(system.log), HttpResponse(StatusCodes.PayloadTooLarge)) should
      be(ToBeClosed)
    }

    "close the connection right away after an early response that closes the connection" in {
      earlyResponseCompleted(new MockSlotContext(system.log),
        HttpResponse(headers = headers.Connection("close") :: Nil)) should be(ToBeClosed)
    }
  }

  /** Runs a slot through a streamed request whose response entity completes before the request entity */
  private def earlyResponseCompleted(context: MockSlotContext, response: HttpResponse): SlotState = {
    var state: SlotState = Unconnected
    state = context.expectOpenConnection {
      state.onPreConnect(context)
    }
    state = state.onNewRequest(context, TheRequestContext)
    state = state.onConnectionAttemptSucceeded(context, outgoingConnection)
    state = state.onRequestDispatched(context)

    state = state.onResponseReceived(context, response)
    state = state.onResponseDispatchable(context)
    state = state.onResponseEntitySubscribed(context)
    state.onResponseEntityCompleted(context)
  }

  class MockSlotContext(
      _log: LoggingAdapter, val settings: ConnectionPoolSettings = ConnectionPoolSettings("")) extends SlotContext {
    override def log: LoggingAdapter = _log

    var connectionClosed = true
    var connectionOpenRequested = false
    var pushedRequest: Option[HttpRequest] = None
    var dispatchedResponse: Option[Try[HttpResponse]] = None

    override def openConnection(): Unit = {
      connectionOpenRequested should be(false)
      connectionOpenRequested = true
    }

    override def isConnectionClosed: Boolean = connectionClosed

    override def dispatchResponseResult(req: PoolFlow.RequestContext, result: Try[HttpResponse]): Unit =
      dispatchedResponse = Some(result)

    override def willCloseAfter(response: HttpResponse): Boolean =
      response.header[headers.Connection].exists(_.hasClose)

    def expectOpenConnection[T](cb: => T) = {
      connectionClosed should be(true)
      connectionOpenRequested should be(false)

      val res = cb
      connectionOpenRequested should be(true)

      connectionOpenRequested = false
      connectionClosed = false
      res
    }
  }
}
