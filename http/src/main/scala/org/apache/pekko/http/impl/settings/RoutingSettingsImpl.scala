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

package org.apache.pekko.http.impl.settings

import scala.util.hashing.MurmurHash3

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.impl.util._

import com.typesafe.config.Config

/** INTERNAL API */
@InternalApi
private[http] final case class RoutingSettingsImpl(
    verboseErrorMessages: Boolean,
    fileGetConditional: Boolean,
    renderVanityFooter: Boolean,
    rangeCountLimit: Int,
    rangeCoalescingThreshold: Long,
    decodeMaxBytesPerChunk: Int,
    decodeMaxSize: Long,
    useJarFileCache: Boolean,
    rejectUnsafeResourceNames: Boolean) extends pekko.http.scaladsl.settings.RoutingSettings {

  // Keep the nine-field case-class shape (including its extractor). The private constructor opts in
  // before the snapshot enters the settings cache or is returned; nothing mutates it afterwards.
  @volatile private var rangeRequestOrder = false

  private def this(
      verboseErrorMessages: Boolean,
      fileGetConditional: Boolean,
      renderVanityFooter: Boolean,
      rangeCountLimit: Int,
      rangeCoalescingThreshold: Long,
      decodeMaxBytesPerChunk: Int,
      decodeMaxSize: Long,
      useJarFileCache: Boolean,
      rejectUnsafeResourceNames: Boolean,
      rangeRequestOrderEnabled: Boolean) = {
    this(verboseErrorMessages, fileGetConditional, renderVanityFooter, rangeCountLimit, rangeCoalescingThreshold,
      decodeMaxBytesPerChunk, decodeMaxSize, useJarFileCache, rejectUnsafeResourceNames)
    if (rangeRequestOrderEnabled) rangeRequestOrder = true
  }

  def rangeRequestOrderEnabled: Boolean = rangeRequestOrder

  def copy(
      verboseErrorMessages: Boolean = this.verboseErrorMessages,
      fileGetConditional: Boolean = this.fileGetConditional,
      renderVanityFooter: Boolean = this.renderVanityFooter,
      rangeCountLimit: Int = this.rangeCountLimit,
      rangeCoalescingThreshold: Long = this.rangeCoalescingThreshold,
      decodeMaxBytesPerChunk: Int = this.decodeMaxBytesPerChunk,
      decodeMaxSize: Long = this.decodeMaxSize,
      useJarFileCache: Boolean = this.useJarFileCache,
      rejectUnsafeResourceNames: Boolean = this.rejectUnsafeResourceNames): RoutingSettingsImpl =
    new RoutingSettingsImpl(verboseErrorMessages, fileGetConditional, renderVanityFooter, rangeCountLimit,
      rangeCoalescingThreshold, decodeMaxBytesPerChunk, decodeMaxSize, useJarFileCache, rejectUnsafeResourceNames,
      rangeRequestOrderEnabled)

  override def equals(other: Any): Boolean = other match {
    case that: RoutingSettingsImpl =>
      (this eq that) || (
        verboseErrorMessages == that.verboseErrorMessages &&
        fileGetConditional == that.fileGetConditional &&
        renderVanityFooter == that.renderVanityFooter &&
        rangeCountLimit == that.rangeCountLimit &&
        rangeCoalescingThreshold == that.rangeCoalescingThreshold &&
        decodeMaxBytesPerChunk == that.decodeMaxBytesPerChunk &&
        decodeMaxSize == that.decodeMaxSize &&
        useJarFileCache == that.useJarFileCache &&
        rejectUnsafeResourceNames == that.rejectUnsafeResourceNames &&
        rangeRequestOrderEnabled == that.rangeRequestOrderEnabled)
    case _ => false
  }

  override def hashCode(): Int = {
    val legacy = MurmurHash3.productHash(this, RoutingSettingsImpl.LegacyHashSeed, ignorePrefix = true)
    if (rangeRequestOrderEnabled) ~legacy else legacy
  }

  override def productPrefix = "RoutingSettings"
}

object RoutingSettingsImpl extends SettingsCompanionImpl[RoutingSettingsImpl]("pekko.http.routing") {
  // The legacy synthetic hash uses the class name, not the display product prefix.
  private val LegacyHashSeed = MurmurHash3.mix(MurmurHash3.productSeed, "RoutingSettingsImpl".hashCode)

  def fromSubConfig(root: Config, c: Config) = new RoutingSettingsImpl(
    c.getBoolean("verbose-error-messages"),
    c.getBoolean("file-get-conditional"),
    c.getBoolean("render-vanity-footer"),
    c.getInt("range-count-limit"),
    c.getBytes("range-coalescing-threshold"),
    c.getIntBytes("decode-max-bytes-per-chunk"),
    c.getPossiblyInfiniteBytes("decode-max-size"),
    c.getBoolean("use-jar-file-cache"),
    c.getBoolean("reject-unsafe-resource-names"),
    c.hasPath("preserve-range-request-order") && c.getBoolean("preserve-range-request-order"))
}
