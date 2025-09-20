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

package org.apache.pekko.http.scaladsl.coding

import org.apache.pekko
import pekko.http.scaladsl.model.{ HttpRequest, HttpResponse, RequestEntity, ResponseEntity }
import pekko.stream.scaladsl.Flow
import pekko.util.ByteString

/** An abstraction to transform data bytes of HttpMessages or HttpEntities */
sealed trait DataMapper[T] {
  def transformDataBytes(t: T, transformer: Flow[ByteString, ByteString, _]): T
}
object DataMapper {
  implicit val mapRequestEntity: DataMapper[RequestEntity] =
    new DataMapper[RequestEntity] {
      def transformDataBytes(t: RequestEntity, transformer: Flow[ByteString, ByteString, _]): RequestEntity =
        t.transformDataBytes(transformer)
    }
  implicit val mapResponseEntity: DataMapper[ResponseEntity] =
    new DataMapper[ResponseEntity] {
      def transformDataBytes(t: ResponseEntity, transformer: Flow[ByteString, ByteString, _]): ResponseEntity =
        t.transformDataBytes(transformer)
    }

  implicit val mapRequest: DataMapper[HttpRequest] = mapMessage(mapRequestEntity)((m, f) => m.withEntity(f(m.entity)))
  implicit val mapResponse: DataMapper[HttpResponse] =
    mapMessage(mapResponseEntity)((m, f) => m.withEntity(f(m.entity)))

  def mapMessage[T, E](entityMapper: DataMapper[E])(mapEntity: (T, E => E) => T): DataMapper[T] =
    new DataMapper[T] {
      def transformDataBytes(t: T, transformer: Flow[ByteString, ByteString, _]): T =
        mapEntity(t, entityMapper.transformDataBytes(_, transformer))
    }
}
