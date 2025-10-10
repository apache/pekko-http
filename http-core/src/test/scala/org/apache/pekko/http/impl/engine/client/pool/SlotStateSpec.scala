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
import pekko.http.scaladsl.model.{ headers, HttpRequest, HttpResponse }
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

    "consider a slot 'idle' only when the request has been successfully sent" in {
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
      state = state.onResponseEntityCompleted(context)
      // We don't want this slot to become idle yet, because consuming the request might still take
      // a while, in which case putting this request on another connection would be lower latency.
      // In theory we could get higher throughput by allowing requests on busy connections, but
      // there are also situations where it would have an adverse effect, so better to be safe.
      state.isIdle should be(false)

      state = state.onRequestEntityCompleted(context)
      state.isIdle should be(true)
    }
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
