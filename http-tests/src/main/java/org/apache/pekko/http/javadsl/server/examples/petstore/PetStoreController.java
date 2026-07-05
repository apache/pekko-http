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

import static org.apache.pekko.http.javadsl.server.Directives.*;

import java.util.Map;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.Route;

/** A simple controller for the pet store example. */
public class PetStoreController {
  private Map<Integer, Pet> dataStore;

  /**
   * Creates a new PetStoreController.
   *
   * @param dataStore the backing data store for pets
   */
  public PetStoreController(Map<Integer, Pet> dataStore) {
    this.dataStore = dataStore;
  }

  /**
   * Deletes a pet by its id.
   *
   * @param petId the id of the pet to delete
   * @return a route completing with OK status
   */
  public Route deletePet(int petId) {
    dataStore.remove(petId);
    return complete(StatusCodes.OK);
  }
}
