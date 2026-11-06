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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.apache.pekko.NotUsed;
import org.apache.pekko.http.javadsl.coding.Coder;
import org.apache.pekko.http.javadsl.coding.DictionaryCoder;
import org.apache.pekko.http.javadsl.coding.FlowCoder;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.headers.AcceptEncoding;
import org.apache.pekko.http.javadsl.model.headers.AvailableDictionary;
import org.apache.pekko.http.javadsl.model.headers.ContentEncoding;
import org.apache.pekko.http.javadsl.model.headers.HttpEncodingRanges;
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
  public void testResponseEncodingAcceptedForImplicitIdentity() {
    TestRoute route =
        testRoute(responseEncodingAccepted(HttpEncodings.IDENTITY, () -> complete("content")));

    route
        .run(HttpRequest.GET("/").addHeader(AcceptEncoding.create(HttpEncodings.GZIP)))
        .assertStatusCode(200)
        .assertEntity("content");
    for (AcceptEncoding excluded :
        List.of(
            AcceptEncoding.create(
                HttpEncodings.IDENTITY.toRange().withQValue(0f), HttpEncodingRanges.ALL),
            AcceptEncoding.create(HttpEncodingRanges.ALL.withQValue(0f)))) {
      route.run(HttpRequest.GET("/").addHeader(excluded)).assertStatusCode(406);
    }
    route
        .run(
            HttpRequest.GET("/")
                .addHeader(
                    AcceptEncoding.create(
                        HttpEncodingRanges.ALL.withQValue(0f), HttpEncodings.IDENTITY.toRange())))
        .assertStatusCode(200)
        .assertEntity("content");
  }

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

  @Test
  public void testFlowCoderIsRouteLocalAndCreatesOneDecoderPerRun() throws Exception {
    AtomicInteger decoderAllocations = new AtomicInteger();
    FlowCoder coder =
        FlowCoder.create(
            "x-test-gzip",
            Compression.gzip(),
            chunkSize -> {
              decoderAllocations.incrementAndGet();
              return Compression.gzipDecompress(chunkSize);
            });
    TestRoute decoder =
        testRoute(
            decodeRequestWithFlowCoder(coder, () -> extractEntity(entity -> complete(entity))));
    ByteString compressed = compress("flow fixture", Compression.gzip());
    HttpRequest post =
        HttpRequest.POST("/")
            .addHeader(ContentEncoding.create(coder.encoding()))
            .withEntity(compressed);
    Assertions.assertEquals(0, decoderAllocations.get());
    decoder.run(post).assertStatusCode(200).assertEntity("flow fixture");
    decoder.run(post).assertStatusCode(200).assertEntity("flow fixture");
    Assertions.assertEquals(2, decoderAllocations.get());

    HttpRequest get = HttpRequest.create().addHeader(AcceptEncoding.create(coder.encoding()));
    TestRoute encoder =
        testRoute(encodeResponseWithFlowCoder(coder, () -> complete("flow fixture")));
    TestRouteResult encoded =
        encoder
            .run(get)
            .assertStatusCode(200)
            .assertHeaderExists(ContentEncoding.create(coder.encoding()));
    ByteString decoded =
        Coder.Gzip.decode(encoded.entityBytes(), materializer())
            .toCompletableFuture()
            .get(3, TimeUnit.SECONDS);
    Assertions.assertEquals("flow fixture", decoded.utf8String());
    testRoute(encodeResponse(() -> complete("flow fixture"))).run(get).assertEntity("flow fixture");
    Predicate<Object> skipEncoding = ignored -> false;
    FlowCoder filtered =
        FlowCoder.create(
            "x-test-gzip", Compression.gzip(), Compression::gzipDecompress, skipEncoding);
    testRoute(encodeResponseWithFlowCoder(filtered, () -> complete("flow fixture")))
        .run(get)
        .assertEntity("flow fixture");
  }

  private ByteString compress(String input, Flow<ByteString, ByteString, NotUsed> compressionFlow)
      throws Exception {
    return Source.single(ByteString.fromString(input))
        .via(compressionFlow)
        .runFold(ByteString.emptyByteString(), ByteString::concat, materializer())
        .toCompletableFuture()
        .get(); // Wait for result
  }

  @Test
  public void testDecodeResponseWithMatchingCoder() throws Exception {
    for (Coder coder : List.of(Coder.Zstd, Coder.Gzip, Coder.Deflate)) {
      HttpResponse encoded = coder.encodeMessage(HttpResponse.create().withEntity("tester"));
      Assertions.assertEquals(coder._underlyingScalaCoder().encoding(), encoded.encoding());

      HttpResponse decoded = Coder.decodeResponse(encoded, Coder.Zstd, Coder.Gzip, Coder.Deflate);
      Assertions.assertEquals(HttpEncodings.IDENTITY, decoded.encoding());
      Assertions.assertEquals(
          "tester",
          decoded
              .entity()
              .toStrict(3000, materializer())
              .toCompletableFuture()
              .get(3, TimeUnit.SECONDS)
              .getData()
              .utf8String());
    }
  }

  @Test
  public void testExplicitResponseDecoderKeepsInnerCoding() throws Exception {
    ByteString inner = compress("two-layer response", Compression.gzip());
    ByteString outer =
        Source.single(inner)
            .via(Compression.deflate())
            .runFold(ByteString.emptyByteString(), ByteString::concat, materializer())
            .toCompletableFuture()
            .get(3, TimeUnit.SECONDS);
    HttpResponse response =
        HttpResponse.create()
            .withEntity(outer)
            .withHeaders(
                List.of(
                    ContentEncoding.create(HttpEncodings.GZIP),
                    ContentEncoding.create(HttpEncodings.DEFLATE)));

    Assertions.assertSame(response, Coder.Gzip.decodeMessage(response));
    Assertions.assertSame(response, Coder.decodeResponse(response));

    HttpResponse partiallyDecoded = Coder.Deflate.decodeMessage(response);
    Assertions.assertEquals(
        ContentEncoding.create(HttpEncodings.GZIP),
        partiallyDecoded.getHeader(ContentEncoding.class).orElseThrow());

    HttpResponse decoded = Coder.Gzip.decodeMessage(partiallyDecoded);
    Assertions.assertTrue(decoded.getHeader(ContentEncoding.class).isEmpty());
    Assertions.assertEquals(
        "two-layer response",
        decoded
            .entity()
            .toStrict(3000, materializer())
            .toCompletableFuture()
            .get(3, TimeUnit.SECONDS)
            .getData()
            .utf8String());
  }

  @Test
  public void testDecodeResponseLeavesUnsupportedEncodingUnchanged() {
    HttpResponse encoded = Coder.Zstd.encodeMessage(HttpResponse.create().withEntity("tester"));
    // zstd is not one of the default coders
    HttpResponse decoded = Coder.decodeResponse(encoded);
    Assertions.assertEquals(HttpEncodings.ZSTD, decoded.encoding());
    Assertions.assertEquals(encoded, decoded);
  }

  @Test
  public void testDictionaryCoder() throws Exception {
    DictionaryCoder coder = DictionaryCoder.create(ByteString.fromString("a dictionary"));
    HttpResponse encoded = coder.encodeMessage(HttpResponse.create().withEntity("tester"));
    Assertions.assertEquals(HttpEncodings.DCZ, encoded.encoding());
    Assertions.assertEquals(32, coder.getDictionaryHash().length());

    HttpResponse decoded = coder.decodeResponse(encoded);
    Assertions.assertEquals(HttpEncodings.IDENTITY, decoded.encoding());
    Assertions.assertEquals(
        "tester",
        decoded
            .entity()
            .toStrict(1000, materializer())
            .toCompletableFuture()
            .get(3, TimeUnit.SECONDS)
            .getData()
            .utf8String());

    // a message with another encoding is not decoded
    HttpResponse gzipped = Coder.Gzip.encodeMessage(HttpResponse.create().withEntity("tester"));
    Assertions.assertEquals(HttpEncodings.GZIP, coder.decodeResponse(gzipped).encoding());
  }

  @Test
  public void testEncodeResponseWithDictionary() {
    DictionaryCoder coder = DictionaryCoder.create(ByteString.fromString("a dictionary"));
    TestRoute route =
        testRoute(
            encodeResponseWithDictionary(
                hash ->
                    hash.equals(coder.getDictionaryHash()) ? Optional.of(coder) : Optional.empty(),
                List.of(Coder.Gzip),
                () -> complete("tester")));

    route
        .run(
            HttpRequest.create()
                .addHeader(AcceptEncoding.create(HttpEncodings.DCZ))
                .addHeader(AvailableDictionary.create(coder.getDictionaryHash())))
        .assertHeaderExists(ContentEncoding.create(HttpEncodings.DCZ));

    route
        .run(HttpRequest.create().addHeader(AcceptEncoding.create(HttpEncodings.GZIP)))
        .assertHeaderExists(ContentEncoding.create(HttpEncodings.GZIP));
  }
}
