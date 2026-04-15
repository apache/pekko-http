/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.http.javadsl.testkit

import com.typesafe.config.{ Config, ConfigFactory }

/**
 * A RouteTest that uses JUnit 5 (Jupiter) assertions.
 *
 * @deprecated Use [[JUnitJupiterRouteTestBase]] instead. The class name has been updated
 *             to follow the standard JUnit 5 naming convention.
 */
@deprecated("Use JUnitJupiterRouteTestBase instead", "1.1.0")
abstract class JUnit5RouteTestBase extends JUnitJupiterRouteTestBase

/**
 * JUnit 5 (Jupiter) route test base class.
 *
 * @deprecated Use [[JUnitJupiterRouteTest]] instead. The class name has been updated
 *             to follow the standard JUnit 5 naming convention.
 */
@deprecated("Use JUnitJupiterRouteTest instead", "1.1.0")
abstract class JUnit5RouteTest extends JUnitJupiterRouteTest
