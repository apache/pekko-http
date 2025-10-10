/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.settings

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.event.LoggingAdapter
import pekko.http.scaladsl.ConnectionContext
import pekko.http.scaladsl.settings.ConnectionPoolSettings

/** INTERNAL API */
@InternalApi
private[pekko] final case class ConnectionPoolSetup(
    settings: ConnectionPoolSettings,
    connectionContext: ConnectionContext = ConnectionContext.noEncryption(),
    log: LoggingAdapter)
