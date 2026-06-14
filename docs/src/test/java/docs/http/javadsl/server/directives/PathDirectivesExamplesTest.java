/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.http.javadsl.server.directives;

import static java.util.regex.Pattern.compile;
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.extractUnmatchedPath;
import static org.apache.pekko.http.javadsl.server.Directives.ignoreTrailingSlash;
import static org.apache.pekko.http.javadsl.server.Directives.path;
import static org.apache.pekko.http.javadsl.server.Directives.pathEnd;
import static org.apache.pekko.http.javadsl.server.Directives.pathEndOrSingleSlash;
import static org.apache.pekko.http.javadsl.server.Directives.pathPrefix;
import static org.apache.pekko.http.javadsl.server.Directives.pathPrefixTest;
import static org.apache.pekko.http.javadsl.server.Directives.pathSingleSlash;
import static org.apache.pekko.http.javadsl.server.Directives.pathSuffix;
import static org.apache.pekko.http.javadsl.server.Directives.pathSuffixTest;
import static org.apache.pekko.http.javadsl.server.Directives.rawPathPrefix;
import static org.apache.pekko.http.javadsl.server.Directives.rawPathPrefixTest;
import static org.apache.pekko.http.javadsl.server.Directives.redirectToNoTrailingSlashIfPresent;
import static org.apache.pekko.http.javadsl.server.Directives.redirectToTrailingSlashIfMissing;
import static org.apache.pekko.http.javadsl.server.PathMatchers.integerSegment;
import static org.apache.pekko.http.javadsl.server.PathMatchers.neutral;
import static org.apache.pekko.http.javadsl.server.PathMatchers.segment;
import static org.apache.pekko.http.javadsl.server.PathMatchers.separateOnSlashes;
import static org.apache.pekko.http.javadsl.server.PathMatchers.slash;

import java.util.function.Supplier;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.PathMatcher1;
import org.apache.pekko.http.javadsl.server.PathMatchers;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.server.directives.RouteAdapter;
import org.apache.pekko.http.javadsl.testkit.JUnitJupiterRouteTest;
import org.junit.jupiter.api.Test;

// #path-matcher
// #path-matcher
// #path-matcher
// #path-matcher

// #path-matcher

// #path-matcher

// #path-prefix-test, path-suffix, raw-path-prefix, raw-path-prefix-test

// #path-prefix-test, path-suffix, raw-path-prefix, raw-path-prefix-test

// #path-dsl

// #path-dsl
// #pathPrefix
// #pathPrefix

// #path-end-or-single-slash
// #path-end-or-single-slash
// #path-prefix
// #path-prefix
// #path-prefix-test
// #path-prefix-test
// #path-single-slash
// #path-single-slash
// #path-suffix
// #path-suffix
// #path-suffix-test
// #path-suffix-test
// #raw-path-prefix
// #raw-path-prefix
// #raw-path-prefix-test
// #raw-path-prefix-test
// #redirect-notrailing-slash-missing
// #redirect-notrailing-slash-missing
// #redirect-notrailing-slash-present
// #redirect-notrailing-slash-present
// #ignoreTrailingSlash
// #ignoreTrailingSlash

public class PathDirectivesExamplesTest extends JUnitJupiterRouteTest {

  // #path-prefix-test, path-suffix, raw-path-prefix, raw-path-prefix-test
  Supplier<RouteAdapter> completeWithUnmatchedPath =
      () -> extractUnmatchedPath((path) -> complete(path.toString()));

  // #path-prefix-test, path-suffix, raw-path-prefix, raw-path-prefix-test

  @Test
  public void testPathMatcher() {
    // #path-matcher
    PathMatcher1<Integer> matcher =
        PathMatchers.segment("foo")
            .slash("bar")
            .slash(segment("X").concat(integerSegment()))
            .slash(segment("edit").orElse(segment("create")));

    Route route = path(matcher, i -> complete("Matched X" + i));
    // #path-matcher
  }

