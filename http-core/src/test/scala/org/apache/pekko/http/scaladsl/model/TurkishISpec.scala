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

import java.util.Locale
import org.apache.pekko.http.impl.util._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TurkishISpec extends AnyWordSpec with Matchers {
  "Model" should {
    "not suffer from turkish-i problem" in {
      val charsetCons = Class.forName("org.apache.pekko.http.scaladsl.model.HttpCharsets$").getDeclaredConstructor()
      charsetCons.setAccessible(true)

      val previousLocale = Locale.getDefault

      try {
        // recreate HttpCharsets in turkish locale
        Locale.setDefault(new Locale("tr", "TR"))

        val testString = "ISO-8859-1"
        // demonstrate difference between toRootLowerCase and toLowerCase(turkishLocale)
        (testString.toLowerCase should not).equal(testString.toRootLowerCase)

        val newCharsets = charsetCons.newInstance().asInstanceOf[HttpCharsets.type]
        newCharsets.getForKey("iso-8859-1") shouldEqual Some(newCharsets.`ISO-8859-1`)
      } finally {
        Locale.setDefault(previousLocale)
      }
    }

    "parse a header name containing a capital I in the turkish locale" in withTurkishLocale {
      HttpHeader.parse("If-Match", "\"xyzzy\"") match {
        case HttpHeader.ParsingResult.Ok(header, Nil) => header shouldBe a[headers.`If-Match`]
        case other                                    => fail(s"Expected a modelled If-Match header but got $other")
      }
    }

    "normalize a uri scheme containing a capital I in the turkish locale" in withTurkishLocale {
      Uri(scheme = "IPP", authority = Uri.Authority(Uri.Host("example.com"))).scheme shouldEqual "ipp"
    }

    "resolve a media type for an upper case file extension in the turkish locale" in withTurkishLocale {
      MediaTypes.forExtension("TIFF") shouldEqual MediaTypes.`image/tiff`
    }

    "lowercase an error header name in the turkish locale" in withTurkishLocale {
      ErrorInfo("summary", "detail").withErrorHeaderName("If-Match").errorHeaderName shouldEqual "if-match"
    }
  }

  private def withTurkishLocale(body: => Any): Unit = {
    val previousLocale = Locale.getDefault
    try {
      Locale.setDefault(new Locale("tr", "TR"))
      body
    } finally {
      Locale.setDefault(previousLocale)
    }
  }
}
