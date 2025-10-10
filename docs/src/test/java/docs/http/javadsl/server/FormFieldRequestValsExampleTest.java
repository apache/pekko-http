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

package docs.http.javadsl.server;

import org.junit.Test;

import org.apache.pekko.http.javadsl.model.FormData;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.unmarshalling.StringUnmarshallers;
import org.apache.pekko.http.javadsl.unmarshalling.StringUnmarshaller;
import org.apache.pekko.http.javadsl.unmarshalling.Unmarshaller;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.apache.pekko.japi.Pair;

// #simple
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.formField;

// #simple
// #custom-unmarshal
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.formField;

// #custom-unmarshal

public class FormFieldRequestValsExampleTest extends JUnitRouteTest {

  @Test
  public void testFormFieldVals() {
    // #simple

    final Route route =
        formField(
            "name",
            n ->
                formField(
                    StringUnmarshallers.INTEGER,
                    "age",
                    a -> complete(String.format("Name: %s, age: %d", n, a))));

    // tests:
    final FormData formData =
        FormData.create(Pair.create("name", "Blippy"), Pair.create("age", "42"));
    final HttpRequest request = HttpRequest.POST("/").withEntity(formData.toEntity());
    testRoute(route).run(request).assertEntity("Name: Blippy, age: 42");

    // #simple
  }

  @Test
  public void testFormFieldValsUnmarshaling() {
    // #custom-unmarshal
    Unmarshaller<String, SampleId> SAMPLE_ID =
        StringUnmarshaller.sync(s -> new SampleId(Integer.valueOf(s)));

    final Route route =
        formField(SAMPLE_ID, "id", sid -> complete(String.format("SampleId: %s", sid.id)));

    // tests:
    final FormData formData = FormData.create(Pair.create("id", "1337"));
    final HttpRequest request = HttpRequest.POST("/").withEntity(formData.toEntity());
    testRoute(route).run(request).assertEntity("SampleId: 1337");

    // #custom-unmarshal
  }

  static class SampleId {
    public final int id;

    SampleId(int id) {
      this.id = id;
    }
  }
}
