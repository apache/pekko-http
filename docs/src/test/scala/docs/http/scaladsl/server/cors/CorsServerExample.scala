/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package docs.http.scaladsl.server.cors

// #cors-server-example
import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.scaladsl.Behaviors
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model.StatusCodes
import pekko.http.scaladsl.server.Directives._
import pekko.http.scaladsl.server._

import scala.io.StdIn
import scala.util.{ Failure, Success }

object CorsServerExample {
  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "cors-server")
    import system.executionContext

    val futureBinding = Http().newServerAt("localhost", 8080).bind(route)

    futureBinding.onComplete {
      case Success(_) =>
        system.log.info("Server online at http://localhost:8080/\nPress RETURN to stop...")
      case Failure(exception) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", exception)
        system.terminate()
    }

    StdIn.readLine() // let it run until user presses return
    futureBinding
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }

  def route: Route = {
    import pekko.http.cors.scaladsl.CorsDirectives._

    // Your CORS settings are loaded from `application.conf`

    // Your rejection handler
    val rejectionHandler = corsRejectionHandler.withFallback(RejectionHandler.default)

    // Your exception handler
    val exceptionHandler = ExceptionHandler { case e: NoSuchElementException =>
      complete(StatusCodes.NotFound -> e.getMessage)
    }

    // Combining the two handlers only for convenience
    val handleErrors = handleRejections(rejectionHandler) & handleExceptions(exceptionHandler)

    // Note how rejections and exceptions are handled *before* the CORS directive (in the inner route).
    // This is required to have the correct CORS headers in the response even when an error occurs.
    handleErrors {
      cors() {
        handleErrors {
          path("ping") {
            complete("pong")
          } ~
          path("pong") {
            failWith(new NoSuchElementException("pong not found, try with ping"))
          }
        }
      }
    }
  }
}
// #cors-server-example
