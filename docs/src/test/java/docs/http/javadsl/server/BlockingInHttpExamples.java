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

package docs.http.javadsl.server;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.dispatch.MessageDispatcher;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;

// #blocking-example-in-default-dispatcher
import static org.apache.pekko.http.javadsl.server.Directives.completeWithFuture;
import static org.apache.pekko.http.javadsl.server.Directives.post;

// #blocking-example-in-default-dispatcher
// #blocking-example-in-dedicated-dispatcher
import static org.apache.pekko.http.javadsl.server.Directives.completeWithFuture;
import static org.apache.pekko.http.javadsl.server.Directives.post;

// #blocking-example-in-dedicated-dispatcher
public class BlockingInHttpExamples extends JUnitRouteTest {

  @Test
  public void compileOnlySpec() throws Exception {
    // just making sure for it to be really compiled / run even if empty
  }

  void blockingHttpDefaultDispatcher() {
    // #blocking-example-in-default-dispatcher
    // BAD (due to blocking in Future, on default dispatcher)
    final Route routes =
        post(
            () ->
                completeWithFuture(
                    CompletableFuture.supplyAsync(
                        () -> { // uses defaultDispatcher
                          try {
                            Thread.sleep(5000L); // will block on default dispatcher,
                          } catch (InterruptedException e) {
                          }
                          return HttpResponse.create() // Starving the routing infrastructure
                              .withEntity(Long.toString(System.currentTimeMillis()));
                        })));
    // #blocking-example-in-default-dispatcher
  }

  void blockingHttpDedicatedDispatcher() {
    final ActorSystem system = ActorSystem.create();
    // #blocking-example-in-dedicated-dispatcher
    // GOOD (the blocking is now isolated onto a dedicated dispatcher):
    final Route routes =
        post(
            () -> {
              final MessageDispatcher dispatcher =
                  system.dispatchers().lookup("my-blocking-dispatcher");
              return completeWithFuture(
                  CompletableFuture.supplyAsync(
                      () -> {
                        try {
                          Thread.sleep(5000L);
                        } catch (InterruptedException e) {
                        }
                        return HttpResponse.create()
                            .withEntity(Long.toString(System.currentTimeMillis()));
                      },
                      dispatcher // uses the good "blocking dispatcher" that we
                      // configured, instead of the default dispatcher to isolate the blocking.
                      ));
            });
    // #blocking-example-in-dedicated-dispatcher
  }
}
