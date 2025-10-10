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
import pekko.http.scaladsl.model.{ HttpEntity, HttpRequest }
import pekko.http.scaladsl.model.headers.{ Authorization, BasicHttpCredentials }
import pekko.testkit.PekkoSpec

import scala.collection.immutable

class HttpRequestDetailedStringExampleSpec extends PekkoSpec {

  // Custom string representation which includes headers
  def toDetailedString(request: HttpRequest): String = {
    import request._
    s"""HttpRequest(${_1},${_2},${_3},${_4},${_5})"""
  }

  "Include headers in custom string representation" in {

    // An HTTP header containing Personal Identifying Information
    val piiHeader = Authorization(BasicHttpCredentials("user", "password"))

    // An HTTP entity containing Personal Identifying Information
    val piiBody: HttpEntity.Strict =
      "This body contains information about [user]"

    val httpRequestWithHeadersAndBody =
      HttpRequest(entity = piiBody, headers = immutable.Seq(piiHeader))

    // Our custom string representation includes body and headers string representations...
    assert(
      toDetailedString(httpRequestWithHeadersAndBody)
        .contains(piiHeader.toString))
    assert(
      toDetailedString(httpRequestWithHeadersAndBody).contains(piiBody.toString))

    // ... while default `toString` doesn't.
    assert(!s"$httpRequestWithHeadersAndBody".contains(piiHeader.unsafeToString))
    assert(!s"$httpRequestWithHeadersAndBody".contains(piiBody.data.utf8String))
  }

}
