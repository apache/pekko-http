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

import org.apache.pekko
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.util.FastFuture._

trait PredefinedToRequestMarshallers {
  private type TRM[T] = ToRequestMarshaller[T] // brevity alias

  implicit val fromRequest: TRM[HttpRequest] = Marshaller.opaque(identity)

  implicit def fromUri: TRM[Uri] =
    Marshaller.strict { uri => Marshalling.Opaque(() => HttpRequest(uri = uri)) }

  implicit def fromMethodAndUriAndValue[S, T](implicit mt: ToEntityMarshaller[T]): TRM[(HttpMethod, Uri, T)] =
    fromMethodAndUriAndHeadersAndValue[T].compose { case (m, u, v) => (m, u, Nil, v) }

  implicit def fromMethodAndUriAndHeadersAndValue[T](
      implicit mt: ToEntityMarshaller[T]): TRM[(HttpMethod, Uri, immutable.Seq[HttpHeader], T)] =
    Marshaller(implicit ec => {
      case (m, u, h, v) => mt(v).fast.map(_.map(_.map(HttpRequest(m, u, h, _))))
    })
}

object PredefinedToRequestMarshallers extends PredefinedToRequestMarshallers
