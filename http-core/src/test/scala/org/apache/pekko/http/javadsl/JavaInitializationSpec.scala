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

package org.apache.pekko.http.javadsl

import org.apache.pekko
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class JavaInitializationSpec extends AnyWordSpec with Matchers {

  implicit class HeaderCheck[T](self: T) {
    def =!=(expected: String) = {
      self should !==(null)
      self.toString shouldBe expected
    }
  }

  "EntityTagRange" should {
    "initializes the right field" in {
      pekko.http.scaladsl.model.headers.EntityTagRange.`*` =!= "*"
      pekko.http.javadsl.model.headers.EntityTagRanges.ALL =!= "*"
    }
  }

  "HttpEncodingRange" should {
    "initializes the right field" in {
      pekko.http.scaladsl.model.headers.HttpEncodingRange.`*` =!= "*"
      pekko.http.javadsl.model.headers.HttpEncodingRanges.ALL =!= "*"
    }
  }

  "HttpEntity" should {
    "initializes the right field" in {
      pekko.http.scaladsl.model.HttpEntity.Empty =!= "HttpEntity.Strict(none/none,0 bytes total)"
      pekko.http.javadsl.model.HttpEntities.EMPTY =!= "HttpEntity.Strict(none/none,0 bytes total)"
    }
  }

  "HttpOriginRange" should {
    "initializes the right field" in {
      pekko.http.scaladsl.model.headers.HttpOriginRange.`*` =!= "*"
      pekko.http.javadsl.model.headers.HttpOriginRanges.ALL =!= "*"
    }
  }

  "LanguageRange" should {
    "initializes the right field" in {
      pekko.http.scaladsl.model.headers.LanguageRange.`*` =!= "*" // first we touch the scala one, it should force init the Java one
      pekko.http.javadsl.model.headers.LanguageRanges.ALL =!= "*" // this is recommended and should work well too
    }
  }

  "RemoteAddress" should {
    "initializes the right field" in {
      pekko.http.scaladsl.model.RemoteAddress.Unknown =!= "unknown"
      pekko.http.javadsl.model.RemoteAddresses.UNKNOWN =!= "unknown"
    }
  }

}
