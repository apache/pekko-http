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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.junit.jupiter.api.Test;

public class HttpResponseResultTest {

  private final HttpResponse response = HttpResponse.create().withStatus(StatusCodes.ACCEPTED);
  private final RuntimeException failure = new RuntimeException("boom");

  @Test
  public void successExposesTheResponse() {
    HttpResponseResult result = HttpResponseResult.success(response);

    assertTrue(result.isSuccess());
    assertFalse(result.isFailure());
    assertSame(response, result.getResponse());
    assertThrows(IllegalStateException.class, result::getFailure);
    assertEquals("ok", result.fold(r -> "ok", ex -> "failed"));
    assertEquals("202 Accepted, null", result.handle((r, ex) -> r.status().toString() + ", " + ex));
  }

  @Test
  public void failureExposesTheFailure() {
    HttpResponseResult result = HttpResponseResult.failure(failure);

    assertFalse(result.isSuccess());
    assertTrue(result.isFailure());
    assertSame(failure, result.getFailure());
    IllegalStateException thrown = assertThrows(IllegalStateException.class, result::getResponse);
    assertSame(failure, thrown.getCause());
    assertEquals("failed: boom", result.fold(r -> "ok", ex -> "failed: " + ex.getMessage()));
    assertNull(result.handle((r, ex) -> r));
  }

  @Test
  public void functionsMayThrowCheckedExceptions() {
    IOException ioe = new IOException("cannot read");
    HttpResponseResult result = HttpResponseResult.success(response);

    assertSame(
        ioe,
        assertThrows(
            IOException.class,
            () ->
                result.fold(
                    r -> {
                      throw ioe;
                    },
                    ex -> "failed")));
    assertSame(
        ioe,
        assertThrows(
            IOException.class,
            () ->
                result.handle(
                    (r, ex) -> {
                      throw ioe;
                    })));
  }

  @Test
  public void factoriesRejectNull() {
    assertThrows(NullPointerException.class, () -> HttpResponseResult.success(null));
    assertThrows(NullPointerException.class, () -> HttpResponseResult.failure(null));
  }

  @Test
  public void equalityIsBasedOnContents() {
    assertEquals(HttpResponseResult.success(response), HttpResponseResult.success(response));
    assertEquals(
        HttpResponseResult.success(response).hashCode(),
        HttpResponseResult.success(response).hashCode());
    assertEquals(HttpResponseResult.failure(failure), HttpResponseResult.failure(failure));
    assertNotEquals(HttpResponseResult.success(response), HttpResponseResult.failure(failure));
  }
}
