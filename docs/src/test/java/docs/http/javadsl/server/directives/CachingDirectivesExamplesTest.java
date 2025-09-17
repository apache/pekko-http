/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.http.javadsl.server.directives;

import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pekko.japi.JavaPartialFunction;
import org.apache.pekko.http.javadsl.model.Uri;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpMethods;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.model.headers.*;
import org.apache.pekko.http.javadsl.model.headers.CacheDirectives.*;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.server.RequestContext;
// #caching-directives-import
import static org.apache.pekko.http.javadsl.server.directives.CachingDirectives.*;
// #caching-directives-import
// #time-unit-import
import java.time.Duration;
import java.util.concurrent.TimeUnit;
// #time-unit-import
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

import org.apache.pekko.http.javadsl.server.RouteResult;
// #create-cache-imports
import org.apache.pekko.http.caching.javadsl.Cache;
import org.apache.pekko.http.caching.javadsl.CachingSettings;
import org.apache.pekko.http.caching.javadsl.LfuCacheSettings;
import org.apache.pekko.http.caching.LfuCache;
// #create-cache-imports

// #cache
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.extractUri;
import static org.apache.pekko.http.javadsl.server.Directives.path;
import static org.apache.pekko.http.javadsl.server.PathMatchers.segment;
// #cache

public class CachingDirectivesExamplesTest extends JUnitRouteTest {

  @Test
  public void testCache() {
    // #cache
    final CachingSettings cachingSettings = CachingSettings.create(system());
    final JavaPartialFunction<RequestContext, Uri> simpleKeyer =
        new JavaPartialFunction<RequestContext, Uri>() {
          public Uri apply(RequestContext in, boolean isCheck) {
            final HttpRequest request = in.getRequest();
            final boolean isGet = request.method() == HttpMethods.GET;
            final boolean isAuthorized = request.getHeader(Authorization.class).isPresent();

            if (isGet && !isAuthorized) return request.getUri();
            else throw noMatch();
          }
        };

    // Created outside the route to allow using
    // the same cache across multiple calls
    final Cache<Uri, RouteResult> myCache = routeCache(cachingSettings);

    final AtomicInteger count = new AtomicInteger(0);
    final Route route =
        path(
            segment("cached"),
            () ->
                cache(
                    myCache,
                    simpleKeyer,
                    () ->
                        extractUri(
                            uri ->
                                complete(
                                    String.format(
                                        "Request for %s @ count %d",
                                        uri, count.incrementAndGet())))));

    // tests:
    testRoute(route)
        .run(HttpRequest.GET("/cached"))
        .assertEntity("Request for http://example.com/cached @ count 1");

    // now cached
    testRoute(route)
        .run(HttpRequest.GET("/cached"))
        .assertEntity("Request for http://example.com/cached @ count 1");

    // caching prevented
    final CacheControl noCache = CacheControl.create(CacheDirectives.NO_CACHE);
    testRoute(route)
        .run(HttpRequest.GET("/cached").addHeader(noCache))
        .assertEntity("Request for http://example.com/cached @ count 2");
    // #cache
  }

  @Test
  public void testAlwaysCache() {
    // #always-cache
    final CachingSettings cachingSettings = CachingSettings.create(system());
    // Example keyer for non-authenticated GET requests
    final JavaPartialFunction<RequestContext, Uri> simpleKeyer =
        new JavaPartialFunction<RequestContext, Uri>() {
          public Uri apply(RequestContext in, boolean isCheck) {
            final HttpRequest request = in.getRequest();
            final boolean isGet = request.method() == HttpMethods.GET;
            final boolean isAuthorized = request.getHeader(Authorization.class).isPresent();

            if (isGet && !isAuthorized) return request.getUri();
            else throw noMatch();
          }
        };

    // Created outside the route to allow using
    // the same cache across multiple calls
    final Cache<Uri, RouteResult> myCache = routeCache(cachingSettings);

    final AtomicInteger count = new AtomicInteger(0);
    final Route route =
        path(
            "cached",
            () ->
                alwaysCache(
                    myCache,
                    simpleKeyer,
                    () ->
                        extractUri(
                            uri ->
                                complete(
                                    String.format(
                                        "Request for %s @ count %d",
                                        uri, count.incrementAndGet())))));

    // tests:
    testRoute(route)
        .run(HttpRequest.GET("/cached"))
        .assertEntity("Request for http://example.com/cached @ count 1");

    // now cached
    testRoute(route)
        .run(HttpRequest.GET("/cached"))
        .assertEntity("Request for http://example.com/cached @ count 1");

    final CacheControl noCache = CacheControl.create(CacheDirectives.NO_CACHE);
    testRoute(route)
        .run(HttpRequest.GET("/cached").addHeader(noCache))
        .assertEntity("Request for http://example.com/cached @ count 1");
    // #always-cache
  }

  @Test
  public void testCachingProhibited() {
    // #caching-prohibited
    final Route route = cachingProhibited(() -> complete("abc"));

    // tests:
    testRoute(route).run(HttpRequest.GET("/")).assertStatusCode(StatusCodes.NOT_FOUND);

    final CacheControl noCache = CacheControl.create(CacheDirectives.NO_CACHE);
    testRoute(route).run(HttpRequest.GET("/").addHeader(noCache)).assertEntity("abc");
    // #caching-prohibited
  }

  @Test
  public void testCreateCache() {
    // #keyer-function

    // Use the request's URI as the cache's key
    final JavaPartialFunction<RequestContext, Uri> keyerFunction =
        new JavaPartialFunction<RequestContext, Uri>() {
          public Uri apply(RequestContext in, boolean isCheck) {
            return in.getRequest().getUri();
          }
        };
    // #keyer-function

    final AtomicInteger count = new AtomicInteger(0);
    final Route innerRoute =
        extractUri(
            uri ->
                complete(String.format("Request for %s @ count %d", uri, count.incrementAndGet())));

    // #create-cache
    final CachingSettings defaultCachingSettings = CachingSettings.create(system());
    final LfuCacheSettings lfuCacheSettings =
        defaultCachingSettings
            .lfuCacheSettings()
            .withInitialCapacity(25)
            .withMaxCapacity(50)
            .withTimeToLive(Duration.ofSeconds(20))
            .withTimeToIdle(Duration.ofSeconds(10));
    final CachingSettings cachingSettings =
        defaultCachingSettings.withLfuCacheSettings(lfuCacheSettings);
    final Cache<Uri, RouteResult> lfuCache = LfuCache.create(cachingSettings);

    // Create the route
    final Route route = cache(lfuCache, keyerFunction, () -> innerRoute);
    // #create-cache

    // We don't test the eviction settings here. Deterministic testing of eviction is hard because
    // caffeine's LFU is probabilistic.
  }
}