  @Test
  public void testPathExamples() {
    // #path-dsl
    // matches /foo/
    path(segment("foo").slash(), () -> complete(StatusCodes.OK));

    // matches /foo/bar
    path(segment("foo").slash(segment("boo")), () -> complete(StatusCodes.OK));

    // NOTE: matches /foo%2Fbar and doesn't match /foo/bar
    path(segment("foo/bar"), () -> complete(StatusCodes.OK));

    // NOTE: matches /foo/bar
    path(separateOnSlashes("foo/bar"), () -> complete(StatusCodes.OK));

    // matches e.g. /foo/123 and extracts "123" as a String
    path(segment("foo").slash(segment(compile("\\d+"))), (value) -> complete(StatusCodes.OK));

    // matches e.g. /foo/bar123 and extracts "123" as a String
    path(segment("foo").slash(segment(compile("bar(\\d+)"))), (value) -> complete(StatusCodes.OK));

    // similar to `path(Segments)`
    path(neutral().repeat(0, 10), () -> complete(StatusCodes.OK));

    // identical to path("foo" ~ (PathEnd | Slash))
    path(segment("foo").orElse(slash()), () -> complete(StatusCodes.OK));
    // #path-dsl
  }

  @Test
  public void testBasicExamples() {
    path("test", () -> complete(StatusCodes.OK));

    // matches "/test", as well
    path(segment("test"), () -> complete(StatusCodes.OK));
  }

  @Test
  public void testPathExample() {
    // #pathPrefix
    final Route route =
        concat(
            path("foo", () -> complete("/foo")),
            path(segment("foo").slash("bar"), () -> complete("/foo/bar")),
            pathPrefix(
                "ball",
                () ->
                    concat(
                        pathEnd(() -> complete("/ball")),
                        path(
                            integerSegment(),
                            (i) -> complete((i % 2 == 0) ? "even ball" : "odd ball")))));

    // tests:
    testRoute(route).run(HttpRequest.GET("/")).assertStatusCode(StatusCodes.NOT_FOUND);
    testRoute(route).run(HttpRequest.GET("/foo")).assertEntity("/foo");
    testRoute(route).run(HttpRequest.GET("/foo/bar")).assertEntity("/foo/bar");
    testRoute(route).run(HttpRequest.GET("/ball/1337")).assertEntity("odd ball");
    // #pathPrefix
  }

  @Test
  public void testPathEnd() {
    // #path-end
    final Route route =
        concat(
            pathPrefix(
                "foo",
                () ->
                    concat(
                        pathEnd(() -> complete("/foo")), path("bar", () -> complete("/foo/bar")))));

    // tests:
    testRoute(route).run(HttpRequest.GET("/foo")).assertEntity("/foo");
    testRoute(route).run(HttpRequest.GET("/foo/")).assertStatusCode(StatusCodes.NOT_FOUND);
    testRoute(route).run(HttpRequest.GET("/foo/bar")).assertEntity("/foo/bar");
    // #path-end
  }

  @Test
  public void testPathEndOrSingleSlash() {
    // #path-end-or-single-slash
    final Route route =
        concat(
            pathPrefix(
                "foo",
                () ->
                    concat(
                        pathEndOrSingleSlash(() -> complete("/foo")),
                        path("bar", () -> complete("/foo/bar")))));
    // tests:
    testRoute(route).run(HttpRequest.GET("/foo")).assertEntity("/foo");
    testRoute(route).run(HttpRequest.GET("/foo/")).assertEntity("/foo");
    testRoute(route).run(HttpRequest.GET("/foo/bar")).assertEntity("/foo/bar");
    // #path-end-or-single-slash
  }

  @Test
  public void testPathPrefix() {
    // #path-prefix
    final Route route =
        concat(
            pathPrefix(
                "ball",
                () ->
                    concat(
                        pathEnd(() -> complete("/ball")),
                        path(
                            integerSegment(),
                            (i) -> complete((i % 2 == 0) ? "even ball" : "odd ball")))));
    // tests:
    testRoute(route).run(HttpRequest.GET("/")).assertStatusCode(StatusCodes.NOT_FOUND);
    testRoute(route).run(HttpRequest.GET("/ball")).assertEntity("/ball");
    testRoute(route).run(HttpRequest.GET("/ball/1337")).assertEntity("odd ball");
    // #path-prefix
  }

