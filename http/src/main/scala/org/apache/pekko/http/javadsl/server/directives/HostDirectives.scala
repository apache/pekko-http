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

package org.apache.pekko.http.javadsl.server
package directives

import java.lang.{ Iterable => JIterable }
import java.util.function.{ Function => JFunction }
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.regex.Pattern

import scala.jdk.CollectionConverters._

import org.apache.pekko
import pekko.http.javadsl.common.RegexConverters.toScala
import pekko.http.scaladsl.server.{ Directives => D }

abstract class HostDirectives extends AttributeDirectives {

  /**
   * Extracts the hostname part of the Host request header value.
   */
  def extractHost(inner: JFunction[String, Route]): Route = RouteAdapter {
    D.extractHost { host => inner.apply(host).delegate }
  }

  /**
   * Rejects all requests with a host name different from the given ones.
   */
  def host(hostNames: JIterable[String], inner: Supplier[Route]): Route = RouteAdapter {
    D.host(hostNames.asScala.toSeq: _*) { inner.get().delegate }
  }

  /**
   * Rejects all requests with a host name different from the given one.
   */
  def host(hostName: String, inner: Supplier[Route]): Route = RouteAdapter {
    D.host(hostName) { inner.get().delegate }
  }

  /**
   * Rejects all requests for whose host name the given predicate function returns false.
   */
  def host(predicate: Predicate[String], inner: Supplier[Route]): Route = RouteAdapter {
    D.host(s => predicate.test(s)) { inner.get().delegate }
  }

  /**
   * Rejects all requests with a host name that doesn't have a prefix matching the given regular expression.
   * For all matching requests the prefix string matching the regex is extracted and passed to the inner route.
   * If the regex contains a capturing group only the string matched by this group is extracted.
   * If the regex contains more than one capturing group an IllegalArgumentException is thrown.
   */
  def host(regex: Pattern, inner: JFunction[String, Route]): Route = RouteAdapter {
    D.host(toScala(regex)) { s => inner.apply(s).delegate }
  }

}
