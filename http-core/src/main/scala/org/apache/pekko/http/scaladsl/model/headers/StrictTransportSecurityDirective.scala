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

package org.apache.pekko.http.scaladsl.model.headers

import org.apache.pekko.annotation.DoNotInherit

/** Not for user extension */
@DoNotInherit
sealed abstract class StrictTransportSecurityDirective
final case class IgnoredDirective(value: String) extends StrictTransportSecurityDirective
case object IncludeSubDomains extends StrictTransportSecurityDirective
final case class MaxAge(value: Long) extends StrictTransportSecurityDirective
