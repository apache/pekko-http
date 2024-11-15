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

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.ConnectHttp;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.model.HttpCharsets;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.MediaType;
import org.apache.pekko.http.javadsl.model.MediaTypes;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.settings.ParserSettings;
import org.apache.pekko.http.javadsl.settings.ServerSettings;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.junit.Test;

import java.util.HashMap;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.apache.pekko.util.ByteString.emptyByteString;

// #application-custom-java
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.extractRequest;

// #application-custom-java

public class CustomMediaTypesExampleTest extends JUnitRouteTest {

  @Test
  public void customMediaTypes() throws ExecutionException, InterruptedException {

    final ActorSystem system = system();
    final String host = "127.0.0.1";

    // #application-custom-java
    // Define custom media type:
    final MediaType.WithFixedCharset applicationCustom =
        MediaTypes.customWithFixedCharset(
            "application",
            "custom", // The new Media Type name
            HttpCharsets.UTF_8, // The charset used
            new HashMap<>(), // Empty parameters
            false); // No arbitrary subtypes are allowed

    // Add custom media type to parser settings:
    final ParserSettings parserSettings =
        ParserSettings.forServer(system).withCustomMediaTypes(applicationCustom);
    final ServerSettings serverSettings =
        ServerSettings.create(system).withParserSettings(parserSettings);

    final Route route =
        extractRequest(
            req ->
                complete(
                    req.entity().getContentType().value()
                        + " = "
                        + req.entity().getContentType().getClass()));

    final CompletionStage<ServerBinding> binding =
        Http.get(system).newServerAt(host, 0).withSettings(serverSettings).bind(route);

    // #application-custom-java
    final ServerBinding serverBinding = binding.toCompletableFuture().get();

    final int port = serverBinding.localAddress().getPort();

    final HttpResponse response =
        Http.get(system)
            .singleRequest(
                HttpRequest.GET("http://" + host + ":" + port + "/")
                    .withEntity(applicationCustom.toContentType(), "~~example~=~value~~"))
            .toCompletableFuture()
            .get();

    assertEquals(StatusCodes.OK, response.status());
    final String body =
        response
            .entity()
            .toStrict(1000, system)
            .toCompletableFuture()
            .get()
            .getDataBytes()
            .runFold(emptyByteString(), (a, b) -> a.$plus$plus(b), system)
            .toCompletableFuture()
            .get()
            .utf8String();
    assertEquals(
        "application/custom = class org.apache.pekko.http.scaladsl.model.ContentType$WithFixedCharset",
        body); // it's the Scala DSL package because it's the only instance of the Java DSL
  }
}
