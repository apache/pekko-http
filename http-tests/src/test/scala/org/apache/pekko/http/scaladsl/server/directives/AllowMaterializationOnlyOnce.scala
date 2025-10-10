/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.server.directives

import java.util.concurrent.atomic.AtomicBoolean

import org.apache.pekko
import pekko.NotUsed
import pekko.stream.scaladsl.Flow

object AllowMaterializationOnlyOnce {
  def apply[T, Mat](): Flow[T, T, NotUsed] = {
    val materialized = new AtomicBoolean(false)
    Flow[T].mapMaterializedValue { mat =>
      if (materialized.compareAndSet(false, true)) {
        mat
      } else {
        throw new IllegalStateException("Substream Source cannot be materialized more than once")
      }
    }
  }
}
