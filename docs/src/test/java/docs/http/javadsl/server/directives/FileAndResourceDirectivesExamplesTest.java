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

import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.PathMatchers;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.server.directives.DirectoryRenderer;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.junit.Ignore;
import org.junit.Test;
import scala.NotImplementedError;

import static org.apache.pekko.http.javadsl.server.PathMatchers.segment;

// #getFromFile
import static org.apache.pekko.http.javadsl.server.Directives.getFromFile;
import static org.apache.pekko.http.javadsl.server.Directives.path;

// #getFromFile
// #getFromResource
import static org.apache.pekko.http.javadsl.server.Directives.getFromResource;
import static org.apache.pekko.http.javadsl.server.Directives.path;

// #getFromResource
// #listDirectoryContents
import org.apache.pekko.http.javadsl.server.Directives;

import static org.apache.pekko.http.javadsl.server.Directives.listDirectoryContents;
import static org.apache.pekko.http.javadsl.server.Directives.path;

// #listDirectoryContents
// #getFromBrowseableDirectory
import static org.apache.pekko.http.javadsl.server.Directives.getFromBrowseableDirectory;
import static org.apache.pekko.http.javadsl.server.Directives.path;

// #getFromBrowseableDirectory
// #getFromBrowseableDirectories
import static org.apache.pekko.http.javadsl.server.Directives.getFromBrowseableDirectories;
import static org.apache.pekko.http.javadsl.server.Directives.path;

// #getFromBrowseableDirectories
// #getFromDirectory
import static org.apache.pekko.http.javadsl.server.Directives.getFromDirectory;
import static org.apache.pekko.http.javadsl.server.Directives.pathPrefix;

// #getFromDirectory
// #getFromResourceDirectory
import static org.apache.pekko.http.javadsl.server.Directives.getFromResourceDirectory;
import static org.apache.pekko.http.javadsl.server.Directives.pathPrefix;

// #getFromResourceDirectory

public class FileAndResourceDirectivesExamplesTest extends JUnitRouteTest {

  @Ignore("Compile only test")
  @Test
  public void testGetFromFile() {
    // #getFromFile
    final Route route =
        path(PathMatchers.segment("logs").slash(segment()), name -> getFromFile(name + ".log"));

    // tests:
    testRoute(route).run(HttpRequest.GET("/logs/example")).assertEntity("example file contents");
    // #getFromFile
  }

  @Ignore("Compile only test")
  @Test
  public void testGetFromResource() {
    // #getFromResource
    final Route route =
        path(PathMatchers.segment("logs").slash(segment()), name -> getFromResource(name + ".log"));

    // tests:
    testRoute(route).run(HttpRequest.GET("/logs/example")).assertEntity("example file contents");
    // #getFromResource
  }

  @Ignore("Compile only test")
  @Test
  public void testListDirectoryContents() {
    // #listDirectoryContents
    final Route route =
        Directives.concat(
            path("tmp", () -> listDirectoryContents("/tmp")),
            path(
                "custom",
                () -> {
                  // implement your custom renderer here
                  final DirectoryRenderer renderer =
                      renderVanityFooter -> {
                        throw new NotImplementedError();
                      };
                  return listDirectoryContents(renderer, "/tmp");
                }));

    // tests:
    testRoute(route).run(HttpRequest.GET("/logs/example")).assertEntity("example file contents");
    // #listDirectoryContents
  }

  @Ignore("Compile only test")
  @Test
  public void testGetFromBrowseableDirectory() {
    // #getFromBrowseableDirectory
    final Route route = path("tmp", () -> getFromBrowseableDirectory("/tmp"));

    // tests:
    testRoute(route).run(HttpRequest.GET("/tmp")).assertStatusCode(StatusCodes.OK);
    // #getFromBrowseableDirectory
  }

  @Ignore("Compile only test")
  @Test
  public void testGetFromBrowseableDirectories() {
    // #getFromBrowseableDirectories
    final Route route = path("tmp", () -> getFromBrowseableDirectories("/main", "/backups"));

    // tests:
    testRoute(route).run(HttpRequest.GET("/tmp")).assertStatusCode(StatusCodes.OK);
    // #getFromBrowseableDirectories
  }

  @Ignore("Compile only test")
  @Test
  public void testGetFromDirectory() {
    // #getFromDirectory
    final Route route = pathPrefix("tmp", () -> getFromDirectory("/tmp"));

    // tests:
    testRoute(route).run(HttpRequest.GET("/tmp/example")).assertEntity("example file contents");
    // #getFromDirectory
  }

  @Ignore("Compile only test")
  @Test
  public void testGetFromResourceDirectory() {
    // #getFromResourceDirectory
    final Route route = pathPrefix("examples", () -> getFromResourceDirectory("/examples"));

    // tests:
    testRoute(route)
        .run(HttpRequest.GET("/examples/example-1"))
        .assertEntity("example file contents");
    // #getFromResourceDirectory
  }
}
