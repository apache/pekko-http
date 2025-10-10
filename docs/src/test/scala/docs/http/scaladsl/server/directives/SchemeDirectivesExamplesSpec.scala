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
import pekko.http.scaladsl.server.RoutingSpec
import docs.CompileOnlySpec

class SchemeDirectivesExamplesSpec extends RoutingSpec with CompileOnlySpec {
  "example-1" in {
    // #example-1
    val route =
      extractScheme { scheme =>
        complete(s"The scheme is '$scheme'")
      }

    // tests:
    Get("https://www.example.com/") ~> route ~> check {
      responseAs[String] shouldEqual "The scheme is 'https'"
    }
    // #example-1
  }

  "example-2" in {
    // #example-2
    import org.apache.pekko
    import pekko.http.scaladsl.model._
    import pekko.http.scaladsl.model.headers.Location
    import StatusCodes.MovedPermanently

    val route =
      concat(
        scheme("http") {
          extract(_.request.uri) { uri =>
            redirect(uri.copy(scheme = "https"), MovedPermanently)
          }
        },
        scheme("https") {
          complete(s"Safe and secure!")
        })

    // tests:
    Get("http://www.example.com/hello") ~> route ~> check {
      status shouldEqual MovedPermanently
      header[Location] shouldEqual Some(Location(Uri("https://www.example.com/hello")))
    }

    Get("https://www.example.com/hello") ~> route ~> check {
      responseAs[String] shouldEqual "Safe and secure!"
    }
    // #example-2
  }
}
