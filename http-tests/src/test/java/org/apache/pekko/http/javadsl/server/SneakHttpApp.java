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
import scala.runtime.BoxedUnit;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

public class SneakHttpApp extends MinimalHttpApp {

  AtomicBoolean postServerShutdownCalled = new AtomicBoolean(false);
  AtomicBoolean postBindingCalled = new AtomicBoolean(false);
  AtomicBoolean postBindingFailureCalled = new AtomicBoolean(false);

  @Override
  protected void postServerShutdown(Optional<Throwable> failure, ActorSystem system) {
    postServerShutdownCalled.set(true);
  }

  @Override
  protected void postHttpBinding(ServerBinding binding) {
    postBindingCalled.set(true);
    bindingPromise.complete(binding);
  }

  @Override
  protected void postHttpBindingFailure(Throwable cause) {
    postBindingFailureCalled.set(true);
  }
}
