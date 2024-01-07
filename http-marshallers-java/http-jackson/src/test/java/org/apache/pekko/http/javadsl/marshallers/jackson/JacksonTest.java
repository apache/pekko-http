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

package org.apache.pekko.http.javadsl.marshallers.jackson;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamWriteConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import org.apache.pekko.actor.ActorSystem;
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
      throw new AssertionError("Invalid json should not parse to object");
    } catch (ExecutionException ex) {
      // CompletableFuture.get wraps in one layer of ExecutionException
      assertTrue(
          ex.getCause()
              .getMessage()
              .startsWith("Cannot unmarshal JSON as SomeData: Unrecognized field \"droids\""));
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
            + "read.max-nesting-depth="
            + maxNestingDepth;
    Config config =
        ConfigFactory.parseString(configText)
            .withFallback(ConfigFactory.load().getConfig("pekko.http.jackson"));
    ObjectMapper mapper = Jackson.createMapper(config);
    StreamReadConstraints constraints = mapper.getFactory().streamReadConstraints();
    assertEquals(maxNumLen, constraints.getMaxNumberLength());
    assertEquals(maxNameLen, constraints.getMaxNameLength());
    assertEquals(maxStringLen, constraints.getMaxStringLength());
    assertEquals(maxDocLen, constraints.getMaxDocumentLength());
    assertEquals(maxNestingDepth, constraints.getMaxNestingDepth());
  }

  @Test
  public void configStreamWritesConstraints() throws Exception {
    final int maxNestingDepth = 5;
    String configText = "write.max-nesting-depth=" + maxNestingDepth;
    Config config =
        ConfigFactory.parseString(configText)
            .withFallback(ConfigFactory.load().getConfig("pekko.http.jackson"));
    ObjectMapper mapper = Jackson.createMapper(config);
    StreamWriteConstraints constraints = mapper.getFactory().streamWriteConstraints();
    assertEquals(maxNestingDepth, constraints.getMaxNestingDepth());
  }
}
