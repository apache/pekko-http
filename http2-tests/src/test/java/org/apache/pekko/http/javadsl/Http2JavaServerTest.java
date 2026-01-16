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

package org.apache.pekko.http.javadsl;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.impl.util.ExampleHttpContexts;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.japi.function.Function;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class Http2JavaServerTest {
  public static void main(String[] args) {
    Config testConf =
        ConfigFactory.parseString(
            "pekko.loglevel = INFO\n"
                + "pekko.log-dead-letters = off\n"
                + "pekko.stream.materializer.debug.fuzzing-mode = off\n"
                + "pekko.actor.serialize-creators = off\n"
                + "pekko.actor.serialize-messages = off\n"
                + "#pekko.actor.default-dispatcher.throughput = 1000\n"
                + "pekko.actor.default-dispatcher.fork-join-executor.parallelism-max=8\n");
    ActorSystem system = ActorSystem.create("ServerTest", testConf);

    Function<HttpRequest, CompletionStage<HttpResponse>> handler =
        request ->
            CompletableFuture.completedFuture(HttpResponse.create().withEntity(request.entity()));

    HttpsConnectionContext httpsConnectionContext = ExampleHttpContexts.getExampleServerContext();

    Http.get(system)
        .newServerAt("localhost", 9001)
        .enableHttps(httpsConnectionContext)
        .bind(handler);

    // TODO what about unencrypted http2?
  }
}
