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

import scala.collection.mutable
import scala.util.control.NoStackTrace

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.impl.engine.http2.FrameEvent._
import pekko.http.impl.engine.http2.Http2Protocol.ErrorCode
import pekko.http.impl.engine.rendering.DateHeaderRendering
import pekko.http.scaladsl.model.http2.PeerClosedStreamException
import pekko.http.scaladsl.model.{ AttributeKey, EntityStreamException, ErrorInfo, HttpEntity, HttpMethods }
import pekko.http.scaladsl.settings.Http2CommonSettings
import pekko.macros.LogHelper
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.stream.stage.{ GraphStageLogic, InHandler, OutHandler }
import pekko.util.ByteString
import pekko.util.OptionVal

/**
 * INTERNAL API
 *
 * Handles HTTP/2 stream states
 * Accepts `FrameEvent`s from the network side and emits `ByteHttp2SubStream`s for streams
 * to be handled by the Pekko HTTP layer.
 *
 * Mixed into the Http2ServerDemux graph logic.
 */
@InternalApi
private[http2] trait Http2StreamHandling extends GraphStageLogic with LogHelper { self =>
  // required API from demux
  def isContentFreeFinalStatus(status: String): Boolean
  def isServer: Boolean
  def multiplexer: Http2Multiplexer
  def settings: Http2CommonSettings
  def pushGOAWAY(errorCode: ErrorCode, debug: String): Unit
  def dispatchSubstream(initialHeaders: ParsedHeadersFrame, data: Either[ByteString, Source[Any, Any]],
      correlationAttributes: Map[AttributeKey[?], ?]): Unit
  def isUpgraded: Boolean
  def shouldSendContinue: ParsedHeadersFrame => Boolean

  def wrapTrailingHeaders(headers: ParsedHeadersFrame): Option[HttpEntity.ChunkStreamPart]

  def flowController: IncomingFlowController = IncomingFlowController.default(settings)

  private def isHeadRequest(frame: ParsedHeadersFrame): Boolean =
    frame.keyValuePairs.exists { case (name, value) => name == ":method" && (value eq HttpMethods.HEAD) }

  /**
   * Tries to generate demand of SubStreams on the inlet from the user handler. The
   * attemp to demand will succeed if the inlet is open and has no pending pull, and,
   * in the case of a client, if we're not exceedingthe number of active streams.
   * This method must be invoked any time the collection of active streams or the
   * value of maxConcurrentStreams are modified but the invocation must happen _after_
   * the collection or the limit are modified.
   */
  def tryPullSubStreams(): Unit

  private val streamStates = new mutable.LongMap[StreamState](settings.maxConcurrentStreams)
  // Stream ids of incoming HEAD requests. The response to a HEAD request must not carry content
  // (RFC 9110 section 9.3.2), but the response path only ever sees an HttpResponse and so cannot know the request
  // method. Entries are removed when the response is created or when the stream is closed.
  private val headRequestStreamIds = mutable.Set.empty[Int]

  /**
   * Server side: for a request stream that declared a `content-length` and is still receiving DATA, the number of
   * bytes it may still send. RFC 9113 section 8.1.1 makes a request whose DATA payload lengths do not add up to its
   * `content-length` malformed, and that is a stream error of type PROTOCOL_ERROR.
   */
  private val remainingContentLength = mutable.Map.empty[Int, Long]

  /**
   * The `content-length` of a request, or -1 if it has none or one that `RequestParsing` rejects with a 400. Both use
   * the same parser, so every length the request parser accepts is accounted for here.
   */
  private def declaredContentLength(frame: ParsedHeadersFrame): Long =
    frame.keyValuePairs.collectFirst { case ("content-length", value: String) => value } match {
      case Some(value) => RequestParsing.parseContentLength(value)
      case None        => -1L
    }

  /**
   * Accounts `payloadLength` received bytes against the declared `content-length` of the stream, if it has one, and
   * returns whether they exceed it or, if the stream ends here, fall short of it.
   */
  private def violatesContentLength(streamId: Int, payloadLength: Int, endStream: Boolean): Boolean =
    remainingContentLength.get(streamId) match {
      case Some(remaining) =>
        val left = remaining - payloadLength
        if (left < 0 || (endStream && left != 0)) true
        else {
          if (endStream) remainingContentLength -= streamId
          else remainingContentLength.update(streamId, left)
          false
        }
      case None => false
    }
  private var largestIncomingStreamId = 0
  private var outstandingConnectionLevelWindow = Http2Protocol.InitialWindowSize
  private var totalBufferedData = 0

  /**
   * The "last peer-initiated stream that was or might be processed on the sending endpoint in this connection"
   *
   * @see https://www.rfc-editor.org/rfc/rfc9113.html#section-6.8
   */
  def lastStreamId(): Int = largestIncomingStreamId

  private var maxConcurrentStreams = Http2Protocol.InitialMaxConcurrentStreams
  def setMaxConcurrentStreams(newValue: Int): Unit = maxConcurrentStreams = newValue

  /**
   * @return true if the number of outgoing Active streams (Active includes Open
   *         and any variant of HalfClosedXxx) doesn't exceed MaxConcurrentStreams
   */
  def hasCapacityToCreateStreams: Boolean = {
    // StreamStates only contains streams in active states (active states are any variation
    // of Open, HalfClosed) so using the `size` works fine to compute the capacity
    activeStreamCount() < maxConcurrentStreams
  }

  /** Called when all streams in StreamHandling buffers are closed and the stage is completing. */
  def onAllStreamsClosed(): Unit

  private def streamFor(streamId: Int): StreamState =
    streamStates.getOrNull(streamId) match {
      case null =>
        if (streamId <= largestIncomingStreamId) Closed // closed streams are never put into the map
        else if (isUpgraded && streamId == 1) {
          require(isServer)
          // Stream 1 is implicitly "half-closed" from the client toward the server (see Section 5.1), since the request is completed as an HTTP/1.1 request
          // https://http2.github.io/http2-spec/#discover-http
          largestIncomingStreamId = streamId
          streamStates.put(streamId, HalfClosedRemoteWaitingForOutgoingStream(0))
          HalfClosedRemoteWaitingForOutgoingStream(0)
        } else {
          largestIncomingStreamId = streamId
          streamStates.put(streamId, Idle)
          Idle
        }
      case state => state
    }

  def activeStreamCount(): Int = streamStates.size

  /** Called by Http2ServerDemux to let the state machine handle StreamFrameEvents */
  def handleStreamEvent(e: StreamFrameEvent): Unit =
    updateState(e.streamId, _.handle(e), "handleStreamEvent", e.frameTypeName)

  /** Called by Http2ServerDemux when a stream comes in from the user-handler */
  def handleOutgoingCreated(outgoing: Http2SubStream): Unit = {
    // a response to a HEAD request must not carry content (RFC 9110 section 9.3.2); the headers are sent unchanged
    // so that the peer still learns the `content-length` it would have received for a GET
    val stream =
      if (headRequestStreamIds.remove(outgoing.streamId) && outgoing.hasEntity) {
        outgoing.data.foreach(_.runWith(Sink.cancelled)(subFusingMaterializer))
        outgoing.withoutData
      } else outgoing

    stream.initialHeaders.priorityInfo.foreach(multiplexer.updatePriority)
    if (streamFor(stream.streamId) != Closed) {
      multiplexer.pushControlFrame(stream.initialHeaders)

      if (stream.initialHeaders.endStream) {
        updateState(stream.streamId, _.handleOutgoingCreatedAndFinished(stream.correlationAttributes),
          "handleOutgoingCreatedAndFinished")
      } else {
        val outStream = OutStream(stream)
        updateState(stream.streamId, _.handleOutgoingCreated(outStream, stream.correlationAttributes),
          "handleOutgoingCreated")
      }
    } else
      // stream was cancelled by peer before our response was ready
      stream.data.foreach(_.runWith(Sink.cancelled)(subFusingMaterializer))

  }

  // Called by the outgoing stream multiplexer when that side of the stream is ended.
  def handleOutgoingEnded(streamId: Int): Unit =
    updateState(streamId, _.handleOutgoingEnded(), "handleOutgoingEnded")

  def handleOutgoingFailed(streamId: Int, cause: Throwable): Unit =
    updateState(streamId, _.handleOutgoingFailed(cause), "handleOutgoingFailed")

  /** Called by multiplexer to distribute changes from INITIAL_WINDOW_SIZE to all streams */
  def distributeWindowDeltaToAllStreams(delta: Int): Unit = {
    var overflow = false
    def overflowsWithInitialWindow(extraInitialWindow: Int): Boolean =
      multiplexer.currentInitialWindow.toLong + extraInitialWindow > Http2Protocol.MaxWindowSize
    updateAllStates({
        case s: Sending =>
          if (!s.applyInitialWindowDelta(delta)) overflow = true
          s
        // Before the response starts, WINDOW_UPDATE credit is stored separately; a future OutStream will use
        // the new initial window. Validate that sum now rather than deferring the error until the response.
        case s: CollectingIncomingData =>
          if (overflowsWithInitialWindow(s.extraInitialWindow)) overflow = true
          s
        case s: OpenReceivingDataFirst =>
          if (overflowsWithInitialWindow(s.extraInitialWindow)) overflow = true
          s
        case s: HalfClosedRemoteWaitingForOutgoingStream =>
          if (overflowsWithInitialWindow(s.extraInitialWindow)) overflow = true
          s
        case x => x
      }, "distributeWindowDeltaToAllStreams")
    // SETTINGS changes have connection error scope, unlike a stream's WINDOW_UPDATE. Throw only after the
    // state machine has completed its update, so that postStop can still shut down all remaining streams.
    if (overflow)
      throw new Http2Compliance.Http2ProtocolException(ErrorCode.FLOW_CONTROL_ERROR,
        "SETTINGS_INITIAL_WINDOW_SIZE would exceed maximum stream-level flow-control window size")
  }

  /** Called by the multiplexer if ready to send a data frame */
  def pullNextFrame(streamId: Int, maxSize: Int): PullFrameResult =
    updateStateAndReturn(streamId, _.pullNextFrame(maxSize), "pullNextFrame")

  /** Entry-point to handle IncomingStreamBuffer.onPull through the state machine */
  def incomingStreamPulled(streamId: Int): Unit =
    updateState(streamId, _.incomingStreamPulled(), "incomingStreamPulled")

  private def updateAllStates(handle: StreamState => StreamState, event: String, eventArg: AnyRef = null): Unit =
    streamStates.keys.foreach(streamId => updateState(streamId.toInt, handle, event, eventArg))

  private def updateState(
      streamId: Int, handle: StreamState => StreamState, event: String, eventArg: AnyRef = null): Unit =
    updateStateAndReturn(streamId, x => (handle(x), ()), event, eventArg)

  // Calling multiplexer.enqueueOutStream directly out of the state machine is not allowed, because it might try to
  // reenter the state machine with `pullNextState`. This call defers enqueuing until the current state machine operation
  // is done.
  private var deferredStreamToEnqueue: Int = -1
  private def enqueueOutStream(streamId: Int): Unit = if (stateMachineRunning) {
    require(deferredStreamToEnqueue == -1, "Only one stream can be enqueued during a single state change")
    deferredStreamToEnqueue = streamId
  } else
    multiplexer.enqueueOutStream(streamId)

  /**
   * Removes a closed stream, then reports when the last stream has closed and pulls more substreams if the freed
   * capacity allows. Shared by the state machine and the incoming-entity cancellation path, which bypasses it.
   */
  private def streamClosed(streamId: Int): Unit = {
    streamStates.remove(streamId)
    headRequestStreamIds -= streamId
    remainingContentLength -= streamId
    if (streamStates.isEmpty) onAllStreamsClosed()
    tryPullSubStreams()
  }

  private var stateMachineRunning = false
  private def updateStateAndReturn[R](streamId: Int, handle: StreamState => (StreamState, R), event: String,
      eventArg: AnyRef = null): R = {
    require(!stateMachineRunning, "State machine already running")
    stateMachineRunning = true

    val oldState = streamFor(streamId)

    val (newState, ret) = handle(oldState)
    newState match {
      case Closed   => streamClosed(streamId)
      case newState => streamStates.put(streamId, newState)
    }

    debug(
      s"Incoming side of stream [$streamId] changed state: ${oldState.stateName} -> ${newState.stateName} after handling [$event${if (eventArg ne
          null)
          s"($eventArg)"
        else ""}]")

    stateMachineRunning = false
    if (deferredStreamToEnqueue != -1) {
      val streamId = deferredStreamToEnqueue
      deferredStreamToEnqueue = -1
      if (streamStates.contains(streamId))
        multiplexer.enqueueOutStream(streamId)
    }
    ret
  }

  /** Called to cleanup any state when the connection is torn down */
  def shutdownStreamHandling(): Unit = updateAllStates({ id => id.shutdown(); Closed }, "shutdownStreamHandling")

  /** Fail open incoming entity outlets on client read EOF, without changing upload lifecycle. */
  def failOpenIncomingEntities(): Unit = streamStates.valuesIterator.foreach {
    case receiving: ReceivingDataWithBuffer => receiving.failOpenIncomingEntity()
    case _                                  => ()
  }

  def resetStream(streamId: Int, errorCode: ErrorCode): Unit = {
    // Hand the RST_STREAM to the multiplexer first: closing the last stream can complete the connection once all
    // outgoing data is flushed, and a frame pushed after that would be lost.
    multiplexer.pushControlFrame(RstStreamFrame(streamId, errorCode))
    updateState(streamId, _ => Closed, "resetStream") // force stream to be closed
  }

  /**
   * States roughly correspond to states as given in https://http2.github.io/http2-spec/#StreamStates.
   *
   * Substates were introduced to also track the state of our user-side
   *
   * States:
   *   * Idle                                     | server | client <- Idle, initial state, usually not tracked explicitly
   *   * OpenReceivingDataFirst                   | server          <- Open, got (request) HEADERS but didn't send (response) HEADERS yet
   *   * OpenSendingData                          |          client <- Open, sent (request) HEADERS but didn't receive (response) HEADERS yet
   *   * Open                                     | server | client <- Open, bidirectional, both sides sent HEADERS
   *   * HalfClosedLocalWaitingForPeerStream      |          client <- HalfClosedLocal, our stream side done, waiting for peer HEADERS
   *   * HalfClosedLocal                          | server | client <- HalfClosedLocal, all HEADERS sent, sent our endStream = true, receiving DATA from peer
   *   * HalfClosedRemoteWaitingForOutgoingStream | server          <- HalfClosedRemote, waiting for our HEADERS from user
   *   * HalfClosedRemoteSendingData              | server | client <- HalfClosedRemote, sent our HEADERS, now sending out DATA
   *   * Closed                                   | server | client <- Closed, final state, not tracked explicitly
   *
   * Server states:
   *   * Idle -> OpenReceivingDataFirst: on receiving request HEADERS with endStream = false
   *   * Idle -> HalfClosedRemoteWaitingForOutgoingStream: on receiving HEADERS with endStream = true
   *   * OpenReceivingDataFirst -> HalfClosedRemoteWaitingForOutgoingStream: after receiving endStream
   *   * OpenReceivingDataFirst -> Open: after user provided response before request was fully streamed in
   *   * HalfClosedRemoteWaitingForOutgoingStream -> HalfClosedRemoteSendingData: we sent response HEADERS with endStream = false
   *   * HalfClosedRemoteWaitingForOutgoingStream -> Closed: we sent response HEADERS with endStream = true
   *   * HalfClosedRemoteSendingData -> Closed: we sent DATA with endStream = true
   *   * Open -> HalfClosedRemoteSendingData: on receiving request DATA with endStream = true
   *   * Open -> HalfClosedLocal: on receiving response DATA with endStream = true before request has been fully received (uncommon)
   *   * HalfClosedLocal -> Closed: on receiving request DATA with endStream = true
   *
   * Client states:
   *   * Idle -> OpenSendingData: on sending out (request) HEADERS with endStream = false
   *   * Idle -> HalfClosedLocalWaitingForPeerStream: on sending out (request) HEADERS with endStream = true
   *   * OpenSendingData -> HalfClosedLocalWaitingForPeerStream: on sending out DATA with endStream = true
   *   * OpenSendingData -> Open: on receiving response HEADERS before request DATA was fully sent out
   *   * HalfClosedLocalWaitingForPeerStream -> HalfClosedLocal: on receiving response HEADERS with endStream = false
   *   * HalfClosedLocalWaitingForPeerStream -> Closed: on receiving response HEADERS with endStream = true
   *   * HalfClosedLocal -> Closed: on receiving response DATA with endStream = true
   *   * OpenSendingData -> OpenReceivingNoContent: on receiving content-free (204/205/304) HEADERS without END_STREAM
   *   * HalfClosedLocalWaitingForPeerStream -> HalfClosedLocalReceivingNoContent: same, after the upload ended
   *   * OpenReceivingNoContent -> HalfClosedLocalReceivingNoContent: on ending the upload before the response wire end
   *   * OpenReceivingNoContent -> HalfClosedRemoteSendingData: on receiving the empty response DATA with END_STREAM
   *   * HalfClosedLocalReceivingNoContent -> Closed: on receiving the empty response DATA with END_STREAM
   *   * Open -> HalfClosedLocal: on sending out request DATA with endStream = true
   *   * Open -> HalfClosedRemoteSendingData: on receiving response DATA with endStream = true before request has been fully sent out (uncommon)
   *   * HalfClosedRemoteSendingData -> Closed: on sending out request DATA with endStream = true
   */
  sealed abstract class StreamState { self: Product =>
    def handle(event: StreamFrameEvent): StreamState

    def stateName: String = productPrefix

    /** Called when we receive a user-created stream (that is open for more data) */
    def handleOutgoingCreated(outStream: OutStream, correlationAttributes: Map[AttributeKey[?], ?]): StreamState = {
      warning(
        s"handleOutgoingCreated received unexpectedly in state $stateName. This indicates a bug in Pekko HTTP, please report it to the issue tracker.")
      this
    }

    /** Called when we receive a user-created stream that is already closed */
    def handleOutgoingCreatedAndFinished(correlationAttributes: Map[AttributeKey[?], ?]): StreamState = {
      warning(
        s"handleOutgoingCreatedAndFinished received unexpectedly in state $stateName. This indicates a bug in Pekko HTTP, please report it to the issue tracker.")
      this
    }
    def handleOutgoingEnded(): StreamState = {
      warning(
        s"handleOutgoingEnded received unexpectedly in state $stateName. This indicates a bug in Pekko HTTP, please report it to the issue tracker.")
      this
    }
    def handleOutgoingFailed(cause: Throwable): StreamState = {
      warning(
        s"handleOutgoingFailed received unexpectedly in state $stateName. This indicates a bug in Pekko HTTP, please report it to the issue tracker.")
      this
    }
    def receivedUnexpectedFrame(e: StreamFrameEvent): StreamState = {
      debug(s"Received unexpected frame of type ${e.frameTypeName} for stream ${e.streamId} in state $stateName")
      pushGOAWAY(ErrorCode.PROTOCOL_ERROR,
        s"Received unexpected frame of type ${e.frameTypeName} for stream ${e.streamId} in state $stateName")
      shutdown()
      Closed
    }

    /**
     * A final response whose status forbids content (`allowsEntity == false`), e.g. 204, 205 or 304. Such a response can
     * end in a later empty DATA frame. Informational (1xx) responses are not final and are excluded.
     */
    protected def isContentFreeFinalResponse(frame: ParsedHeadersFrame): Boolean =
      frame.keyValuePairs.exists {
        case (":status", status: String) =>
          isContentFreeFinalStatus(status)
        case _ => false
      }

    protected def expectIncomingStream(
        event: StreamFrameEvent,
        nextStateEmpty: StreamState,
        nextStateStream: IncomingStreamBuffer => StreamState,
        correlationAttributes: Map[AttributeKey[?], ?] = Map.empty,
        noContentState: Option[StreamState] = None): StreamState =
      event match {
        case frame: ParsedHeadersFrame if !isServer && !frame.endStream && frame.keyValuePairs.exists {
              case (":status", status: String) =>
                Http2ResponseStatus.isInformational(status) && status != "101"
              case _ => false
            } =>
          // Fully parse each informational section in the HTTP layer before filtering it from delivery.
          // There is no per-stream count limit on informational responses before the final response.
          // Keep waiting for the final response without changing an ongoing request upload.
          dispatchSubstream(frame, Left(ByteString.empty), correlationAttributes)
          this

        case frame @ ParsedHeadersFrame(streamId, endStream, _, _, _) =>
          if (isServer && isHeadRequest(frame)) headRequestStreamIds += streamId
          val contentLength = if (isServer) declaredContentLength(frame) else -1L
          if (!endStream && contentLength >= 0) remainingContentLength.update(streamId, contentLength)
          // Resolve the expectation before collecting or exposing any entity data. It is an
          // informational field block, not the start of the final response substream.
          if (isServer && !endStream && shouldSendContinue(frame))
            multiplexer.pushControlFrame(
              ParsedHeadersFrame(streamId, endStream = false, Seq(":status" -> "100"), None, None))
          if (endStream && contentLength > 0) {
            // the request ends without the content its `content-length` announces, so it is malformed
            multiplexer.pushControlFrame(RstStreamFrame(streamId, ErrorCode.PROTOCOL_ERROR))
            Closed
          } else if (endStream) {
            dispatchSubstream(frame, Left(ByteString.empty), correlationAttributes)
            nextStateEmpty
          } else if (noContentState.isDefined && isContentFreeFinalResponse(frame)) {
            // A content-free response can end in a later empty DATA frame. Expose its known-empty entity now,
            // but keep the wire stream alive until END_STREAM, without creating an unconsumed entity source.
            dispatchSubstream(frame, Left(ByteString.empty), correlationAttributes)
            noContentState.get
          } else if (settings.minCollectStrictEntitySize > 0)
            CollectingIncomingData(frame, correlationAttributes, ByteString.empty, extraInitialWindow = 0)
          else
            dispatchStream(streamId, frame, ByteString.empty, correlationAttributes, nextStateStream)

        case x => receivedUnexpectedFrame(x)
      }

    protected def dispatchStream(
        streamId: Int,
        headers: ParsedHeadersFrame,
        initialData: ByteString,
        correlationAttributes: Map[AttributeKey[?], ?],
        nextStateStream: IncomingStreamBuffer => StreamState): StreamState = {
      val subSource = new SubSourceOutlet[Any](s"substream-out-$streamId")
      val buffer = new IncomingStreamBuffer(streamId, subSource)
      if (initialData.nonEmpty) buffer.onDataFrame(DataFrame(streamId, endStream = false, initialData)) // fabricate frame
      dispatchSubstream(headers, Right(Source.fromGraph(subSource.source)), correlationAttributes)
      nextStateStream(buffer)
    }

    def pullNextFrame(maxSize: Int): (StreamState, PullFrameResult) =
      throw new IllegalStateException(s"pullNextFrame not supported in state $stateName")
    def incomingStreamPulled(): StreamState =
      throw new IllegalStateException(s"incomingStreamPulled not supported in state $stateName")

    /** Called to cleanup any state when the connection is torn down */
    def shutdown(): Unit = ()
  }

  case object Idle extends StreamState {
    def handle(event: StreamFrameEvent): StreamState =
      if (event.isInstanceOf[ParsedHeadersFrame] && activeStreamCount() > settings.maxConcurrentStreams) {
        // When trying to open a new Stream, if that op would exceed the maxConcurrentStreams, then refuse the op
        debug("Peer trying to open stream that would exceed `maxConcurrentStreams`, refusing stream")
        multiplexer.pushControlFrame(RstStreamFrame(event.streamId, ErrorCode.REFUSED_STREAM))
        Closed
      } else
        expectIncomingStream(event, HalfClosedRemoteWaitingForOutgoingStream(0), OpenReceivingDataFirst(_, 0))

    override def handleOutgoingCreated(
        outStream: OutStream, correlationAttributes: Map[AttributeKey[?], ?]): StreamState =
      OpenSendingData(outStream, correlationAttributes)
    override def handleOutgoingCreatedAndFinished(correlationAttributes: Map[AttributeKey[?], ?]): StreamState =
      HalfClosedLocalWaitingForPeerStream(correlationAttributes)
  }

  /** Special state that allows collecting some incoming data before dispatching it either as strict or streamed entity */
  case class CollectingIncomingData(
      headers: ParsedHeadersFrame,
      correlationAttributes: Map[AttributeKey[?], ?],
      collectedData: ByteString,
      extraInitialWindow: Int) extends ReceivingData {

    override protected def onDataFrame(dataFrame: DataFrame): StreamState = {
      val newData = collectedData ++ dataFrame.payload

      if (dataFrame.endStream) {
        totalBufferedData -= newData.length
        dispatchSubstream(headers, Left(newData), correlationAttributes)
        HalfClosedRemoteWaitingForOutgoingStream(extraInitialWindow)
      } else if (newData.length >= settings.minCollectStrictEntitySize)
        dispatchStream(dataFrame.streamId, headers, newData, correlationAttributes,
          OpenReceivingDataFirst(_, extraInitialWindow))
      else
        copy(collectedData = newData)
    }

    override protected def onTrailer(parsedHeadersFrame: ParsedHeadersFrame): StreamState = this // trailing headers not supported for requests right now
    override protected def incrementWindow(delta: Int): StreamState =
      copy(extraInitialWindow = extraInitialWindow + delta)
    // the data collected so far is dropped together with this state, so stop reserving connection-level window for it
    override protected def onRstStreamFrame(rstStreamFrame: RstStreamFrame): Unit =
      totalBufferedData -= collectedData.length
    // nothing has been dispatched yet, only the collected data has to be released
    override protected def failIncoming(cause: Throwable): Unit =
      totalBufferedData -= collectedData.length
  }
  case class OpenReceivingDataFirst(buffer: IncomingStreamBuffer, extraInitialWindow: Int = 0)
      extends ReceivingDataWithBuffer(HalfClosedRemoteWaitingForOutgoingStream(extraInitialWindow)) {
    override def handleOutgoingCreated(
        outStream: OutStream, correlationAttributes: Map[AttributeKey[?], ?]): StreamState = {
      if (outStream.increaseWindow(extraInitialWindow)) Open(buffer, outStream)
      else {
        outStream.cancelStream()
        multiplexer.pushControlFrame(RstStreamFrame(outStream.streamId, ErrorCode.FLOW_CONTROL_ERROR))
        Closed
      }
    }
    override def handleOutgoingCreatedAndFinished(correlationAttributes: Map[AttributeKey[?], ?]): StreamState =
      HalfClosedLocal(buffer)
    override def handleOutgoingEnded(): StreamState = Closed

    override def incrementWindow(delta: Int): StreamState = copy(extraInitialWindow = extraInitialWindow + delta)
  }
  trait Sending extends StreamState { self: Product =>
    protected def outStream: OutStream

    override def pullNextFrame(maxSize: Int): (StreamState, PullFrameResult) = {
      val frame = outStream.nextFrame(maxSize)

      val res =
        outStream.endStreamIfPossible() match {
          case Some(trailer) =>
            PullFrameResult.SendFrameAndTrailer(frame, trailer)
          case None =>
            PullFrameResult.SendFrame(frame, outStream.canSend)
        }

      val nextState =
        if (outStream.isDone) this.handleOutgoingEnded()
        else this

      (nextState, res)
    }

    def handleWindowUpdate(windowUpdate: WindowUpdateFrame): StreamState =
      increaseWindow(windowUpdate.windowSizeIncrement)

    override def handleOutgoingFailed(cause: Throwable): StreamState = Closed

    abstract override def shutdown(): Unit = {
      outStream.cancelStream()
      super.shutdown()
    }

    def applyInitialWindowDelta(delta: Int): Boolean = outStream.increaseWindow(delta)

    def increaseWindow(delta: Int): StreamState = {
      if (outStream.increaseWindow(delta)) this
      else {
        multiplexer.pushControlFrame(RstStreamFrame(outStream.streamId, ErrorCode.FLOW_CONTROL_ERROR))
        Closed
      }
    }
  }

  case class OpenSendingData(outStream: OutStream, correlationAttributes: Map[AttributeKey[?], ?]) extends StreamState
      with Sending {
    override def handle(event: StreamFrameEvent): StreamState = event match {
      case _: ParsedHeadersFrame =>
        expectIncomingStream(event, HalfClosedRemoteSendingData(outStream), Open(_, outStream), correlationAttributes,
          Some(OpenReceivingNoContent(outStream)))
      case w: WindowUpdateFrame =>
        handleWindowUpdate(w)
      case r: RstStreamFrame =>
        outStream.cancelStream()
        Closed
      case _ =>
        outStream.cancelStream()
        receivedUnexpectedFrame(event)
    }

    override def handleOutgoingEnded(): StreamState = HalfClosedLocalWaitingForPeerStream(correlationAttributes)
  }
  case class HalfClosedLocalWaitingForPeerStream(correlationAttributes: Map[AttributeKey[?], ?]) extends StreamState {
    override def handle(event: StreamFrameEvent): StreamState = event match {
      case _: WindowUpdateFrame =>
        // We're not planning on sending any data on this stream anymore, so we don't care about window updates.
        this
      case _ =>
        expectIncomingStream(event, Closed, HalfClosedLocal(_), correlationAttributes,
          Some(HalfClosedLocalReceivingNoContent(event.streamId)))
    }
  }

  /** Receives the wire termination of a content-free (204/205/304) response whose known-empty entity has already been delivered. */
  sealed abstract class ReceivingNoContent extends ReceivingData { self: Product =>
    protected def streamId: Int
    protected def afterRemoteEnd: StreamState

    private def rejectContent(): StreamState = {
      shutdown()
      multiplexer.pushControlFrame(RstStreamFrame(streamId, ErrorCode.PROTOCOL_ERROR))
      Closed
    }

    override protected def onDataFrame(frame: DataFrame): StreamState = {
      // ReceivingData accounted for these bytes; this state does not retain a body buffer.
      totalBufferedData -= frame.payload.length
      if (frame.payload.nonEmpty) rejectContent()
      else if (frame.endStream) afterRemoteEnd
      else this
    }

    override protected def onTrailer(frame: ParsedHeadersFrame): StreamState = rejectContent()
    override protected def onRstStreamFrame(frame: RstStreamFrame): Unit = shutdown()
  }

  case class HalfClosedLocalReceivingNoContent(streamId: Int) extends ReceivingNoContent {
    override protected def afterRemoteEnd: StreamState = Closed
    override protected def incrementWindow(delta: Int): StreamState = this
  }

  case class OpenReceivingNoContent(outStream: OutStream) extends ReceivingNoContent with Sending {
    override protected def streamId: Int = outStream.streamId
    override protected def afterRemoteEnd: StreamState = HalfClosedRemoteSendingData(outStream)
    override protected def incrementWindow(delta: Int): StreamState = increaseWindow(delta)
    override def handleOutgoingEnded(): StreamState = HalfClosedLocalReceivingNoContent(streamId)
  }

  sealed abstract class ReceivingData extends StreamState { self: Product =>
    def handle(event: StreamFrameEvent): StreamState = event match {
      case d: DataFrame =>
        outstandingConnectionLevelWindow -= d.sizeInWindow
        totalBufferedData += d.payload.length // padding can be seen as instantly discarded

        if (outstandingConnectionLevelWindow < 0) {
          shutdown()
          pushGOAWAY(ErrorCode.FLOW_CONTROL_ERROR, "Received more data than connection-level window would allow")
          Closed
        } else {
          val nextState =
            if (violatesContentLength(d.streamId, d.payload.length, d.endStream)) {
              // ReceivingData accounted for these bytes, but they are dropped with the stream
              totalBufferedData -= d.payload.length
              rejectContentLength(d.streamId)
            } else onDataFrame(d)

          val windowSizeIncrement =
            flowController.onConnectionDataReceived(outstandingConnectionLevelWindow, totalBufferedData)
          if (windowSizeIncrement > 0) {
            multiplexer.pushControlFrame(WindowUpdateFrame(Http2Protocol.NoStreamId, windowSizeIncrement))
            outstandingConnectionLevelWindow += windowSizeIncrement
          }
          nextState
        }
      case r: RstStreamFrame =>
        onRstStreamFrame(r)
        Closed

      case h: ParsedHeadersFrame if h.endStream && violatesContentLength(h.streamId, 0, endStream = true) =>
        rejectContentLength(h.streamId)

      case h: ParsedHeadersFrame =>
        onTrailer(h)

      case w: WindowUpdateFrame =>
        incrementWindow(w.windowSizeIncrement)

      case _ => receivedUnexpectedFrame(event)
    }

    /**
     * The request's DATA does not add up to its `content-length` (RFC 9113 section 8.1.1): fail the entity, stop any
     * response and reset the stream with PROTOCOL_ERROR. The connection and its other streams carry on.
     */
    private def rejectContentLength(streamId: Int): StreamState = {
      failIncoming(EntityStreamException(ErrorInfo(
        "HTTP/2 request content does not match its content-length",
        s"The DATA frames received on stream $streamId do not add up to the declared content-length.")))
      shutdown()
      multiplexer.pushControlFrame(RstStreamFrame(streamId, ErrorCode.PROTOCOL_ERROR))
      multiplexer.dequeueOutStream(streamId)
      Closed
    }

    /** Fails what the application sees of the incoming data with `cause`, and releases what is buffered of it */
    protected def failIncoming(cause: Throwable): Unit = ()

    protected def onDataFrame(dataFrame: DataFrame): StreamState
    protected def onTrailer(parsedHeadersFrame: ParsedHeadersFrame): StreamState
    protected def incrementWindow(delta: Int): StreamState
    protected def onRstStreamFrame(rstStreamFrame: RstStreamFrame): Unit
  }
  sealed abstract class ReceivingDataWithBuffer(afterEndStreamReceived: StreamState) extends ReceivingData {
    self: Product =>
    protected def buffer: IncomingStreamBuffer

    final def failOpenIncomingEntity(): Unit = if (!buffer.isDone) buffer.shutdown()

    override protected def onDataFrame(dataFrame: DataFrame): StreamState = {
      buffer.onDataFrame(dataFrame)
      afterBufferEvent
    }
    override protected def onTrailer(parsedHeadersFrame: ParsedHeadersFrame): StreamState = {
      buffer.onTrailingHeaders(parsedHeadersFrame)
      afterBufferEvent
    }

    override protected def onRstStreamFrame(rstStreamFrame: RstStreamFrame): Unit =
      buffer.onRstStreamFrame(rstStreamFrame)

    override protected def failIncoming(cause: Throwable): Unit = buffer.fail(cause)

    override def incomingStreamPulled(): StreamState = {
      buffer.dispatchNextChunk()
      afterBufferEvent
    }

    override def shutdown(): Unit = {
      buffer.shutdown()
      super.shutdown()
    }

    def incrementWindow(delta: Int): StreamState

    def afterBufferEvent: StreamState = if (buffer.isDone) afterEndStreamReceived else this
  }

  // on the incoming side there's (almost) no difference between Open and HalfClosedLocal
  case class Open(buffer: IncomingStreamBuffer, outStream: OutStream)
      extends ReceivingDataWithBuffer(HalfClosedRemoteSendingData(outStream)) with Sending {
    override def handleOutgoingEnded(): StreamState = HalfClosedLocal(buffer)

    override protected def onRstStreamFrame(rstStreamFrame: RstStreamFrame): Unit = {
      super.onRstStreamFrame(rstStreamFrame)
      outStream.cancelStream()
    }
    override def incrementWindow(delta: Int): StreamState = {
      if (outStream.increaseWindow(delta)) this
      else {
        outStream.cancelStream()
        multiplexer.pushControlFrame(RstStreamFrame(outStream.streamId, ErrorCode.FLOW_CONTROL_ERROR))
        Closed
      }
    }
  }

  /**
   * We have closed the outgoing stream, but the incoming stream is still going.
   */
  case class HalfClosedLocal(buffer: IncomingStreamBuffer) extends ReceivingDataWithBuffer(Closed) {
    override def incrementWindow(delta: Int): StreamState = this // no op, already finished sending
  }

  case class HalfClosedRemoteWaitingForOutgoingStream(extraInitialWindow: Int) extends StreamState {
    // FIXME: DRY with below
    override def handle(event: StreamFrameEvent): StreamState = event match {
      case r: RstStreamFrame    => Closed
      case w: WindowUpdateFrame => copy(extraInitialWindow = extraInitialWindow + w.windowSizeIncrement)
      case _                    => receivedUnexpectedFrame(event)
    }

    override def handleOutgoingCreated(
        outStream: OutStream, correlationAttributes: Map[AttributeKey[?], ?]): StreamState = {
      if (outStream.increaseWindow(extraInitialWindow)) HalfClosedRemoteSendingData(outStream)
      else {
        outStream.cancelStream()
        multiplexer.pushControlFrame(RstStreamFrame(outStream.streamId, ErrorCode.FLOW_CONTROL_ERROR))
        Closed
      }
    }
    override def handleOutgoingCreatedAndFinished(correlationAttributes: Map[AttributeKey[?], ?]): StreamState = Closed
  }

  /**
   * They have closed the incoming stream, but the outgoing stream is still going.
   */
  case class HalfClosedRemoteSendingData(outStream: OutStream) extends StreamState with Sending {
    def handle(event: StreamFrameEvent): StreamState = event match {
      case r: RstStreamFrame =>
        outStream.cancelStream()
        Closed
      case w: WindowUpdateFrame => handleWindowUpdate(w)
      case _                    => receivedUnexpectedFrame(event)
    }

    override def handleOutgoingEnded(): StreamState = Closed
  }
  case object Closed extends StreamState {
    override def handleOutgoingEnded(): StreamState = this
    override def handleOutgoingFailed(cause: Throwable): StreamState = this

    def handle(event: StreamFrameEvent): StreamState = event match {
      // https://http2.github.io/http2-spec/#StreamStates
      // Endpoints MUST ignore WINDOW_UPDATE or RST_STREAM frames received in this state,
      case _: RstStreamFrame | _: WindowUpdateFrame | _: DataFrame =>
        // FIXME: do we need to adjust connection level window here as described in https://httpwg.org/specs/rfc7540.html#StreamStates:
        // > Flow-controlled frames (i.e., DATA) received after sending RST_STREAM are counted toward the connection
        // > flow-control window. Even though these frames might be ignored, because they are sent before the sender
        // > receives the RST_STREAM, the sender will consider the frames to count against the flow-control window.
        //
        // or should connection window adjustments be done in Http2ServerDemux directly?
        this
      case _ =>
        receivedUnexpectedFrame(event)
    }
  }

  class IncomingStreamBuffer(streamId: Int, outlet: SubSourceOutlet[Any]) extends OutHandler {
    private var buffer: ByteString = ByteString.empty
    private var trailingHeaders: Option[HttpEntity.ChunkStreamPart] = None
    private var wasClosed: Boolean = false
    private var outstandingStreamWindow: Int = Http2Protocol.InitialWindowSize // adapt if we negotiate greater sizes by settings
    outlet.setHandler(this)

    def onPull(): Unit = incomingStreamPulled(streamId)
    override def onDownstreamFinish(cause: Throwable): Unit = {
      debug(s"Incoming side of stream [$streamId]: cancelling because downstream finished")
      multiplexer.pushControlFrame(RstStreamFrame(streamId, ErrorCode.CANCEL))
      wasClosed = true
      discardBuffer()
      trailingHeaders = None
      // FIXME: go through state machine and don't manipulate vars directly here
      streamClosed(streamId)
    }

    /**
     * Drops what is still buffered and releases the connection-level window it reserved. Without the release the
     * connection-level flow controller keeps counting these bytes as buffered forever, so it stops replenishing the
     * connection window and every stream on the connection eventually stalls.
     */
    private def discardBuffer(): Unit = {
      totalBufferedData -= buffer.length
      buffer = ByteString.empty
    }

    def isDone: Boolean = outlet.isClosed

    def onDataFrame(data: DataFrame): Unit =
      if (wasClosed) {
        shutdown()
        pushGOAWAY(ErrorCode.PROTOCOL_ERROR, s"Received unexpected DATA frame after stream was already (half-)closed")
      } else {
        wasClosed = data.endStream

        outstandingStreamWindow -= data.sizeInWindow
        if (outstandingStreamWindow < 0) {
          // the frame is never buffered, so stop reserving connection-level window for it as well
          totalBufferedData -= data.payload.length
          shutdown()
          multiplexer.pushControlFrame(RstStreamFrame(streamId, ErrorCode.FLOW_CONTROL_ERROR))
          // also stop sending the response if that has already started
          multiplexer.dequeueOutStream(streamId)
        } else {
          buffer ++= data.payload
          debug(
            s"Received DATA ${data.sizeInWindow} for stream [$streamId], remaining window space now $outstandingStreamWindow, buffered: ${buffer.length}")
          dispatchNextChunk()
        }
      }
    def onTrailingHeaders(headers: ParsedHeadersFrame): Unit = {
      trailingHeaders = wrapTrailingHeaders(headers)
      if (headers.endStream)
        // simulate end stream by empty dataframe
        onDataFrame(DataFrame(headers.streamId, endStream = true, ByteString.empty))
      else
        pushGOAWAY(Http2Protocol.ErrorCode.PROTOCOL_ERROR, "Got unexpected mid-stream HEADERS frame")
    }
    def onRstStreamFrame(rst: RstStreamFrame): Unit = {
      outlet.fail(new PeerClosedStreamException(rst.streamId, rst.errorCode))
      discardBuffer()
      trailingHeaders = None
      wasClosed = true
    }

    def dispatchNextChunk(): Unit = {
      if (buffer.nonEmpty && outlet.isAvailable) {
        val dataSize = buffer.length min settings.requestEntityChunkSize
        outlet.push(buffer.take(dataSize))
        buffer = buffer.drop(dataSize)

        totalBufferedData -= dataSize

        debug(
          s"Dispatched chunk of $dataSize for stream [$streamId], remaining window space now $outstandingStreamWindow, buffered: ${buffer.length}")
        updateWindows()
      }
      if (buffer.isEmpty && wasClosed) {
        trailingHeaders match {
          case Some(trailer) =>
            if (outlet.isAvailable) {
              outlet.push(trailer)
              trailingHeaders = None
              outlet.complete()
            }
          case None =>
            outlet.complete()
        }

      }
    }

    private def updateWindows(): Unit = {
      val IncomingFlowController.WindowIncrements(connectionLevel, streamLevel) = flowController.onStreamDataDispatched(
        outstandingConnectionLevelWindow, totalBufferedData,
        outstandingStreamWindow, buffer.length)

      if (connectionLevel > 0) {
        multiplexer.pushControlFrame(WindowUpdateFrame(Http2Protocol.NoStreamId, connectionLevel))
        outstandingConnectionLevelWindow += connectionLevel
      }
      if (streamLevel > 0 && !wasClosed /* No reason to update window on closed streams */ ) {
        multiplexer.pushControlFrame(WindowUpdateFrame(streamId, streamLevel))
        outstandingStreamWindow += streamLevel
      }

      debug(
        s"adjusting con-level window by $connectionLevel, stream-level window by $streamLevel, " +
        s"remaining window space now $outstandingStreamWindow, buffered: ${buffer.length}, " +
        s"remaining connection window space now $outstandingConnectionLevelWindow, total buffered: $totalBufferedData")
    }

    /**
     * Tears the incoming side of the stream down. Everything still buffered is dropped here, so it has to be released
     * from the connection-level accounting like on the other discard paths: the stream-level flow control error in
     * `onDataFrame` resets a single stream and leaves the connection running, so anything kept counted there would
     * stall the whole connection for good.
     */
    def shutdown(): Unit = fail(Http2StreamHandling.ConnectionWasAbortedException)

    def fail(cause: Throwable): Unit = {
      discardBuffer()
      trailingHeaders = None
      if (!outlet.isClosed) outlet.fail(cause)
    }
  }

  trait OutStream {
    def streamId: Int
    def canSend: Boolean
    def cancelStream(): Unit
    def endStreamIfPossible(): Option[FrameEvent]
    def nextFrame(maxBytesToSend: Int): DataFrame
    def increaseWindow(delta: Int): Boolean
    def isDone: Boolean
  }
  object OutStream {
    def apply(sub: Http2SubStream): OutStream = {
      val info = new OutStreamImpl(sub.streamId, OptionVal.None, multiplexer.currentInitialWindow, sub.trailingHeaders)
      sub.data match {
        case Right(data) =>
          val subIn = new SubSinkInlet[Any](s"substream-in-${sub.streamId}")
          info.registerIncomingData(subIn)
          data.runWith(subIn.sink)(subFusingMaterializer)
        case Left(data) =>
          info.addAllData(data)
      }
      info
    }
  }
  final class OutStreamImpl(
      val streamId: Int,
      private var maybeInlet: OptionVal[SubSinkInlet[?]],
      var outboundWindowLeft: Int,
      var trailer: OptionVal[ParsedHeadersFrame]) extends InHandler with OutStream {
    private def inlet: SubSinkInlet[?] = maybeInlet.get

    private var buffer: ByteString = ByteString.empty
    private var upstreamClosed: Boolean = false
    private var isEnqueued: Boolean = false
    var endStreamSent: Boolean = false

    /** Designates whether nextFrame can be called to get the next frame. */
    def canSend: Boolean = buffer.nonEmpty && outboundWindowLeft > 0
    def isDone: Boolean = endStreamSent

    def enqueueIfPossible(): Unit =
      if (canSend && !isEnqueued) {
        isEnqueued = true
        enqueueOutStream(streamId)
      }

    def registerIncomingData(inlet: SubSinkInlet[?]): Unit = {
      require(!maybeInlet.isDefined)

      this.maybeInlet = OptionVal.Some(inlet)
      inlet.pull()
      inlet.setHandler(this)
    }
    def addAllData(data: ByteString): Unit = {
      require(buffer.isEmpty)
      buffer = data
      upstreamClosed = true
      enqueueIfPossible()
    }

    def nextFrame(maxBytesToSend: Int): DataFrame = {
      val toTake = maxBytesToSend min buffer.length min outboundWindowLeft
      val toSend = buffer.take(toTake)
      require(toSend.nonEmpty)

      outboundWindowLeft -= toTake
      buffer = buffer.drop(toTake)

      val endStream = upstreamClosed && buffer.isEmpty && trailer.isEmpty
      if (endStream) {
        endStreamSent = true
      } else
        maybePull()

      debug(
        s"[$streamId] sending ${toSend.length} bytes, endStream = $endStream, remaining buffer [${buffer.length}], remaining stream-level WINDOW [$outboundWindowLeft]")

      // Multiplexer will enqueue for us if we report more data being available
      // We cannot call `multiplexer.enqueueOutStream` from here because this is called from the multiplexer.
      isEnqueued = !isDone && canSend

      DataFrame(streamId, endStream, toSend)
    }

    private def readyToSendFinalFrame: Boolean = upstreamClosed && !endStreamSent && buffer.isEmpty

    def endStreamIfPossible(): Option[FrameEvent] =
      if (readyToSendFinalFrame) {
        val finalFrame = trailer.getOrElse(DataFrame(streamId, endStream = true, ByteString.empty))
        endStreamSent = true
        Some(finalFrame)
      } else
        None

    private def maybePull(): Unit =
      // TODO: Check that buffer is not too much over the limit (which we might warn the user about)
      //       The problem here is that backpressure will only work properly if batch elements like
      //       ByteString have a reasonable size.
      if (!upstreamClosed && buffer.length < multiplexer.maxBytesToBufferPerSubstream && !inlet.hasBeenPulled &&
        !inlet.isClosed)
        inlet.pull()

    /** Cleans up internal state (but not external) */
    private def cleanupStream(): Unit = {
      buffer = ByteString.empty
      upstreamClosed = true
      endStreamSent = true
      maybeInlet match {
        case OptionVal.Some(inlet) => inlet.cancel()
        case OptionVal.None        => // nothing to clean up
      }
    }

    def cancelStream(): Unit = {
      cleanupStream()
      if (isEnqueued) multiplexer.dequeueOutStream(streamId)
    }
    def bufferedBytes: Int = buffer.length

    // `increment` is negative only for a SETTINGS_INITIAL_WINDOW_SIZE decrease (via `applyInitialWindowDelta`).
    // WINDOW_UPDATE increments are always positive: frame parsing rejects others (`requirePositiveWindowUpdateIncrement`).
    override def increaseWindow(increment: Int): Boolean = {
      val newWindow = outboundWindowLeft.toLong + increment
      if (newWindow > Http2Protocol.MaxWindowSize) {
        false
      } else {
        outboundWindowLeft = newWindow.toInt
        debug(s"Updating window for $streamId by $increment to $outboundWindowLeft buffered bytes: $bufferedBytes")
        if (outboundWindowLeft <= 0 && isEnqueued) {
          // nothing can be sent until the window grows again: stop scheduling the stream, `enqueueIfPossible`
          // enqueues it again once a later increase makes it sendable
          multiplexer.dequeueOutStream(streamId)
          isEnqueued = false
        }
        enqueueIfPossible()
        true
      }
    }

    // external callbacks, need to make sure that potential stream state changing events are run through the state machine
    override def onPush(): Unit = {
      inlet.grab() match {
        case newData: ByteString              => buffer ++= newData
        case HttpEntity.Chunk(newData, _)     => buffer ++= newData
        case HttpEntity.LastChunk(_, headers) =>
          if (headers.nonEmpty && !trailer.isEmpty)
            log.warning(
              "Found both an attribute with trailing headers, and headers in the `LastChunk`. This is not supported.")
          trailer = OptionVal.Some(ParsedHeadersFrame(streamId, endStream = true,
            HttpMessageRendering.renderHeaders(headers, log, isServer, shouldRenderAutoHeaders = false,
              isTrailer = true, dateHeaderRendering = DateHeaderRendering.Unavailable), None, None))
      }

      maybePull()

      // else wait for more data being drained
      enqueueIfPossible() // multiplexer might call pullNextFrame which goes through the state machine => OK
    }

    override def onUpstreamFinish(): Unit = {
      upstreamClosed = true

      endStreamIfPossible().foreach { frame =>
        multiplexer.pushControlFrame(frame)
        // also notify state machine that stream is done
        handleOutgoingEnded(streamId)
        cleanupStream()
      }
    }
    override def onUpstreamFailure(ex: Throwable): Unit = {
      log.error(ex, "Substream {} failed with {}", streamId, ex)
      multiplexer.pushControlFrame(RstStreamFrame(streamId, Http2Protocol.ErrorCode.INTERNAL_ERROR))
      handleOutgoingFailed(streamId, ex)
      cleanupStream()
    }
  }
  // needed once PUSH_PROMISE support was added
  // case object ReservedLocal extends IncomingStreamState
  // case object ReservedRemote extends IncomingStreamState
}
private[http2] object Http2StreamHandling {
  val ConnectionWasAbortedException =
    new IllegalStateException("The HTTP/2 connection was shut down while the request was still ongoing")
      with NoStackTrace
}
