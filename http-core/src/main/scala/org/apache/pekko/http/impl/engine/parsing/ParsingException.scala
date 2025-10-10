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

package org.apache.pekko.http.impl.engine
package parsing

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.impl.util.SingletonException
import pekko.http.scaladsl.model.{ ErrorInfo, StatusCode, StatusCodes }

/**
 * INTERNAL API
 */
@InternalApi
private[parsing] class ParsingException(
    val status: StatusCode,
    val info: ErrorInfo) extends RuntimeException(info.formatPretty) {
  def this(status: StatusCode, summary: String) =
    this(status, ErrorInfo(if (summary.isEmpty) status.defaultMessage else summary))
  def this(summary: String) =
    this(StatusCodes.BadRequest, ErrorInfo(summary))
  def this(summary: String, detail: String) =
    this(StatusCodes.BadRequest, ErrorInfo(summary, detail))
}

/**
 * INTERNAL API
 */
@InternalApi
private[parsing] object NotEnoughDataException extends SingletonException
