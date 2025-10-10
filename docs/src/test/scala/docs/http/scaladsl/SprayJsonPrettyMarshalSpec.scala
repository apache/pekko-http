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

package docs.http.scaladsl

import org.apache.pekko
import pekko.http.scaladsl.server.Directives
import pekko.http.scaladsl.server.RoutingSpec
import docs.CompileOnlySpec

class SprayJsonPrettyMarshalSpec extends RoutingSpec with CompileOnlySpec {

  "spray-json example" in {
    // #example
    import pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
    import spray.json._

    // domain model
    final case class PrettyPrintedItem(name: String, id: Long)

    object PrettyJsonFormatSupport {
      import DefaultJsonProtocol._
      implicit val printer: JsonPrinter = PrettyPrinter
      implicit val prettyPrintedItemFormat: RootJsonFormat[PrettyPrintedItem] = jsonFormat2(PrettyPrintedItem.apply)
    }

    // use it wherever json (un)marshalling is needed
    class MyJsonService extends Directives {
      import PrettyJsonFormatSupport._

      // format: OFF
      val route =
        get {
          pathSingleSlash {
            complete {
              PrettyPrintedItem("pekko", 42) // will render as JSON
            }
          }
        }
      // format: ON
    }

    val service = new MyJsonService

    // verify the pretty printed JSON
    Get("/") ~> service.route ~> check {
      responseAs[String] shouldEqual
      """{""" + "\n" +
      """  "id": 42,""" + "\n" +
      """  "name": "pekko"""" + "\n" +
      """}"""
    }
    // #example
  }
}
