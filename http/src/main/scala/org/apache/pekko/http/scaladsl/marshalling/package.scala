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

package org.apache.pekko.http.scaladsl

import scala.collection.immutable

import org.apache.pekko
import pekko.http.scaladsl.model._
import pekko.util.ByteString

package object marshalling {
  // #marshaller-aliases
  type ToEntityMarshaller[T] = Marshaller[T, MessageEntity]
  type ToByteStringMarshaller[T] = Marshaller[T, ByteString]
  type ToHeadersAndEntityMarshaller[T] = Marshaller[T, (immutable.Seq[HttpHeader], MessageEntity)]
  type ToResponseMarshaller[T] = Marshaller[T, HttpResponse]
  type ToRequestMarshaller[T] = Marshaller[T, HttpRequest]
  // #marshaller-aliases
}
