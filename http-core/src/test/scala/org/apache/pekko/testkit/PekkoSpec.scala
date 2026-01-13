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

package org.apache.pekko.testkit

import org.scalactic.{ CanEqual, TypeCheckedTripleEquals }

import language.postfixOps
import org.scalatest.{ BeforeAndAfterAll, TestSuite }
import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.event.{ Logging, LoggingAdapter }

import scala.concurrent.duration._
import scala.concurrent.Future
import com.typesafe.config.{ Config, ConfigFactory }
import pekko.dispatch.Dispatchers
import pekko.testkit.TestEvent._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

object PekkoSpec {
  val testConf: Config = ConfigFactory.parseString("""
      pekko {
        loggers = ["org.apache.pekko.testkit.TestEventListener"]
        loglevel = "WARNING"
        stdout-loglevel = "WARNING"
        actor {
          default-dispatcher {
            executor = "fork-join-executor"
            fork-join-executor {
              parallelism-min = 8
              parallelism-factor = 2.0
              parallelism-max = 8
            }
          }
        }
      }
                                                   """)

  def mapToConfig(map: Map[String, Any]): Config = {
    import scala.jdk.CollectionConverters._
    ConfigFactory.parseMap(map.asJava)
  }

  def getCallerName(clazz: Class[_]): String = {
    val s = Thread.currentThread.getStackTrace.map(_.getClassName).drop(1)
      .dropWhile(_.matches("(java.lang.Thread|.*PekkoSpec.?$|.*StreamSpec.?$)"))
    val reduced = s.lastIndexWhere(_ == clazz.getName) match {
      case -1 => s
      case z  => s.drop(z + 1)
    }
    reduced.head.replaceFirst(""".*\.""", "").replaceAll("[^a-zA-Z_0-9]", "_")
  }

}

abstract class PekkoSpec(_system: ActorSystem) extends PekkoBaseSpec(_system) with AnyWordSpecLike
    with BeforeAndAfterAll {
  def this(config: Config) = this(ActorSystem(
    PekkoSpec.getCallerName(getClass),
    ConfigFactory.load(config.withFallback(PekkoSpec.testConf))))

  def this(s: String) = this(ConfigFactory.parseString(s))

  def this(configMap: Map[String, _]) = this(PekkoSpec.mapToConfig(configMap))

  def this() = this(ActorSystem(PekkoSpec.getCallerName(getClass), PekkoSpec.testConf))

  override val invokeBeforeAllAndAfterAllEvenIfNoTestsAreExpected = true

  final override def beforeAll(): Unit = {
    startCoroner()
    atStartup()
  }

  final override def afterAll(): Unit = {
    beforeTermination()
    shutdown()
    afterTermination()
    stopCoroner()
  }
}

// FreeSpec version of PekkoSpec, unfortunately, some boilerplate is needed to make it work
abstract class PekkoFreeSpec(_system: ActorSystem) extends PekkoBaseSpec(_system) with AnyFreeSpecLike
    with BeforeAndAfterAll {
  def this(config: Config) = this(ActorSystem(
    PekkoSpec.getCallerName(getClass),
    ConfigFactory.load(config.withFallback(PekkoSpec.testConf))))

  def this(s: String) = this(ConfigFactory.parseString(s))

  def this(configMap: Map[String, _]) = this(PekkoSpec.mapToConfig(configMap))

  def this() = this(ActorSystem(PekkoSpec.getCallerName(getClass), PekkoSpec.testConf))

  override val invokeBeforeAllAndAfterAllEvenIfNoTestsAreExpected = true

  final override def beforeAll(): Unit = {
    startCoroner()
    atStartup()
  }

  final override def afterAll(): Unit = {
    beforeTermination()
    shutdown()
    afterTermination()
    stopCoroner()
  }
}

abstract class PekkoBaseSpec(_system: ActorSystem)
    extends TestKit(_system) with Matchers with WatchedByCoroner
    with TypeCheckedTripleEquals with ScalaFutures with TestSuite {

  implicit val patience: PatienceConfig = PatienceConfig(testKitSettings.DefaultTimeout.duration)

  def this(config: Config) = this(ActorSystem(
    PekkoSpec.getCallerName(getClass),
    ConfigFactory.load(config.withFallback(PekkoSpec.testConf))))

  def this(s: String) = this(ConfigFactory.parseString(s))

  def this(configMap: Map[String, _]) = this(PekkoSpec.mapToConfig(configMap))

  def this() = this(ActorSystem(PekkoSpec.getCallerName(getClass), PekkoSpec.testConf))

  val log: LoggingAdapter = Logging(system, this.getClass.asInstanceOf[Class[Any]])

  protected def atStartup(): Unit = {}

  protected def beforeTermination(): Unit = {}

  protected def afterTermination(): Unit = {}

  def spawn(dispatcherId: String = Dispatchers.DefaultDispatcherId)(body: => Unit): Unit =
    Future(body)(system.dispatchers.lookup(dispatcherId))

  override def expectedTestDuration: FiniteDuration = 60.seconds

  def muteDeadLetters(messageClasses: Class[_]*)(sys: ActorSystem = system): Unit =
    if (!sys.log.isDebugEnabled) {
      def mute(clazz: Class[_]): Unit =
        sys.eventStream.publish(Mute(DeadLettersFilter(clazz)(occurrences = Int.MaxValue)))
      if (messageClasses.isEmpty) mute(classOf[AnyRef])
      else messageClasses.foreach(mute)
    }

  // for ScalaTest === compare of Class objects
  implicit def classEqualityConstraint[A, B]: CanEqual[Class[A], Class[B]] =
    new CanEqual[Class[A], Class[B]] {
      def areEqual(a: Class[A], b: Class[B]) = a == b
    }

  implicit def setEqualityConstraint[A, T <: Set[_ <: A]]: CanEqual[Set[A], T] =
    new CanEqual[Set[A], T] {
      def areEqual(a: Set[A], b: T) = a == b
    }
}
