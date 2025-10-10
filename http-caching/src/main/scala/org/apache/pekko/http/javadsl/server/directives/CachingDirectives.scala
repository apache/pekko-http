/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.server.directives

import java.util.function.Supplier

import org.apache.pekko
import pekko.annotation.ApiMayChange
import pekko.http.caching.{ CacheJavaMapping, LfuCache }
import pekko.http.caching.javadsl.{ Cache, CachingSettings }
import pekko.http.impl.util.JavaMapping
import pekko.http.javadsl.server.{ RequestContext, Route, RouteResult }

@ApiMayChange
object CachingDirectives {

  import pekko.http.scaladsl.server.directives.{ CachingDirectives => D }

  private implicit def routeResultCacheMapping[K]: JavaMapping[Cache[K, RouteResult], pekko.http.caching.scaladsl.Cache[
      K, pekko.http.scaladsl.server.RouteResult]] =
    CacheJavaMapping.cacheMapping[K, RouteResult, K, pekko.http.scaladsl.server.RouteResult]

  /**
   * Wraps its inner Route with caching support using the given [[pekko.http.caching.scaladsl.Cache]] implementation and
   * keyer function.
   *
   * Use [[pekko.japi.JavaPartialFunction]] to build the `keyer`.
   */
  def cache[K](cache: Cache[K, RouteResult], keyer: PartialFunction[RequestContext, K], inner: Supplier[Route]) =
    RouteAdapter {
      D.cache(
        JavaMapping.toScala(cache),
        toScalaKeyer(keyer)) { inner.get.delegate }
    }

  private def toScalaKeyer[K](
      keyer: PartialFunction[RequestContext, K]): PartialFunction[pekko.http.scaladsl.server.RequestContext, K] = {
    case scalaRequestContext: pekko.http.scaladsl.server.RequestContext => {
      val javaRequestContext = pekko.http.javadsl.server.RoutingJavaMapping.RequestContext.toJava(scalaRequestContext)
      keyer(javaRequestContext)
    }
  }

  /**
   * Passes only requests to the inner route that explicitly forbid caching with a `Cache-Control` header with either
   * a `no-cache` or `max-age=0` setting.
   */
  def cachingProhibited(inner: Supplier[Route]) = RouteAdapter {
    D.cachingProhibited { inner.get.delegate }
  }

  /**
   * Wraps its inner Route with caching support using the given [[Cache]] implementation and
   * keyer function. Note that routes producing streaming responses cannot be wrapped with this directive.
   */
  def alwaysCache[K](cache: Cache[K, RouteResult], keyer: PartialFunction[RequestContext, K], inner: Supplier[Route]) =
    RouteAdapter {
      D.alwaysCache(
        JavaMapping.toScala(cache),
        toScalaKeyer(keyer)) { inner.get.delegate }
    }

  /**
   * Creates an [[LfuCache]]
   *
   * Default settings are available via [[pekko.http.caching.javadsl.CachingSettings.create]].
   */
  def routeCache[K](settings: CachingSettings): Cache[K, RouteResult] =
    LfuCache.create[K, RouteResult](settings)
}
