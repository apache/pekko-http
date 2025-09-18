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

import scala.concurrent.{ ExecutionContext, Future }

import org.apache.pekko.http.scaladsl.model._

/** Something that can later be marshalled into a response */
trait ToResponseMarshallable {
  type T
  def value: T
  implicit def marshaller: ToResponseMarshaller[T]

  def apply(request: HttpRequest)(implicit ec: ExecutionContext): Future[HttpResponse] =
    Marshal(value).toResponseFor(request)
}

object ToResponseMarshallable {
  implicit def apply[A](_value: A)(implicit _marshaller: ToResponseMarshaller[A]): ToResponseMarshallable =
    new ToResponseMarshallable {
      type T = A
      def value: T = _value
      def marshaller: ToResponseMarshaller[T] = _marshaller
    }

  implicit val marshaller: ToResponseMarshaller[ToResponseMarshallable] =
    Marshaller { implicit ec => marshallable => marshallable.marshaller(marshallable.value) }
}
