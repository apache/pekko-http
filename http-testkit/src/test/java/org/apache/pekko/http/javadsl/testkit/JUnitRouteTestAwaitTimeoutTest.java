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

package org.apache.pekko.http.javadsl.testkit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.junit.jupiter.api.Test;

public class JUnitRouteTestAwaitTimeoutTest extends JUnitJupiterRouteTest {

  @Override
  public Duration awaitTimeout() {
    return Duration.ofMillis(200);
  }

  @Test
  public void testAwaitTimeoutOverrideIsUsedForRouteResults() {
    CompletableFuture<String> neverCompleted = new CompletableFuture<>();
    TestRoute route = testRoute(onSuccess(neverCompleted, result -> complete(result)));
    TestRouteResult result = route.run(HttpRequest.GET("/"));

    long start = System.nanoTime();
    assertThrows(TimeoutException.class, () -> result.assertStatusCode(StatusCodes.OK));
    long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();
    assertTrue(
        elapsedMillis < 2500,
        "expected the overridden 200ms await timeout, not the 3s default, but waited "
            + elapsedMillis
            + "ms");
  }
}
