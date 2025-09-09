/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.http.javadsl.server.directives;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.event.LoggingAdapter;
import org.apache.pekko.event.NoLogging;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.model.*;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.settings.ParserSettings;
import org.apache.pekko.http.javadsl.settings.ServerSettings;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.apache.pekko.stream.javadsl.Flow;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.apache.pekko.http.javadsl.model.HttpProtocols.HTTP_1_1;
import static org.apache.pekko.http.javadsl.model.RequestEntityAcceptances.Expected;

// #customHttpMethod
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.extractMethod;

// #customHttpMethod
public class CustomHttpMethodExamplesTest extends JUnitRouteTest {

  @Test
  public void testComposition() throws InterruptedException, ExecutionException, TimeoutException {
    ActorSystem system = system();
    LoggingAdapter loggingAdapter = NoLogging.getInstance();

    int port = 9090;
    String host = "127.0.0.1";

    // #customHttpMethod

    // define custom method type:
    HttpMethod BOLT = HttpMethods.custom("BOLT", false, true, Expected);

    // add custom method to parser settings:
    final ParserSettings parserSettings = ParserSettings.forServer(system).withCustomMethods(BOLT);
    final ServerSettings serverSettings =
        ServerSettings.create(system).withParserSettings(parserSettings);

    final Route routes =
        concat(extractMethod(method -> complete("This is a " + method.name() + " request.")));
    final Http http = Http.get(system);
    final CompletionStage<ServerBinding> binding =
        http.newServerAt(host, port)
            .withSettings(serverSettings)
            .logTo(loggingAdapter)
            .bind(routes);

    HttpRequest request =
        HttpRequest.create()
            .withUri("http://" + host + ":" + Integer.toString(port))
            .withMethod(BOLT)
            .withProtocol(HTTP_1_1);

    CompletionStage<HttpResponse> response = http.singleRequest(request);
    // #customHttpMethod

    assertEquals(StatusCodes.OK, response.toCompletableFuture().get(3, TimeUnit.SECONDS).status());
    assertEquals(
        "This is a BOLT request.",
        response
            .toCompletableFuture()
            .get()
            .entity()
            .toStrict(3000, system)
            .toCompletableFuture()
            .get()
            .getData()
            .utf8String());
  }
}
