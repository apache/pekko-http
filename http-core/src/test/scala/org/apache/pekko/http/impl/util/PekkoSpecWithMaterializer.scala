/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.util

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.http.scaladsl.Http
import pekko.stream.{ Materializer, SystemMaterializer }
import pekko.testkit.PekkoSpec
import pekko.testkit.EventFilter
import com.typesafe.config.ConfigFactory

abstract class PekkoSpecWithMaterializer(configOverrides: String)
    extends PekkoSpec(
      ActorSystem(
        PekkoSpecWithMaterializer.callerName(),
        ConfigFactory.load(ConfigFactory.parseString(
          configOverrides +
          """
            pekko.loglevel = DEBUG
            pekko.loggers = ["org.apache.pekko.http.impl.util.SilenceAllTestEventListener"]
          """).withFallback(PekkoSpec.testConf)))) with WithLogCapturing {

  def this() = this("")

  implicit val materializer: Materializer = SystemMaterializer(system).materializer

  override protected def beforeTermination(): Unit =
    // don't log anything during shutdown, especially not AbruptTerminationExceptions
    EventFilter.custom { case x => true }.intercept {
      // shutdown materializer first, otherwise it will only be shutdown during
      // main system guardian being shutdown which will be after the logging has
      // reverted to stdout logging that cannot be intercepted
      materializer.shutdown()
      Http().shutdownAllConnectionPools()
      // materializer shutdown is async but cannot be watched
      Thread.sleep(10)
    }
}
object PekkoSpecWithMaterializer {
  // adapted version of PekkoSpec.getCallerName that also works for `PekkoSpecWithMaterializer`
  def callerName(): String =
    Thread.currentThread.getStackTrace.map(_.getClassName).drop(1)
      .dropWhile(_.matches("(java.lang.Thread|.*PekkoSpecWithMaterializer.?$|.*StreamSpec.?$)"))
      .head.replaceFirst(""".*\.""", "").replaceAll("[^a-zA-Z_0-9]", "_")
}
