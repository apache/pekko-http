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

package org.apache.pekko.http.javadsl.marshallers.jackson3;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.util.BufferRecycler;
import tools.jackson.core.util.JsonRecyclerPools.BoundedPool;
import tools.jackson.core.util.RecyclerPool;
import tools.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.marshallers.jackson3.Jackson;
import org.apache.pekko.http.javadsl.model.ContentTypes;
import org.apache.pekko.http.javadsl.model.HttpEntities;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.RequestEntity;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;

import org.junit.Test;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class JacksonTest extends JUnitRouteTest {

  public static class SomeData {
    public final String field;

    @JsonCreator
    public SomeData(@JsonProperty("field") String field) {
      this.field = field;
    }
  }

  RequestEntity invalidEntity =
      HttpEntities.create(
          ContentTypes.APPLICATION_JSON, "{\"droids\":\"not the ones you are looking for\"}");

  @Override
  public Config additionalConfig() {
    return ConfigFactory.parseString("");
  }

  @Test
  public void failingToUnmarshallShouldProvideFailureDetails() throws Exception {
    ActorSystem sys = ActorSystem.create("test");
    try {
      CompletionStage<SomeData> unmarshalled =
          Jackson.unmarshaller(SomeData.class).unmarshal(invalidEntity, system());

      SomeData result = unmarshalled.toCompletableFuture().get(3, TimeUnit.SECONDS);
      fail("Invalid json should not parse to object");
    } catch (ExecutionException ex) {
      // CompletableFuture.get wraps in one layer of ExecutionException
      String message = ex.getCause().getMessage();
      assertTrue(
          "Unexpected exception message: " + message,
          message.startsWith(
              "Cannot unmarshal JSON as SomeData: Unrecognized property \"droids\""));
    } finally {
      sys.terminate();
    }
  }

  @Test
  public void detailsShouldBeHiddenFromResponseEntity() throws Exception {
    Route route = entity(Jackson.unmarshaller(SomeData.class), theData -> complete(theData.field));

    runRoute(route.seal(), HttpRequest.PUT("/").withEntity(invalidEntity))
        .assertEntity("The request content was malformed:\nCannot unmarshal JSON as SomeData");
  }

  @Test
  public void configStreamReadsConstraints() throws Exception {
    final int maxNumLen = 987;
    final int maxNameLen = 54321;
    final int maxStringLen = 1234567;
    final long maxDocLen = 123456789L;
    final long maxTokenCount = 9876543210L;
    final int maxNestingDepth = 5;
    String configText =
        "read.max-number-length="
            + maxNumLen
            + "\n"
            + "read.max-name-length="
            + maxNameLen
            + "\n"
            + "read.max-string-length="
            + maxStringLen
            + "\n"
            + "read.max-document-length="
            + maxDocLen
            + "\n"
            + "read.max-token-count="
            + maxTokenCount
            + "\n"
            + "read.max-nesting-depth="
            + maxNestingDepth;
    Config config = ConfigFactory.parseString(configText).withFallback(getDefaultConfig());
    JsonFactory jsonFactory = Jackson.createJsonFactory(config);
    StreamReadConstraints constraints = jsonFactory.streamReadConstraints();
    assertEquals(maxNumLen, constraints.getMaxNumberLength());
    assertEquals(maxNameLen, constraints.getMaxNameLength());
    assertEquals(maxStringLen, constraints.getMaxStringLength());
    assertEquals(maxDocLen, constraints.getMaxDocumentLength());
    assertEquals(maxTokenCount, constraints.getMaxTokenCount());
    assertEquals(maxNestingDepth, constraints.getMaxNestingDepth());
  }

  @Test
  public void configStreamWritesConstraints() throws Exception {
    final int maxNestingDepth = 5;
    String configText = "write.max-nesting-depth=" + maxNestingDepth;
    Config config = ConfigFactory.parseString(configText).withFallback(getDefaultConfig());
    JsonFactory jsonFactory = Jackson.createJsonFactory(config);
    StreamWriteConstraints constraints = jsonFactory.streamWriteConstraints();
    assertEquals(maxNestingDepth, constraints.getMaxNestingDepth());
  }

  @Test
  public void testDefaultFactory() throws Exception {
    JsonFactory jsonFactory = Jackson.createJsonFactory(getDefaultConfig());
    RecyclerPool<BufferRecycler> recyclerPool = jsonFactory._getRecyclerPool();
    assertEquals("ThreadLocalPool", recyclerPool.getClass().getSimpleName());
  }

  @Test
  public void testFactoryWithBufferRecyclerSetting() throws Exception {
    final String poolType = "bounded";
    final int poolSize = 10;
    String configText =
        "buffer-recycler.pool-instance="
            + poolType
            + "\nbuffer-recycler.bounded-pool-size="
            + poolSize;
    Config config = ConfigFactory.parseString(configText).withFallback(getDefaultConfig());
    JsonFactory jsonFactory = Jackson.createJsonFactory(config);
    RecyclerPool<BufferRecycler> recyclerPool = jsonFactory._getRecyclerPool();
    assertEquals("BoundedPool", recyclerPool.getClass().getSimpleName());
    assertEquals(poolSize, ((BoundedPool) recyclerPool).capacity());
  }

  private static Config getDefaultConfig() {
    return ConfigFactory.load().getConfig("pekko.http.marshallers.jackson3");
  }
}
