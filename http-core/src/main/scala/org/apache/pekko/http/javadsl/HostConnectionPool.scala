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

package org.apache.pekko.http.javadsl

import java.util.concurrent.CompletionStage

import org.apache.pekko
import pekko.Done
import pekko.annotation.DoNotInherit
import pekko.http.impl.settings.HostConnectionPoolSetup

@DoNotInherit
abstract class HostConnectionPool private[http] {
  def setup: HostConnectionPoolSetup

  /**
   * Asynchronously triggers the shutdown of the host connection pool.
   *
   * The produced [[CompletionStage]] is fulfilled when the shutdown has been completed.
   */
  def shutdown(): CompletionStage[Done]
}
