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

package org.apache.pekko.http.ccompat

import scala.collection.immutable.StringOps

object ImplicitUtils {
  // Scala 3 resolves implicit conversions differently than Scala 2,
  // in some instances overriding StringOps operations, like *.
  implicit class Scala3StringOpsFix(string: String) {
    def *(amount: Int): String = StringOps(string) * amount
  }
}
