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

package org.apache.pekko.http.impl.util

import java.io.{ OutputStream, PrintStream }

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.event.Logging
import pekko.event.Logging._
import pekko.testkit.{ EventFilter, TestEventListener }
import org.scalatest.{ Failed, Outcome, SuiteMixin, TestSuite }

/**
 * Mixin this trait to a test to make log lines appear only when the test failed.
 */
trait WithLogCapturing extends SuiteMixin { this: TestSuite =>
  implicit def system: ActorSystem

  /**
   * Can be overridden to return true to check that no warning or error messages are logged during
   * the execution of the test
   */
  protected def failOnSevereMessages: Boolean = false

  /**
   * We expect a severe message but the message should contain this text. If there are any other severe messages,
   * the test will fail.
   */
  protected val expectSevereLogsOnlyToMatch: Option[String] = None

  /**
   * Can be overridden to adapt which events should be considered as severe if `failOnSevereMessages` is
   * enabled.
   */
  protected def isSevere(event: LogEvent): Boolean =
    event.level <= Logging.WarningLevel // yeah, lower levels are more severe

  abstract override def withFixture(test: NoArgTest): Outcome = {
    // When filtering just collects events into this var (yeah, it's a hack to do that in a filter).
    // We assume that the filter will always ever be used from a single actor, so a regular var should be fine.
    var events: List[LogEvent] = Nil

    object LogEventCollector extends EventFilter(Int.MaxValue) {
      override protected def matches(event: Logging.LogEvent): Boolean = {
        events ::= event
        true
      }
    }

    val myLogger = Logging(system, classOf[WithLogCapturing])
    val res = LogEventCollector.intercept {
      myLogger.debug(s"Logging started for test [${test.name}]")
      val r = test()
      myLogger.debug(s"Logging finished for test [${test.name}]")
      r
    }

    def flushLog(): Unit = {
      println(s"--> [${Console.BLUE}${test.name}${Console.RESET}] Start of log messages of test that [$res]")
      val logger = new StdOutLogger {}
      withPrefixedOut("| ") {
        events.reverse.foreach { ev =>
          if (ev.level == WarningLevel) print(Console.YELLOW)
          else if (ev.level == ErrorLevel) print(Console.RED)
          else if (isSevere(ev)) print(Console.YELLOW) // also mark other severe messages with YELLOW
          logger.print(ev)
          if (ev.level <= WarningLevel) print(Console.RESET)
        }
      }
      println(s"<-- [${Console.BLUE}${test.name}${Console.RESET}] End of log messages of test that [$res]")
    }

    if (!(res.isSucceeded || res.isPending)) {
      flushLog()
      res
    } else if (failOnSevereMessages && events.exists(isSevere)) {
      val stats = events.groupBy(_.level).view.mapValues(_.size).toMap.withDefaultValue(0)
      flushLog()

      Failed(new AssertionError(
        s"No severe log messages should be emitted during test run but got [${stats(
            Logging.WarningLevel)}] warnings and [${stats(Logging.ErrorLevel)}] errors (see marked lines above)"))
    } else if (expectSevereLogsOnlyToMatch.nonEmpty) {
      val severeEvents = events.filter(isSevere(_))
      val matchingEvents = severeEvents.filter(_.message.toString.contains(expectSevereLogsOnlyToMatch.get))
      if (severeEvents.isEmpty || matchingEvents != severeEvents) {
        val stats = events.groupBy(_.level).view.mapValues(_.size).toMap.withDefaultValue(0)
        flushLog()

        Failed(new AssertionError(
          s"Expected an error during test run but got unexpected results - got [${
              stats(
                Logging.WarningLevel)
            }] warnings and [${stats(Logging.ErrorLevel)}] errors (see marked lines above)"))
      } else res
    } else res

  }

  /** Adds a prefix to every line printed out during execution of the thunk. */
  private def withPrefixedOut[T](prefix: String)(thunk: => T): T = {
    val oldOut = Console.out
    val prefixingOut =
      new PrintStream(new OutputStream {
        override def write(b: Int): Unit = oldOut.write(b)
      }) {
        override def println(x: Any): Unit =
          oldOut.println(prefix + String.valueOf(x).replaceAllLiterally("\n", s"\n$prefix"))
      }

    Console.withOut(prefixingOut) {
      thunk
    }
  }
}

/**
 * An adaption of TestEventListener that never prints debug logs itself. Use together with [[pekko.http.impl.util.WithLogCapturing]].
 * It allows to enable noisy DEBUG logging for tests and silence pre/post test DEBUG output completely while still showing
 * test-specific DEBUG output selectively
 */
class DebugLogSilencingTestEventListener extends TestEventListener {
  override def print(event: Any): Unit = event match {
    case d: Debug => // ignore
    case _        => super.print(event)
  }
}

/**
 * An adaption of TestEventListener that does not print out any logs. Use together with [[pekko.http.impl.util.WithLogCapturing]].
 * It allows to enable noisy logging for tests and silence pre/post test log output completely while still showing
 * test-specific log output selectively on failures.
 */
class SilenceAllTestEventListener extends TestEventListener {
  override def print(event: Any): Unit = ()
}
