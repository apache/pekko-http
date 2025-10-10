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

package org.apache.pekko.http.scaladsl.server

import org.apache.pekko
import pekko.http.impl.util.WithLogCapturing
import pekko.http.scaladsl.model.HttpResponse
import pekko.http.scaladsl.testkit.ScalatestRouteTest
import pekko.testkit.TestKitBase

import org.scalatest.Suite
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

trait GenericRoutingSpec extends Matchers with Directives with ScalatestRouteTest { this: Suite =>
  val Ok = HttpResponse()
  val completeOk = complete(Ok)

  def echoComplete[T]: T => Route = { x => complete(x.toString) }
  def echoComplete2[T, U]: (T, U) => Route = { (x, y) => complete(s"$x $y") }
}

// FIXME: currently cannot use `PekkoSpec` or `PekkoSpecWithMaterializer`, see https://github.com/akka/akka-http/issues/3313
abstract class RoutingSpec extends AnyWordSpec with GenericRoutingSpec with WithLogCapturing with TestKitBase
    with ScalaFutures {
  override def testConfigSource: String =
    """
       pekko.loglevel = DEBUG
       pekko.loggers = ["org.apache.pekko.http.impl.util.SilenceAllTestEventListener"]
    """

  implicit val patience: PatienceConfig = PatienceConfig(testKitSettings.DefaultTimeout.duration)
}
