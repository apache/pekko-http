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
package javadsl
package unmarshalling
package sse

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.http.javadsl.model.HttpEntity
import pekko.http.javadsl.model.sse.ServerSentEvent
import pekko.http.scaladsl.model.sse
import pekko.http.scaladsl.settings.ServerSentEventSettings
import pekko.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import pekko.stream.javadsl.Source
import pekko.stream.scaladsl

/**
 * Using `fromEventsStream` lets a `HttpEntity` with a `text/event-stream` media type be unmarshalled to a source of
 * `ServerSentEvent`s.
 */
object EventStreamUnmarshalling {

  /**
   * Lets an `HttpEntity` with a `text/event-stream` media type be unmarshalled to a source of `ServerSentEvent`s.
   */
  def fromEventsStream(implicit system: ActorSystem): Unmarshaller[HttpEntity, Source[ServerSentEvent, NotUsed]] = {
    fromEventsStream(ServerSentEventSettings(system))
  }

  /**
   * Lets an `HttpEntity` with a `text/event-stream` media type be unmarshalled to a source of `ServerSentEvent`s.
   * @param settings overrides the default unmarshalling behavior.
   */
  def fromEventsStream(settings: ServerSentEventSettings): Unmarshaller[HttpEntity, Source[ServerSentEvent, NotUsed]] =
    asHttpEntityUnmarshaller(pekko.http.scaladsl.unmarshalling.sse.EventStreamUnmarshalling.fromEventsStream(settings))

  // for binary-compatibility, since Akka HTTP 10.1.7
  @deprecated(
    "Binary compatibility method. Invocations should have an implicit ActorSystem in scope to provide access to configuration",
    "Akka HTTP 10.1.8")
  val fromEventStream: Unmarshaller[HttpEntity, Source[ServerSentEvent, NotUsed]] =
    asHttpEntityUnmarshaller(pekko.http.scaladsl.unmarshalling.sse.EventStreamUnmarshalling.fromEventStream)

  private def asHttpEntityUnmarshaller(value: FromEntityUnmarshaller[scaladsl.Source[sse.ServerSentEvent, NotUsed]])
      : Unmarshaller[HttpEntity, Source[ServerSentEvent, NotUsed]] = {
    value
      .map(_.asJava)
      .asInstanceOf[Unmarshaller[HttpEntity, Source[ServerSentEvent, NotUsed]]]
  }

}
