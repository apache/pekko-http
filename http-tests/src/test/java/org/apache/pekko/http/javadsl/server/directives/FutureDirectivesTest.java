/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.http.javadsl.server.directives;

import static org.apache.pekko.http.javadsl.server.Directives.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.testkit.JUnitJupiterRouteTest;
import org.apache.pekko.http.javadsl.testkit.TestRoute;
import org.junit.jupiter.api.Test;

public class FutureDirectivesTest extends JUnitJupiterRouteTest {

  private Route handle(String value, Throwable failure) {
    if (failure != null) {
      return complete(StatusCodes.INTERNAL_SERVER_ERROR, "failed: " + failure.getMessage());
    }
    return complete("value: " + value);
  }

  private static CompletionStage<String> failingStage() {
    // supplyAsync wraps the exception in a CompletionException, which must be unwrapped
    return CompletableFuture.supplyAsync(
        () -> {
          throw new IllegalStateException("boom");
        });
  }

  @Test
  public void testOnCompleteWithSupplierAndBiFunction() {
    TestRoute ok =
        testRoute(
            onComplete(
                () -> CompletableFuture.completedFuture("ok"),
                (String value, Throwable failure) -> handle(value, failure)));
    ok.run(HttpRequest.GET("/")).assertStatusCode(StatusCodes.OK).assertEntity("value: ok");

    TestRoute failed =
        testRoute(
            onComplete(
                FutureDirectivesTest::failingStage,
                (String value, Throwable failure) -> handle(value, failure)));
    failed
        .run(HttpRequest.GET("/"))
        .assertStatusCode(StatusCodes.INTERNAL_SERVER_ERROR)
        .assertEntity("failed: boom");
  }

  @Test
  public void testOnCompleteWithStageAndBiFunction() {
    TestRoute ok =
        testRoute(
            onComplete(
                CompletableFuture.completedFuture("ok"),
                (String value, Throwable failure) -> handle(value, failure)));
    ok.run(HttpRequest.GET("/")).assertStatusCode(StatusCodes.OK).assertEntity("value: ok");

    TestRoute failed =
        testRoute(
            onComplete(
                failingStage(), (String value, Throwable failure) -> handle(value, failure)));
    failed
        .run(HttpRequest.GET("/"))
        .assertStatusCode(StatusCodes.INTERNAL_SERVER_ERROR)
        .assertEntity("failed: boom");
  }

  @Test
  public void testOnCompleteWithImplicitlyTypedLambdas() {
    // one- and two-argument lambdas must resolve to the Try and BiFunction overloads respectively
    TestRoute tryRoute =
        testRoute(
            onComplete(
                CompletableFuture.completedFuture("ok"),
                result -> complete("try: " + result.get())));
    tryRoute.run(HttpRequest.GET("/")).assertEntity("try: ok");

    TestRoute biRoute =
        testRoute(
            onComplete(
                CompletableFuture.completedFuture("ok"),
                (value, failure) -> complete("bi: " + value + ", " + failure)));
    biRoute.run(HttpRequest.GET("/")).assertEntity("bi: ok, null");
  }
}
