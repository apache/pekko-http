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

import org.apache.pekko.Done;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.scaladsl.Http;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;

public class MinimalHttpApp extends HttpApp {

  CompletableFuture<Done> shutdownTrigger = new CompletableFuture<>();
  CompletableFuture<ServerBinding> bindingPromise = new CompletableFuture<>();

  public void shutdown() {
    shutdownTrigger.complete(Done.getInstance());
  }

  @Override
  protected Route routes() {
    return path("foo", () -> complete("bar"));
  }

  @Override
  protected void postHttpBinding(ServerBinding binding) {
    super.postHttpBinding(binding);
    bindingPromise.complete(binding);
  }

  @Override
  protected void postHttpBindingFailure(Throwable cause) {
    super.postHttpBindingFailure(cause);
    bindingPromise.completeExceptionally(cause);
  }

  @Override
  protected CompletionStage<Done> waitForShutdownSignal(ActorSystem system) {
    return shutdownTrigger;
  }
}
