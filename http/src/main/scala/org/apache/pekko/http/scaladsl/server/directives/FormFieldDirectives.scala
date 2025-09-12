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

package org.apache.pekko.http.scaladsl.server
package directives

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.impl.util._
import pekko.http.scaladsl.common._
import pekko.http.scaladsl.server.directives.RouteDirectives._
import pekko.http.scaladsl.unmarshalling.Unmarshaller.UnsupportedContentTypeException
import pekko.http.scaladsl.util.FastFuture._

import scala.annotation.tailrec
import scala.collection.immutable
import scala.concurrent.Future
import scala.util.{ Failure, Success }
import BasicDirectives._
import pekko.http.ccompat.since213

/**
 * @groupname form Form field directives
 * @groupprio form 90
 */
trait FormFieldDirectives extends FormFieldDirectivesInstances with ToNameReceptacleEnhancements {
  import FormFieldDirectives._

  /**
   * Extracts HTTP form fields from the request as a ``Map[String, String]``.
   *
   * @group form
   */
  def formFieldMap: Directive1[Map[String, String]] = _formFieldMap

  /**
   * Extracts HTTP form fields from the request as a ``Map[String, List[String]]``.
   *
   * @group form
   */
  def formFieldMultiMap: Directive1[Map[String, List[String]]] = _formFieldMultiMap

  /**
   * Extracts HTTP form fields from the request as a ``Seq[(String, String)]``.
   *
   * @group form
   */
  def formFieldSeq: Directive1[immutable.Seq[(String, String)]] = _formFieldSeq
}

object FormFieldDirectives extends FormFieldDirectives {

  private val _formFieldSeq: Directive1[immutable.Seq[(String, String)]] = {
    import FutureDirectives._
    import pekko.http.scaladsl.unmarshalling._

    extract { ctx =>
      import ctx.{ executionContext, materializer }
      Unmarshal(ctx.request.entity).to[StrictForm].fast.flatMap { form =>
        val fields = form.fields.collect {
          case (name, field) if name.nonEmpty =>
            Unmarshal(field).to[String].map(fieldString => (name, fieldString))
        }
        Future.sequence(fields)
      }
    }.flatMap { sequenceF =>
      onComplete(sequenceF).flatMap {
        case Success(x) => provide(x)
        case Failure(x: UnsupportedContentTypeException) =>
          reject(UnsupportedRequestContentTypeRejection(x.supported, x.actualContentType))
        case Failure(x) => reject(MalformedRequestContentRejection(x.getMessage.nullAsEmpty, x))
      }
    }
  }

  private val _formFieldMultiMap: Directive1[Map[String, List[String]]] = {
    @tailrec def append(
        map: Map[String, List[String]],
        fields: immutable.Seq[(String, String)]): Map[String, List[String]] = {
      if (fields.isEmpty) {
        map
      } else {
        val (key, value) = fields.head
        append(map.updated(key, value :: map.getOrElse(key, Nil)), fields.tail)
      }
    }

    _formFieldSeq.map {
      case seq =>
        append(immutable.TreeMap.empty, seq)
    }
  }

  private val _formFieldMap: Directive1[Map[String, String]] = _formFieldSeq.map(toMap)

  private def toMap(seq: Seq[(String, String)]): Map[String, String] = immutable.TreeMap(seq: _*)

  trait FieldSpec {
    type Out
    def get: Directive1[Out]
  }
  object FieldSpec {
    type Aux[T] = FieldSpec { type Out = T }

    def apply[T](directive: Directive1[T]): FieldSpec.Aux[T] = new FieldSpec {
      type Out = T
      def get: Directive1[T] = toStrictEntity(StrictForm.toStrictTimeout).wrap { directive }
    }

    import Impl._
    import pekko.http.scaladsl.unmarshalling.{ FromStrictFormFieldUnmarshaller => FSFFU, _ }
    type FSFFOU[T] = Unmarshaller[Option[StrictForm.Field], T]

    implicit def forString(fieldName: String): FieldSpec.Aux[String] = forName(fieldName, stringFromStrictForm)
    implicit def forSymbol(symbol: Symbol): FieldSpec.Aux[String] = forName(symbol.name, stringFromStrictForm)
    implicit def forNR[T](r: NameReceptacle[T])(implicit fu: FSFFU[T]): FieldSpec.Aux[T] = forName(r.name, fu)
    implicit def forNUR[T](r: NameUnmarshallerReceptacle[T]): FieldSpec.Aux[T] =
      forName(r.name, StrictForm.Field.unmarshallerFromFSU(r.um))
    implicit def forNOR[T](r: NameOptionReceptacle[T])(implicit fu: FSFFOU[T]): FieldSpec.Aux[Option[T]] =
      forName(r.name, fu)
    implicit def forNDR[T](r: NameDefaultReceptacle[T])(implicit fu: FSFFOU[T]): FieldSpec.Aux[T] =
      forName(r.name, fu.withDefaultValue(r.default))
    implicit def forNOUR[T](r: NameOptionUnmarshallerReceptacle[T]): FieldSpec.Aux[Option[T]] =
      forName(r.name, StrictForm.Field.unmarshallerFromFSU(r.um): FSFFOU[T])
    implicit def forNDUR[T](r: NameDefaultUnmarshallerReceptacle[T]): FieldSpec.Aux[T] =
      forName(r.name, (StrictForm.Field.unmarshallerFromFSU(r.um): FSFFOU[T]).withDefaultValue(r.default))

