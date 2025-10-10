/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.server
package directives

import Directives._

object CompatFormField {
  def oneParameter: Directive1[Int] =
    formField("num".as[Int])
  def oneParameterRoute: Route =
    oneParameter { num =>
      complete(num.toString)
    }

  def twoParameters: Directive[(String, Int)] =
    formFields(("name", "age".as[Int]))
  def twoParametersRoute: Route =
    twoParameters { (name, age) =>
      complete(s"$name $age")
    }
}
