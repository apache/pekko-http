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
package marshalling
package sse

import org.apache.pekko
import pekko.NotUsed
import pekko.http.javadsl.model.RequestEntity
import pekko.http.javadsl.model.sse.ServerSentEvent
import pekko.stream.javadsl.Source

/**
 * Using `eventStreamMarshaller` lets a source of [[ServerSentEvent]]s be marshalled to a `HttpResponse`.
 */
object EventStreamMarshalling {

  /**
   * Lets a source of [[ServerSentEvent]]s be marshalled to a `HttpResponse`.
   */
  val toEventStream: Marshaller[Source[ServerSentEvent, NotUsed], RequestEntity] = {
    def asScala(eventStream: Source[ServerSentEvent, NotUsed]) =
      eventStream.asScala.asInstanceOf[pekko.stream.scaladsl.Source[scaladsl.model.sse.ServerSentEvent, NotUsed]]
    Marshaller.fromScala(scaladsl.marshalling.sse.EventStreamMarshalling.toEventStream.compose(asScala))
  }
}
