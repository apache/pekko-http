/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.http.javadsl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.HttpsConnectionContext;
// #trailingHeaders
import org.apache.pekko.http.javadsl.model.Trailer;
import org.apache.pekko.http.javadsl.model.headers.RawHeader;
import static org.apache.pekko.http.javadsl.model.AttributeKeys.trailer;

// #trailingHeaders
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.japi.function.Function;

// #bindAndHandleSecure
// #bindAndHandlePlain
// #http2Client
// #http2ClientWithPriorKnowledge
import org.apache.pekko.http.javadsl.Http;

// #http2ClientWithPriorKnowledge
// #http2Client
// #bindAndHandlePlain
// #bindAndHandleSecure

class Http2Test {
  void testBindAndHandleAsync() {
    Function<HttpRequest, CompletionStage<HttpResponse>> asyncHandler =
        r -> CompletableFuture.completedFuture(HttpResponse.create());
    ActorSystem system = ActorSystem.create();
    HttpsConnectionContext httpsConnectionContext = null;

    // #bindAndHandleSecure
    Http.get(system)
        .newServerAt("127.0.0.1", 8443)
        .enableHttps(httpsConnectionContext)
        .bind(asyncHandler);
    // #bindAndHandleSecure

    // #bindAndHandlePlain
    Http.get(system).newServerAt("127.0.0.1", 8443).bind(asyncHandler);
    // #bindAndHandlePlain

    // #http2Client
    Http.get(system).connectionTo("127.0.0.1").toPort(8443).http2();
    // #http2Client

    // #http2ClientWithPriorKnowledge
    Http.get(system).connectionTo("127.0.0.1").toPort(8080).http2WithPriorKnowledge();
    // #http2ClientWithPriorKnowledge

    // #trailingHeaders
    HttpResponse.create()
        .withStatus(200)
        .addAttribute(trailer, Trailer.create().addHeader(RawHeader.create("name", "value")));
    // #trailingHeaders
  }
}
