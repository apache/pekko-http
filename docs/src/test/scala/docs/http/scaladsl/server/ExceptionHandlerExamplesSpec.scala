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

package docs.http.scaladsl.server

import org.apache.pekko
import pekko.http.scaladsl.server.RoutingSpec
import docs.CompileOnlySpec
import scala.annotation.nowarn

// format: OFF

object MyExplicitExceptionHandler {

  //#explicit-handler-example
  import org.apache.pekko
  import pekko.actor.ActorSystem
  import pekko.http.scaladsl.Http
  import pekko.http.scaladsl.model._
  import StatusCodes._
  import pekko.http.scaladsl.server._
  import Directives._

  val myExceptionHandler = ExceptionHandler {
    case _: ArithmeticException =>
      extractUri { uri =>
        println(s"Request to $uri could not be handled normally")
        complete(HttpResponse(InternalServerError, entity = "Bad numbers, bad result!!!"))
      }
  }

  object MyApp extends App {

    implicit val system: ActorSystem = ActorSystem()

    val route: Route =
      handleExceptions(myExceptionHandler) {
        // ... some route structure
        null // #hide
      }

    Http().newServerAt("localhost", 8080).bind(route)
  }

  //#explicit-handler-example
}

object MyImplicitExceptionHandler {

  //#implicit-handler-example
  import org.apache.pekko
  import pekko.actor.ActorSystem
  import pekko.http.scaladsl.Http
  import pekko.http.scaladsl.model._
  import StatusCodes._
  import pekko.http.scaladsl.server._
  import Directives._

  implicit def myExceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case _: ArithmeticException =>
        extractUri { uri =>
          println(s"Request to $uri could not be handled normally")
          complete(HttpResponse(InternalServerError, entity = "Bad numbers, bad result!!!"))
        }
    }

  object MyApp extends App {

    implicit val system: ActorSystem = ActorSystem()

    val route: Route =
    // ... some route structure
      null // #hide

    Http().newServerAt("localhost", 8080).bind(route)
  }

  //#implicit-handler-example
}

@nowarn("msg=Evaluation of a constant expression results in an arithmetic error")
object ExceptionHandlerInSealExample {
  //#seal-handler-example
  import org.apache.pekko
  import pekko.http.scaladsl.model.HttpResponse
  import pekko.http.scaladsl.model.StatusCodes._
  import pekko.http.scaladsl.server._
  import Directives._

  object SealedRouteWithCustomExceptionHandler {

    implicit def myExceptionHandler: ExceptionHandler =
      ExceptionHandler {
        case _: ArithmeticException =>
          extractUri { uri =>
            println(s"Request to $uri could not be handled normally")
            complete(HttpResponse(InternalServerError, entity = "Bad numbers, bad result!!!"))
          }
      }

    val route: Route = Route.seal(
      path("divide") {
        complete((1 / 0).toString) //Will throw ArithmeticException
      }
    ) // this one takes `myExceptionHandler` implicitly

  }
  //#seal-handler-example
}

@nowarn("msg=Evaluation of a constant expression results in an arithmetic error")
object RespondWithHeaderExceptionHandlerExample {
  //#respond-with-header-exceptionhandler-example
  import org.apache.pekko
  import pekko.actor.ActorSystem
  import pekko.http.scaladsl.model.HttpResponse
  import pekko.http.scaladsl.model.StatusCodes._
  import pekko.http.scaladsl.model.headers.RawHeader
  import pekko.http.scaladsl.server._
  import Directives._
  import pekko.http.scaladsl.Http
  import RespondWithHeaderExceptionHandler.route


  object RespondWithHeaderExceptionHandler {
    def myExceptionHandler: ExceptionHandler =
      ExceptionHandler {
        case _: ArithmeticException =>
          extractUri { uri =>
            println(s"Request to $uri could not be handled normally")
            complete(HttpResponse(InternalServerError, entity = "Bad numbers, bad result!!!"))
          }
      }

    val greetingRoutes: Route = path("greetings") {
      complete("Hello!")
    }

    val divideRoutes: Route = path("divide") {
      complete((1 / 0).toString) //Will throw ArithmeticException
    }

    val route: Route =
      respondWithHeader(RawHeader("X-Outer-Header", "outer")) { // will apply, since it gets the response from the handler
        handleExceptions(myExceptionHandler) {
          greetingRoutes ~ divideRoutes ~ respondWithHeader(RawHeader("X-Inner-Header", "inner")) {
            throw new Exception("Boom!") //Will cause Internal server error,
            // only ArithmeticExceptions are handled by myExceptionHandler.
          }
        }
      }
  }

  object MyApp extends App {
    implicit val system: ActorSystem = ActorSystem()

    Http().newServerAt("localhost", 8080).bind(route)
  }
  //#respond-with-header-exceptionhandler-example
}

@nowarn("msg=Evaluation of a constant expression results in an arithmetic error")
class ExceptionHandlerExamplesSpec extends RoutingSpec with CompileOnlySpec {

  "test explicit example" in {
    // tests:
    Get() ~> handleExceptions(MyExplicitExceptionHandler.myExceptionHandler) {
      _.complete((1 / 0).toString)
    } ~> check {
      responseAs[String] shouldEqual "Bad numbers, bad result!!!"
    }
  }

  "test implicit example" in {
    import MyImplicitExceptionHandler.myExceptionHandler
    import pekko.http.scaladsl.server._
    // tests:
    Get() ~> Route.seal(ctx => ctx.complete((1 / 0).toString)) ~> check {
      responseAs[String] shouldEqual "Bad numbers, bad result!!!"
    }
  }

  "test respond with outer header only example" in {
    import pekko.http.scaladsl.model.headers.RawHeader
    import RespondWithHeaderExceptionHandlerExample.RespondWithHeaderExceptionHandler.route

    Get("/divide") ~> route ~> check {
      header("X-Outer-Header") shouldEqual Some(RawHeader("X-Outer-Header", "outer"))
      responseAs[String] shouldEqual "Bad numbers, bad result!!!"
    }
  }

  "test sealed route" in {
    import ExceptionHandlerInSealExample.SealedRouteWithCustomExceptionHandler.route

    Get("/divide") ~> route ~> check {
      responseAs[String] shouldEqual "Bad numbers, bad result!!!"
    }
  }

  "do not include possibly-sensitive details in the error response" in {
    //#no-exception-details-in-response
    import org.apache.pekko.http.scaladsl.model.IllegalHeaderException

    val route = get {
      throw IllegalHeaderException("Value of header Foo was illegal", "Found illegal value \"<script>alert('evil_xss_or_xsrf_reflection')</script>\"")
    }

    // Test:
    Get("/") ~> route ~> check {
      responseAs[String] should include("header Foo was illegal")
      responseAs[String] shouldNot include("evil_xss_or_xsrf_reflection")
    }
    //#no-exception-details-in-response
  }
}
