/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.model.headers

import java.util.{ Locale, Optional }

import scala.jdk.OptionConverters._

import org.parboiled2.CharPredicate

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.impl.model.parser.CharacterClasses
import pekko.http.impl.util.JavaMapping.Implicits._
import pekko.http.impl.util._
import pekko.http.javadsl.{ model => jm }
import pekko.http.scaladsl.model.UriRendering.UriRenderer
import pekko.http.scaladsl.model._

final case class LinkValue(uri: Uri, params: Seq[LinkParam]) extends jm.headers.LinkValue
    with ValueRenderable {
  def render[R <: Rendering](r: R): r.type = {
    r ~~ '<' ~~ uri ~~ '>'
    if (params.nonEmpty) r ~~ "; " ~~ params
    r
  }

  def getUri: jm.Uri = uri.asJava
  def getParams: java.lang.Iterable[jm.headers.LinkParam] = params.asJava
}

object LinkValue {
  def apply(uri: Uri, firstParam: LinkParam, otherParams: LinkParam*): LinkValue = apply(uri, firstParam +: otherParams)
}

sealed abstract class LinkParam extends jm.headers.LinkParam with ToStringRenderable {
  val key: String = getClass.getSimpleName
  def value: AnyRef
}
object LinkParam {
  implicit val paramsRenderer: Renderer[Seq[LinkParam]] = Renderer.seqRenderer(separator = "; ")
}

object LinkParams {
  // Note rfc5988 only lists " ,;" but we add : to align with rfc8288 which quotes anything not a token as defined
  // in rfc7230 (but without having to parse token)
  private val reserved = CharPredicate(" ,;:")
  private val standardNames = Set("rel", "anchor", "rev", "hreflang", "media", "title", "title*", "type")

  // A few convenience rels
  val next = rel("next")
  val prev = rel("prev")
  val first = rel("first")
  val last = rel("last")
  val blockedBy = rel("blocked-by")

  /**
   * An extension target attribute of a `Link` header.
   *
   * A parameter without a value has `valueOption == None`, while an explicitly empty value is `Some("")`.
   * The `key` is normalized to lowercase; the value is not case-folded.
   * The untyped `LinkParam.value` accessor returns null for a valueless attribute.
   *
   * @since 2.1.0
   */
  sealed trait Extension extends jm.headers.LinkParam.Extension { this: LinkParam =>
    def key: String
    def valueOption: Option[String]

    /** Java API: an absent value is distinct from an explicitly empty value. */
    final override def getValueOption(): Optional[String] = valueOption.toJava
  }

  /**
   * Creates an extension attribute without a value.
   * @since 2.1.0
   */
  def extension(name: String): LinkParam with Extension = ExtensionImpl(validateExtensionName(name), None)

  /**
   * Creates an extension attribute with the given decoded value.
   * @since 2.1.0
   */
  def extension(name: String, value: String): LinkParam with Extension = {
    require(value != null && value.forall(c =>
        c == CharacterClasses.HTAB ||
        (!CharacterClasses.CTL(c) && c != '\uffff')), "Invalid Link extension value")
    ExtensionImpl(validateExtensionName(name), Some(value))
  }

  /** INTERNAL API: used to prevent typed parameters falling through to the extension rule. */
  @InternalApi private[http] def isExtensionName(name: String): Boolean =
    !standardNames.contains(name.toLowerCase(Locale.ROOT))

  private def validateExtensionName(name: String): String = {
    require(name != null && name.nonEmpty && CharacterClasses.tchar.matchesAll(name),
      "Link extension name must be a token")
    val normalized = name.toLowerCase(Locale.ROOT)
    require(!standardNames.contains(normalized), "Use the typed factory for a standard Link parameter")
    normalized
  }

