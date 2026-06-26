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

package org.apache.pekko.http.javadsl.server.directives;

import static org.apache.pekko.http.javadsl.server.Directives.*;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.pekko.NotUsed;
import org.apache.pekko.http.javadsl.coding.Coder;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.headers.AcceptEncoding;
import org.apache.pekko.http.javadsl.model.headers.ContentEncoding;
import org.apache.pekko.http.javadsl.model.headers.HttpEncodings;
import org.apache.pekko.http.javadsl.testkit.*;
import org.apache.pekko.stream.javadsl.Compression;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.util.ByteString;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CodingDirectivesTest extends JUnitJupiterRouteTest {

  @Test
  public void testAutomaticEncodingWhenNoEncodingRequested() throws Exception {
    TestRoute route = testRoute(encodeResponse(() -> complete("TestString")));

    TestRouteResult response = route.run(HttpRequest.create());
    response.assertStatusCode(200);

    Assertions.assertEquals("TestString", response.entityBytes().utf8String());
  }

  @Test
  public void testAutomaticEncodingWhenDeflateRequested() throws Exception {
    TestRoute route = testRoute(encodeResponse(() -> complete("tester")));

    HttpRequest request =
        HttpRequest.create().addHeader(AcceptEncoding.create(HttpEncodings.DEFLATE));
    TestRouteResult response = route.run(request);
    response
        .assertStatusCode(200)
        .assertHeaderExists(ContentEncoding.create(HttpEncodings.DEFLATE));

    ByteString decompressed =
        Coder.Deflate.decode(response.entityBytes(), materializer())
            .toCompletableFuture()
            .get(3, TimeUnit.SECONDS);
    Assertions.assertEquals("tester", decompressed.utf8String());
  }

  @Test
  public void testEncodingWhenDeflateRequestedAndGzipSupported() {
    TestRoute route = testRoute(encodeResponseWith(List.of(Coder.Gzip), () -> complete("tester")));

    HttpRequest request =
        HttpRequest.create().addHeader(AcceptEncoding.create(HttpEncodings.DEFLATE));
    route
        .run(request)
        .assertStatusCode(406)
        .assertEntity(
            "Resource representation is only available with these Content-Encodings:\ngzip");
  }

  @Test
  public void testAutomaticDecoding() throws Exception {
    TestRoute route = testRoute(decodeRequest(() -> extractEntity(entity -> complete(entity))));

    ByteString compressedDeflate = compress("abcdef", Compression.deflate());
    HttpRequest deflateRequest =
        HttpRequest.POST("/")
            .addHeader(ContentEncoding.create(HttpEncodings.DEFLATE))
            .withEntity(compressedDeflate);
    route.run(deflateRequest).assertStatusCode(200).assertEntity("abcdef");

    ByteString compressedGzip = compress("hijklmnopq", Compression.gzip());
    HttpRequest gzipRequest =
        HttpRequest.POST("/")
            .addHeader(ContentEncoding.create(HttpEncodings.GZIP))
            .withEntity(compressedGzip);
    route.run(gzipRequest).assertStatusCode(200).assertEntity("hijklmnopq");
  }

  @Test
  public void testGzipDecoding() throws Exception {
    TestRoute route =
        testRoute(
            decodeRequestWith(Set.of(Coder.Gzip), () -> extractEntity(entity -> complete(entity))));

    ByteString compressedGzip = compress("hijklmnopq", Compression.gzip());
    HttpRequest gzipRequest =
        HttpRequest.POST("/")
            .addHeader(ContentEncoding.create(HttpEncodings.GZIP))
            .withEntity(compressedGzip);
    route.run(gzipRequest).assertStatusCode(200).assertEntity("hijklmnopq");

    ByteString compressedDeflate = compress("abcdef", Compression.deflate());
    HttpRequest deflateRequest =
        HttpRequest.POST("/")
            .addHeader(ContentEncoding.create(HttpEncodings.DEFLATE))
            .withEntity(compressedDeflate);
    route
        .run(deflateRequest)
        .assertStatusCode(400)
        .assertEntity("The request's Content-Encoding is not supported. Expected:\ngzip");
  }

  private ByteString compress(String input, Flow<ByteString, ByteString, NotUsed> compressionFlow)
      throws Exception {
    return Source.single(ByteString.fromString(input))
        .via(compressionFlow)
        .runFold(ByteString.emptyByteString(), ByteString::concat, materializer())
        .toCompletableFuture()
        .get(); // Wait for result
  }
}
