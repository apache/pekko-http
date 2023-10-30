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

package org.apache.pekko.http.javadsl.server.examples.petstore;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.Http;
// #imports
import org.apache.pekko.http.javadsl.marshallers.jackson.Jackson;
import org.apache.pekko.http.javadsl.model.StatusCodes;
// #imports
import org.apache.pekko.http.javadsl.server.Route;

import java.io.IOException;
// #imports
import java.util.Map;
import java.util.concurrent.CompletableFuture;
// #imports
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

// #imports
import static org.apache.pekko.http.javadsl.server.Directives.*;
import static org.apache.pekko.http.javadsl.unmarshalling.StringUnmarshallers.INTEGER;

// #imports

public class PetStoreExample {

  // #marshall
  private static Route putPetHandler(Map<Integer, Pet> pets, Pet thePet) {
    pets.put(thePet.getId(), thePet);
    return complete(StatusCodes.OK, thePet, Jackson.<Pet>marshaller());
  }

  private static Route alternativeFuturePutPetHandler(Map<Integer, Pet> pets, Pet thePet) {
    pets.put(thePet.getId(), thePet);
    CompletableFuture<Pet> futurePet = CompletableFuture.supplyAsync(() -> thePet);
    return completeOKWithFuture(futurePet, Jackson.<Pet>marshaller());
  }
  // #marshall

  // #unmarshall
  public static Route appRoute(final Map<Integer, Pet> pets) {
    PetStoreController controller = new PetStoreController(pets);

    // Defined as Function in order to refer to [pets], but this could also be an ordinary method.
    Function<Integer, Route> existingPet =
        petId -> {
          Pet pet = pets.get(petId);
          return (pet == null)
              ? reject()
              : complete(StatusCodes.OK, pet, Jackson.<Pet>marshaller());
        };

    // The directives here are statically imported, but you can also inherit from AllDirectives.
    return concat(
        path("", () -> getFromResource("web/index.html")),
        pathPrefix(
            "pet",
            () ->
                path(
                    INTEGER,
                    petId ->
                        concat(
                            // demonstrates different ways of handling requests:

                            // 1. using a Function
                            get(() -> existingPet.apply(petId)),

                            // 2. using a method
                            put(
                                () ->
                                    entity(
                                        Jackson.unmarshaller(Pet.class),
                                        thePet -> putPetHandler(pets, thePet))),
                            // 2.1. using a method, and internally handling a Future value
                            path(
                                "alternate",
                                () ->
                                    put(
                                        () ->
                                            entity(
                                                Jackson.unmarshaller(Pet.class),
                                                thePet -> putPetHandler(pets, thePet)))),

                            // 3. calling a method of a controller instance
                            delete(() -> controller.deletePet(petId))))));
  }
  // #unmarshall

  public static void main(String[] args) throws IOException {
    Map<Integer, Pet> pets = new ConcurrentHashMap<>();
    Pet dog = new Pet(0, "dog");
    Pet cat = new Pet(1, "cat");
    pets.put(0, dog);
    pets.put(1, cat);

    final ActorSystem system = ActorSystem.create();

    Http.get(system).newServerAt("127.0.0.1", 8080).bind(appRoute(pets));

    System.console().readLine("Type RETURN to exit...");
    system.terminate();
  }
}
