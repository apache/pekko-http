/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.http.javadsl.testkit

import scala.concurrent.Await
import scala.concurrent.duration._

import org.junit.jupiter.api.extension.{ AfterEachCallback, BeforeEachCallback, ExtensionContext }

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.stream.Materializer
import pekko.stream.SystemMaterializer

import com.typesafe.config.{ Config, ConfigFactory }

/**
 * A JUnit 5 (Jupiter) Extension that manages the lifecycle of an [[ActorSystem]].
 *
 * This is the JUnit 5 counterpart of [[ActorSystemResource]] which uses JUnit 4's
 * [[org.junit.rules.ExternalResource]]. It implements [[BeforeEachCallback]] and
 * [[AfterEachCallback]] to create and terminate the ActorSystem around each test method.
 *
 * Usage with `@RegisterExtension`:
 * {{{
 * @RegisterExtension
 * ActorSystemExtension systemExtension = new ActorSystemExtension("MyTest", ConfigFactory.empty());
 * }}}
 *
 * Or use it indirectly through [[JUnitJupiterRouteTest]].
 */
class ActorSystemExtension(name: String, additionalConfig: Config)
    extends BeforeEachCallback with AfterEachCallback {

  protected def config: Config = additionalConfig.withFallback(ConfigFactory.load())
  protected def createSystem(): ActorSystem = ActorSystem(name, config)

  implicit def system: ActorSystem = _system
  implicit def materializer: Materializer = SystemMaterializer.get(system).materializer

  private[this] var _system: ActorSystem = null

  override def beforeEach(context: ExtensionContext): Unit = {
    require(_system eq null, "ActorSystem already created; nested test execution is not supported")
    _system = createSystem()
  }

  override def afterEach(context: ExtensionContext): Unit = {
    if (_system ne null) {
      Await.result(_system.terminate(), 5.seconds)
      _system = null
    }
  }
}
