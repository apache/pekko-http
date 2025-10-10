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

package org.apache.pekko.http.scaladsl.marshallers.sprayjson

import java.lang.StringBuilder

import scala.collection.immutable.ListMap

import spray.json.{ DefaultJsonProtocol, JsValue, JsonPrinter, PrettyPrinter }
import spray.json.RootJsonFormat

import org.apache.pekko
import pekko.http.scaladsl.marshallers.{ Employee, JsonSupportSpec }
import pekko.http.scaladsl.marshalling.ToEntityMarshaller
import pekko.http.scaladsl.unmarshalling.FromEntityUnmarshaller

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
