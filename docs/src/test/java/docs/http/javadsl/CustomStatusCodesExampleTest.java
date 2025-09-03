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
import org.apache.pekko.http.javadsl.ConnectionContext;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.StatusCode;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.settings.ClientConnectionSettings;
import org.apache.pekko.http.javadsl.settings.ConnectionPoolSettings;
import org.apache.pekko.http.javadsl.settings.ParserSettings;
import org.apache.pekko.http.javadsl.settings.ServerSettings;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.apache.pekko.stream.Materializer;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;

// #application-custom-java
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.extractRequest;

// #application-custom-java

public class CustomStatusCodesExampleTest extends JUnitRouteTest {

  @Test
  public void customStatusCodes()
      throws ExecutionException, InterruptedException, NoSuchAlgorithmException {

    final ActorSystem system = system();
    final String host = "127.0.0.1";

    // #application-custom-java
    // Define custom status code:
    final StatusCode leetCode =
        StatusCodes.custom(
            777, // Our custom status code
            "LeetCode", // Our custom reason
            "Some reason", // Our custom default message
            true, // It should be considered a success response
            false); // Does not allow entities

    // Add custom method to parser settings:
    final ParserSettings parserSettings =
        ParserSettings.forServer(system).withCustomStatusCodes(leetCode);
    final ServerSettings serverSettings =
        ServerSettings.create(system).withParserSettings(parserSettings);

    final ClientConnectionSettings clientConSettings =
        ClientConnectionSettings.create(system).withParserSettings(parserSettings);
    final ConnectionPoolSettings clientSettings =
        ConnectionPoolSettings.create(system).withConnectionSettings(clientConSettings);

    final Route route = extractRequest(req -> complete(HttpResponse.create().withStatus(leetCode)));

    // Use serverSettings in server:
    final CompletionStage<ServerBinding> binding =
        Http.get(system).newServerAt(host, 0).withSettings(serverSettings).bind(route);

    final ServerBinding serverBinding = binding.toCompletableFuture().get();

    final int port = serverBinding.localAddress().getPort();

    // Use clientSettings in client:
    final HttpResponse response =
        Http.get(system)
            .singleRequest(
                HttpRequest.GET("http://" + host + ":" + port + "/"),
                ConnectionContext.httpsClient(SSLContext.getDefault()),
                clientSettings,
                system.log())
            .toCompletableFuture()
            .get();

    // Check we get the right code back
    assertEquals(leetCode, response.status());
    // #application-custom-java
  }
}