  @Test
  public void testPathPrefixTest() {
    // #path-prefix-test
    final Route route =
        concat(
            pathPrefixTest(
                segment("foo").orElse("bar"),
                () ->
                    concat(
                        pathPrefix("foo", () -> completeWithUnmatchedPath.get()),
                        pathPrefix("bar", () -> completeWithUnmatchedPath.get()))));
    // tests:
    testRoute(route).run(HttpRequest.GET("/foo/doo")).assertEntity("/doo");
    testRoute(route).run(HttpRequest.GET("/bar/yes")).assertEntity("/yes");
    // #path-prefix-test
  }

  @Test
  public void testPathSingleSlash() {
    // #path-single-slash
    final Route route =
        concat(
            pathSingleSlash(() -> complete("root")),
            pathPrefix(
                "ball",
                () ->
                    concat(
                        pathSingleSlash(() -> complete("/ball/")),
                        path(
                            integerSegment(),
                            (i) -> complete((i % 2 == 0) ? "even ball" : "odd ball")))));
    // tests:
    testRoute(route).run(HttpRequest.GET("/")).assertEntity("root");
    testRoute(route).run(HttpRequest.GET("/ball")).assertStatusCode(StatusCodes.NOT_FOUND);
    testRoute(route).run(HttpRequest.GET("/ball/")).assertEntity("/ball/");
    testRoute(route).run(HttpRequest.GET("/ball/1337")).assertEntity("odd ball");
    // #path-single-slash
  }

  @Test
  public void testPathSuffix() {
    // #path-suffix
    final Route route =
        concat(
            pathPrefix(
                "start",
                () ->
                    concat(
                        pathSuffix("end", () -> completeWithUnmatchedPath.get()),
                        pathSuffix(
                            segment("foo").slash("bar").concat("baz"),
                            () -> completeWithUnmatchedPath.get()))));
    // tests:
    testRoute(route).run(HttpRequest.GET("/start/middle/end")).assertEntity("/middle/");
    testRoute(route)
        .run(HttpRequest.GET("/start/something/barbaz/foo"))
        .assertEntity("/something/");
    // #path-suffix
  }

  @Test
  public void testPathSuffixTest() {
    // #path-suffix-test
    final Route route =
        concat(pathSuffixTest(slash(), () -> complete("slashed")), complete("unslashed"));
    // tests:
    testRoute(route).run(HttpRequest.GET("/foo/")).assertEntity("slashed");
    testRoute(route).run(HttpRequest.GET("/foo")).assertEntity("unslashed");
    // #path-suffix-test
  }

  @Test
  public void testRawPathPrefix() {
    // #raw-path-prefix
    final Route route =
        concat(
            pathPrefix(
                "foo",
                () ->
                    concat(
                        rawPathPrefix("bar", () -> completeWithUnmatchedPath.get()),
                        rawPathPrefix("doo", () -> completeWithUnmatchedPath.get()))));
    // tests:
    testRoute(route).run(HttpRequest.GET("/foobar/baz")).assertEntity("/baz");
    testRoute(route).run(HttpRequest.GET("/foodoo/baz")).assertEntity("/baz");
    // #raw-path-prefix
  }

  @Test
  public void testRawPathPrefixTest() {
    // #raw-path-prefix-test
    final Route route =
        concat(
            pathPrefix(
                "foo", () -> rawPathPrefixTest("bar", () -> completeWithUnmatchedPath.get())));
    // tests:
    testRoute(route).run(HttpRequest.GET("/foobar")).assertEntity("bar");
    testRoute(route).run(HttpRequest.GET("/foobaz")).assertStatusCode(StatusCodes.NOT_FOUND);
    // #raw-path-prefix-test
  }

