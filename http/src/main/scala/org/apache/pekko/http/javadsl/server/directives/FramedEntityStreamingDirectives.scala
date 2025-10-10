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

package org.apache.pekko.http.javadsl.server.directives

import org.apache.pekko
import pekko.NotUsed
import pekko.http.javadsl.common.EntityStreamingSupport
import pekko.http.javadsl.marshalling.Marshaller
import pekko.http.javadsl.model.{ HttpEntity, _ }
import pekko.http.javadsl.server.Route
import pekko.http.javadsl.unmarshalling.Unmarshaller
import pekko.http.scaladsl.marshalling.ToResponseMarshallable
import pekko.http.scaladsl.marshalling.ToResponseMarshaller
import pekko.http.scaladsl.server.{ Directives => D }
import pekko.stream.javadsl.Source
import pekko.util.ByteString

/** EXPERIMENTAL API */
abstract class FramedEntityStreamingDirectives extends TimeoutDirectives {

  import pekko.http.javadsl.server.RoutingJavaMapping._
  import pekko.http.javadsl.server.RoutingJavaMapping.Implicits._

  @CorrespondsTo("asSourceOf")
  def entityAsSourceOf[T](um: Unmarshaller[ByteString, T], support: EntityStreamingSupport,
      inner: java.util.function.Function[Source[T, NotUsed], Route]): Route = RouteAdapter {
    val umm = D.asSourceOf(um.asScala, support.asScala)
    D.entity(umm) { (s: pekko.stream.scaladsl.Source[T, NotUsed]) =>
      inner(s.asJava).delegate
    }
  }

  // implicits and multiple parameter lists used internally, Java caller does not benefit or use it
  @CorrespondsTo("complete")
  def completeWithSource[T, M](source: Source[T, M])(implicit m: Marshaller[T, ByteString],
      support: EntityStreamingSupport): Route = RouteAdapter {
    import pekko.http.scaladsl.marshalling.PredefinedToResponseMarshallers._
    val mm = m.map(ByteStringAsEntityFn).asScalaCastOutput[pekko.http.scaladsl.model.RequestEntity]
    val mmm = fromEntityStreamingSupportAndEntityMarshaller[T, M](support.asScala, mm, null)
    val response = ToResponseMarshallable(source.asScala)(mmm)
    D.complete(response)
  }

  // implicits and multiple parameter lists used internally, Java caller does not benefit or use it
  @CorrespondsTo("complete")
  def completeOKWithSource[T, M](source: Source[T, M])(implicit m: Marshaller[T, RequestEntity],
      support: EntityStreamingSupport): Route = RouteAdapter {
    import pekko.http.scaladsl.marshalling.PredefinedToResponseMarshallers._
    // don't try this at home:
    val mm = m.asScalaCastOutput[pekko.http.scaladsl.model.RequestEntity].map(
      _.httpEntity.asInstanceOf[pekko.http.scaladsl.model.RequestEntity])
    implicit val mmm: ToResponseMarshaller[pekko.stream.scaladsl.Source[T, M]] =
      fromEntityStreamingSupportAndEntityMarshaller[T, M](support.asScala, mm, null)
    val response = ToResponseMarshallable(source.asScala)
    D.complete(response)
  }

  private[this] val ByteStringAsEntityFn = new java.util.function.Function[ByteString, HttpEntity]() {
    override def apply(bs: ByteString): HttpEntity = HttpEntities.create(bs)
  }
}

object FramedEntityStreamingDirectives extends FramedEntityStreamingDirectives
