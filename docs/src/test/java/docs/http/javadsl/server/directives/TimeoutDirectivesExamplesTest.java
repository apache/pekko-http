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

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.StatusCode;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.testkit.TestKit;
import org.apache.pekko.util.JavaDurationConverters;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

public class TimeoutDirectivesExamplesTest extends AllDirectives {
  // #testSetup
  private final Config testConf =
      ConfigFactory.parseString(
          "pekko.loggers = [\"org.apache.pekko.testkit.TestEventListener\"]\n"
              + "pekko.loglevel = ERROR\n"
              + "pekko.stdout-loglevel = ERROR\n"
              + "windows-connection-abort-workaround-enabled = auto\n"
              + "pekko.log-dead-letters = OFF\n"
              + "pekko.http.server.request-timeout = 1000s");
  // large timeout - 1000s (please note - setting to infinite will disable Timeout-Access header
  // and withRequestTimeout will not work)

  private final ActorSystem system = ActorSystem.create("TimeoutDirectivesExamplesTest", testConf);

  private final Http http = Http.get(system);

  private void shutdown(ServerBinding b) throws Exception {
    System.out.println(String.format("Unbinding from %s", b.localAddress()));

    b.unbind().toCompletableFuture().get(3, TimeUnit.SECONDS);
  }

  private Optional<HttpResponse> runRoute(Route route, String routePath) throws Exception {
    final ServerBinding binding =
        http.newServerAt("localhost", 0).bind(route).toCompletableFuture().get();

    final CompletionStage<HttpResponse> responseCompletionStage =
        http.singleRequest(
            HttpRequest.create(
                "http://localhost:" + binding.localAddress().getPort() + "/" + routePath));

    CompletableFuture<HttpResponse> responseFuture = responseCompletionStage.toCompletableFuture();

    Optional<HttpResponse> responseOptional;
    try {
      responseOptional = Optional.of(responseFuture.get(3, TimeUnit.SECONDS)); // patienceConfig
    } catch (Exception e) {
      responseOptional = Optional.empty();
    }

    shutdown(binding);

    return responseOptional;
  }
  // #testSetup

  @After
  public void shutDown() {
    TestKit.shutdownActorSystem(
        system, scala.concurrent.duration.Duration.create(1, TimeUnit.SECONDS), false);
  }

  @Test
  public void testRequestTimeoutIsConfigurable() throws Exception {
    // #withRequestTimeout-plain
    final Duration timeout = Duration.ofSeconds(1);
    CompletionStage<String> slowFuture = new CompletableFuture<>();

    final Route route =
        path(
            "timeout",
            () ->
                withRequestTimeout(
                    timeout,
                    () -> {
                      return completeOKWithFutureString(slowFuture); // very slow
                    }));

    // test:
    StatusCode statusCode = runRoute(route, "timeout").get().status();
    assert (StatusCodes.SERVICE_UNAVAILABLE.equals(statusCode));
    // #withRequestTimeout-plain
  }

  @Test
  public void testRequestWithoutTimeoutCancelsTimeout() throws Exception {
    // #withoutRequestTimeout-1
    CompletionStage<String> slowFuture = new CompletableFuture<>();

    final Route route =
        path(
            "timeout",
            () ->
                withoutRequestTimeout(
                    () -> {
                      return completeOKWithFutureString(slowFuture); // very slow
                    }));

    // test:
    Boolean receivedReply = runRoute(route, "timeout").isPresent();
    assert (!receivedReply); // timed-out
    // #withoutRequestTimeout-1
  }

  @Test
  public void testRequestTimeoutAllowsCustomResponse() throws Exception {
    // #withRequestTimeout-with-handler
    final Duration timeout = Duration.ofMillis(1);
    CompletionStage<String> slowFuture = new CompletableFuture<>();

    HttpResponse enhanceYourCalmResponse =
        HttpResponse.create()
            .withStatus(StatusCodes.ENHANCE_YOUR_CALM)
            .withEntity("Unable to serve response within time limit, please enhance your calm.");

    final Route route =
        path(
            "timeout",
            () ->
                withRequestTimeout(
                    timeout,
                    (request) -> enhanceYourCalmResponse,
                    () -> {
                      return completeOKWithFutureString(slowFuture); // very slow
                    }));

    // test:
    StatusCode statusCode = runRoute(route, "timeout").get().status();
    assert (StatusCodes.ENHANCE_YOUR_CALM.equals(statusCode));
    // #withRequestTimeout-with-handler
  }

  // make it compile only to avoid flaking in slow builds
  @Ignore("Compile only test")
  @Test
  public void testRequestTimeoutCustomResponseCanBeAddedSeparately() throws Exception {
    // #withRequestTimeoutResponse
    final Duration timeout = Duration.ofMillis(100);
    CompletionStage<String> slowFuture = new CompletableFuture<>();

    HttpResponse enhanceYourCalmResponse =
        HttpResponse.create()
            .withStatus(StatusCodes.ENHANCE_YOUR_CALM)
            .withEntity("Unable to serve response within time limit, please enhance your calm.");

    final Route route =
        path(
            "timeout",
            () ->
                withRequestTimeout(
                    timeout,
                    () ->
                        // racy! for a very short timeout like 1.milli you can still get 503
                        withRequestTimeoutResponse(
                            (request) -> enhanceYourCalmResponse,
                            () -> {
                              return completeOKWithFutureString(slowFuture); // very slow
                            })));

    // test:
    StatusCode statusCode = runRoute(route, "timeout").get().status();
    assert (StatusCodes.ENHANCE_YOUR_CALM.equals(statusCode));
    // #withRequestTimeoutResponse
  }

  @Test
  public void extractRequestTimeout() throws Exception {
    // #extractRequestTimeout
    Duration timeout1 = Duration.ofMillis(500);
    Duration timeout2 = Duration.ofMillis(1000);
    Route route =
        path(
            "timeout",
            () ->
                withRequestTimeout(
                    timeout1,
                    () ->
                        extractRequestTimeout(
                            t1 ->
                                withRequestTimeout(
                                    timeout2,
                                    () ->
                                        extractRequestTimeout(
                                            t2 -> {
                                              if (t1.equals(
                                                      JavaDurationConverters.asFiniteDuration(
                                                          timeout1))
                                                  && t2.equals(
                                                      JavaDurationConverters.asFiniteDuration(
                                                          timeout2)))
                                                return complete(StatusCodes.OK);
                                              else
                                                return complete(StatusCodes.INTERNAL_SERVER_ERROR);
                                            })))));
    // #extractRequestTimeout
    StatusCode statusCode = runRoute(route, "timeout").get().status();
    assert (StatusCodes.OK.equals(statusCode));
  }
}
