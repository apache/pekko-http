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

package org.apache.pekko.http
package scaladsl
package unmarshalling
package sse

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.annotation.ApiMayChange
import pekko.http.impl.settings.ServerSentEventSettingsImpl
import pekko.http.scaladsl.model.HttpEntity
import pekko.http.scaladsl.model.MediaTypes.`text/event-stream`
import pekko.http.scaladsl.model.sse.ServerSentEvent
import pekko.http.scaladsl.settings.ServerSentEventSettings
import pekko.stream.scaladsl.{ Keep, Source }

/**
 * Importing [[EventStreamUnmarshalling.fromEventsStream]] lets an `HttpEntity` with a `text/event-stream` media type be
 * unmarshalled to a source of [[ServerSentEvent]]s.
 *
 * The maximum size for parsing server-sent events is 8KiB. The maximum size for parsing lines of a server-sent event
 * is 4KiB. If you need to customize any of these, set the `pekko.http.sse.max-event-size` and
 * `pekko.http.sse.max-line-size` properties respectively.
 */
@ApiMayChange
object EventStreamUnmarshalling extends EventStreamUnmarshalling

/**
 * Mixing in this trait lets a `HttpEntity` with a `text/event-stream` media type be unmarshalled to a source of
 * [[ServerSentEvent]]s.
 *
 * The maximum size for parsing server-sent events is 8KiB by default and can be customized by configuring
 * `pekko.http.sse.max-event-size`. The maximum size for parsing lines of a server-sent event is 4KiB by
 * default and can be customized by configuring `pekko.http.sse.max-line-size`.
 */
@ApiMayChange
trait EventStreamUnmarshalling {

  /**
   * Lets an `HttpEntity` with a `text/event-stream` media type be unmarshalled to a source of `ServerSentEvent`s.
   */
  implicit final def fromEventsStream(
      implicit system: ActorSystem): FromEntityUnmarshaller[Source[ServerSentEvent, NotUsed]] = {
    fromEventsStream(ServerSentEventSettingsImpl(system))
  }

  /**
   * Lets an `HttpEntity` with a `text/event-stream` media type be unmarshalled to a source of `ServerSentEvent`s.
   * @param settings overrides the default unmarshalling behavior.
   */
  def fromEventsStream(settings: ServerSentEventSettings): FromEntityUnmarshaller[Source[ServerSentEvent, NotUsed]] = {
    fromEventsStream(settings.maxLineSize, settings.maxEventSize, settings.emitEmptyEvents)
  }

  private final def fromEventsStream(maxLineSize: Int, maxEventSize: Int, emitEmptyEvents: Boolean)
      : FromEntityUnmarshaller[Source[ServerSentEvent, NotUsed]] = {
    val eventStreamParser = EventStreamParser(maxLineSize, maxEventSize, emitEmptyEvents)
    def unmarshal(entity: HttpEntity) =
      entity
        .withoutSizeLimit // Because of streaming: the server keeps the response open and potentially streams huge amounts of data
        .dataBytes
        .viaMat(eventStreamParser)(Keep.none)

    Unmarshaller.strict(unmarshal).forContentTypes(`text/event-stream`)
  }
}
