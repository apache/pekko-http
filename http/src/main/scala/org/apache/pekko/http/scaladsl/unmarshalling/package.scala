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

import org.apache.pekko
import pekko.http.scaladsl.common.StrictForm
import pekko.http.scaladsl.model._
import pekko.util.ByteString

package object unmarshalling {
  // #unmarshaller-aliases
  type FromEntityUnmarshaller[T] = Unmarshaller[HttpEntity, T]
  type FromMessageUnmarshaller[T] = Unmarshaller[HttpMessage, T]
  type FromResponseUnmarshaller[T] = Unmarshaller[HttpResponse, T]
  type FromRequestUnmarshaller[T] = Unmarshaller[HttpRequest, T]
  type FromByteStringUnmarshaller[T] = Unmarshaller[ByteString, T]
  type FromStringUnmarshaller[T] = Unmarshaller[String, T]
  type FromStrictFormFieldUnmarshaller[T] = Unmarshaller[StrictForm.Field, T]
  // #unmarshaller-aliases
}
