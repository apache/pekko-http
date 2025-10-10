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

import java.util.function

import org.apache.pekko
import pekko.http.impl.util.JavaMapping.Implicits._
import pekko.http.javadsl.model.HttpMethod
import pekko.http.javadsl.server.Route
import pekko.http.scaladsl.server.directives.{ MethodDirectives => D }

abstract class MethodDirectives extends MarshallingDirectives {
  def delete(inner: function.Supplier[Route]): Route = RouteAdapter {
    D.delete { inner.get.delegate }
  }

  def get(inner: function.Supplier[Route]): Route = RouteAdapter {
    D.get { inner.get.delegate }
  }

  def head(inner: function.Supplier[Route]): Route = RouteAdapter {
    D.head { inner.get.delegate }
  }

  def options(inner: function.Supplier[Route]): Route = RouteAdapter {
    D.options { inner.get.delegate }
  }
  def patch(inner: function.Supplier[Route]): Route = RouteAdapter {
    D.patch { inner.get.delegate }
  }
  def post(inner: function.Supplier[Route]): Route = RouteAdapter {
    D.post { inner.get.delegate }
  }
  def put(inner: function.Supplier[Route]): Route = RouteAdapter {
    D.put { inner.get.delegate }
  }

  def extractMethod(inner: function.Function[HttpMethod, Route]) = RouteAdapter {
    D.extractMethod { m =>
      inner.apply(m).delegate
    }
  }

  def method(method: HttpMethod, inner: function.Supplier[Route]): Route = RouteAdapter {
    D.method(method.asScala) { inner.get.delegate }
  }

  /**
   * Changes the HTTP method of the request to the value of the specified query string parameter. If the query string
   * parameter is not specified this directive has no effect. If the query string is specified as something that is not
   * a HTTP method, then this directive completes the request with a `501 Not Implemented` response.
   *
   * This directive is useful for:
   *  - Use in combination with JSONP (JSONP only supports GET)
   *  - Supporting older browsers that lack support for certain HTTP methods. E.g. IE8 does not support PATCH
   */
  def overrideMethodWithParameter(paramName: String, inner: function.Supplier[Route]): Route = RouteAdapter {
    D.overrideMethodWithParameter(paramName) { inner.get.delegate }
  }

}
