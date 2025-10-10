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

package org.apache.pekko.http.javadsl.model;

import org.apache.pekko.Done;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.japi.function.Procedure;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.util.ByteString;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

import scala.util.Try;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;

public class EntityDiscardingTest extends JUnitSuite {

  private ActorSystem sys = ActorSystem.create("test");
  private Iterable<ByteString> testData =
      Arrays.asList(ByteString.fromString("abc"), ByteString.fromString("def"));

  @Test
  public void testHttpRequestDiscardEntity() {

    CompletableFuture<Done> f = new CompletableFuture<>();
    Source<ByteString, ?> s = Source.from(testData).alsoTo(Sink.onComplete(completeDone(f)));

    RequestEntity reqEntity = HttpEntities.create(ContentTypes.TEXT_PLAIN_UTF8, s);
    HttpRequest req = HttpRequest.create().withEntity(reqEntity);

    HttpMessage.DiscardedEntity de = req.discardEntityBytes(sys);

    assertEquals(Done.getInstance(), f.join());
    assertEquals(Done.getInstance(), de.completionStage().toCompletableFuture().join());
  }

  @Test
  public void testHttpResponseDiscardEntity() {

    CompletableFuture<Done> f = new CompletableFuture<>();
    Source<ByteString, ?> s = Source.from(testData).alsoTo(Sink.onComplete(completeDone(f)));

    ResponseEntity respEntity = HttpEntities.create(ContentTypes.TEXT_PLAIN_UTF8, s);
    HttpResponse resp = HttpResponse.create().withEntity(respEntity);

    HttpMessage.DiscardedEntity de = resp.discardEntityBytes(sys);

    assertEquals(Done.getInstance(), f.join());
    assertEquals(Done.getInstance(), de.completionStage().toCompletableFuture().join());
  }

  private Procedure<Try<Done>> completeDone(CompletableFuture<Done> p) {
    return new Procedure<Try<Done>>() {
      @Override
      public void apply(Try<Done> t) throws Exception {
        if (t.isSuccess()) p.complete(Done.getInstance());
        else p.completeExceptionally(t.failed().get());
      }
    };
  }
}
