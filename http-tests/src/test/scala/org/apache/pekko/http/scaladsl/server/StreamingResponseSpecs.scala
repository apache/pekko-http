/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.server

import org.apache.pekko
import pekko.http.scaladsl.model.{ ContentTypes, HttpEntity, HttpResponse, StatusCodes }
import pekko.stream.scaladsl.Source
import pekko.util.ByteString

class StreamingResponseSpecs extends RoutingSpec {

  "streaming ByteString responses" should {
    "should render empty string if stream was empty" in {

      val src = Source.empty[ByteString]
      val entity = HttpEntity.Chunked.fromData(ContentTypes.`application/json`, src)
      val response = HttpResponse(status = StatusCodes.OK, entity = entity)
      val route = complete(response)

      Get() ~> route ~> check {
        status should ===(StatusCodes.OK)
        responseAs[String] should ===("")
      }
    }

  }
}
