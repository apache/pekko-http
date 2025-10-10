/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.http.javadsl.server.directives;

import org.apache.pekko.http.javadsl.marshallers.jackson.Jackson;
import org.apache.pekko.http.javadsl.marshalling.Marshaller;
import org.apache.pekko.http.javadsl.model.*;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.apache.pekko.http.javadsl.testkit.TestRouteResult;
import org.apache.pekko.http.javadsl.unmarshalling.Unmarshaller;
import org.junit.Test;

import java.util.function.Consumer;
import java.util.function.Function;

// #example-entity-with-json
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.entity;

// #example-entity-with-json
// #example-completeWith-with-json
import static org.apache.pekko.http.javadsl.server.Directives.completeWith;

// #example-completeWith-with-json
// #example-handleWith-with-json
import static org.apache.pekko.http.javadsl.server.Directives.handleWith;

// #example-handleWith-with-json

public class MarshallingDirectivesExamplesTest extends JUnitRouteTest {

  // #person
  public static class Person {
    private final String name;
    private final int favoriteNumber;

    // default constructor required for Jackson
    public Person() {
      this.name = "";
      this.favoriteNumber = 0;
    }

    public Person(String name, int favoriteNumber) {
      this.name = name;
      this.favoriteNumber = favoriteNumber;
    }

    public String getName() {
      return name;
    }

    public int getFavoriteNumber() {
      return favoriteNumber;
    }
  }
  // #person

  @Test
  public void testEntity() {
    // #example-entity-with-json
    final Unmarshaller<HttpEntity, Person> unmarshaller = Jackson.unmarshaller(Person.class);

    final Route route =
        entity(
            unmarshaller,
            person ->
                complete(
                    "Person:"
                        + person.getName()
                        + " - favoriteNumber:"
                        + person.getFavoriteNumber()));

    testRoute(route)
        .run(
            HttpRequest.POST("/")
                .withEntity(
                    HttpEntities.create(
                        ContentTypes.APPLICATION_JSON,
                        "{\"name\":\"Jane\",\"favoriteNumber\":42}")))
        .assertEntity("Person:Jane - favoriteNumber:42");
    // #example-entity-with-json
  }

  @Test
  public void testCompleteWith() {
    // #example-completeWith-with-json
    final Marshaller<Person, HttpResponse> marshaller =
        Marshaller.entityToOKResponse(Jackson.<Person>marshaller());

    // Please note that you can also pass completionFunction to another thread and use it there to
    // complete the request.
    // For example:
    // final Consumer<Consumer<Person>> findPerson = completionFunction -> {
    //  CompletableFuture.runAsync(() ->
    //   /* ... some processing logic... */
    //   completionFunction.accept(new Person("Jane", 42)));
    // };
    final Consumer<Consumer<Person>> findPerson =
        completionFunction -> {

          // ... some processing logic...

          // complete the request
          completionFunction.accept(new Person("Jane", 42));
        };

    final Route route = completeWith(marshaller, findPerson);

    // tests:
    final TestRouteResult routeResult = testRoute(route).run(HttpRequest.GET("/"));
    routeResult.assertMediaType(MediaTypes.APPLICATION_JSON);
    routeResult.assertEntity("{\"favoriteNumber\":42,\"name\":\"Jane\"}");
    // #example-completeWith-with-json
  }

  @Test
  public void testHandleWith() {
    // #example-handleWith-with-json
    final Unmarshaller<HttpEntity, Person> unmarshaller = Jackson.unmarshaller(Person.class);
    final Marshaller<Person, HttpResponse> marshaller =
        Marshaller.entityToOKResponse(Jackson.<Person>marshaller());

    final Function<Person, Person> updatePerson =
        person -> {

          // ... some processing logic...

          // return the person
          return person;
        };

    final Route route = handleWith(unmarshaller, marshaller, updatePerson);

    // tests:
    final TestRouteResult routeResult =
        testRoute(route)
            .run(
                HttpRequest.POST("/")
                    .withEntity(
                        HttpEntities.create(
                            ContentTypes.APPLICATION_JSON,
                            "{\"name\":\"Jane\",\"favoriteNumber\":42}")));
    routeResult.assertMediaType(MediaTypes.APPLICATION_JSON);
    routeResult.assertEntity("{\"favoriteNumber\":42,\"name\":\"Jane\"}");
    // #example-handleWith-with-json
  }
}
