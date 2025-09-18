/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.testkit

import scala.jdk.CollectionConverters._

import org.apache.pekko
import pekko.http.impl.util.JavaMapping.Implicits._
import pekko.http.javadsl.model.{ HttpRequest, Uri }
import pekko.http.javadsl.model.ws.Message
import pekko.http.scaladsl.{ model => sm }
import pekko.http.scaladsl.{ testkit => st }
import pekko.stream.{ scaladsl, Materializer }
import pekko.stream.javadsl.Flow

trait WSTestRequestBuilding {

  def WS[T](uri: Uri, clientSideHandler: Flow[Message, Message, T], materializer: Materializer): HttpRequest = {
    WS(uri, clientSideHandler, materializer, java.util.Collections.emptyList())
  }

  def WS[T](
      uri: Uri,
      clientSideHandler: Flow[Message, Message, T],
      materializer: Materializer,
      subprotocols: java.util.List[String]): HttpRequest = {

    val handler = scaladsl.Flow[sm.ws.Message].map(_.asJava).via(clientSideHandler).map(_.asScala)
    st.WSTestRequestBuilding.WS(uri.asScala, handler, subprotocols.asScala.toSeq)(materializer)
  }

}
