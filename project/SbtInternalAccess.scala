/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package sbt {
  package object access {
    import java.lang.invoke.{ MethodHandles, MethodType }

    type Aggregation = sbt.internal.Aggregation
    val Aggregation = sbt.internal.Aggregation

    private val showRunHandle = {
      val aggregationClass = Aggregation.getClass
      MethodHandles
        .privateLookupIn(aggregationClass, MethodHandles.lookup())
        .findVirtual(
          aggregationClass,
          "showRun",
          MethodType.methodType(
            Void.TYPE,
            classOf[sbt.internal.Aggregation.Complete[?]],
            classOf[sbt.internal.Aggregation.ShowConfig],
            classOf[Show[ScopedKey[?]]]))
    }
    def AggregationShowRun[T](complete: sbt.internal.Aggregation.Complete[T],
        show: sbt.internal.Aggregation.ShowConfig)(
        implicit display: Show[ScopedKey[?]]): Unit = showRunHandle.invoke(Aggregation, complete, show, display)
  }
}
