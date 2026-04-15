/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.http.javadsl.testkit

import scala.concurrent.Future

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.extension.RegisterExtension

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.event.Logging
import pekko.http.javadsl.model.HttpRequest
import pekko.http.javadsl.server._
import pekko.stream.Materializer

import com.typesafe.config.{ Config, ConfigFactory }

/**
 * A RouteTest that uses JUnit Jupiter assertions. ActorSystem and Materializer lifecycle
 * is managed via [[ActorSystemExtension]], the JUnit Jupiter equivalent of
 * [[org.junit.rules.ExternalResource]].
 *
 * This is the JUnit Jupiter counterpart of [[JUnitRouteTest]]. Migrate your tests from
 * `extends JUnitRouteTest` to `extends JUnitJupiterRouteTest` to use JUnit Jupiter annotations
 * and assertions.
 */
abstract class JUnitJupiterRouteTestBase extends RouteTest {
  protected def systemExtension: ActorSystemExtension
  implicit def system: ActorSystem = systemExtension.system
  implicit def materializer: Materializer = systemExtension.materializer

  protected def createTestRouteResultAsync(request: HttpRequest, result: Future[RouteResult]): TestRouteResult =
    new TestRouteResult(result, awaitDuration)(system.dispatcher, materializer) {
      protected def assertEquals(expected: AnyRef, actual: AnyRef, message: String): Unit =
        reportDetails { Assertions.assertEquals(expected, actual, message) }

      protected def assertEquals(expected: Int, actual: Int, message: String): Unit =
        Assertions.assertEquals(expected, actual, message)

      protected def assertTrue(predicate: Boolean, message: String): Unit =
        Assertions.assertTrue(predicate, message)

      protected def fail(message: String): Unit = {
        Assertions.fail[Unit](message)
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

/**
 * JUnit Jupiter route test base class. Extend this class to write HTTP route tests
 * using JUnit Jupiter annotations (`@Test`, `@BeforeEach`, `@AfterEach`, etc.).
 *
 * The ActorSystem is managed automatically via [[ActorSystemExtension]]. Override
 * `additionalConfig` to provide custom configuration.
 *
 * Example usage:
 * {{{
 * import org.junit.jupiter.api.Test;
 * import static org.junit.jupiter.api.Assertions.*;
 *
 * public class MyRouteTest extends JUnitJupiterRouteTest {
 *     @Test
 *     public void testHello() {
 *         testRoute(get(() -> complete("hello")))
 *             .run(HttpRequest.GET("/"))
 *             .assertStatusCode(StatusCodes.OK);
 *     }
 * }
 * }}}
 */
abstract class JUnitJupiterRouteTest extends JUnitJupiterRouteTestBase {
  protected def additionalConfig: Config = ConfigFactory.empty()

  @RegisterExtension
  protected val systemExtension: ActorSystemExtension =
    new ActorSystemExtension(Logging.simpleName(getClass), additionalConfig)
}
