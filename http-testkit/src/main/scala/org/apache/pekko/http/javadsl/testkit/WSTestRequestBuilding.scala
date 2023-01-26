/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.testkit

import org.apache.pekko
import pekko.http.javadsl.model.ws.Message
import pekko.http.javadsl.model.{ HttpRequest, Uri }
import pekko.http.scaladsl.{ model => sm }
import pekko.stream.javadsl.Flow

import pekko.http.scaladsl.{ testkit => st }

import pekko.http.impl.util.JavaMapping.Implicits._
import scala.collection.JavaConverters._
import pekko.stream.{ scaladsl, Materializer }

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
