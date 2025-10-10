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

package docs.http.scaladsl

import org.apache.pekko
import pekko.http.impl.util.ExampleHttpContexts
import pekko.http.scaladsl.model.{ HttpRequest, HttpResponse, StatusCodes }

//#bindAndHandleSecure
import scala.concurrent.Future

//#bindAndHandleSecure

//#bindAndHandleSecure
//#bindAndHandlePlain
import org.apache.pekko
import pekko.http.scaladsl.Http
//#bindAndHandlePlain
//#bindAndHandleSecure

//#bindAndHandleSecure
import pekko.http.scaladsl.HttpsConnectionContext
//#bindAndHandleSecure

// #http2ClientWithPriorKnowledge
// #http2Client
import org.apache.pekko.http.scaladsl.Http

// #http2Client
// #http2ClientWithPriorKnowledge

//#bindAndHandlePlain
import pekko.http.scaladsl.HttpConnectionContext

//#bindAndHandlePlain

import pekko.actor.ActorSystem

object Http2Spec {
  implicit val system: ActorSystem = ActorSystem()

  {
    val asyncHandler: HttpRequest => Future[HttpResponse] =
      _ => Future.successful(HttpResponse(status = StatusCodes.ImATeapot))
    val httpsServerContext: HttpsConnectionContext = ExampleHttpContexts.exampleServerContext

    // #bindAndHandleSecure
    Http().newServerAt(interface = "localhost", port = 8443).enableHttps(httpsServerContext).bind(asyncHandler)
    // #bindAndHandleSecure
  }

  {
    import pekko.http.scaladsl.server.Route
    import pekko.http.scaladsl.server.directives.RouteDirectives.complete

    val handler: HttpRequest => Future[HttpResponse] =
      Route.toFunction(complete(StatusCodes.ImATeapot))
    // #bindAndHandlePlain
    Http().newServerAt("localhost", 8080).bind(handler)
    // #bindAndHandlePlain
  }

  {
    // #http2Client
    Http().connectionTo("localhost").toPort(8443).http2()
    // #http2Client
    // #http2ClientWithPriorKnowledge
    Http().connectionTo("localhost").toPort(8080).http2WithPriorKnowledge()
    // #http2ClientWithPriorKnowledge
  }

  {
    // #trailingHeaders
    import org.apache.pekko
    import pekko.http.scaladsl.model.ContentTypes
    import pekko.http.scaladsl.model.HttpEntity
    import pekko.http.scaladsl.model.Trailer
    import pekko.http.scaladsl.model.AttributeKeys.trailer
    import pekko.http.scaladsl.model.headers.RawHeader
    import pekko.util.ByteString

    HttpResponse(StatusCodes.OK, entity = HttpEntity.Strict(ContentTypes.`text/plain(UTF-8)`, ByteString("Tralala")))
      .addAttribute(trailer, Trailer(RawHeader("name", "value")))
    // #trailingHeaders
  }
}
