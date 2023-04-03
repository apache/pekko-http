/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.http.javadsl.server.testkit;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.scaladsl.model.StatusCodes;
import org.apache.pekko.japi.pf.PFBuilder;

import java.time.Duration;

public class MyAppWithActor extends AllDirectives {
  public static class Ping {
    public final ActorRef<String> replyTo;
    public Ping(ActorRef<String> replyTo) {
      this.replyTo = replyTo;
    }
  }

    public Route createRoute(ActorRef<Ping> actorRef, Scheduler scheduler) {
      Duration timeout = Duration.ofSeconds(3);
      return
        path("ping", () ->
          onSuccess(AskPattern.ask(actorRef, Ping::new, timeout, scheduler), result ->
            complete(result)
          )
        );
    }

}
