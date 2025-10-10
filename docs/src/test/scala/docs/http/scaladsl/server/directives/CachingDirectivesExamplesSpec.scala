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

package docs.http.scaladsl.server.directives

import org.apache.pekko
import pekko.http.scaladsl.server.RoutingSpec
import docs.CompileOnlySpec
//#caching-directives-import
//#always-cache
//#cache
import org.apache.pekko
import pekko.http.scaladsl.server.directives.CachingDirectives._
//#caching-directives-import
//#always-cache
//#cache
import pekko.http.scaladsl.model.HttpMethods.GET

class CachingDirectivesExamplesSpec extends RoutingSpec with CompileOnlySpec {

  "cache" in {
    // #cache
    import pekko.http.scaladsl.server.RequestContext
    import pekko.http.scaladsl.model.Uri
    import pekko.http.scaladsl.model.headers.{ Authorization, `Cache-Control` }
    import pekko.http.scaladsl.model.headers.CacheDirectives.`no-cache`

    // Example keyer for non-authenticated GET requests
    val simpleKeyer: PartialFunction[RequestContext, Uri] = {
      val isGet: RequestContext => Boolean = _.request.method == GET
      val isAuthorized: RequestContext => Boolean =
        _.request.headers.exists(_.is(Authorization.lowercaseName))
      val result: PartialFunction[RequestContext, Uri] = {
        case r: RequestContext if isGet(r) && !isAuthorized(r) => r.request.uri
      }
      result
    }

    // Created outside the route to allow using
    // the same cache across multiple calls
    val myCache = routeCache[Uri]

    var i = 0
    val route =
      path("cached") {
        cache(myCache, simpleKeyer) {
          complete {
            i += 1
            i.toString
          }
        }
      }

    Get("/cached") ~> route ~> check {
      responseAs[String] shouldEqual "1"
    }
    // now cached
    Get("/cached") ~> route ~> check {
      responseAs[String] shouldEqual "1"
    }
    // caching prevented
    Get("/cached") ~> `Cache-Control`(`no-cache`) ~> route ~> check {
      responseAs[String] shouldEqual "2"
    }
    // #cache
  }
  "alwaysCache" in {
    // #always-cache
    import pekko.http.scaladsl.server.RequestContext
    import pekko.http.scaladsl.model.Uri
    import pekko.http.scaladsl.model.headers.{ Authorization, `Cache-Control` }
    import pekko.http.scaladsl.model.headers.CacheDirectives.`no-cache`

    // Example keyer for non-authenticated GET requests
    val simpleKeyer: PartialFunction[RequestContext, Uri] = {
      val isGet: RequestContext => Boolean = _.request.method == GET
      val isAuthorized: RequestContext => Boolean =
        _.request.headers.exists(_.is(Authorization.lowercaseName))
      val result: PartialFunction[RequestContext, Uri] = {
        case r: RequestContext if isGet(r) && !isAuthorized(r) => r.request.uri
      }
      result
    }

    // Created outside the route to allow using
    // the same cache across multiple calls
    val myCache = routeCache[Uri]

    var i = 0
    val route =
      path("cached") {
        alwaysCache(myCache, simpleKeyer) {
          complete {
            i += 1
            i.toString
          }
        }
      }

    Get("/cached") ~> route ~> check {
      responseAs[String] shouldEqual "1"
    }
    // now cached
    Get("/cached") ~> route ~> check {
      responseAs[String] shouldEqual "1"
    }
    Get("/cached") ~> `Cache-Control`(`no-cache`) ~> route ~> check {
      responseAs[String] shouldEqual "1"
    }
    // #always-cache
  }
  "cachingProhibited" in {
    // #caching-prohibited
    import org.apache.pekko
    import pekko.http.scaladsl.model.headers.`Cache-Control`
    import pekko.http.scaladsl.model.headers.CacheDirectives.`no-cache`

    val route =
      cachingProhibited {
        complete("abc")
      }

    Get("/") ~> route ~> check {
      handled shouldEqual false
    }
    Get("/") ~> `Cache-Control`(`no-cache`) ~> route ~> check {
      responseAs[String] shouldEqual "abc"
    }
    // #caching-prohibited
  }

  "createCache" in {
    // #keyer-function
    import org.apache.pekko
    import pekko.http.caching.scaladsl.Cache
    import pekko.http.caching.scaladsl.CachingSettings
    import pekko.http.caching.LfuCache
    import pekko.http.scaladsl.server.RequestContext
    import pekko.http.scaladsl.server.RouteResult
    import pekko.http.scaladsl.model.Uri
    import pekko.http.scaladsl.server.directives.CachingDirectives._
    import scala.concurrent.duration._

    // Use the request's URI as the cache's key
    val keyerFunction: PartialFunction[RequestContext, Uri] = {
      case r: RequestContext => r.request.uri
    }
    // #keyer-function

    var count = 0
    val innerRoute = extractUri { uri =>
      count += 1
      complete(s"Request for $uri @ count $count")
    }

    // #create-cache
    val defaultCachingSettings = CachingSettings(system)
    val lfuCacheSettings =
      defaultCachingSettings.lfuCacheSettings
        .withInitialCapacity(25)
        .withMaxCapacity(50)
        .withTimeToLive(20.seconds)
        .withTimeToIdle(10.seconds)
    val cachingSettings =
      defaultCachingSettings.withLfuCacheSettings(lfuCacheSettings)
    val lfuCache: Cache[Uri, RouteResult] = LfuCache(cachingSettings)

    // Create the route
    val route = cache(lfuCache, keyerFunction)(innerRoute)
    // #create-cache

    // We don't test the eviction settings here. Deterministic testing of eviction is hard because
    // caffeine's LFU is probabilistic.
  }
}
