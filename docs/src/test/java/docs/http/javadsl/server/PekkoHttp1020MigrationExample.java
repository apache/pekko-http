/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.http.javadsl.server;

import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.http.javadsl.ConnectHttp;
import org.apache.pekko.http.javadsl.Http;
import static org.apache.pekko.http.javadsl.server.Directives.*;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.stream.ActorMaterializer;
import org.apache.pekko.stream.Materializer;

@SuppressWarnings("deprecation")
public class PekkoHttp1020MigrationExample {
    public static void main(String[] args) {
        {
            //#old-binding
            // only worked with classic actor system
            org.apache.pekko.actor.ActorSystem system = org.apache.pekko.actor.ActorSystem.create("TheSystem");
            Materializer mat = ActorMaterializer.create(system);
            Route route = get(() -> complete("Hello World!"));
            Http.get(system).bindAndHandle(route.flow(system), ConnectHttp.toHost("localhost", 8080), mat);
            //#old-binding
        }

        {
            //#new-binding
            // works with classic or typed actor system
            org.apache.pekko.actor.typed.ActorSystem system = org.apache.pekko.actor.typed.ActorSystem.create(Behaviors.empty(), "TheSystem");
            // or
            // org.apache.pekko.actor.ActorSystem system = org.apache.pekko.actor.ActorSystem.create("TheSystem");

            // materializer not needed any more

            Route route = get(() -> complete("Hello World!"));
            Http.get(system).newServerAt("localhost", 8080).bind(route);
            //#new-binding
        }
    }

}
