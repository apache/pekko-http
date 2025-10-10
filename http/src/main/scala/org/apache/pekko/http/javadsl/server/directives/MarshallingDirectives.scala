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
import pekko.http.javadsl.marshalling.Marshaller
import pekko.http.javadsl.model.{ HttpEntity, HttpRequest, HttpResponse }
import pekko.http.javadsl.server.Route
import pekko.http.javadsl.unmarshalling.Unmarshaller
import pekko.http.scaladsl.server.directives.{ MarshallingDirectives => D }

abstract class MarshallingDirectives extends HostDirectives {

  /**
   * Unmarshalls the request using the given unmarshaller, and passes the result to [inner].
   * If there is a problem with unmarshalling the request is rejected with the [[pekko.http.javadsl.server.Rejection]]
   * produced by the unmarshaller.
   */
  def request[T](
      unmarshaller: Unmarshaller[_ >: HttpRequest, T],
      inner: java.util.function.Function[T, Route]): Route = RouteAdapter {
    D.entity(unmarshaller.asScala) { value =>
      inner.apply(value).delegate
    }
  }

  /**
   * Unmarshalls the requests entity using the given unmarshaller, and passes the result to [inner].
   * If there is a problem with unmarshalling the request is rejected with the [[pekko.http.javadsl.server.Rejection]]
   * produced by the unmarshaller.
   */
  def entity[T](
      unmarshaller: Unmarshaller[_ >: HttpEntity, T],
      inner: java.util.function.Function[T, Route]): Route = RouteAdapter {
    D.entity(Unmarshaller.requestToEntity.flatMap(unmarshaller).asScala) { value =>
      inner.apply(value).delegate
    }
  }
  // If you want the raw entity, use BasicDirectives.extractEntity

  /**
   * Uses the marshaller for the given type to produce a completion function that is passed to its inner function.
   * You can use it do decouple marshaller resolution from request completion.
   */
  def completeWith[T](
      marshaller: Marshaller[T, _ <: HttpResponse],
      inner: java.util.function.Consumer[java.util.function.Consumer[T]]): Route = RouteAdapter {
    D.completeWith[T](marshaller) { f =>
      inner.accept(new java.util.function.Consumer[T]() {
        def accept(t: T): Unit = f(t)
      })
    }
  }

  /**
   * Completes the request using the given function. The input to the function is produced with the in-scope
   * entity unmarshaller and the result value of the function is marshalled with the in-scope marshaller.
   */
  def handleWith[T, R](
      unmarshaller: Unmarshaller[_ >: HttpEntity, T],
      marshaller: Marshaller[R, _ <: HttpResponse],
      inner: java.util.function.Function[T, R]): Route = RouteAdapter {
    D.handleWith[T, R] { entity =>
      inner.apply(entity)
    }(Unmarshaller.requestToEntity.flatMap(unmarshaller).asScala, marshaller)
  }

}
