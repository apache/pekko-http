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
package marshalling
package sse

import org.apache.pekko
import pekko.annotation.ApiMayChange
import pekko.http.scaladsl.model.HttpEntity
import pekko.http.scaladsl.model.MediaTypes.`text/event-stream`
import pekko.http.scaladsl.model.sse.ServerSentEvent
import pekko.stream.scaladsl.Source

/**
 * Importing [[EventStreamMarshalling.toEventStream]] lets a source of [[ServerSentEvent]]s be marshalled to a
 * `HttpEntity` and hence as a `HttpResponse`.
 */
@ApiMayChange
object EventStreamMarshalling extends EventStreamMarshalling

/**
 * Mixing in this trait lets a source of [[ServerSentEvent]]s be marshalled to a `HttpEntity` and hence as a
 * `HttpResponse`.
 */
@ApiMayChange
trait EventStreamMarshalling {

  implicit final val toEventStream: ToEntityMarshaller[Source[ServerSentEvent, Any]] = {
    def marshal(messages: Source[ServerSentEvent, Any]) = HttpEntity(`text/event-stream`, messages.map(_.encode))
    Marshaller.withFixedContentType(`text/event-stream`)(marshal)
  }
}
