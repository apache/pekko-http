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

import org.apache.pekko.http.javadsl.marshallers.jackson.Jackson;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.MediaTypes;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.testkit.*;

import static org.junit.Assert.*;

import org.apache.pekko.http.javadsl.testkit.TestRoute;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class PetStoreAPITest extends JUnitRouteTest {
  @Test
  public void testGetPet() {
    TestRouteResult response = createRoute().run(HttpRequest.GET("/pet/1"));

    response.assertStatusCode(StatusCodes.OK).assertMediaType("application/json");

    Pet pet = response.entity(Jackson.unmarshaller(Pet.class));
    assertEquals("cat", pet.getName());
    assertEquals(1, pet.getId());
  }

  @Test
  public void testGetMissingPet() {
    createRoute().run(HttpRequest.GET("/pet/999")).assertStatusCode(StatusCodes.NOT_FOUND);
  }

  @Test
  public void testPutPet() {
    HttpRequest request =
        HttpRequest.PUT("/pet/1")
            .withEntity(
                MediaTypes.APPLICATION_JSON.toContentType(), "{\"id\": 1, \"name\": \"giraffe\"}");

    TestRouteResult response = createRoute().run(request);

    response.assertStatusCode(StatusCodes.OK);

    Pet pet = response.entity(Jackson.unmarshaller(Pet.class));
    assertEquals("giraffe", pet.getName());
    assertEquals(1, pet.getId());
  }

  @Test
  public void testDeletePet() {
    Map<Integer, Pet> data = createData();

    HttpRequest request = HttpRequest.DELETE("/pet/0");

    createRoute(data).run(request).assertStatusCode(StatusCodes.OK);

    // test actual deletion from data store
    assertFalse(data.containsKey(0));
  }

  private TestRoute createRoute() {
    return createRoute(createData());
  }

  private TestRoute createRoute(Map<Integer, Pet> pets) {
    return testRoute(PetStoreExample.appRoute(pets));
  }

  private Map<Integer, Pet> createData() {
    Map<Integer, Pet> pets = new HashMap<Integer, Pet>();
    Pet dog = new Pet(0, "dog");
    Pet cat = new Pet(1, "cat");
    pets.put(0, dog);
    pets.put(1, cat);

    return pets;
  }
}
