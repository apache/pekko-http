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

import java.util

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.impl.util._
import pekko.http.javadsl.{ model => jm }

sealed abstract class MediaRange extends jm.MediaRange with Renderable with WithQValue[MediaRange] {
  def value: String
  def mainType: String
  def params: Map[String, String]
  def qValue: Float
  def matches(mediaType: MediaType): Boolean

  def isApplication = false
  def isAudio = false
  def isImage = false
  def isMessage = false
  def isMultipart = false
  def isText = false
  def isVideo = false
  def isWildcard = mainType == "*"

  /**
   * Returns a copy of this instance with the params replaced by the given ones.
   * If the given map contains a "q" value the `qValue` member is (also) updated.
   */
  def withParams(params: Map[String, String]): MediaRange

  /**
   * Constructs a `ContentTypeRange` from this instance and the given charset.
   */
  def withCharsetRange(charsetRange: HttpCharsetRange): ContentTypeRange = ContentTypeRange(this, charsetRange)

  /** Java API */
  def getParams: util.Map[String, String] = {
    import scala.jdk.CollectionConverters._
    params.asJava
  }

  /** Java API */
  def matches(mediaType: jm.MediaType): Boolean = {
    import pekko.http.impl.util.JavaMapping.Implicits._
    matches(mediaType.asScala)
  }
}

object MediaRange {
  private[http] def splitOffQValue(params: Map[String, String], defaultQ: Float = 1.0f): (Map[String, String], Float) =
    params.get("q") match {
      case Some(x) => (params - "q") ->
        (try x.toFloat
        catch { case _: NumberFormatException => 1.0f })
      case None => params -> defaultQ
    }

  private def parameterMatches(params: Map[String, String], name: String, expected: String,
      ignoreNameCase: Boolean): Boolean =
    params.get(name) match {
      case Some(value)            => value == expected
      case None if ignoreNameCase =>
        // With no exact key to disambiguate aliases, all matching spellings must agree.
        val entries = params.iterator
        var matched = false
        while (entries.hasNext) {
          val (key, value) = entries.next()
          if (CharUtils.equalsAsciiCaseInsensitive(name, key)) {
            if (value != expected) return false
            matched = true
          }
        }
        matched
      case None => false
    }

  private def matchesCustom(mainType: String, params: Map[String, String], mediaType: MediaType,
      ignoreNameCase: Boolean): Boolean = (mainType == "*" || mediaType.mainType == mainType) &&
    params.forall { case (key, value) => parameterMatches(mediaType.params, key, value, ignoreNameCase) }

  private def matchesOne(expected: MediaType, mediaType: MediaType, ignoreNameCase: Boolean): Boolean =
    expected.mainType == mediaType.mainType &&
    expected.subType == mediaType.subType &&
    expected.params.forall {
      // Keep the established charset exception; only outbound Accept treats its spelling case-insensitively.
      case ("charset", _)                                                                     => true
      case (key, _) if ignoreNameCase && CharUtils.equalsAsciiCaseInsensitive(key, "charset") => true
      case (key, value)                                                                       => parameterMatches(mediaType.params, key, value, ignoreNameCase)
    }

  /** INTERNAL API: match media parameter names for outbound Accept negotiation. */
  @InternalApi private[http] object Negotiation {
    def matches(range: MediaRange, mediaType: MediaType): Boolean = range match {
      case one: One       => matchesOne(one.mediaType, mediaType, ignoreNameCase = true)
      case custom: Custom => matchesCustom(custom.mainType, custom.params, mediaType, ignoreNameCase = true)
      case other          => other.matches(mediaType)
    }
  }

