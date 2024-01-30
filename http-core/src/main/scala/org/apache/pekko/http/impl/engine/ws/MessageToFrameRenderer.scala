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

package org.apache.pekko.http.impl.engine.ws

import java.nio.charset.StandardCharsets
import org.apache.pekko
import pekko.NotUsed
import pekko.util.ByteString
import pekko.stream.scaladsl.{ Flow, Source }
import Protocol.Opcode
import pekko.annotation.InternalApi
import pekko.http.impl.util.StreamUtils
import pekko.http.scaladsl.model.ws._

/**
 * Renders messages to full frames.
 *
 * INTERNAL API
 */
@InternalApi
private[http] object MessageToFrameRenderer {
  def create(serverSide: Boolean): Flow[Message, FrameStart, NotUsed] = {
    def strictFrames(opcode: Opcode, data: ByteString): Source[FrameStart, _] =
      // FIXME: fragment?
      Source.single(FrameEvent.fullFrame(opcode, None, data, fin = true))

    def streamedFrames[M](opcode: Opcode, data: Source[ByteString, M]): Source[FrameStart, Any] =
      data.via(StreamUtils.statefulMap(() => {
        var isFirst = true

        { data =>
          val frameOpcode =
            if (isFirst) {
              isFirst = false
              opcode
            } else Opcode.Continuation

          FrameEvent.fullFrame(frameOpcode, None, data, fin = false)
        }
      })) ++
      Source.single(FrameEvent.emptyLastContinuationFrame)

    Flow[Message]
      .flatMapConcat {
        case BinaryMessage.Strict(data) => strictFrames(Opcode.Binary, data)
        case bm: BinaryMessage          => streamedFrames(Opcode.Binary, bm.dataStream)
        case TextMessage.Strict(text)   => strictFrames(Opcode.Text, ByteString(text, StandardCharsets.UTF_8))
        case tm: TextMessage            => streamedFrames(Opcode.Text, tm.textStream.via(Utf8Encoder))
      }
  }
}
