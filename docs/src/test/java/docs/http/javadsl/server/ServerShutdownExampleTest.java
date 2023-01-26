/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.http.javadsl.server;

import org.apache.pekko.actor.CoordinatedShutdown;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.server.Route;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

public class ServerShutdownExampleTest {

    public void mountCoordinatedShutdown() {
        ActorSystem<?> system = ActorSystem.create(Behaviors.empty(), "http-server");

        Route routes = null;

        // #suggested
        CompletionStage<ServerBinding> bindingFuture = Http
            .get(system)
            .newServerAt("localhost", 8080)
            .bind(routes)
            .thenApply(binding -> binding.addToCoordinatedShutdown(Duration.ofSeconds(10), system));
        // #suggested

        bindingFuture.exceptionally(cause -> {
            system.log().error("Error starting the server: " + cause.getMessage(), cause);
            return null;
        });

        // #shutdown
        // shut down with `ActorSystemTerminateReason`
        system.terminate();

        // or define a specific reason
        final class UserInitiatedShutdown implements CoordinatedShutdown.Reason {
            @Override
            public String toString() {
                return "UserInitiatedShutdown";
            }
        }

        CoordinatedShutdown.get(system).run(new UserInitiatedShutdown());
        // #shutdown
    }
}
