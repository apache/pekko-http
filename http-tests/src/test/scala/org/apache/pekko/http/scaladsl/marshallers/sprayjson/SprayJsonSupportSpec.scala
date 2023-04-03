/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.marshallers.sprayjson

import java.lang.StringBuilder

import org.apache.pekko
import pekko.http.scaladsl.marshallers.{ Employee, JsonSupportSpec }
import pekko.http.scaladsl.marshalling.ToEntityMarshaller
import pekko.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import spray.json.{ DefaultJsonProtocol, JsValue, JsonPrinter, PrettyPrinter }

import scala.collection.immutable.ListMap
import spray.json.RootJsonFormat

class SprayJsonSupportSpec extends JsonSupportSpec {
  object EmployeeJsonProtocol extends DefaultJsonProtocol {
    implicit val employeeFormat: RootJsonFormat[Employee] = jsonFormat5(Employee.apply)
  }
  import EmployeeJsonProtocol._

  implicit val orderedFieldPrint: JsonPrinter = new PrettyPrinter {
    override protected def printObject(members: Map[String, JsValue], sb: StringBuilder, indent: Int): Unit =
      super.printObject(ListMap(members.toSeq.sortBy(_._1): _*), sb, indent)
  }

  implicit def marshaller: ToEntityMarshaller[Employee] = SprayJsonSupport.sprayJsonMarshaller[Employee]
  implicit def unmarshaller: FromEntityUnmarshaller[Employee] = SprayJsonSupport.sprayJsonUnmarshaller[Employee]
}
