/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.http.javadsl;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.model.AttributeKey;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.ResponseFuture;
import org.apache.pekko.http.javadsl.model.headers.AcceptEncoding;
import org.apache.pekko.http.javadsl.model.headers.HttpEncodings;
import org.apache.pekko.stream.BoundedSourceQueue;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.OverflowStrategy;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.javadsl.SourceQueueWithComplete;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * A small example app that shows how to use the HTTP/2 client API currently against actual internet
 * servers Mirroring the scaladsl counterpart
 */
public class Http2ClientApp {

  public static void main(String[] args) {
    Config config =
        ConfigFactory.parseString(
                "#pekko.loglevel = debug\n"
                    + "pekko.http.client.http2.log-frames = true\n"
                    + "pekko.http.client.parsing.max-content-length = 20m")
            .withFallback(ConfigFactory.load());

    ActorSystem system = ActorSystem.create("Http2ClientApp", config);
    Materializer mat = SystemMaterializer.get(system).materializer();

    // #response-future-association
    Function<HttpRequest, CompletionStage<HttpResponse>> dispatch =
        singleRequest(system, Http.get(system).connectionTo("pekko.apache.org").http2());

    dispatch
        .apply(
            HttpRequest.create(
                    "https://pekko.apache.org/api/pekko/current/org/apache/pekko/actor/typed/scaladsl/index.html")
                .withHeaders(Arrays.asList(AcceptEncoding.create(HttpEncodings.GZIP))))
        .thenAccept(
            res -> {
              System.out.println("[1] Got index.html: " + res);
              res.entity()
                  .getDataBytes()
                  .runWith(Sink.ignore(), mat)
                  .thenAccept(
                      consumedRes -> System.out.println("Finished reading [1] " + consumedRes));
            });

    // #response-future-association
    dispatch
        .apply(HttpRequest.create("https://pekko.apache.org/api/pekko/current/index.js"))
        .thenAccept(
            res -> {
              System.out.println("[2] Got index.js: " + res);
              res.entity()
                  .getDataBytes()
                  .runWith(Sink.ignore(), mat)
                  .thenAccept(consumedRes -> System.out.println("Finished reading [2] " + res));
            });
    dispatch
        .apply(
            HttpRequest.create(
                "https://pekko.apache.org/api/pekko/current/lib/MaterialIcons-Regular.woff"))
        .thenCompose(res -> res.toStrict(1000, system))
        .thenAccept(res -> System.out.println("[3] Got font: " + res));
    dispatch
        .apply(HttpRequest.create("https://pekko.apache.org/favicon.png"))
        .thenCompose(res -> res.toStrict(1000, system))
        .thenAccept(res -> System.out.println("[4] Got favicon: " + res));
  }

  // #response-future-association
  private static Function<HttpRequest, CompletionStage<HttpResponse>> singleRequest(
      ActorSystem system, Flow<HttpRequest, HttpResponse, ?> connection) {
    BoundedSourceQueue<HttpRequest> queue =
        Source.<HttpRequest>queue(100)
            .via(connection)
            .to(
                Sink.foreach(
                    res -> {
                      try {
                        // complete the future with the response when it arrives
                        ResponseFuture responseFuture =
                            res.getAttribute(ResponseFuture.KEY()).get();
                        responseFuture.future().complete(res);
                      } catch (Exception ex) {
                        ex.printStackTrace();
                      }
                    }))
            .run(SystemMaterializer.get(system).materializer());

    return (HttpRequest req) -> {
      // create a future of the response for each request and set it as an attribute on the request
      CompletableFuture<HttpResponse> future = new CompletableFuture<>();
      ResponseFuture attribute = new ResponseFuture(future);
      queue.offer(req.addAttribute(ResponseFuture.KEY(), attribute));
      // return the future response
      return attribute.future();
    };
  }
  // #response-future-association
}
