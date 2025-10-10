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

import org.apache.pekko.http.javadsl.model.StatusCodes;
import static org.apache.pekko.http.javadsl.server.Directives.*;

import org.apache.pekko.http.javadsl.server.Route;

import java.util.Map;

public class PetStoreController {
  private Map<Integer, Pet> dataStore;

  public PetStoreController(Map<Integer, Pet> dataStore) {
    this.dataStore = dataStore;
  }

  public Route deletePet(int petId) {
    dataStore.remove(petId);
    return complete(StatusCodes.OK);
  }
}
