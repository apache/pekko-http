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

package org.apache.pekko.http.javadsl.model

import java.util

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.jdk.FutureConverters._

import com.typesafe.config.{ Config, ConfigFactory }
import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.stream.SystemMaterializer
import pekko.stream.javadsl.Source
import pekko.testkit._

import org.scalatest.{ BeforeAndAfterAll, Inside }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MultipartsSpec extends AnyWordSpec with Matchers with Inside with BeforeAndAfterAll {

  val testConf: Config = ConfigFactory.parseString("""
  pekko.event-handlers = ["pekko.testkit.TestEventListener"]
  pekko.loglevel = WARNING""")
  implicit val system: ActorSystem = ActorSystem(getClass.getSimpleName, testConf)
  val materializer = SystemMaterializer.get(system).materializer
  override def afterAll() = TestKit.shutdownActorSystem(system)

  "Explicit form-data filename encoding" should {
    "support Java interface implementations through default methods" in {
      val part = Multiparts.createFormDataBodyPartStrict("upload", HttpEntities.create("body"),
        util.Collections.singletonMap("filename", "café.txt"))
      val delegate = Multiparts.createStrictFormDataFromParts(part)
      val wrapper = new Multipart.FormData.Strict {
        override def getMediaType = delegate.getMediaType
        override def getParts = delegate.getParts
        override def getStrictParts = delegate.getStrictParts
        override def toStrict(timeout: Long, mat: org.apache.pekko.stream.Materializer) =
          delegate.toStrict(timeout, mat)
        override def toEntity(boundary: String) = delegate.toEntity(boundary)
        override def toEntity() = delegate.toEntity()
      }
      val strict: HttpEntity.Strict = wrapper.toEntity("boundary", Multipart.FormData.FilenameEncoding.PercentEncoded)
      strict.getData.utf8String should include("filename=\"caf%C3%A9.txt\"")
      (strict.getData.utf8String should not).include("filename*")

      val streamedDelegate = Multiparts.createFormDataFromParts(part)
      val streamedWrapper = new Multipart.FormData {
        override def getMediaType = streamedDelegate.getMediaType
        override def getParts = streamedDelegate.getParts
        override def toStrict(timeout: Long, mat: org.apache.pekko.stream.Materializer) =
          streamedDelegate.toStrict(timeout, mat)
        override def toEntity(boundary: String) = streamedDelegate.toEntity(boundary)
        override def toEntity() = streamedDelegate.toEntity()
      }
      val encoded = streamedWrapper.toEntity("boundary", Multipart.FormData.FilenameEncoding.PercentEncoded)
      Await.result(encoded.toStrict(1000, materializer).asScala, 1.second.dilated).getData shouldEqual strict.getData
    }
  }

  "Multiparts.createFormDataFromParts" should {
    "create a model from Multiparts.createFormDataBodyPartparts" in {
      val streamed = Multiparts.createFormDataFromParts(
        Multiparts.createFormDataBodyPart("foo", HttpEntities.create("FOO")),
        Multiparts.createFormDataBodyPart("bar", HttpEntities.create("BAR")))
      val strictCS = streamed.toStrict(1000, materializer)
      val strict = Await.result(strictCS.asScala, 1.second.dilated)

      strict shouldEqual org.apache.pekko.http.scaladsl.model.Multipart.FormData(
        Map("foo" -> org.apache.pekko.http.scaladsl.model.HttpEntity("FOO"),
          "bar" -> org.apache.pekko.http.scaladsl.model.HttpEntity("BAR")))
    }
    "create a model from Multiparts.createFormDataFromSourceParts" in {
      val streamed = Multiparts.createFormDataFromSourceParts(Source.from(util.Arrays.asList(
        Multiparts.createFormDataBodyPart("foo", HttpEntities.create("FOO")),
        Multiparts.createFormDataBodyPart("bar", HttpEntities.create("BAR")))))
      val strictCS = streamed.toStrict(1000, materializer)
      val strict = Await.result(strictCS.asScala, 1.second.dilated)
      strict shouldEqual org.apache.pekko.http.scaladsl.model.Multipart.FormData(
        Map("foo" -> org.apache.pekko.http.scaladsl.model.HttpEntity("FOO"),
          "bar" -> org.apache.pekko.http.scaladsl.model.HttpEntity("BAR")))
    }
  }

  "Multiparts.createFormDataFromFields" should {
    "create a model from a map of fields" in {
      val fields = new util.HashMap[String, HttpEntity.Strict]
      fields.put("foo", HttpEntities.create("FOO"))
      val streamed = Multiparts.createFormDataFromFields(fields)
      val strictCS = streamed.toStrict(1000, materializer)
      val strict = Await.result(strictCS.asScala, 1.second.dilated)

      strict shouldEqual org.apache.pekko.http.scaladsl.model.Multipart.FormData(
        Map("foo" -> org.apache.pekko.http.scaladsl.model.HttpEntity("FOO")))
    }
  }

  "Multiparts.createStrictFormDataFromParts" should {
    "create a strict model from Multiparts.createFormDataBodyPartStrict parts" in {
      val streamed = Multiparts.createStrictFormDataFromParts(
        Multiparts.createFormDataBodyPartStrict("foo", HttpEntities.create("FOO")),
        Multiparts.createFormDataBodyPartStrict("bar", HttpEntities.create("BAR")))
      val strict = streamed

      strict shouldEqual org.apache.pekko.http.scaladsl.model.Multipart.FormData(
        Map("foo" -> org.apache.pekko.http.scaladsl.model.HttpEntity("FOO"),
          "bar" -> org.apache.pekko.http.scaladsl.model.HttpEntity("BAR")))
    }
  }
}