  @Test
  public void testRedirectToNoTrailingSlashIfMissing() {
    // #redirect-notrailing-slash-missing
    final Route route =
        redirectToTrailingSlashIfMissing(
            StatusCodes.MOVED_PERMANENTLY,
            () ->
                concat(
                    path(segment("foo").slash(), () -> complete("OK")),
                    path(
                        segment("bad-1"),
                        () ->
                            // MISTAKE!
                            // Missing .slash() in path, causes this path to never match,
                            // because it is inside a `redirectToTrailingSlashIfMissing`
                            complete(StatusCodes.NOT_IMPLEMENTED)),
                    path(
                        segment("bad-2/"),
                        () ->
                            // MISTAKE!
                            // / should be explicit with `.slash()` and not *in* the path element
                            // So it should be: segment("bad-2").slash()
                            complete(StatusCodes.NOT_IMPLEMENTED))));
    // tests:
    testRoute(route)
        .run(HttpRequest.GET("/foo"))
        .assertStatusCode(StatusCodes.MOVED_PERMANENTLY)
        .assertEntity(
            "This and all future requests should be directed to "
                + "<a href=\"http://example.com/foo/\">this URI</a>.");

    testRoute(route)
        .run(HttpRequest.GET("/foo/"))
        .assertStatusCode(StatusCodes.OK)
        .assertEntity("OK");

    testRoute(route).run(HttpRequest.GET("/bad-1/")).assertStatusCode(StatusCodes.NOT_FOUND);
    // #redirect-notrailing-slash-missing
  }

  @Test
  public void testRedirectToNoTrailingSlashIfPresent() {
    // #redirect-notrailing-slash-present
    final Route route =
        redirectToNoTrailingSlashIfPresent(
            StatusCodes.MOVED_PERMANENTLY,
            () ->
                concat(
                    path("foo", () -> complete("OK")),
                    path(
                        segment("bad").slash(),
                        () ->
                            // MISTAKE!
                            // Since inside a `redirectToNoTrailingSlashIfPresent` directive
                            // the matched path here will never contain a trailing slash,
                            // thus this path will never match.
                            //
                            // It should be `path("bad")` instead.
                            complete(StatusCodes.NOT_IMPLEMENTED))));
    // tests:
    testRoute(route)
        .run(HttpRequest.GET("/foo/"))
        .assertStatusCode(StatusCodes.MOVED_PERMANENTLY)
        .assertEntity(
            "This and all future requests should be directed to "
                + "<a href=\"http://example.com/foo\">this URI</a>.");

    testRoute(route)
        .run(HttpRequest.GET("/foo"))
        .assertStatusCode(StatusCodes.OK)
        .assertEntity("OK");

    testRoute(route).run(HttpRequest.GET("/bad")).assertStatusCode(StatusCodes.NOT_FOUND);
    // #redirect-notrailing-slash-present
  }

  @Test
  public void testIgnoreTrailingSlash() {
    // #ignoreTrailingSlash
    final Route route =
        ignoreTrailingSlash(
            () ->
                concat(
                    path(
                        "foo",
                        () ->
                            // Thanks to `ignoreTrailingSlash` it will serve both `/foo` and
                            // `/foo/`.
                            complete("OK")),
                    path(
                        PathMatchers.segment("bar").slash(),
                        () ->
                            // Thanks to `ignoreTrailingSlash` it will serve both `/bar` and
                            // `/bar/`.
                            complete("OK"))));

    // tests:
    testRoute(route)
        .run(HttpRequest.GET("/foo"))
        .assertStatusCode(StatusCodes.OK)
        .assertEntity("OK");
    testRoute(route)
        .run(HttpRequest.GET("/foo/"))
        .assertStatusCode(StatusCodes.OK)
        .assertEntity("OK");

    testRoute(route)
        .run(HttpRequest.GET("/bar"))
        .assertStatusCode(StatusCodes.OK)
        .assertEntity("OK");
    testRoute(route)
        .run(HttpRequest.GET("/bar/"))
        .assertStatusCode(StatusCodes.OK)
        .assertEntity("OK");
    // #ignoreTrailingSlash
  }
}
