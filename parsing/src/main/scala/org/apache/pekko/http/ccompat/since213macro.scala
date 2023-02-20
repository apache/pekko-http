/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.ccompat

import scala.annotation.StaticAnnotation
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

object since213macro {
  def impl(c: Context)(annottees: c.Expr[Any]*): c.Expr[Any] = annottees match {
    case Seq(method) =>
      import c.universe._
      if (scala.util.Properties.versionNumberString.startsWith("2.13"))
        method
      else
        c.Expr[Nothing](EmptyTree)
    case _ =>
      throw new IllegalArgumentException("Please annotate single expressions")
  }
}
class since213 extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro since213macro.impl
}
