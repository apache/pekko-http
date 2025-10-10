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

import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.model.headers.*;
import org.apache.pekko.http.javadsl.server.PathMatchers;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.apache.pekko.http.javadsl.unmarshalling.Unmarshaller;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.function.Function;

// #withSizeLimitExample
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.entity;
import static org.apache.pekko.http.javadsl.server.Directives.withSizeLimit;

// #withSizeLimitExample
// #withSizeLimitExampleNested
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.entity;
import static org.apache.pekko.http.javadsl.server.Directives.withSizeLimit;

// #withSizeLimitExampleNested
// #withoutSizeLimitExample
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.entity;
import static org.apache.pekko.http.javadsl.server.Directives.withoutSizeLimit;

// #withoutSizeLimitExample
// #extractClientIP
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.extractClientIP;

// #extractClientIP
// #requestEntity-empty-present-example
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.requestEntityEmpty;
import static org.apache.pekko.http.javadsl.server.Directives.requestEntityPresent;

// #requestEntity-empty-present-example
// #selectPreferredLanguage
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.selectPreferredLanguage;

// #selectPreferredLanguage
// #validate-example
import static org.apache.pekko.http.javadsl.server.Directives.extractUri;
import static org.apache.pekko.http.javadsl.server.Directives.validate;

// #validate-example
// #rejectEmptyResponse-example
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.path;
import static org.apache.pekko.http.javadsl.server.Directives.rejectEmptyResponse;

// #rejectEmptyResponse-example

public class MiscDirectivesExamplesTest extends JUnitRouteTest {

  @Test
  public void testWithSizeLimit() {
    // #withSizeLimitExample
    final Route route =
        withSizeLimit(500, () -> entity(Unmarshaller.entityToString(), (entity) -> complete("ok")));

    Function<Integer, HttpRequest> withEntityOfSize =
        (sizeLimit) -> {
          char[] charArray = new char[sizeLimit];
          Arrays.fill(charArray, '0');
          return HttpRequest.POST("/").withEntity(new String(charArray));
        };

    // tests:
    testRoute(route).run(withEntityOfSize.apply(500)).assertStatusCode(StatusCodes.OK);

    testRoute(route)
        .run(withEntityOfSize.apply(501))
        .assertStatusCode(StatusCodes.CONTENT_TOO_LARGE);
    // #withSizeLimitExample
  }

  @Test
  public void testWithSizeLimitNested() {
    // #withSizeLimitExampleNested
    final Route route =
        withSizeLimit(
            500,
            () ->
                withSizeLimit(
                    800, () -> entity(Unmarshaller.entityToString(), (entity) -> complete("ok"))));

    Function<Integer, HttpRequest> withEntityOfSize =
        (sizeLimit) -> {
          char[] charArray = new char[sizeLimit];
          Arrays.fill(charArray, '0');
          return HttpRequest.POST("/").withEntity(new String(charArray));
        };

    // tests:
    testRoute(route).run(withEntityOfSize.apply(800)).assertStatusCode(StatusCodes.OK);

    testRoute(route)
        .run(withEntityOfSize.apply(801))
        .assertStatusCode(StatusCodes.CONTENT_TOO_LARGE);
    // #withSizeLimitExampleNested
  }

  @Test
  public void testWithoutSizeLimit() {
    // #withoutSizeLimitExample
    final Route route =
        withoutSizeLimit(() -> entity(Unmarshaller.entityToString(), (entity) -> complete("ok")));

    Function<Integer, HttpRequest> withEntityOfSize =
        (sizeLimit) -> {
          char[] charArray = new char[sizeLimit];
          Arrays.fill(charArray, '0');
          return HttpRequest.POST("/").withEntity(new String(charArray));
        };

    // tests:
    // will work even if you have configured pekko.http.parsing.max-content-length = 500
    testRoute(route).run(withEntityOfSize.apply(501)).assertStatusCode(StatusCodes.OK);
    // #withoutSizeLimitExample
  }

  @Test
  public void testExtractClientIP() throws UnknownHostException {
    // #extractClientIPExample
    final Route route =
        extractClientIP(
            remoteAddr ->
                complete(
                    "Client's IP is "
                        + remoteAddr
                            .getAddress()
                            .map(InetAddress::getHostAddress)
                            .orElseGet(() -> "unknown")));

    // tests:
    final String ip = "192.168.1.2";
    final org.apache.pekko.http.javadsl.model.RemoteAddress remoteAddress =
        org.apache.pekko.http.javadsl.model.RemoteAddress.create(InetAddress.getByName(ip));

    final HttpRequest request =
        HttpRequest.GET("/").addHeader(XForwardedFor.create(remoteAddress)); //

    testRoute(route).run(request).assertEntity("Client's IP is " + ip);

    testRoute(route).run(HttpRequest.GET("/")).assertEntity("Client's IP is unknown");
    // #extractClientIPExample
  }

  @Test
  public void testRequestEntityEmpty() {
    // #requestEntity-empty-present-example
    final Route route =
        requestEntityEmpty(() -> complete("request entity empty"))
            .orElse(requestEntityPresent(() -> complete("request entity present")));

    // tests:
    testRoute(route).run(HttpRequest.POST("/")).assertEntity("request entity empty");
    testRoute(route)
        .run(HttpRequest.POST("/").withEntity("foo"))
        .assertEntity("request entity present");
    // #requestEntity-empty-present-example
  }

  @Test
  public void testSelectPreferredLanguage() {
    // #selectPreferredLanguage
    final Route enRoute =
        selectPreferredLanguage(
            Arrays.asList(Language.create("en"), Language.create("en-US")),
            lang -> complete(lang.toString()));
    final Route deHuRoute =
        selectPreferredLanguage(
            Arrays.asList(Language.create("de-DE"), Language.create("hu")),
            lang -> complete(lang.toString()));

    // tests:
    final HttpRequest request =
        HttpRequest.GET("/")
            .addHeader(
                AcceptLanguage.create(
                    Language.create("en-US").withQValue(1f),
                    Language.create("en").withQValue(0.7f),
                    LanguageRanges.ALL.withQValue(0.1f),
                    Language.create("de-DE").withQValue(0.5f)));

    testRoute(enRoute).run(request).assertEntity("en-US");
    testRoute(deHuRoute).run(request).assertEntity("de-DE");
    // #selectPreferredLanguage
  }

  @Test
  public void testValidate() {
    // #validate-example
    final Route route =
        extractUri(
            uri ->
                validate(
                    () -> uri.path().length() < 5,
                    "Path too long: " + uri.path(),
                    () -> complete("Full URI: " + uri.toString())));

    // tests:
    testRoute(route).run(HttpRequest.GET("/234")).assertEntity("Full URI: http://example.com/234");
    testRoute(route)
        .run(HttpRequest.GET("/abcdefghijkl"))
        .assertEntity("Path too long: /abcdefghijkl");
    // #validate-example
  }

  @Test
  public void testRejectEmptyResponse() {
    // #rejectEmptyResponse-example
    final Route route =
        rejectEmptyResponse(
            () ->
                path(
                    PathMatchers.segment("even").slash(PathMatchers.integerSegment()),
                    (value) -> {
                      String response = "";
                      if (value % 2 == 0) {
                        response = "Number " + value + " is even";
                      }
                      return complete(response);
                    }));

    // tests:
    testRoute(route).run(HttpRequest.GET("/even/24")).assertEntity("Number 24 is even");
    testRoute(route).run(HttpRequest.GET("/even/23")).assertStatusCode(StatusCodes.NOT_FOUND);

    // #rejectEmptyResponse-example
  }
}
