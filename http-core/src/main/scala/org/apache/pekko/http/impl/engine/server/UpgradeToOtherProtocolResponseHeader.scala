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

package org.apache.pekko.http.impl.engine.server

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.impl.engine.ws.FrameOutHandler
import pekko.http.scaladsl.model.{ HttpHeader, HttpResponse }
import pekko.http.scaladsl.model.headers.{ `Sec-WebSocket-Accept`, CustomHeader }
import pekko.stream.Attributes
import pekko.stream.scaladsl.Flow
import pekko.util.ByteString

/**
 * Internal API
 */
@InternalApi
private[http] sealed abstract class ProtocolSwitchResponseHeader
    extends InternalCustomHeader("UpgradeToOtherProtocolResponseHeader") {
  def handler: Flow[ByteString, ByteString, Any]

  final def handlerFor(response: HttpResponse): Flow[ByteString, ByteString, Any] =
    response.attribute(FrameOutHandler.completionCloseKey).fold(handler)(close =>
      handler.addAttributes(Attributes(close)))
}

/** Internal API */
@InternalApi
private[http] final case class UpgradeToOtherProtocolResponseHeader(handler: Flow[ByteString, ByteString, Any])
    extends ProtocolSwitchResponseHeader

/** Internal API */
@InternalApi
private[http] final class WebSocketUpgradeResponseHeader(
    val handler: Flow[ByteString, ByteString, Any],
    accept: `Sec-WebSocket-Accept`)
    extends ProtocolSwitchResponseHeader {

  def headersWithGeneratedAccept(headers: Seq[HttpHeader]): Seq[HttpHeader] = {
    val result = List.newBuilder[HttpHeader]
    val acceptName = accept.lowercaseName
    var emittedAccept = false
    headers.foreach { header =>
      if (!header.is(acceptName)) result += header
      else if (!emittedAccept) {
        result += accept
        emittedAccept = true
      }
    }
    if (!emittedAccept) result += accept
    result.result()
  }
}

/**
 * Internal API
 */
@InternalApi
private[http] abstract class InternalCustomHeader(val name: String) extends CustomHeader {
  final def renderInRequests = false
  final def renderInResponses = false
  def value: String = ""
}