  /**
   * This can be either a bare word, an absolute URI, or a quoted, space-separated string of zero-or-more of either
   *
   * For a more detailed breakdown of the relation type link parameter, see
   * http://tools.ietf.org/html/rfc5988#section-5.3
   */
  final case class rel(value: String) extends LinkParam {
    def render[R <: Rendering](r: R): r.type = {
      r ~~ "rel="
      if (reserved.matchesAny(value)) r ~~ '"' ~~ value ~~ '"' else r ~~ value
    }
  }

  /**
   * For a more detailed breakdown of the anchor link parameter, see
   * http://tools.ietf.org/html/rfc5988#section-5.2
   */
  final case class anchor(uri: Uri) extends LinkParam {
    def value: AnyRef = uri

    def render[R <: Rendering](r: R): r.type = r ~~ "anchor=\"" ~~ uri ~~ '"'
  }

  /**
   * This can be either a bare word, an absolute URI, or a quoted, space-separated string of zero-or-more of either.
   *
   * For a more detailed breakdown of the reverse relationship link parameter, see
   * http://tools.ietf.org/html/rfc5988#section-5.3
   */
  final case class rev(value: String) extends LinkParam {
    def render[R <: Rendering](r: R): r.type = {
      r ~~ "rev="
      if (reserved.matchesAny(value)) r ~~ '"' ~~ value ~~ '"' else r ~~ value
    }
  }

  /**
   * For a more detailed breakdown of the dereferenced language hint link parameter
   * http://tools.ietf.org/html/rfc5988#section-5.4
   */
  final case class hreflang(lang: Language) extends LinkParam {
    def value: AnyRef = lang

    def render[R <: Rendering](r: R): r.type = r ~~ "hreflang=" ~~ lang
  }

  /**
   * For a more detailed breakdown of how to use the dereferenced language hint link parameter, see
   * http://tools.ietf.org/html/rfc5988#section-5.4
   */
  final case class media(desc: String) extends LinkParam {
    def value: AnyRef = desc

    def render[R <: Rendering](r: R): r.type = {
      r ~~ "media="
      if (reserved.matchesAny(desc)) r ~~ '"' ~~ desc ~~ '"' else r ~~ desc
    }
  }

  /**
   * For a more detailed breakdown of how to use the dereferenced language hint link parameter, see
   * http://tools.ietf.org/html/rfc5988#section-5.4
   */
  final case class title(title: String) extends LinkParam {
    def value: AnyRef = title

    def render[R <: Rendering](r: R): r.type = r ~~ "title=\"" ~~ title ~~ '"'
  }

  /**
   * For a more detailed breakdown of how to use the dereferenced language hint link parameter, see
   * http://tools.ietf.org/html/rfc5988#section-5.4
   */
  final case class `title*`(title: String) extends LinkParam {
    def value: AnyRef = title

    def render[R <: Rendering](r: R): r.type = {
      r ~~ "title*="
      if (reserved.matchesAny(title)) r ~~ '"' ~~ title ~~ '"' else r ~~ title
    }
  }

  /**
   * For a more detailed breakdown of how to use the dereferenced language hint link parameter, see
   * http://tools.ietf.org/html/rfc5988#section-5.4
   */
  final case class `type`(mediaType: MediaType) extends LinkParam {
    def value: AnyRef = mediaType

    def render[R <: Rendering](r: R): r.type = {
      r ~~ "type="
      if (reserved.matchesAny(mediaType.value)) r ~~ '"' ~~ mediaType.value ~~ '"' else r ~~ mediaType.value
    }
  }

  // Keep the private case after all the other cases so Scala 3 retains its generated LinkParam sum
  // mirror and the existing ordinals. Expose only the stable Extension view, not the case-class shape.
  private final case class ExtensionImpl(override val key: String, valueOption: Option[String])
      extends LinkParam with Extension {
    override def value: AnyRef = valueOption.orNull

    override def render[R <: Rendering](r: R): r.type = {
      r ~~ key
      valueOption match {
        case Some(s) => r ~~ '=' ~~# s
        case None    => r
      }
    }
  }
}