  private final case class Custom(mainType: String, params: Map[String, String], qValue: Float)
      extends MediaRange with ValueRenderable {
    require(0.0f <= qValue && qValue <= 1.0f, "qValue must be >= 0 and <= 1.0")
    def matches(mediaType: MediaType) = matchesCustom(mainType, params, mediaType, ignoreNameCase = false)
    def withParams(params: Map[String, String]) = custom(mainType, params, qValue)
    def withQValue(qValue: Float) = if (qValue != this.qValue) custom(mainType, params, qValue) else this
    def render[R <: Rendering](r: R): r.type = {
      r ~~ mainType ~~ '/' ~~ '*'
      if (qValue < 1.0f) r ~~ ";q=" ~~ qValue
      if (params.nonEmpty) params.foreach { case (k, v) => r ~~ ';' ~~ ' ' ~~ k ~~ '=' ~~# v }
      r
    }
    override def isApplication = mainType == "application"
    override def isAudio = mainType == "audio"
    override def isImage = mainType == "image"
    override def isMessage = mainType == "message"
    override def isMultipart = mainType == "multipart"
    override def isText = mainType == "text"
    override def isVideo = mainType == "video"
  }

  def custom(mainType: String, params: Map[String, String] = Map.empty, qValue: Float = 1.0f): MediaRange = {
    val (ps, q) = splitOffQValue(params, qValue)
    Custom(mainType.toRootLowerCase, ps, q)
  }

  private[http] def customWithParams(mainType: String, params: Map[String, String],
      qValue: Float = 1.0f): MediaRange =
    Custom(mainType.toRootLowerCase, params, qValue)

  final case class One(mediaType: MediaType, qValue: Float) extends MediaRange with ValueRenderable {
    require(0.0f <= qValue && qValue <= 1.0f, "qValue must be >= 0 and <= 1.0")
    def mainType = mediaType.mainType
    def params = mediaType.params
    override def isApplication = mediaType.isApplication
    override def isAudio = mediaType.isAudio
    override def isImage = mediaType.isImage
    override def isMessage = mediaType.isMessage
    override def isMultipart = mediaType.isMultipart
    override def isText = mediaType.isText
    override def isVideo = mediaType.isVideo
    def matches(mediaType: MediaType) = matchesOne(this.mediaType, mediaType, ignoreNameCase = false)
    def withParams(params: Map[String, String]) = copy(mediaType = mediaType.withParams(params))
    def withQValue(qValue: Float) = copy(qValue = qValue)
    def render[R <: Rendering](r: R): r.type = if (qValue < 1.0f) r ~~ mediaType ~~ ";q=" ~~ qValue else r ~~ mediaType
  }

  implicit def apply(mediaType: MediaType): MediaRange = apply(mediaType, 1.0f)
  def apply(mediaType: MediaType, qValue: Float = 1.0f): MediaRange = One(mediaType, qValue)
}

object MediaRanges extends ObjectRegistry[String, MediaRange] {

  sealed abstract case class PredefinedMediaRange(value: String) extends MediaRange with LazyValueBytesRenderable {
    val mainType = value.takeWhile(_ != '/')
    register(mainType, this)
    def params = Map.empty
    def qValue = 1.0f
    def withParams(params: Map[String, String]) = MediaRange.custom(mainType, params)
    def withQValue(qValue: Float) = if (qValue != 1.0f) MediaRange.custom(mainType, params, qValue) else this
  }

  val `*/*` = new PredefinedMediaRange("*/*") {
    def matches(mediaType: MediaType) = true
  }
  val `*/*;q=MIN` = `*/*`.withQValue(Float.MinPositiveValue)
  val `application/*` = new PredefinedMediaRange("application/*") {
    def matches(mediaType: MediaType) = mediaType.isApplication
    override def isApplication = true
  }
  val `audio/*` = new PredefinedMediaRange("audio/*") {
    def matches(mediaType: MediaType) = mediaType.isAudio
    override def isAudio = true
  }
  val `image/*` = new PredefinedMediaRange("image/*") {
    def matches(mediaType: MediaType) = mediaType.isImage
    override def isImage = true
  }
  val `message/*` = new PredefinedMediaRange("message/*") {
    def matches(mediaType: MediaType) = mediaType.isMessage
    override def isMessage = true
  }
  val `multipart/*` = new PredefinedMediaRange("multipart/*") {
    def matches(mediaType: MediaType) = mediaType.isMultipart
    override def isMultipart = true
  }
  val `text/*` = new PredefinedMediaRange("text/*") {
    def matches(mediaType: MediaType) = mediaType.isText
    override def isText = true
  }
  val `video/*` = new PredefinedMediaRange("video/*") {
    def matches(mediaType: MediaType) = mediaType.isVideo
    override def isVideo = true
  }
}
