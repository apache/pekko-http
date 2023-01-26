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
    return path("foo", () ->
        complete("bar")
      );
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
