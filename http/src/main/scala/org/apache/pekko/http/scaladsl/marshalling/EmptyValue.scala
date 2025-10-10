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

import scala.collection.immutable

import org.apache.pekko.http.scaladsl.model._

class EmptyValue[+T] private (val emptyValue: T)

object EmptyValue {
  implicit def emptyEntity: EmptyValue[UniversalEntity] =
    new EmptyValue[UniversalEntity](HttpEntity.Empty)

  implicit val emptyHeadersAndEntity: EmptyValue[(immutable.Seq[HttpHeader], UniversalEntity)] =
    new EmptyValue[(immutable.Seq[HttpHeader], UniversalEntity)](Nil -> HttpEntity.Empty)

  implicit val emptyResponse: EmptyValue[HttpResponse] =
    new EmptyValue[HttpResponse](HttpResponse(entity = emptyEntity.emptyValue))
}
