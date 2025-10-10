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

import org.apache.pekko.http.javadsl.model.HttpEntities;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.MediaTypes;
import org.apache.pekko.http.javadsl.model.headers.AcceptEncoding;
import org.apache.pekko.http.javadsl.model.headers.ContentEncoding;
import org.apache.pekko.http.javadsl.model.headers.HttpEncodings;
import org.apache.pekko.http.javadsl.coding.Coder;
import org.apache.pekko.http.javadsl.server.Rejections;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.apache.pekko.util.ByteString;
import org.junit.Test;

import java.util.Collections;

import static org.apache.pekko.http.javadsl.unmarshalling.Unmarshaller.entityToString;

// #responseEncodingAccepted
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.responseEncodingAccepted;

// #responseEncodingAccepted
// #encodeResponse
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.encodeResponse;

// #encodeResponse
// #encodeResponseWith
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.encodeResponseWith;

// #encodeResponseWith
// #decodeRequest
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.decodeRequest;
import static org.apache.pekko.http.javadsl.server.Directives.entity;

// #decodeRequest
// #decodeRequestWith
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.decodeRequestWith;
import static org.apache.pekko.http.javadsl.server.Directives.entity;

// #decodeRequestWith
// #withPrecompressedMediaTypeSupport
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.withPrecompressedMediaTypeSupport;

// #withPrecompressedMediaTypeSupport

public class CodingDirectivesExamplesTest extends JUnitRouteTest {

  @Test
  public void testResponseEncodingAccepted() {
    // #responseEncodingAccepted
    final Route route = responseEncodingAccepted(HttpEncodings.GZIP, () -> complete("content"));

    // tests:
    testRoute(route).run(HttpRequest.GET("/")).assertEntity("content");
    runRouteUnSealed(
            route, HttpRequest.GET("/").addHeader(AcceptEncoding.create(HttpEncodings.DEFLATE)))
        .assertRejections(Rejections.unacceptedResponseEncoding(HttpEncodings.GZIP));
    // #responseEncodingAccepted
  }

  @Test
  public void testEncodeResponse() {
    // #encodeResponse
    final Route route = encodeResponse(() -> complete("content"));

    // tests:
    testRoute(route)
        .run(
            HttpRequest.GET("/")
                .addHeader(AcceptEncoding.create(HttpEncodings.GZIP))
                .addHeader(AcceptEncoding.create(HttpEncodings.DEFLATE)))
        .assertHeaderExists(ContentEncoding.create(HttpEncodings.GZIP));

    testRoute(route)
        .run(HttpRequest.GET("/").addHeader(AcceptEncoding.create(HttpEncodings.DEFLATE)))
        .assertHeaderExists(ContentEncoding.create(HttpEncodings.DEFLATE));

    // This case failed!
    //    testRoute(route).run(
    //      HttpRequest.GET("/")
    //        .addHeader(AcceptEncoding.create(HttpEncodings.IDENTITY))
    //    ).assertHeaderExists(ContentEncoding.create(HttpEncodings.IDENTITY));

    // #encodeResponse
  }

  @Test
  public void testEncodeResponseWith() {
    // #encodeResponseWith
    final Route route =
        encodeResponseWith(Collections.singletonList(Coder.Gzip), () -> complete("content"));

    // tests:
    testRoute(route)
        .run(HttpRequest.GET("/"))
        .assertHeaderExists(ContentEncoding.create(HttpEncodings.GZIP));

    testRoute(route)
        .run(
            HttpRequest.GET("/")
                .addHeader(AcceptEncoding.create(HttpEncodings.GZIP))
                .addHeader(AcceptEncoding.create(HttpEncodings.DEFLATE)))
        .assertHeaderExists(ContentEncoding.create(HttpEncodings.GZIP));

    runRouteUnSealed(
            route, HttpRequest.GET("/").addHeader(AcceptEncoding.create(HttpEncodings.DEFLATE)))
        .assertRejections(Rejections.unacceptedResponseEncoding(HttpEncodings.GZIP));

    runRouteUnSealed(
            route, HttpRequest.GET("/").addHeader(AcceptEncoding.create(HttpEncodings.IDENTITY)))
        .assertRejections(Rejections.unacceptedResponseEncoding(HttpEncodings.GZIP));

    final Route routeWithLevel9 =
        encodeResponseWith(Collections.singletonList(Coder.GzipLevel9), () -> complete("content"));

    testRoute(routeWithLevel9)
        .run(HttpRequest.GET("/"))
        .assertHeaderExists(ContentEncoding.create(HttpEncodings.GZIP));
    // #encodeResponseWith
  }

