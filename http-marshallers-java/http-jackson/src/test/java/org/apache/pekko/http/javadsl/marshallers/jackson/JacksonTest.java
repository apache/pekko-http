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
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.model.ContentTypes;
import org.apache.pekko.http.javadsl.model.HttpEntities;
import org.apache.pekko.http.javadsl.model.HttpEntity;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.RequestEntity;
import org.apache.pekko.http.javadsl.server.ExceptionHandler;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.settings.RoutingSettings;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.apache.pekko.stream.ActorMaterializer;
import org.apache.pekko.stream.Materializer;

import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
      Materializer materializer = ActorMaterializer.create(sys);
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
}
