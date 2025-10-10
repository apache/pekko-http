/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.http.javadsl;

// Run with:
// sbt "docs/test:runMain docs.http.javadsl.HttpServerWithActorsSample"

// Example commands:
// curl -X POST -H "Content-Type: application/json" -d "{ \"id\": 42 }" localhost:8080/jobs
// curl localhost:8080/jobs/42

// #bootstrap

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.BehaviorBuilder;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.server.Route;

import java.util.concurrent.CompletionStage;

public class HttpServerWithActorsSample {

  interface Message {}

  private static final class StartFailed implements Message {
    final Throwable ex;

    public StartFailed(Throwable ex) {
      this.ex = ex;
    }
  }

  private static final class Started implements Message {
    final ServerBinding binding;

    public Started(ServerBinding binding) {
      this.binding = binding;
    }
  }

  private static final class Stop implements Message {}

  public static Behavior<Message> create(String host, Integer port) {
    return Behaviors.setup(
        ctx -> {
          ActorSystem<Void> system = ctx.getSystem();
          ActorRef<JobRepository.Command> buildJobRepository =
              ctx.spawn(JobRepository.create(), "JobRepository");
          Route routes = new JobRoutes(buildJobRepository, ctx.getSystem()).jobRoutes();

          CompletionStage<ServerBinding> serverBinding =
              Http.get(system).newServerAt(host, port).bind(routes);

          ctx.pipeToSelf(
              serverBinding,
              (binding, failure) -> {
                if (binding != null) return new Started(binding);
                else return new StartFailed(failure);
              });

          return starting(false);
        });
  }

  private static Behavior<Message> starting(boolean wasStopped) {
    return Behaviors.setup(
        ctx ->
            BehaviorBuilder.<Message>create()
                .onMessage(
                    StartFailed.class,
                    failed -> {
                      throw new RuntimeException("Server failed to start", failed.ex);
                    })
                .onMessage(
                    Started.class,
                    msg -> {
                      ctx.getLog()
                          .info(
                              "Server online at http://{}:{}",
                              msg.binding.localAddress().getAddress(),
                              msg.binding.localAddress().getPort());

                      if (wasStopped) ctx.getSelf().tell(new Stop());

                      return running(msg.binding);
                    })
                .onMessage(
                    Stop.class,
                    s -> {
                      // we got a stop message but haven't completed starting yet,
                      // we cannot stop until starting has completed
                      return starting(true);
                    })
                .build());
  }

  private static Behavior<Message> running(ServerBinding binding) {
    return BehaviorBuilder.<Message>create()
        .onMessage(Stop.class, msg -> Behaviors.stopped())
        .onSignal(
            PostStop.class,
            msg -> {
              binding.unbind();
              return Behaviors.same();
            })
        .build();
  }

  public static void main(String[] args) {
    ActorSystem<Message> system =
        ActorSystem.create(HttpServerWithActorsSample.create("localhost", 8080), "BuildJobsServer");
  }
}
// #bootstrap
