/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.server.directives

import org.apache.pekko
import pekko.http.scaladsl.model.StatusCodes
import pekko.http.scaladsl.model.headers.{ Accept, RawHeader }
import pekko.http.scaladsl.server.RoutingSpec

/**
 * Has to exercise the entire stack, thus uses ~!> (not reproducible using just ~>).
 */
class IllegalHeadersIntegrationSpec extends RoutingSpec {

  "Illegal content type in request" should {
    val route = extractRequest { req =>
      complete(s"Accept:${req.header[Accept]}, byName:${req.headers.find(_.is("accept"))}")
    }

    // see: https://github.com/apache/incubator-pekko-http/issues/1072
    "not StackOverflow but be rejected properly" in {
      val theIllegalHeader = RawHeader("Accept", "*/xml")
      Get().addHeader(theIllegalHeader) ~!> route ~> check {
        status should ===(StatusCodes.OK)
        responseAs[String] shouldEqual "Accept:None, byName:Some(accept: */xml)"
      }
    }
  }

}
