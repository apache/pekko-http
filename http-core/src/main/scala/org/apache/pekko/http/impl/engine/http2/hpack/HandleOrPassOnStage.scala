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

package org.apache.pekko.http.impl.engine.http2.hpack

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream.{ FlowShape, Inlet, Outlet }
import pekko.stream.stage.{ GraphStageLogic, InHandler, OutHandler }

/**
 * INTERNAL API
 */
@InternalApi
private[http2] abstract class HandleOrPassOnStage[T <: U, U](shape: FlowShape[T, U]) extends GraphStageLogic(shape) {
  private def in: Inlet[T] = shape.in
  private def out: Outlet[U] = shape.out

  def become(state: State): Unit = setHandlers(in, out, state)
  abstract class State extends InHandler with OutHandler {
    val handleEvent: PartialFunction[T, Unit]

    def onPush(): Unit = {
      val event = grab(in)
      handleEvent.applyOrElse[T, Unit](event, ev => push(out, ev))
    }
    def onPull(): Unit = pull(in)
  }
}