    //////////////////// required formField support ////////////////////

    implicit def forRVR[T](r: RequiredValueReceptacle[T])(implicit fu: FSFFU[T]): FieldSpec.Aux[Unit] =
      forNameRequired(r.name, fu, r.requiredValue)
    implicit def forRVDR[T](r: RequiredValueUnmarshallerReceptacle[T]): FieldSpec.Aux[Unit] =
      forNameRequired(r.name, StrictForm.Field.unmarshallerFromFSU(r.um), r.requiredValue)

    //////////////////// repeated formField support ////////////////////

    implicit def forRepVR[T](r: RepeatedValueReceptacle[T])(implicit fu: FSFFU[T]): FieldSpec.Aux[Iterable[T]] =
      forNameRepeated(r.name, fu)
    implicit def forRepVDR[T](r: RepeatedValueUnmarshallerReceptacle[T]): FieldSpec.Aux[Iterable[T]] =
      forNameRepeated(r.name, StrictForm.Field.unmarshallerFromFSU(r.um))

    private def forName[T](name: String, fu: FSFFOU[T]): FieldSpec.Aux[T] = FieldSpec(filter(name, fu))
    private def forNameRequired[T](name: String, fsu: FSFFOU[T], requiredValue: T): FieldSpec.Aux[Unit] =
      FieldSpec(requiredFilter(name, fsu, requiredValue).tmap(_ => Tuple1(())))
    private def forNameRepeated[T](name: String, fsu: FSFFU[T]): FieldSpec.Aux[Iterable[T]] =
      FieldSpec(repeatedFilter(name, fsu))
  }

  @InternalApi
  private[http] object Impl {
    import BasicDirectives._
    import FutureDirectives._
    import RouteDirectives._
    import pekko.http.scaladsl.unmarshalling.{ FromStrictFormFieldUnmarshaller => FSFFU, _ }

    type SFU = FromEntityUnmarshaller[StrictForm]
    type FSFFOU[T] = Unmarshaller[Option[StrictForm.Field], T]

    protected def handleFieldResult[T](fieldName: String, result: Future[T]): Directive1[T] =
      onComplete(result).flatMap {
        case Success(x)                               => provide(x)
        case Failure(Unmarshaller.NoContentException) => reject(MissingFormFieldRejection(fieldName))
        case Failure(x: UnsupportedContentTypeException) =>
          reject(UnsupportedRequestContentTypeRejection(x.supported, x.actualContentType))
        case Failure(x) => reject(MalformedFormFieldRejection(fieldName, x.getMessage.nullAsEmpty, Option(x.getCause)))
      }

    private def strictFormUnmarshaller(ctx: RequestContext): SFU =
      StrictForm.unmarshaller(
        Unmarshaller.defaultUrlEncodedFormDataUnmarshaller,
        MultipartUnmarshallers.multipartFormDataUnmarshaller(ctx.log, ctx.parserSettings))
    val stringFromStrictForm: FSFFU[String] =
      StrictForm.Field.unmarshaller(StrictForm.Field.FieldUnmarshaller.stringFieldUnmarshaller)

    def fieldOfForm[T](
        fieldName: String, fu: Unmarshaller[Option[StrictForm.Field], T]): RequestContext => Future[T] = { ctx =>
      import ctx.{ executionContext, materializer }
      strictFormUnmarshaller(ctx)(ctx.request.entity).fast.flatMap(form => fu(form.field(fieldName)))
    }
    def filter[T](fieldName: String, fu: FSFFOU[T]): Directive1[T] =
      extract(fieldOfForm(fieldName, fu)).flatMap(r => handleFieldResult(fieldName, r))

    def repeatedFilter[T](fieldName: String, fu: FSFFU[T]): Directive1[Iterable[T]] =
      extract { ctx =>
        import ctx.{ executionContext, materializer }
        strictFormUnmarshaller(ctx)(ctx.request.entity).fast.flatMap(form =>
          Future.sequence(form.fields.collect { case (`fieldName`, value) => fu(value) }))
      }.flatMap { result =>
        handleFieldResult(fieldName, result)
      }

    def requiredFilter[T](
        fieldName: String, fu: Unmarshaller[Option[StrictForm.Field], T], requiredValue: Any): Directive0 =
      extract(fieldOfForm(fieldName, fu)).flatMap {
        onComplete(_).flatMap {
          case Success(value) if value == requiredValue => pass
          case _                                        => reject
        }
      }
  }
}
