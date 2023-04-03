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
