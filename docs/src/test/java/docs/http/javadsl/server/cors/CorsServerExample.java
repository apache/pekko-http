/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package docs.http.javadsl.server.cors;

// #cors-server-example
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.ExceptionHandler;
import org.apache.pekko.http.javadsl.server.RejectionHandler;
import org.apache.pekko.http.javadsl.server.Route;

import java.util.NoSuchElementException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.apache.pekko.http.cors.javadsl.CorsDirectives.cors;
import static org.apache.pekko.http.cors.javadsl.CorsDirectives.corsRejectionHandler;
import static org.apache.pekko.http.javadsl.server.Directives.*;

public class CorsServerExample {

  public static void main(String[] args) throws Exception {
    final ActorSystem<Void> system = ActorSystem.create(Behaviors.empty(), "cors-server");

    final CorsServerExample app = new CorsServerExample();

    final CompletionStage<ServerBinding> futureBinding =
        Http.get(system).newServerAt("localhost", 8080).bind(app.createRoute());

    futureBinding.whenComplete(
        (binding, exception) -> {
          if (binding != null) {
            system.log().info("Server online at http://localhost:8080/\nPress RETURN to stop...");
          } else {
            system.log().error("Failed to bind HTTP endpoint, terminating system", exception);
            system.terminate();
          }
        });

    System.in.read(); // let it run until user presses return
    futureBinding.thenCompose(ServerBinding::unbind).thenAccept(unbound -> system.terminate());
  }

  private Route createRoute() {

    // Your CORS settings are loaded from `application.conf`

    // Your rejection handler
    final RejectionHandler rejectionHandler =
        corsRejectionHandler().withFallback(RejectionHandler.defaultHandler());

    // Your exception handler
    final ExceptionHandler exceptionHandler =
        ExceptionHandler.newBuilder()
            .match(
                NoSuchElementException.class,
                ex -> complete(StatusCodes.NOT_FOUND, ex.getMessage()))
            .build();

    // Combining the two handlers only for convenience
    final Function<Supplier<Route>, Route> handleErrors =
        inner ->
            allOf(
                s -> handleExceptions(exceptionHandler, s),
                s -> handleRejections(rejectionHandler, s),
                inner);

    // Note how rejections and exceptions are handled *before* the CORS directive (in the inner
    // route).
    // This is required to have the correct CORS headers in the response even when an error occurs.
    return handleErrors.apply(
        () ->
            cors(
                () ->
                    handleErrors.apply(
                        () ->
                            concat(
                                path("ping", () -> complete("pong")),
                                path(
                                    "pong",
                                    () ->
                                        failWith(
                                            new NoSuchElementException(
                                                "pong not found, try with ping")))))));
  }
}
// #cors-server-example
