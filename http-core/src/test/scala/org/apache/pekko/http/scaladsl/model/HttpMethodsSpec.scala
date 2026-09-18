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

package org.apache.pekko.http.scaladsl.model

import org.scalatest.wordspec.AnyWordSpec

class HttpMethodsSpec extends AnyWordSpec {
  "HttpMethods.getForKeyCaseInsensitive()" must {
    "return HttpMethods.CONNECT" in {
      assert(HttpMethods.getForKeyCaseInsensitive("CONNECT") == Option(HttpMethods.CONNECT))
    }
    "return HttpMethods.DELETE" in {
      assert(HttpMethods.getForKeyCaseInsensitive("Delete") == Option(HttpMethods.DELETE))
    }
    "return HttpMethods.GET" in {
      assert(HttpMethods.getForKeyCaseInsensitive("get") == Option(HttpMethods.GET))
    }
    "return HttpMethods.HEAD" in {
      assert(HttpMethods.getForKeyCaseInsensitive("HeaD") == Option(HttpMethods.HEAD))
    }
    "return HttpMethods.OPTIONS" in {
      assert(HttpMethods.getForKeyCaseInsensitive("oPtIoNs") == Option(HttpMethods.OPTIONS))
    }
    "return HttpMethods.QUERY" in {
      assert(HttpMethods.getForKeyCaseInsensitive("query") == Option(HttpMethods.QUERY))
    }
  }

  "HttpMethod.custom" must {
    "accept any token" in {
      assert(HttpMethod.custom("PROPFIND").value == "PROPFIND")
      assert(HttpMethod.custom("M-SEARCH").value == "M-SEARCH")
      // every tchar: ALPHA, DIGIT and the sixteen special characters RFC 9110 allows in a token
      val allTchars = ('A' to 'Z').mkString + ('a' to 'z').mkString + ('0' to '9').mkString + "!#$%&'*+-.^_`|~"
      assert(HttpMethod.custom(allTchars).value == allTchars)
    }
    "reject a name that is not a token" in {
      // the name is written into the request line as given, ahead of the request target
      val e = intercept[IllegalArgumentException](
        HttpMethod.custom("GET /admin HTTP/1.1\u000d\u000aX-Injected: 1\u000d\u000aFOO"))
      assert(e.getMessage.contains("found U+0020 at index 3"))
      intercept[IllegalArgumentException](HttpMethod.custom("GET\u000d\u000a"))
      intercept[IllegalArgumentException](HttpMethod.custom("GET\u0009"))
      intercept[IllegalArgumentException](HttpMethod.custom("GET\u0000"))
      intercept[IllegalArgumentException](HttpMethod.custom("GET\u007f"))
      // delimiters are visible ASCII but not tchar
      intercept[IllegalArgumentException](HttpMethod.custom("GET/"))
      intercept[IllegalArgumentException](HttpMethod.custom("GET:"))
      intercept[IllegalArgumentException](HttpMethod.custom("G\u010dT"))
      intercept[IllegalArgumentException](HttpMethod.custom(""))
      // every overload validates
      intercept[IllegalArgumentException](HttpMethod.custom("GET ", safe = false, idempotent = false,
        requestEntityAcceptance = RequestEntityAcceptance.Expected, contentLengthAllowed = true))
    }
  }

  "HttpMethods.QUERY" must {
    "be safe per RFC 10008" in {
      assert(HttpMethods.QUERY.isSafe)
    }
    "be idempotent per RFC 10008" in {
      assert(HttpMethods.QUERY.isIdempotent)
    }
    "expect a request entity per RFC 10008" in {
      assert(HttpMethods.QUERY.requestEntityAcceptance == RequestEntityAcceptance.Expected)
    }
  }
}
