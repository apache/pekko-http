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

package org.apache.pekko.http.javadsl.unmarshalling

import java.util.concurrent.CompletionStage
import java.util.Optional

import org.apache.pekko
import pekko.actor.ClassicActorSystemProvider
import pekko.annotation.InternalApi
import pekko.http.impl.model.JavaQuery
import pekko.http.impl.util.JavaMapping
import pekko.http.impl.util.JavaMapping.Implicits._
import pekko.http.{ javadsl => jm }
import jm.model._
import pekko.http.scaladsl.model.{ ContentTypeRange, ContentTypes }
import pekko.http.scaladsl.unmarshalling
import pekko.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import pekko.http.scaladsl.unmarshalling.Unmarshaller.EnhancedFromEntityUnmarshaller
import pekko.http.scaladsl.util.FastFuture
import pekko.stream.{ Materializer, SystemMaterializer }
import pekko.util.ByteString

import scala.annotation.nowarn
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._

object Unmarshaller extends pekko.http.javadsl.unmarshalling.Unmarshallers {
  implicit def fromScala[A, B](scalaUnmarshaller: unmarshalling.Unmarshaller[A, B]): Unmarshaller[A, B] =
    scalaUnmarshaller

  /**
   * Safe downcasting of the output type of the unmarshaller to a superclass.
   *
   * Unmarshaller is covariant in B, i.e. if B2 is a subclass of B1,
   * then Unmarshaller[X,B2] is OK to use where Unmarshaller[X,B1] is expected.
   */
  private def downcast[A, B1, B2 <: B1](m: Unmarshaller[A, B2], target: Class[B1]): Unmarshaller[A, B1] =
    m.asInstanceOf[Unmarshaller[A, B1]]

  /**
   * Creates an unmarshaller from an asynchronous Java function.
   */
  override def async[A, B](f: java.util.function.Function[A, CompletionStage[B]]): Unmarshaller[A, B] =
    unmarshalling.Unmarshaller[A, B] { ctx => a => f(a).asScala }

  /**
   * Creates an unmarshaller from a Java function.
   */
  override def sync[A, B](f: java.util.function.Function[A, B]): Unmarshaller[A, B] =
    unmarshalling.Unmarshaller[A, B] { ctx => a => scala.concurrent.Future.successful(f.apply(a)) }

  // format: OFF
  def entityToByteString: Unmarshaller[HttpEntity, ByteString] = unmarshalling.Unmarshaller.byteStringUnmarshaller
  def entityToByteArray: Unmarshaller[HttpEntity, Array[Byte]] = unmarshalling.Unmarshaller.byteArrayUnmarshaller
  def entityToCharArray: Unmarshaller[HttpEntity, Array[Char]] = unmarshalling.Unmarshaller.charArrayUnmarshaller
  def entityToString: Unmarshaller[HttpEntity, String]         = unmarshalling.Unmarshaller.stringUnmarshaller

  def entityToWwwUrlEncodedFormData: Unmarshaller[HttpEntity, FormData] = unmarshalling.Unmarshaller.defaultUrlEncodedFormDataUnmarshaller.map(scalaFormData => new FormData(JavaQuery(scalaFormData.fields)))

  def entityToMultipartByteRangesUnmarshaller: Unmarshaller[HttpEntity, Multipart.ByteRanges] = downcast(unmarshalling.MultipartUnmarshallers.defaultMultipartByteRangesUnmarshaller, classOf[Multipart.ByteRanges])
  def entityToMultipartFormData: Unmarshaller[HttpEntity, Multipart.FormData]                 = downcast(unmarshalling.MultipartUnmarshallers.multipartFormDataUnmarshaller, classOf[Multipart.FormData])
  // format: ON

  val requestToEntity: Unmarshaller[HttpRequest, RequestEntity] =
    unmarshalling.Unmarshaller.strict[HttpRequest, RequestEntity](_.entity)

  def forMediaType[B](t: MediaType, um: Unmarshaller[HttpEntity, B]): Unmarshaller[HttpEntity, B] = {
    unmarshalling.Unmarshaller.withMaterializer[HttpEntity, B] { implicit ex => implicit mat => jEntity =>
      {
        val entity = jEntity.asScala
        val mediaType = t.asScala
        if (entity.contentType == ContentTypes.NoContentType || mediaType.matches(entity.contentType.mediaType)) {
          um.asScala(entity)
        } else FastFuture.failed(
          pekko.http.scaladsl.unmarshalling.Unmarshaller.UnsupportedContentTypeException(Some(entity.contentType),
            ContentTypeRange(t.toRange.asScala)))
      }
    }
  }

  def forMediaTypes[B](
      types: java.lang.Iterable[MediaType], um: Unmarshaller[HttpEntity, B]): Unmarshaller[HttpEntity, B] = {
    val u: FromEntityUnmarshaller[B] = um.asScala
    val theTypes: Seq[pekko.http.scaladsl.model.ContentTypeRange] = types.asScala.toSeq.map { media =>
      pekko.http.scaladsl.model.ContentTypeRange(media.asScala)
    }
    u.forContentTypes(theTypes: _*)
  }

  override def firstOf[A, B](u1: Unmarshaller[A, B], u2: Unmarshaller[A, B]): Unmarshaller[A, B] = {
    unmarshalling.Unmarshaller.firstOf(u1.asScala, u2.asScala)
  }

  override def firstOf[A, B](
      u1: Unmarshaller[A, B], u2: Unmarshaller[A, B], u3: Unmarshaller[A, B]): Unmarshaller[A, B] = {
    unmarshalling.Unmarshaller.firstOf(u1.asScala, u2.asScala, u3.asScala)
  }

