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
import pekko.http.scaladsl.model.StatusCodes
import pekko.http.scaladsl.server._
import docs.CompileOnlySpec

class ExecutionDirectivesExamplesSpec extends RoutingSpec with CompileOnlySpec {
  "handleExceptions" in {
    // #handleExceptions
    val divByZeroHandler = ExceptionHandler {
      case _: ArithmeticException => complete(StatusCodes.BadRequest, "You've got your arithmetic wrong, fool!")
    }
    val route =
      path("divide" / IntNumber / IntNumber) { (a, b) =>
        handleExceptions(divByZeroHandler) {
          complete(s"The result is ${a / b}")
        }
      }

    // tests:
    Get("/divide/10/5") ~> route ~> check {
      responseAs[String] shouldEqual "The result is 2"
    }
    Get("/divide/10/0") ~> route ~> check {
      status shouldEqual StatusCodes.BadRequest
      responseAs[String] shouldEqual "You've got your arithmetic wrong, fool!"
    }
    // #handleExceptions
  }
  "handleRejections" in {
    // #handleRejections
    val totallyMissingHandler = RejectionHandler.newBuilder()
      .handleNotFound { complete(StatusCodes.NotFound, "Oh man, what you are looking for is long gone.") }
      .handle { case ValidationRejection(msg, _) => complete(StatusCodes.InternalServerError, msg) }
      .result()
    val route =
      pathPrefix("handled") {
        handleRejections(totallyMissingHandler) {
          path("existing")(complete("This path exists")) ~
          path("boom")(reject(new ValidationRejection("This didn't work.")))
        }
      }

    // tests:
    Get("/handled/existing") ~> route ~> check {
      responseAs[String] shouldEqual "This path exists"
    }
    Get("/missing") ~> Route.seal(route) /* applies default handler */ ~> check {
      status shouldEqual StatusCodes.NotFound
      responseAs[String] shouldEqual "The requested resource could not be found."
    }
    Get("/handled/missing") ~> route ~> check {
      status shouldEqual StatusCodes.NotFound
      responseAs[String] shouldEqual "Oh man, what you are looking for is long gone."
    }
    Get("/handled/boom") ~> route ~> check {
      status shouldEqual StatusCodes.InternalServerError
      responseAs[String] shouldEqual "This didn't work."
    }
    // #handleRejections
  }
}
