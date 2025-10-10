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

package org.apache.pekko.http.javadsl.server.directives;

import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.Directives;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.apache.pekko.http.javadsl.testkit.TestRoute;

import org.junit.Test;
import org.apache.pekko.http.javadsl.unmarshalling.Unmarshaller;

import static org.apache.pekko.http.javadsl.server.Directives.entity;

public class MarshallingDirectivesTest extends JUnitRouteTest {

  @Test
  public void testEntityAsString() {
    TestRoute route = testRoute(entity(Unmarshaller.entityToString(), Directives::complete));

    HttpRequest request = HttpRequest.POST("/").withEntity("abcdef");
    route.run(request).assertStatusCode(StatusCodes.OK).assertEntity("abcdef");
  }
}
