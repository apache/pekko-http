/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.engine.http2

import org.apache.pekko
import pekko.NotUsed
import pekko.annotation.InternalApi
import pekko.http.impl.engine.http2.RequestParsing.ParseRequestResult
import pekko.http.scaladsl.model.{ AttributeKey, HttpRequest, HttpResponse, RequestResponseAssociation, StatusCodes }
import pekko.stream.scaladsl.BidiFlow
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import pekko.stream.{ Attributes, BidiShape, Inlet, Outlet }

/**
 * INTERNAL API
 */
@InternalApi
private[http2] object RequestErrorFlow {

  /**
   * Marks the `400 Bad Request` response generated for a malformed request. RFC 9113 section 8.1.1 makes a malformed
   * request a stream error of type `PROTOCOL_ERROR` and allows a response to be sent before the stream is reset, so
   * the stream handling resets the stream after this response if the peer has not finished its request by then.
   *
   * It is a `RequestResponseAssociation` only so that `ResponseRendering` passes it on with the rendered response.
   */
  case object MalformedRequest extends RequestResponseAssociation

  val malformedRequest = AttributeKey[MalformedRequest.type]("x-http2-malformed-request")

  private val _bidiFlow = BidiFlow.fromGraph(new RequestErrorFlow)
  def apply(): BidiFlow[HttpResponse, HttpResponse, ParseRequestResult, HttpRequest, NotUsed] = _bidiFlow

}

/**
 * INTERNAL API
 */
@InternalApi
private[http2] final class RequestErrorFlow
    extends GraphStage[BidiShape[HttpResponse, HttpResponse, ParseRequestResult, HttpRequest]] {

  val requestIn = Inlet[ParseRequestResult]("requestIn")
  val requestOut = Outlet[HttpRequest]("requestOut")
  val responseIn = Inlet[HttpResponse]("responseIn")
  val responseOut = Outlet[HttpResponse]("responseOut")

  override val shape: BidiShape[HttpResponse, HttpResponse, ParseRequestResult, HttpRequest] =
    BidiShape(responseIn, responseOut, requestIn, requestOut)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    setHandlers(requestIn, requestOut,
      new InHandler with OutHandler {
        override def onPush(): Unit = {
          grab(requestIn) match {
            case RequestParsing.OkRequest(request) => push(requestOut, request)
            case notOk: RequestParsing.BadRequest  =>
              emit(responseOut,
                HttpResponse(StatusCodes.BadRequest, entity = notOk.info.summary)
                  .addAttribute(Http2.streamId, notOk.streamId)
                  .addAttribute(RequestErrorFlow.malformedRequest, RequestErrorFlow.MalformedRequest))
              pull(requestIn)
          }
        }

        override def onPull(): Unit = pull(requestIn)
      })
    setHandlers(responseIn, responseOut,
      new InHandler with OutHandler {
        override def onPush(): Unit = push(responseOut, grab(responseIn))
        override def onPull(): Unit = if (!hasBeenPulled(responseIn)) pull(responseIn)
      })

  }
}
