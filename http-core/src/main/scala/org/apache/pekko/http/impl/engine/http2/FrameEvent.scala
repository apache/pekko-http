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

package org.apache.pekko.http.impl.engine.http2

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.impl.engine.http2.Http2Protocol.ErrorCode
import pekko.http.impl.engine.http2.Http2Protocol.FrameType
import pekko.http.impl.engine.http2.Http2Protocol.SettingIdentifier
import pekko.http.scaladsl.model.ErrorInfo
import pekko.util.ByteString

/** INTERNAL API */
@InternalApi
private[http2] sealed trait FrameEvent { self: Product =>
  def frameTypeName: String = productPrefix
}

/** INTERNAL API */
@InternalApi
private[http] object FrameEvent {

  sealed trait StreamFrameEvent extends FrameEvent { self: Product =>
    def streamId: Int
  }

  final case class GoAwayFrame(
      lastStreamId: Int, errorCode: ErrorCode, debug: ByteString = ByteString.empty) extends FrameEvent {
    override def toString: String = s"GoAwayFrame($lastStreamId,$errorCode,debug:<hidden>)"
  }
  sealed trait DataFrameEvent extends StreamFrameEvent { self: Product =>
    def endStream: Boolean
    def payload: ByteString
    def paddingSize: Int = 0
    def sizeInWindow: Int
    final override def frameTypeName: String = "DataFrame"
  }

  final case class DataFrame(
      streamId: Int,
      endStream: Boolean,
      payload: ByteString) extends DataFrameEvent {
    override def sizeInWindow: Int = payload.size
  }

  /** Received DATA with its padding stripped from the entity, but not from flow control (RFC 9113 §6.1). */
  final case class PaddedDataFrame(
      streamId: Int,
      endStream: Boolean,
      payload: ByteString,
      override val paddingSize: Int) extends DataFrameEvent {
    override def sizeInWindow: Int = payload.size + paddingSize // includes the Pad Length field
  }

  /**
   * Representation of the wire-level HEADERS frame. Together with ContinuationFrame's and instance of
   * HeadersFrame will be parsed into a logical ParsedHeadersFrame.
   */
  final case class HeadersFrame(
      streamId: Int,
      endStream: Boolean,
      endHeaders: Boolean,
      headerBlockFragment: ByteString,
      priorityInfo: Option[PriorityFrame]) extends StreamFrameEvent
  final case class ContinuationFrame(
      streamId: Int,
      endHeaders: Boolean,
      payload: ByteString) extends StreamFrameEvent
  case class PushPromiseFrame(
      streamId: Int,
      endHeaders: Boolean,
      promisedStreamId: Int,
      headerBlockFragment: ByteString) extends StreamFrameEvent

  final case class RstStreamFrame(streamId: Int, errorCode: ErrorCode) extends StreamFrameEvent
  final case class SettingsFrame(settings: Seq[Setting]) extends FrameEvent
  final case class SettingsAckFrame(acked: Seq[Setting]) extends FrameEvent

  case class PingFrame(ack: Boolean, data: ByteString) extends FrameEvent {
    require(data.size == 8, s"PingFrame payload must be of size 8 but was ${data.size}")
  }
  final case class WindowUpdateFrame(
      streamId: Int,
      windowSizeIncrement: Int) extends StreamFrameEvent

  final case class PriorityFrame(
      streamId: Int,
      exclusiveFlag: Boolean,
      streamDependency: Int,
      weight: Int) extends StreamFrameEvent

  final case class Setting(
      identifier: SettingIdentifier,
      value: Int)

  object Setting {
    implicit def autoConvertFromTuple(tuple: (SettingIdentifier, Int)): Setting =
      Setting(tuple._1, tuple._2)
  }

  /** Dummy event for all unknown frames */
  final case class UnknownFrameEvent(
      tpe: FrameType,
      flags: ByteFlag,
      streamId: Int,
      payload: ByteString) extends StreamFrameEvent

  /**
   * Convenience (logical) representation of a parsed HEADERS frame with zero, one or
   * many CONTINUATIONS Frames into a single, decompressed object.
   *
   * @param headerParseErrorDetails Only used server side, passes header errors from decompression into error response logic
   */
  final case class ParsedHeadersFrame(
      streamId: Int,
      endStream: Boolean,
      keyValuePairs: Seq[(String, AnyRef)],
      priorityInfo: Option[PriorityFrame],
      headerParseErrorDetails: Option[ErrorInfo]) extends StreamFrameEvent

}
