/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.http.impl.engine.ws

import java.io.ByteArrayOutputStream
import java.util.Random
import java.util.zip.Deflater
import java.util.zip.Inflater
import java.util.zip.DataFormatException

import org.apache.pekko
import pekko.NotUsed
import pekko.annotation.InternalApi
import pekko.http.impl.settings.WebSocketCompressionSettingsImpl
import pekko.http.scaladsl.model.headers.WebSocketExtension
import pekko.stream.scaladsl.BidiFlow
import pekko.stream.scaladsl.Flow
import pekko.stream.stage.GraphStage
import pekko.stream.stage.GraphStageLogic
import pekko.stream.stage.InHandler
import pekko.stream.stage.OutHandler
import pekko.stream.{ Attributes, FlowShape, Inlet, Outlet }
import pekko.util.{ ByteString, ByteStringBuilder }

import scala.collection.immutable
import scala.collection.immutable.ListMap

/**
 * INTERNAL API
 */
@InternalApi
private[http] object PerMessageDeflate {
  private val ExtensionName = "permessage-deflate"
  private val ClientMaxWindowBits = "client_max_window_bits"
  private val ServerMaxWindowBits = "server_max_window_bits"
  private val ClientNoContextTakeover = "client_no_context_takeover"
  private val ServerNoContextTakeover = "server_no_context_takeover"
  private val EmptyStoredBlock = ByteString(0x00, 0x00, 0xFF.toByte, 0xFF.toByte)

  private[ws] trait CompressionFactory {
    def newInflater(): Inflater
    def newDeflater(compressionLevel: Int): Deflater
  }

  private object DefaultCompressionFactory extends CompressionFactory {
    override def newInflater(): Inflater = new Inflater(true)
    override def newDeflater(compressionLevel: Int): Deflater = new Deflater(compressionLevel, true)
  }

  final case class Negotiated(
      responseExtension: WebSocketExtension,
      serverNoContextTakeover: Boolean,
      clientNoContextTakeover: Boolean,
      settings: WebSocketCompressionSettingsImpl) {
    def bidiFlow: BidiFlow[FrameEventOrError, FrameEventOrError, FrameEvent, FrameEvent, NotUsed] =
      BidiFlow.fromFlows(inflaterFlow, deflaterFlow)

    def frameEventBidiFlow(
        maskRandom: () => Random): BidiFlow[FrameEvent, FrameEvent, FrameEvent, FrameEvent, NotUsed] =
      BidiFlow.fromFlows(
        Flow[FrameEvent]
          .via(Masking.unmaskIf(condition = true))
          .via(inflaterFlow)
          .map {
            case frame: FrameEvent => frame
            case FrameError(ex)    => throw ex
          }
          .via(Masking.maskIf(condition = true, maskRandom)),
        deflaterFlow)

    private def inflaterFlow: Flow[FrameEventOrError, FrameEventOrError, NotUsed] =
      createInflaterFlow(clientNoContextTakeover, settings, DefaultCompressionFactory)

    private def deflaterFlow: Flow[FrameEvent, FrameEvent, NotUsed] =
      createDeflaterFlow(serverNoContextTakeover, settings, DefaultCompressionFactory)
  }

  private[ws] def createInflaterFlow(
      noContextTakeover: Boolean,
      settings: WebSocketCompressionSettingsImpl,
      compressionFactory: CompressionFactory): Flow[FrameEventOrError, FrameEventOrError, NotUsed] =
    Flow.fromGraph(new LifecycleMapConcatStage(
      "PerMessageDeflate.inflater",
      () => new InflaterFlow(noContextTakeover, settings, compressionFactory)))

  private[ws] def createDeflaterFlow(
      noContextTakeover: Boolean,
      settings: WebSocketCompressionSettingsImpl,
      compressionFactory: CompressionFactory): Flow[FrameEvent, FrameEvent, NotUsed] =
    Flow.fromGraph(new LifecycleMapConcatStage(
      "PerMessageDeflate.deflater",
      () => new DeflaterFlow(noContextTakeover, settings, compressionFactory)))

  def negotiate(
      requested: immutable.Seq[WebSocketExtension],
      settings: WebSocketCompressionSettingsImpl): Option[Negotiated] = {
    if (!settings.enabled) None
    else {
      requested.collectFirst(Function.unlift { extension =>
        if (extension.name.equalsIgnoreCase(ExtensionName)) negotiate(extension, settings) else None
      })
    }
  }

  private def negotiate(
      extension: WebSocketExtension,
      settings: WebSocketCompressionSettingsImpl): Option[Negotiated] = {
    var responseParams = ListMap.empty[String, String]
    var clientNoContext = false
    var serverNoContext = false
    var accepted = true

    extension.params.foreach {
      case (ClientMaxWindowBits, value) =>
        if (value.isEmpty) responseParams += ClientMaxWindowBits -> settings.preferredClientWindowSize.toString
        else if (validWindowBits(value)) responseParams += ClientMaxWindowBits -> value
        else accepted = false
      case (ServerMaxWindowBits, value) =>
        if (value == "15") responseParams += ServerMaxWindowBits -> value
        else accepted = false
      case (ClientNoContextTakeover, "") =>
        clientNoContext = settings.preferredClientNoContext
        if (clientNoContext) responseParams += ClientNoContextTakeover -> ""
      case (ServerNoContextTakeover, "") =>
        if (settings.allowServerNoContext) {
          serverNoContext = true
          responseParams += ServerNoContextTakeover -> ""
        } else accepted = false
      case _ =>
        accepted = false
    }

    if (accepted) {
      Some(Negotiated(WebSocketExtension(ExtensionName, responseParams), serverNoContext, clientNoContext, settings))
    } else None
  }

  private def validWindowBits(value: String): Boolean =
    value.nonEmpty && value.length <= 2 && value.forall(_.isDigit) && {
      val parsed = value.toInt
      parsed >= 8 && parsed <= 15
    }

  private final class InflaterFlow(
      noContextTakeover: Boolean,
      settings: WebSocketCompressionSettingsImpl,
      compressionFactory: CompressionFactory)
      extends LifecycleMapConcat[FrameEventOrError, FrameEventOrError] {
    private var inflater = compressionFactory.newInflater()
    private var compressedFrame: Option[CompressedFrame] = None
    private var compressedMessageInProgress = false
    private var decompressedMessageBytes = 0L
    private var bypassFrameInProgress = false
    private val buffer = new Array[Byte](8192)

    override def apply(event: FrameEventOrError): immutable.Iterable[FrameEventOrError] = event match {
      case start @ FrameStart(header, data)
          if header.rsv1 &&
          (header.opcode == Protocol.Opcode.Text ||
          header.opcode == Protocol.Opcode.Binary) =>
        if (compressedMessageInProgress || compressedFrame.isDefined)
          throw new ProtocolException("Unexpected data frame while fragmented message is open")
        if (header.rsv2 || header.rsv3) throw new ProtocolException("Unexpected reserved bit for compressed message")
        compressedMessageInProgress = !header.fin
        compressedFrame = Some(CompressedFrame(header.copy(rsv1 = false, length = 0), data, appendTail = header.fin))
        if (start.lastPart) finishFrame() else Nil
      case start @ FrameStart(header, _) if bypassFrameInProgress =>
        throw new ProtocolException(s"Unexpected frame ${header.opcode} while frame data is open")
      case start @ FrameStart(header, _)
          if (compressedFrame.isDefined || compressedMessageInProgress) && header.opcode.isControl =>
        bypassFrameInProgress = !start.lastPart
        start :: Nil
      case start @ FrameStart(header, data)
          if compressedMessageInProgress && header.opcode == Protocol.Opcode.Continuation =>
        if (header.rsv1 || header.rsv2 || header.rsv3)
          throw new ProtocolException("Unexpected reserved bit for continuation frame")
        compressedMessageInProgress = !header.fin
        compressedFrame = Some(CompressedFrame(header.copy(length = 0), data, appendTail = header.fin))
        if (start.lastPart) finishFrame() else Nil
      case start @ FrameStart(header, _) if compressedFrame.isDefined || compressedMessageInProgress =>
        throw new ProtocolException(s"Unexpected frame ${header.opcode} while fragmented message is open")
      case data: FrameData if bypassFrameInProgress =>
        bypassFrameInProgress = !data.lastPart
        data :: Nil
      case data: FrameData if compressedFrame.isDefined =>
        compressedFrame = compressedFrame.map(_.append(data.data))
        if (data.lastPart) finishFrame() else Nil
      case other => other :: Nil
    }

    private def finishFrame(): immutable.Iterable[FrameEventOrError] = {
      val frame = compressedFrame.get
      compressedFrame = None
      val inflated = inflate(frame.data, frame.appendTail)
      if (frame.appendTail) decompressedMessageBytes = 0L
      if (frame.appendTail && noContextTakeover) {
        inflater.end()
        inflater = compressionFactory.newInflater()
      }
      FrameStart(frame.header.copy(length = inflated.length), inflated) :: Nil
    }

    private def inflate(data: ByteString, appendTail: Boolean): ByteString = {
      try {
        val input = if (appendTail) data ++ EmptyStoredBlock else data
        inflater.setInput(input.toArrayUnsafe())
        val output = new ByteArrayOutputStream(1024)
        var count = inflater.inflate(buffer)
        while (count > 0) {
          decompressedMessageBytes += count
          if (settings.maxAllocation > 0 && decompressedMessageBytes > settings.maxAllocation)
            throw new ProtocolException("WebSocket decompressed message exceeds configured maximum allocation")
          output.write(buffer, 0, count)
          count = inflater.inflate(buffer)
        }
        ByteString.fromArrayUnsafe(output.toByteArray)
      } catch {
        case ex: DataFormatException =>
          throw new ProtocolException(s"Invalid WebSocket compressed message: ${ex.getMessage}")
      }
    }

    override def close(): Unit =
      inflater.end()
  }

  private final class DeflaterFlow(
      noContextTakeover: Boolean,
      settings: WebSocketCompressionSettingsImpl,
      compressionFactory: CompressionFactory)
      extends LifecycleMapConcat[FrameEvent, FrameEvent] {
    private var deflater = compressionFactory.newDeflater(settings.compressionLevel)
    private var frame: Option[UncompressedFrame] = None
    private var messageInProgress = false
    private var bypassFrameInProgress = false
    private val buffer = new Array[Byte](8192)

    override def apply(event: FrameEvent): immutable.Iterable[FrameEvent] = event match {
      case FrameStart(header, _)
          if (header.opcode == Protocol.Opcode.Text ||
          header.opcode == Protocol.Opcode.Binary) &&
          (header.rsv1 || header.rsv2 || header.rsv3) =>
        throw new ProtocolException("Unexpected reserved bit for outbound WebSocket message")
      case FrameStart(header, _)
          if header.opcode == Protocol.Opcode.Continuation &&
          (header.rsv1 || header.rsv2 || header.rsv3) =>
        throw new ProtocolException("Unexpected reserved bit for outbound WebSocket continuation frame")
      case start @ FrameStart(header, data)
          if header.opcode == Protocol.Opcode.Text ||
          header.opcode == Protocol.Opcode.Binary =>
        if (messageInProgress || frame.isDefined)
          throw new ProtocolException("Unexpected data frame while fragmented message is open")
        messageInProgress = !header.fin
        frame = Some(UncompressedFrame(header.copy(length = 0, rsv1 = true), data, removeTail = header.fin))
        if (start.lastPart) finishFrame() else Nil
      case start @ FrameStart(header, _) if bypassFrameInProgress =>
        throw new ProtocolException(s"Unexpected frame ${header.opcode} while frame data is open")
      case start @ FrameStart(header, _) if (frame.isDefined || messageInProgress) && header.opcode.isControl =>
        bypassFrameInProgress = !start.lastPart
        start :: Nil
      case start @ FrameStart(header, data) if messageInProgress && header.opcode == Protocol.Opcode.Continuation =>
        messageInProgress = !header.fin
        frame = Some(UncompressedFrame(header.copy(length = 0), data, removeTail = header.fin))
        if (start.lastPart) finishFrame() else Nil
      case start @ FrameStart(header, _) if frame.isDefined || messageInProgress =>
        throw new ProtocolException(s"Unexpected frame ${header.opcode} while fragmented message is open")
      case data: FrameData if bypassFrameInProgress =>
        bypassFrameInProgress = !data.lastPart
        data :: Nil
      case data: FrameData if frame.isDefined =>
        frame = frame.map(_.append(data.data))
        if (data.lastPart) finishFrame() else Nil
      case other => other :: Nil
    }

    private def finishFrame(): immutable.Iterable[FrameEvent] = {
      val current = frame.get
      frame = None
      val compressed = deflate(current.data, current.removeTail)
      if (current.removeTail && noContextTakeover) {
        deflater.end()
        deflater = compressionFactory.newDeflater(settings.compressionLevel)
      }
      FrameStart(current.header.copy(length = compressed.length), compressed) :: Nil
    }

    private def deflate(data: ByteString, removeTail: Boolean): ByteString = {
      deflater.setInput(data.toArrayUnsafe())
      val output = new ByteArrayOutputStream(1024)
      var count = deflater.deflate(buffer, 0, buffer.length, Deflater.SYNC_FLUSH)
      while (count > 0) {
        output.write(buffer, 0, count)
        count = deflater.deflate(buffer, 0, buffer.length, Deflater.SYNC_FLUSH)
      }
      val bytes = ByteString.fromArrayUnsafe(output.toByteArray)
      if (removeTail && bytes.endsWith(EmptyStoredBlock)) bytes.dropRight(EmptyStoredBlock.length) else bytes
    }

    override def close(): Unit =
      deflater.end()
  }

  private trait LifecycleMapConcat[-In, +Out] extends (In => immutable.Iterable[Out]) {
    def close(): Unit
  }

  private final class LifecycleMapConcatStage[In, Out](
      name: String,
      create: () => LifecycleMapConcat[In, Out])
      extends GraphStage[FlowShape[In, Out]] {
    private val in = Inlet[In](s"$name.in")
    private val out = Outlet[Out](s"$name.out")
    override val shape: FlowShape[In, Out] = FlowShape(in, out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) with InHandler with OutHandler {
        private val handler = create()
        private var pending = Iterator.empty[Out]
        private var upstreamFinished = false

        override def onPush(): Unit = {
          pending = handler(grab(in)).iterator
          pushOrPull()
        }

        override def onPull(): Unit =
          pushOrPull()

        override def onUpstreamFinish(): Unit = {
          upstreamFinished = true
          if (!pending.hasNext) completeStage()
        }

        override def postStop(): Unit =
          handler.close()

        private def pushOrPull(): Unit =
          if (pending.hasNext) push(out, pending.next())
          else if (upstreamFinished) completeStage()
          else if (!hasBeenPulled(in)) pull(in)

        setHandler(in, this)
        setHandler(out, this)
      }
  }

  private final case class CompressedFrame(
      header: FrameHeader,
      fragments: Vector[ByteString],
      length: Int,
      appendTail: Boolean) {
    def data: ByteString = compact(fragments, length)
    def append(next: ByteString): CompressedFrame = copy(fragments = fragments :+ next, length = length + next.length)
  }

  private object CompressedFrame {
    def apply(header: FrameHeader, data: ByteString, appendTail: Boolean): CompressedFrame =
      CompressedFrame(header, Vector(data), data.length, appendTail)
  }

  private final case class UncompressedFrame(
      header: FrameHeader,
      fragments: Vector[ByteString],
      length: Int,
      removeTail: Boolean) {
    def data: ByteString = compact(fragments, length)
    def append(next: ByteString): UncompressedFrame = copy(fragments = fragments :+ next, length = length + next.length)
  }

  private object UncompressedFrame {
    def apply(header: FrameHeader, data: ByteString, removeTail: Boolean): UncompressedFrame =
      UncompressedFrame(header, Vector(data), data.length, removeTail)
  }

  private def compact(fragments: Vector[ByteString], length: Int): ByteString =
    if (fragments.lengthCompare(1) == 0) fragments.head
    else {
      val builder = new ByteStringBuilder
      builder.sizeHint(length)
      fragments.foreach(builder.append)
      builder.result()
    }
}
