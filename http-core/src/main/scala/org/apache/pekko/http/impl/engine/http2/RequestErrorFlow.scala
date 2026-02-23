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
import pekko.http.scaladsl.model.{ HttpRequest, HttpResponse, StatusCodes }
import pekko.stream.{ Attributes, BidiShape, Inlet, Outlet }
import pekko.stream.scaladsl.BidiFlow
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }

/**
 * INTERNAL API
 */
@InternalApi
private[http2] object RequestErrorFlow {

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
    setHandlers(requestIn, requestOut, new InHandler with OutHandler {
      override def onPush(): Unit = {
        grab(requestIn) match {
          case RequestParsing.OkRequest(request) => push(requestOut, request)
          case notOk: RequestParsing.BadRequest =>
            emit(responseOut,
              HttpResponse(StatusCodes.BadRequest, entity = notOk.info.summary).addAttribute(Http2.streamId,
                notOk.streamId))
            pull(requestIn)
        }
      }

      override def onPull(): Unit = pull(requestIn)
    })
    setHandlers(responseIn, responseOut, new InHandler with OutHandler {
      override def onPush(): Unit = push(responseOut, grab(responseIn))
      override def onPull(): Unit = if (!hasBeenPulled(responseIn)) pull(responseIn)
    })

  }
}
