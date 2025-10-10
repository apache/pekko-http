/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.engine

import java.net.InetSocketAddress
import java.util.concurrent.TimeoutException

import org.apache.pekko
import pekko.NotUsed
import pekko.annotation.InternalApi
import pekko.stream.scaladsl.{ BidiFlow, Flow }
import pekko.util.ByteString

import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.util.control.NoStackTrace

/** INTERNAL API */
@InternalApi
private[pekko] object HttpConnectionIdleTimeoutBidi {
  def apply(idleTimeout: Duration, remoteAddress: Option[InetSocketAddress])
      : BidiFlow[ByteString, ByteString, ByteString, ByteString, NotUsed] =
    idleTimeout match {
      case f: FiniteDuration => apply(f, remoteAddress)
      case _                 => BidiFlow.identity
    }
  def apply(idleTimeout: FiniteDuration, remoteAddress: Option[InetSocketAddress])
      : BidiFlow[ByteString, ByteString, ByteString, ByteString, NotUsed] = {
    val connectionToString = remoteAddress match {
      case Some(addr) => s" on connection to [$addr]"
      case _          => ""
    }
    val ex = new HttpIdleTimeoutException(
      "HTTP idle-timeout encountered" + connectionToString + ", " +
      "no bytes passed in the last " + idleTimeout + ". " +
      "This is configurable by pekko.http.[server|client].idle-timeout.", idleTimeout)

    val mapError = Flow[ByteString].mapError { case t: TimeoutException => ex }

    val toNetTimeout: BidiFlow[ByteString, ByteString, ByteString, ByteString, NotUsed] =
      BidiFlow.fromFlows(
        mapError,
        Flow[ByteString])
    val fromNetTimeout: BidiFlow[ByteString, ByteString, ByteString, ByteString, NotUsed] =
      toNetTimeout.reversed

    fromNetTimeout.atop(BidiFlow.bidirectionalIdleTimeout[ByteString, ByteString](idleTimeout)).atop(toNetTimeout)
  }

}

class HttpIdleTimeoutException(msg: String, timeout: FiniteDuration) extends TimeoutException(msg: String)
    with NoStackTrace