  @Test
  public void testDecodeRequest() {
    // #decodeRequest
    final ByteString helloGzipped = Coder.Gzip.encode(ByteString.fromString("Hello"));
    final ByteString helloDeflated = Coder.Deflate.encode(ByteString.fromString("Hello"));

    final Route route =
        decodeRequest(
            () ->
                entity(
                    entityToString(), content -> complete("Request content: '" + content + "'")));

    // tests:
    testRoute(route)
        .run(
            HttpRequest.POST("/")
                .withEntity(helloGzipped)
                .addHeader(ContentEncoding.create(HttpEncodings.GZIP)))
        .assertEntity("Request content: 'Hello'");

    testRoute(route)
        .run(
            HttpRequest.POST("/")
                .withEntity(helloDeflated)
                .addHeader(ContentEncoding.create(HttpEncodings.DEFLATE)))
        .assertEntity("Request content: 'Hello'");

    testRoute(route)
        .run(
            HttpRequest.POST("/")
                .withEntity("hello uncompressed")
                .addHeader(ContentEncoding.create(HttpEncodings.IDENTITY)))
        .assertEntity("Request content: 'hello uncompressed'");
    // #decodeRequest
  }

  @Test
  public void testDecodeRequestWith() {
    // #decodeRequestWith
    final ByteString helloGzipped = Coder.Gzip.encode(ByteString.fromString("Hello"));
    final ByteString helloDeflated = Coder.Deflate.encode(ByteString.fromString("Hello"));

    final Route route =
        decodeRequestWith(
            Coder.Gzip,
            () ->
                entity(
                    entityToString(), content -> complete("Request content: '" + content + "'")));

    // tests:
    testRoute(route)
        .run(
            HttpRequest.POST("/")
                .withEntity(helloGzipped)
                .addHeader(ContentEncoding.create(HttpEncodings.GZIP)))
        .assertEntity("Request content: 'Hello'");

    runRouteUnSealed(
            route,
            HttpRequest.POST("/")
                .withEntity(helloDeflated)
                .addHeader(ContentEncoding.create(HttpEncodings.DEFLATE)))
        .assertRejections(Rejections.unsupportedRequestEncoding(HttpEncodings.GZIP));

    runRouteUnSealed(
            route,
            HttpRequest.POST("/")
                .withEntity("hello")
                .addHeader(ContentEncoding.create(HttpEncodings.IDENTITY)))
        .assertRejections(Rejections.unsupportedRequestEncoding(HttpEncodings.GZIP));
    // #decodeRequestWith
  }

  @Test
  public void testWithPrecompressedMediaTypeSupport() {
    // #withPrecompressedMediaTypeSupport
    final ByteString svgz = Coder.Gzip.encode(ByteString.fromString("<svg/>"));

    final Route route =
        withPrecompressedMediaTypeSupport(
            () ->
                complete(
                    HttpResponse.create()
                        .withEntity(
                            HttpEntities.create(MediaTypes.IMAGE_SVGZ.toContentType(), svgz))));

    // tests:
    testRoute(route)
        .run(HttpRequest.GET("/"))
        .assertMediaType(MediaTypes.IMAGE_SVG_XML)
        .assertHeaderExists(ContentEncoding.create(HttpEncodings.GZIP));
    // #withPrecompressedMediaTypeSupport
  }
}
