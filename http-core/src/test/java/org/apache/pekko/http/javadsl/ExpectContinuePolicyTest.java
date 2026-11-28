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

package org.apache.pekko.http.javadsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.apache.pekko.http.ExpectContinuePolicy;
import org.apache.pekko.http.ExpectContinueRetryContext;
import org.apache.pekko.http.javadsl.model.AttributeKey;
import org.apache.pekko.http.javadsl.model.HttpHeader;
import org.apache.pekko.http.javadsl.model.HttpMethods;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.junit.jupiter.api.Test;

class ExpectContinuePolicyTest {
  @Test
  void composesARequestVetoWithExistingJavaExecutors() {
    AttributeKey<ExpectContinuePolicy> key = ExpectContinuePolicy.attributeKey();
    ExpectContinuePolicy custom = context -> context.getMethod() == HttpMethods.PUT;
    HttpRequest request =
        HttpRequest.PUT("https://example.com/document")
            .addHeader(HttpHeader.parse("Expect", "100-continue"))
            .withEntity("document");
    List<Optional<ExpectContinuePolicy>> received = new ArrayList<>();
    AtomicInteger finalResponses = new AtomicInteger();
    Function<HttpRequest, CompletionStage<HttpResponse>> downstream =
        r -> {
          received.add(r.getAttribute(key));
          return CompletableFuture.completedFuture(
              HttpResponse.create().withStatus(StatusCodes.CREATED));
        };
    Function<HttpRequest, CompletionStage<HttpResponse>> client =
        r ->
            downstream
                .apply(r)
                .thenApply(
                    response -> {
                      finalResponses.incrementAndGet();
                      return response;
                    });
    Function<HttpRequest, CompletionStage<HttpResponse>> signedRequest =
        r -> client.apply(r.addAttribute(key, ExpectContinuePolicy.disabled()));

    assertEquals(StatusCodes.CREATED, client.apply(request).toCompletableFuture().join().status());
    assertEquals(
        StatusCodes.CREATED, signedRequest.apply(request).toCompletableFuture().join().status());
    assertEquals(List.of(Optional.empty(), Optional.of(ExpectContinuePolicy.disabled())), received);
    assertEquals(2, finalResponses.get());
    assertSame(custom, request.addAttribute(key, custom).getAttribute(key).orElseThrow());
    assertFalse(
        request.addAttribute(key, custom).removeAttribute(key).getAttribute(key).isPresent());
    assertFalse(request.getAttribute(key).isPresent());
  }

  @Test
  void exposesImmutableMetadataWithoutAPublicConstructor() {
    assertEquals(0, ExpectContinueRetryContext.class.getConstructors().length);
  }
}
