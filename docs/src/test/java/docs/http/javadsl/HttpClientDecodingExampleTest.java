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

package docs.http.javadsl;

// #single-request-decoding-example
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.coding.Coder;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.scaladsl.model.headers.HttpEncodings;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;

public class HttpClientDecodingExampleTest {

  public static void main(String[] args) throws Exception {

    final ActorSystem system = ActorSystem.create();

    final List<HttpRequest> httpRequests =
        Arrays.asList(
            HttpRequest.create("https://httpbin.org/gzip"), // Content-Encoding: gzip in response
            HttpRequest.create(
                "https://httpbin.org/deflate"), // Content-Encoding: deflate in response
            HttpRequest.create("https://httpbin.org/get") // no Content-Encoding in response
            );

    final Http http = Http.get(system);

    final Function<HttpResponse, HttpResponse> decodeResponse =
        response -> {
          // Pick the right coder
          final Coder coder;
          if (HttpEncodings.gzip().equals(response.encoding())) {
            coder = Coder.Gzip;
          } else if (HttpEncodings.deflate().equals(response.encoding())) {
            coder = Coder.Deflate;
          } else {
            coder = Coder.NoCoding;
          }

          // Decode the entity
          return coder.decodeMessage(response);
        };

    List<CompletableFuture<HttpResponse>> futureResponses =
        httpRequests.stream()
            .map(req -> http.singleRequest(req).thenApply(decodeResponse))
            .map(CompletionStage::toCompletableFuture)
            .collect(Collectors.toList());

    for (CompletableFuture<HttpResponse> futureResponse : futureResponses) {
      final HttpResponse httpResponse = futureResponse.get();
      system
          .log()
          .info(
              "response is: "
                  + httpResponse.entity().toStrict(1000, system).toCompletableFuture().get());
    }

    system.terminate();
  }
}
// #single-request-decoding-example
