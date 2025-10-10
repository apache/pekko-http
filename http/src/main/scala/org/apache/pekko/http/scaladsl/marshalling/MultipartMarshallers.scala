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

package org.apache.pekko.http.scaladsl.marshalling

import java.util.concurrent.ThreadLocalRandom

import org.apache.pekko
import pekko.event.LoggingAdapter
import pekko.http.impl.engine.rendering.BodyPartRenderer
import pekko.http.impl.util.DefaultNoLogging
import pekko.http.scaladsl.model._

trait MultipartMarshallers {
  implicit def multipartMarshaller[T <: Multipart](
      implicit log: LoggingAdapter = DefaultNoLogging): ToEntityMarshaller[T] =
    Marshaller.strict { value =>
      val boundary = randomBoundary()
      val mediaType = value.mediaType.withBoundary(boundary)
      Marshalling.WithFixedContentType(mediaType.toContentType, () => value.toEntity(boundary, log))
    }

  /**
   * The random instance that is used to create multipart boundaries. This can be overridden (e.g. in tests) to
   * choose how a boundary is created.
   */
  protected def multipartBoundaryRandom: java.util.Random = ThreadLocalRandom.current()

  /**
   * The length of randomly generated multipart boundaries (before base64 encoding). Can be overridden
   * to configure.
   */
  protected def multipartBoundaryLength: Int = 18

  /**
   * The method used to create boundaries in `multipartMarshaller`. Can be overridden to create custom boundaries.
   */
  protected def randomBoundary(): String =
    BodyPartRenderer.randomBoundary(length = multipartBoundaryLength, random = multipartBoundaryRandom)
}

object MultipartMarshallers extends MultipartMarshallers
