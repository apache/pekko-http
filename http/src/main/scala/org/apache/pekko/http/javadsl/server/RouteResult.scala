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

package org.apache.pekko.http.javadsl.server

import org.apache.pekko
import pekko.http.javadsl.model.HttpResponse

trait RouteResult {}

trait Complete extends RouteResult {
  def getResponse: HttpResponse
}

trait Rejected extends RouteResult {
  def getRejections: java.lang.Iterable[Rejection]
}

object RouteResults {
  import JavaMapping.Implicits._
  import RoutingJavaMapping._

  import pekko.http.impl.util.{ JavaMapping, Util }
  import pekko.http.scaladsl.{ server => s }

  def complete(response: HttpResponse): Complete = {
    s.RouteResult.Complete(JavaMapping.toScala(response))
  }

  def rejected(rejections: java.lang.Iterable[Rejection]): Rejected = {
    s.RouteResult.Rejected(Util.convertIterable[Rejection, Rejection](rejections).map(_.asScala))
  }

}
