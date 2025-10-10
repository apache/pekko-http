/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.http.javadsl.server.directives;

import org.apache.pekko.http.javadsl.model.FormData;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.unmarshalling.StringUnmarshallers;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.apache.pekko.japi.Pair;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

// #formField
import org.apache.pekko.http.javadsl.server.Directives;

import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.formField;

// #formField

// #formFieldMap
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.formFieldMap;

// #formFieldMap
// #formFieldList
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.formFieldList;

// #formFieldList
// #formFieldMultiMap
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.formFieldMultiMap;

// #formFieldMultiMap

public class FormFieldDirectivesExamplesTest extends JUnitRouteTest {

  @Test
  public void testFormField() {
    // #formField
    final Route route =
        Directives.concat(
            formField("color", color -> complete("The color is '" + color + "'")),
            formField(StringUnmarshallers.INTEGER, "id", id -> complete("The id is '" + id + "'")));

    // tests:
    final FormData formData = FormData.create(Pair.create("color", "blue"));
    testRoute(route)
        .run(HttpRequest.POST("/").withEntity(formData.toEntity()))
        .assertEntity("The color is 'blue'");

    testRoute(route)
        .run(HttpRequest.GET("/"))
        .assertStatusCode(StatusCodes.BAD_REQUEST)
        .assertEntity("Request is missing required form field 'color'");
    // #formField
  }

  @Test
  public void testFormFieldMap() {
    // #formFieldMap
    final Function<Map<String, String>, String> mapToString =
        map ->
            map.entrySet().stream()
                .map(e -> e.getKey() + " = '" + e.getValue() + "'")
                .collect(Collectors.joining(", "));

    final Route route =
        formFieldMap(fields -> complete("The form fields are " + mapToString.apply(fields)));

    // tests:
    final FormData formDataDiffKey =
        FormData.create(Pair.create("color", "blue"), Pair.create("count", "42"));
    testRoute(route)
        .run(HttpRequest.POST("/").withEntity(formDataDiffKey.toEntity()))
        .assertEntity("The form fields are color = 'blue', count = '42'");

    final FormData formDataSameKey = FormData.create(Pair.create("x", "1"), Pair.create("x", "5"));
    testRoute(route)
        .run(HttpRequest.POST("/").withEntity(formDataSameKey.toEntity()))
        .assertEntity("The form fields are x = '5'");
    // #formFieldMap
  }

  @Test
  public void testFormFieldMultiMap() {
    // #formFieldMultiMap
    final Function<Map<String, List<String>>, String> mapToString =
        map ->
            map.entrySet().stream()
                .map(e -> e.getKey() + " -> " + e.getValue().size())
                .collect(Collectors.joining(", "));

    final Route route =
        formFieldMultiMap(fields -> complete("There are form fields " + mapToString.apply(fields)));

    // test:
    final FormData formDataDiffKey =
        FormData.create(Pair.create("color", "blue"), Pair.create("count", "42"));
    testRoute(route)
        .run(HttpRequest.POST("/").withEntity(formDataDiffKey.toEntity()))
        .assertEntity("There are form fields color -> 1, count -> 1");

    final FormData formDataSameKey =
        FormData.create(Pair.create("x", "23"), Pair.create("x", "4"), Pair.create("x", "89"));
    testRoute(route)
        .run(HttpRequest.POST("/").withEntity(formDataSameKey.toEntity()))
        .assertEntity("There are form fields x -> 3");
    // #formFieldMultiMap
  }

  @Test
  public void testFormFieldList() {
    // #formFieldList
    final Function<List<Entry<String, String>>, String> listToString =
        list ->
            list.stream()
                .map(e -> e.getKey() + " = '" + e.getValue() + "'")
                .collect(Collectors.joining(", "));

    final Route route =
        formFieldList(fields -> complete("The form fields are " + listToString.apply(fields)));

    // tests:
    final FormData formDataDiffKey =
        FormData.create(Pair.create("color", "blue"), Pair.create("count", "42"));
    testRoute(route)
        .run(HttpRequest.POST("/").withEntity(formDataDiffKey.toEntity()))
        .assertEntity("The form fields are color = 'blue', count = '42'");

    final FormData formDataSameKey =
        FormData.create(Pair.create("x", "23"), Pair.create("x", "4"), Pair.create("x", "89"));
    testRoute(route)
        .run(HttpRequest.POST("/").withEntity(formDataSameKey.toEntity()))
        .assertEntity("The form fields are x = '23', x = '4', x = '89'");
    // #formFieldList
  }
}
