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

package org.apache.pekko.http.scaladsl.server.directives

import scala.concurrent.Future

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.annotation.ApiMayChange
import pekko.http.caching.LfuCache
import pekko.http.caching.scaladsl.{ Cache, CachingSettings }
import pekko.http.scaladsl.model.headers._
import pekko.http.scaladsl.model.headers.CacheDirectives._
import pekko.http.scaladsl.server._
import pekko.http.scaladsl.server.Directive0

@ApiMayChange
trait CachingDirectives {
  import pekko.http.scaladsl.server.directives.BasicDirectives._
  import pekko.http.scaladsl.server.directives.RouteDirectives._

  /**
   * Wraps its inner Route with caching support using the given [[Cache]] implementation and
   * keyer function.
   */
  def cache[K](cache: Cache[K, RouteResult], keyer: PartialFunction[RequestContext, K]): Directive0 =
    cachingProhibited | alwaysCache(cache, keyer)

  /**
   * Passes only requests to the inner route that explicitly forbid caching with a `Cache-Control` header with either
   * a `no-cache` or `max-age=0` setting.
   */
  def cachingProhibited: Directive0 =
    extract(_.request.headers.exists {
      case x: `Cache-Control` => x.directives.exists {
          case `no-cache`   => true
          case `max-age`(0) => true
          case _            => false
        }
      case _ => false
    }).flatMap(if (_) pass else reject)

  /**
   * Wraps its inner Route with caching support using the given [[Cache]] implementation and
   * keyer function. Note that routes producing streaming responses cannot be wrapped with this directive.
   */
  def alwaysCache[K](cache: Cache[K, RouteResult], keyer: PartialFunction[RequestContext, K]): Directive0 =
    // Do directive processing asynchronously to avoid locking the cache accidentally (#4092)
    // This will be slightly slower, but the rational here is that caching is used for slower kind of processing
    // anyway so the performance hit should be acceptable.
    Directive { inner => ctx =>
      import ctx.executionContext
      keyer.lift(ctx) match {
        case Some(key) => cache.apply(key, () => Future(inner(())(ctx)).flatten)
        case None      => inner(())(ctx)
      }
    }

  /**
   * Creates an [[LfuCache]] with default settings obtained from the system's configuration.
   */
  def routeCache[K](implicit s: ActorSystem): Cache[K, RouteResult] =
    LfuCache[K, RouteResult](s)

  /**
   * Creates an [[LfuCache]].
   */
  def routeCache[K](settings: CachingSettings): Cache[K, RouteResult] =
    LfuCache[K, RouteResult](settings)
}

object CachingDirectives extends CachingDirectives
