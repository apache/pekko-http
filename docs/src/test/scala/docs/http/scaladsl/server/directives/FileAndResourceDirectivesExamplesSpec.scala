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

package docs.http.scaladsl.server.directives

import org.apache.pekko
import pekko.http.scaladsl.marshalling.ToEntityMarshaller
import pekko.http.scaladsl.model.StatusCodes
import pekko.http.scaladsl.server.RoutingSpec
import pekko.http.scaladsl.server.directives.DirectoryListing
import pekko.http.scaladsl.server.directives.FileAndResourceDirectives.DirectoryRenderer
import docs.CompileOnlySpec

class FileAndResourceDirectivesExamplesSpec extends RoutingSpec with CompileOnlySpec {
  "getFromFile-examples" in compileOnlySpec {
    // #getFromFile-examples
    import org.apache.pekko.http.scaladsl.server.directives._
    import ContentTypeResolver.Default

    val route =
      path("logs" / Segment) { name =>
        getFromFile(s"$name.log") // uses implicit ContentTypeResolver
      }

    // tests:
    Get("/logs/example") ~> route ~> check {
      responseAs[String] shouldEqual "example file contents"
    }
    // #getFromFile-examples
  }
  "getFromResource-examples" in compileOnlySpec {
    // #getFromResource-examples
    import org.apache.pekko.http.scaladsl.server.directives._
    import ContentTypeResolver.Default

    val route =
      path("logs" / Segment) { name =>
        getFromResource(s"$name.log") // uses implicit ContentTypeResolver
      }

    // tests:
    Get("/logs/example") ~> route ~> check {
      responseAs[String] shouldEqual "example file contents"
    }
    // #getFromResource-examples
  }
  "listDirectoryContents-examples" in compileOnlySpec {
    // #listDirectoryContents-examples
    val route =
      concat(
        path("tmp") {
          listDirectoryContents("/tmp")
        },
        path("custom") {
          // implement your custom renderer here
          val renderer = new DirectoryRenderer {
            override def marshaller(renderVanityFooter: Boolean): ToEntityMarshaller[DirectoryListing] = ???
          }
          listDirectoryContents("/tmp")(renderer)
        })

    // tests:
    Get("/logs/example") ~> route ~> check {
      responseAs[String] shouldEqual "example file contents"
    }
    // #listDirectoryContents-examples
  }
  "getFromBrowseableDirectory-examples" in compileOnlySpec {
    // #getFromBrowseableDirectory-examples
    val route =
      path("tmp") {
        getFromBrowseableDirectory("/tmp")
      }

    // tests:
    Get("/tmp") ~> route ~> check {
      status shouldEqual StatusCodes.OK
    }
    // #getFromBrowseableDirectory-examples
  }
  "getFromBrowseableDirectories-examples" in compileOnlySpec {
    // #getFromBrowseableDirectories-examples
    val route =
      path("tmp") {
        getFromBrowseableDirectories("/main", "/backups")
      }

    // tests:
    Get("/tmp") ~> route ~> check {
      status shouldEqual StatusCodes.OK
    }
    // #getFromBrowseableDirectories-examples
  }
  "getFromDirectory-examples" in compileOnlySpec {
    // #getFromDirectory-examples
    val route =
      pathPrefix("tmp") {
        getFromDirectory("/tmp")
      }

    // tests:
    Get("/tmp/example") ~> route ~> check {
      responseAs[String] shouldEqual "example file contents"
    }
    // #getFromDirectory-examples
  }
  "getFromResourceDirectory-examples" in compileOnlySpec {
    // #getFromResourceDirectory-examples
    val route =
      pathPrefix("examples") {
        getFromResourceDirectory("examples")
      }

    // tests:
    Get("/examples/example-1") ~> route ~> check {
      responseAs[String] shouldEqual "example file contents"
    }
    // #getFromResourceDirectory-examples
  }

}
