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

package org.apache.pekko.http.javadsl.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.apache.pekko.util.ByteString;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class HttpEntitiesTest {
  private static ActorSystem sys;

  @BeforeAll
  public static void setup() {
    sys = ActorSystem.create("HttpEntitiesTest");
  }

  @AfterAll
  public static void teardown() {
    TestKit.shutdownActorSystem(sys);
  }

  @Test
  public void testEntityFactoryRejectsNegativeLength() {
    Source<ByteString, ?> data = Source.empty();
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> HttpEntities.create(ContentTypes.APPLICATION_OCTET_STREAM, -1L, data));
    assertTrue(
        error.getMessage().contains("contentLength must be positive"),
        () -> "Expected the content-length validation diagnostic, but got: " + error.getMessage());
  }

  @Test
  public void testEntityFactoryZeroLengthIsCurrentlyUnsupportedByDefaultReturnType() {
    Source<ByteString, ?> data = Source.empty();
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> HttpEntities.create(ContentTypes.APPLICATION_OCTET_STREAM, 0L, data));
    assertTrue(
        error.getMessage().contains("contentLength must be positive"),
        () -> "Expected the content-length validation diagnostic, but got: " + error.getMessage());
  }

  @Test
  public void testEmptyByteEntityFactoryReturnsEmptyStrictEntity() {
    HttpEntity.Strict entity =
        HttpEntities.create(ContentTypes.APPLICATION_OCTET_STREAM, ByteString.emptyByteString());
    HttpEntity.Strict byteArrayEntity =
        HttpEntities.create(ContentTypes.APPLICATION_OCTET_STREAM, new byte[0]);
    HttpEntity expected = HttpEntities.EMPTY.withContentType(ContentTypes.APPLICATION_OCTET_STREAM);
    assertEquals(
        expected, entity, "The empty ByteString overload must return an empty strict entity");
    assertEquals(
        expected,
        byteArrayEntity,
        "The empty byte array overload must return an empty strict entity");
  }

  @Test
  public void testEntityFactoryPreservesPositiveLengthAndData() throws Exception {
    ByteString bytes = ByteString.fromString("abc");
    Source<ByteString, ?> data = Source.single(bytes).mapMaterializedValue(ignored -> 42);
    HttpEntity.Default entity =
        HttpEntities.create(ContentTypes.APPLICATION_OCTET_STREAM, 3L, data);
    assertEquals(3L, entity.getContentLength());
    assertFalse(entity.isKnownEmpty(), "A positive-length entity must not be known empty");
    assertEquals(
        bytes,
        entity
            .getDataBytes()
            .runFold(ByteString.emptyByteString(), ByteString::concat, sys)
            .toCompletableFuture()
            .get(3, TimeUnit.SECONDS));
  }
}
