/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

import sbt.{ Def, _ }
import Keys._

object NoScala3 extends AutoPlugin {
  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    crossScalaVersions := crossScalaVersions.value.filterNot(_.startsWith("3.")))
}
