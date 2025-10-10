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

package org.apache.pekko.http.javadsl.testkit

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

import org.junit.{ Assert, Rule }
import org.junit.rules.ExternalResource

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.event.Logging
import pekko.http.javadsl.model.HttpRequest
import pekko.http.javadsl.server._
import pekko.stream.Materializer
import pekko.stream.SystemMaterializer

import com.typesafe.config.{ Config, ConfigFactory }

/**
 * A RouteTest that uses JUnit assertions. ActorSystem and Materializer are provided as an [[org.junit.rules.ExternalResource]]
 * and their lifetime is automatically managed.
 */
abstract class JUnitRouteTestBase extends RouteTest {
  protected def systemResource: ActorSystemResource
  implicit def system: ActorSystem = systemResource.system
  implicit def materializer: Materializer = systemResource.materializer

  protected def createTestRouteResultAsync(request: HttpRequest, result: Future[RouteResult]): TestRouteResult =
    new TestRouteResult(result, awaitDuration)(system.dispatcher, materializer) {
      protected def assertEquals(expected: AnyRef, actual: AnyRef, message: String): Unit =
        reportDetails { Assert.assertEquals(message, expected, actual) }

      protected def assertEquals(expected: Int, actual: Int, message: String): Unit =
        Assert.assertEquals(message, expected, actual)

      protected def assertTrue(predicate: Boolean, message: String): Unit =
        Assert.assertTrue(message, predicate)

      protected def fail(message: String): Unit = {
        Assert.fail(message)
        throw new IllegalStateException("Assertion should have failed")
      }

      def reportDetails[T](block: => T): T = {
        try block
        catch {
          case t: Throwable => throw new AssertionError(t.getMessage + "\n" +
              "  Request was:      " + request + "\n" +
              "  Route result was: " + result + "\n", t)
        }
      }
    }
}
abstract class JUnitRouteTest extends JUnitRouteTestBase {
  protected def additionalConfig: Config = ConfigFactory.empty()

  private[this] val _systemResource = new ActorSystemResource(Logging.simpleName(getClass), additionalConfig)
  @Rule
  protected def systemResource: ActorSystemResource = _systemResource
}

class ActorSystemResource(name: String, additionalConfig: Config) extends ExternalResource {
  protected def config = additionalConfig.withFallback(ConfigFactory.load())
  protected def createSystem(): ActorSystem = ActorSystem(name, config)

  implicit def system: ActorSystem = _system
  implicit def materializer: Materializer = SystemMaterializer.get(system).materializer

  private[this] var _system: ActorSystem = null

  override def before(): Unit = {
    require(_system eq null)
    _system = createSystem()
  }
  override def after(): Unit = {
    Await.result(_system.terminate(), 5.seconds)
    _system = null
  }
}
