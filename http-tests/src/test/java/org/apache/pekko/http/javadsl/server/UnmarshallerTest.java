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

package org.apache.pekko.http.javadsl.server;

import org.apache.pekko.http.javadsl.model.*;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.apache.pekko.http.javadsl.unmarshalling.Unmarshaller;
import org.apache.pekko.http.javadsl.unmarshalling.StringUnmarshallers;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class UnmarshallerTest extends JUnitRouteTest {

  @Test
  public void unmarshallerWithoutExecutionContext() throws Exception {
    CompletionStage<Integer> cafe = StringUnmarshallers.INTEGER_HEX.unmarshal("CAFE", system());
    assertEquals(51966, cafe.toCompletableFuture().get(3, TimeUnit.SECONDS).intValue());
  }

  @Test
  public void canChooseOneOfManyUnmarshallers() throws Exception {
    Unmarshaller<HttpEntity, String> jsonUnmarshaller =
        Unmarshaller.forMediaType(MediaTypes.APPLICATION_JSON, Unmarshaller.entityToString())
            .thenApply((str) -> "json");
    Unmarshaller<HttpEntity, String> xmlUnmarshaller =
        Unmarshaller.forMediaType(MediaTypes.TEXT_XML, Unmarshaller.entityToString())
            .thenApply((str) -> "xml");

    final Unmarshaller<HttpEntity, String> both =
        Unmarshaller.firstOf(jsonUnmarshaller, xmlUnmarshaller);

    {
      CompletionStage<String> resultStage =
          both.unmarshal(HttpEntities.create(ContentTypes.TEXT_XML_UTF8, "<suchXml/>"), system());

      assertEquals("xml", resultStage.toCompletableFuture().get(3, TimeUnit.SECONDS));
    }

    {
      CompletionStage<String> resultStage =
          both.unmarshal(HttpEntities.create(ContentTypes.APPLICATION_JSON, "{}"), system());

      assertEquals("json", resultStage.toCompletableFuture().get(3, TimeUnit.SECONDS));
    }
  }

  @Test
  public void oneMarshallerCanHaveMultipleMediaTypes() throws Exception {
    Unmarshaller<HttpEntity, String> xmlUnmarshaller =
        Unmarshaller.forMediaTypes(
                Arrays.asList(MediaTypes.APPLICATION_XML, MediaTypes.TEXT_XML),
                Unmarshaller.entityToString())
            .thenApply((str) -> "xml");

    {
      CompletionStage<String> resultStage =
          xmlUnmarshaller.unmarshal(
              HttpEntities.create(ContentTypes.TEXT_XML_UTF8, "<suchXml/>"), system());

      assertEquals("xml", resultStage.toCompletableFuture().get(3, TimeUnit.SECONDS));
    }

    {
      CompletionStage<String> resultStage =
          xmlUnmarshaller.unmarshal(
              HttpEntities.create(
                  ContentTypes.create(MediaTypes.APPLICATION_XML, HttpCharsets.UTF_8),
                  "<suchXml/>"),
              system());

      assertEquals("xml", resultStage.toCompletableFuture().get(3, TimeUnit.SECONDS));
    }
  }
}