  override def firstOf[A, B](u1: Unmarshaller[A, B], u2: Unmarshaller[A, B], u3: Unmarshaller[A, B],
      u4: Unmarshaller[A, B]): Unmarshaller[A, B] = {
    unmarshalling.Unmarshaller.firstOf(u1.asScala, u2.asScala, u3.asScala, u4.asScala)
  }

  override def firstOf[A, B](u1: Unmarshaller[A, B], u2: Unmarshaller[A, B], u3: Unmarshaller[A, B],
      u4: Unmarshaller[A, B], u5: Unmarshaller[A, B]): Unmarshaller[A, B] = {
    unmarshalling.Unmarshaller.firstOf(u1.asScala, u2.asScala, u3.asScala, u4.asScala, u5.asScala)
  }

  @nowarn("msg=mi in method adaptInputToJava is never used")
  private implicit def adaptInputToJava[JI, SI, O](um: unmarshalling.Unmarshaller[SI, O])(
      implicit mi: JavaMapping[JI, SI]): unmarshalling.Unmarshaller[JI, O] =
    um.asInstanceOf[unmarshalling.Unmarshaller[JI, O]] // since guarantee provided by existence of `mi`

  class UnsupportedContentTypeException(
      private val _supported: java.util.Set[jm.model.ContentTypeRange],
      private val _actualContentType: Optional[jm.model.ContentType])
      extends RuntimeException(_supported.asScala.mkString(
        s"Unsupported Content-Type [${_actualContentType.asScala}], supported: ", ", ", "")) {

    def this(supported: jm.model.ContentTypeRange*) = {
      this(supported.toSet.asJava, Optional.empty[jm.model.ContentType]())
    }

    def this(supported: java.util.Set[jm.model.ContentTypeRange]) = {
      this(supported, Optional.empty[jm.model.ContentType]())
    }

    def this(contentType: Optional[jm.model.ContentType], supported: jm.model.ContentTypeRange*) = {
      this(supported.toSet.asJava, contentType)
    }

    def toScala(): pekko.http.scaladsl.unmarshalling.Unmarshaller.UnsupportedContentTypeException =
      pekko.http.scaladsl.unmarshalling.Unmarshaller.UnsupportedContentTypeException(
        _supported.asScala.toSet.asInstanceOf[Set[pekko.http.scaladsl.model.ContentTypeRange]],
        _actualContentType.asScala)

    def getSupported(): java.util.Set[jm.model.ContentTypeRange] = _supported

    def getActualContentType(): Optional[jm.model.ContentType] = _actualContentType

    override def equals(that: Any): Boolean = that match {
      case that: UnsupportedContentTypeException =>
        that._supported == this._supported && that._actualContentType == this._actualContentType
      case _ => false
    }
  }

}

trait UnmarshallerBase[-A, B]

/**
 * An unmarshaller transforms values of type A into type B.
 */
abstract class Unmarshaller[-A, B] extends UnmarshallerBase[A, B] {

  implicit def asScala: pekko.http.scaladsl.unmarshalling.Unmarshaller[A, B]

  /** INTERNAL API */
  @InternalApi
  private[pekko] def asScalaCastInput[I]: unmarshalling.Unmarshaller[I, B] =
    asScala.asInstanceOf[unmarshalling.Unmarshaller[I, B]]

  /**
   * Apply this Unmarshaller to the given value.
   */
  def unmarshal(value: A, ec: ExecutionContext, mat: Materializer): CompletionStage[B] =
    asScala.apply(value)(ec, mat).asJava

  /**
   * Apply this Unmarshaller to the given value. Uses the default materializer [[ExecutionContext]].
   * If you expect the marshalling to be heavy, it is suggested to provide a specialized context for those operations.
   */
  def unmarshal(value: A, mat: Materializer): CompletionStage[B] = unmarshal(value, mat.executionContext, mat)

  /**
   * Apply this Unmarshaller to the given value.
   */
  def unmarshal(value: A, ec: ExecutionContext, system: ClassicActorSystemProvider): CompletionStage[B] =
    unmarshal(value, ec, SystemMaterializer(system).materializer)

  /**
   * Apply this Unmarshaller to the given value. Uses the default materializer [[ExecutionContext]].
   * If you expect the marshalling to be heavy, it is suggested to provide a specialized context for those operations.
   */
  def unmarshal(value: A, system: ClassicActorSystemProvider): CompletionStage[B] =
    unmarshal(value, system.classicSystem.dispatcher, SystemMaterializer(system).materializer)

  /**
   * Transform the result `B` of this unmarshaller to a `C` producing a marshaller that turns `A`s into `C`s
   *
   * @return A new marshaller that can unmarshall instances of `A` into instances of `C`
   */
  def thenApply[C](f: java.util.function.Function[B, C]): Unmarshaller[A, C] = asScala.map(f.apply)

  def flatMap[C](f: java.util.function.Function[B, CompletionStage[C]]): Unmarshaller[A, C] =
    asScala.flatMap { ctx => mat => b => f.apply(b).asScala }

  def flatMap[C](u: Unmarshaller[_ >: B, C]): Unmarshaller[A, C] =
    asScala.flatMap { ctx => mat => b => u.asScala.apply(b)(ctx, mat) }

  // TODO not exposed for Java yet
  //  def mapWithInput[C](f: java.util.function.BiFunction[A, B, C]): Unmarshaller[A, C] =
  //    asScala.mapWithInput { case (a, b) => f.apply(a, b) }
  //
  //  def flatMapWithInput[C](f: java.util.function.BiFunction[A, B, CompletionStage[C]]): Unmarshaller[A, C] =
  //    asScala.flatMapWithInput { case (a, b) => f.apply(a, b).toScala }
}
