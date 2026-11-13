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

package org.apache.pekko.http.scaladsl

import org.apache.pekko
import pekko.http.scaladsl.marshalling.Marshal
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.unmarshalling.Unmarshal
import pekko.stream.scaladsl.Source
import pekko.util.ByteString
import pekko.testkit.PekkoSpec

class FormDataSpec extends PekkoSpec {
  import system.dispatcher

  val formData = FormData(Map("surname" -> "Smith", "age" -> "42"))

  "The FormData infrastructure" should {
    for {
      (encoded, expected) <- Seq(
        "key" -> List("key" -> ""),
        "a=1&key" -> List("a" -> "1", "key" -> ""),
        "a=1&&key" -> List("a" -> "1", "key" -> ""),
        "&a=1&" -> List("a" -> "1"),
        "&&" -> List.empty[(String, String)],
        "=" -> List("" -> ""),
        "=x" -> List("" -> "x"),
        "a=1&=&=x" -> List("a" -> "1", "" -> "", "" -> "x"))
      streamed <- Seq(false, true)
    } {
      s"interpret ordinary form components '$encoded' (streamed = $streamed)" in {
        val bytes = ByteString(encoded)
        val entity = if (streamed)
          HttpEntity.Chunked.fromData(ContentTypes.`application/x-www-form-urlencoded`, Source(bytes.grouped(2).toList))
        else HttpEntity(ContentTypes.`application/x-www-form-urlencoded`, bytes)
        Unmarshal(entity).to[FormData].futureValue.fields.toList shouldEqual expected
      }
    }

    "preserve explicit empty names in a model round-trip" in {
      val fields = List("" -> "", "" -> "x")
      val entity = FormData(fields: _*).toEntity
      Unmarshal(entity).to[String].futureValue shouldEqual "=&=x"
      Unmarshal(entity).to[FormData].futureValue.fields.toList shouldEqual fields
    }

    "preserve an empty form in a model round-trip" in {
      Uri.Query("") shouldEqual Uri.Query.Empty
      Unmarshal(FormData.Empty.toEntity).to[FormData].futureValue shouldEqual FormData.Empty
    }

    "discard unnamed valueless components rendered from a manually constructed form" in {
      val fields = List("a" -> "1", "" -> Uri.Query.EmptyValue, "" -> "", "key" -> Uri.Query.EmptyValue)
      val entity = FormData(fields: _*).toEntity
      Unmarshal(entity).to[String].futureValue shouldEqual "a=1&&=&key"
      Unmarshal(entity).to[FormData].futureValue.fields.toList shouldEqual List("a" -> "1", "" -> "", "key" -> "")
    }

    "retain empty components in generic URI queries" in {
      val query = Uri.Query("a&")
      query.toList shouldEqual List("a" -> "", "" -> "")
      (query.tail.value eq Uri.Query.EmptyValue) shouldBe true
      (Uri.Query("=").value eq Uri.Query.EmptyValue) shouldBe false
    }

    "properly round-trip the fields of x-www-urlencoded forms" in {
      Marshal(formData).to[HttpEntity]
        .flatMap(Unmarshal(_).to[FormData]).futureValue shouldEqual formData
    }

    "properly marshal x-www-urlencoded forms containing special chars" in {
      val entity = Marshal(FormData(Map("name" -> "Smith&Wesson"))).to[HttpEntity]
      entity.flatMap(Unmarshal(_).to[String]).futureValue shouldEqual "name=Smith%26Wesson"
      entity.flatMap(
        Unmarshal(_).to[
          HttpEntity]).futureValue.getContentType shouldEqual ContentTypes.`application/x-www-form-urlencoded`

      val entity2 = Marshal(FormData(Map("name" -> "Smith+Wesson; hopefully!"))).to[HttpEntity]
      entity2.flatMap(Unmarshal(_).to[String]).futureValue shouldEqual "name=Smith%2BWesson%3B+hopefully%21"
      entity2.flatMap(
        Unmarshal(_).to[
          HttpEntity]).futureValue.getContentType shouldEqual ContentTypes.`application/x-www-form-urlencoded`
    }

    "properly marshal empty x-www-urlencoded form" in {
      val entity = Marshal(FormData(Map.empty[String, String])).to[HttpEntity]
      entity.flatMap(Unmarshal(_).to[String]).futureValue shouldBe empty
      entity.flatMap(
        Unmarshal(_).to[
          HttpEntity]).futureValue.getContentType shouldEqual ContentTypes.`application/x-www-form-urlencoded`
    }
  }
}
