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

package docs.http.javadsl;

import static org.junit.Assert.assertEquals;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;

import org.junit.Test;

// #imports
import org.apache.pekko.http.javadsl.unmarshalling.StringUnmarshallers;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

// #imports

@SuppressWarnings("unused")
public class UnmarshalTest extends JUnitRouteTest {

  @Test
  public void useUnmarshal() throws Exception {
    // #use-unmarshal
    CompletionStage<Integer> integerStage = StringUnmarshallers.INTEGER.unmarshal("42", system());
    int integer =
        integerStage
            .toCompletableFuture()
            .get(1, TimeUnit.SECONDS); // don't block in non-test code!
    assertEquals(integer, 42);

    CompletionStage<Boolean> boolStage = StringUnmarshallers.BOOLEAN.unmarshal("off", system());
    boolean bool =
        boolStage.toCompletableFuture().get(1, TimeUnit.SECONDS); // don't block in non-test code!
    assertEquals(bool, false);
    // #use-unmarshal
  }

  @Test
  public void useUnmarshalWithExecutionContext() throws Exception {
    CompletionStage<Integer> integerStage =
        StringUnmarshallers.INTEGER.unmarshal("42", system().dispatcher(), system());
    int integer =
        integerStage
            .toCompletableFuture()
            .get(1, TimeUnit.SECONDS); // don't block in non-test code!
    assertEquals(integer, 42);

    CompletionStage<Boolean> boolStage =
        StringUnmarshallers.BOOLEAN.unmarshal("off", system().dispatcher(), system());
    boolean bool =
        boolStage.toCompletableFuture().get(1, TimeUnit.SECONDS); // don't block in non-test code!
    assertEquals(bool, false);
  }
}
