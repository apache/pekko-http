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

package docs.http.javadsl;
// #second-jackson-example
import org.apache.pekko.Done;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.marshallers.jackson.Jackson;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.stream.javadsl.Flow;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.apache.pekko.http.javadsl.server.PathMatchers.longSegment;

public class JacksonExampleTest extends AllDirectives {

  public static void main(String[] args) throws Exception {
    // boot up server using the route as defined below
    ActorSystem<Void> system = ActorSystem.create(Behaviors.empty(), "routes");

    final Http http = Http.get(system);

    // In order to access all directives we need an instance where the routes are define.
    JacksonExampleTest app = new JacksonExampleTest();

    final CompletionStage<ServerBinding> binding =
        http.newServerAt("localhost", 8080).bind(app.createRoute());

    System.out.println("Server online at http://localhost:8080/\nPress RETURN to stop...");
    System.in.read(); // let it run until user presses return

    binding
        .thenCompose(ServerBinding::unbind) // trigger unbinding from the port
        .thenAccept(unbound -> system.terminate()); // and shutdown when done
  }

  // (fake) async database query api
  private CompletionStage<Optional<Item>> fetchItem(long itemId) {
    return CompletableFuture.completedFuture(Optional.of(new Item("foo", itemId)));
  }

  // (fake) async database query api
  private CompletionStage<Done> saveOrder(final Order order) {
    return CompletableFuture.completedFuture(Done.getInstance());
  }

  private Route createRoute() {

    return concat(
        get(
            () ->
                pathPrefix(
                    "item",
                    () ->
                        path(
                            longSegment(),
                            (Long id) -> {
                              final CompletionStage<Optional<Item>> futureMaybeItem = fetchItem(id);
                              return onSuccess(
                                  futureMaybeItem,
                                  maybeItem ->
                                      maybeItem
                                          .map(item -> completeOK(item, Jackson.marshaller()))
                                          .orElseGet(
                                              () -> complete(StatusCodes.NOT_FOUND, "Not Found")));
                            }))),
        post(
            () ->
                path(
                    "create-order",
                    () ->
                        entity(
                            Jackson.unmarshaller(Order.class),
                            order -> {
                              CompletionStage<Done> futureSaved = saveOrder(order);
                              return onSuccess(futureSaved, done -> complete("order created"));
                            }))));
  }

  private static class Item {

    final String name;
    final long id;

    @JsonCreator
    Item(@JsonProperty("name") String name, @JsonProperty("id") long id) {
      this.name = name;
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public long getId() {
      return id;
    }
  }

  private static class Order {

    final List<Item> items;

    @JsonCreator
    Order(@JsonProperty("items") List<Item> items) {
      this.items = items;
    }

    public List<Item> getItems() {
      return items;
    }
  }
}
// #second-jackson-example
