/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.http.impl.engine.client

import java.util.concurrent.TimeoutException

import scala.concurrent.duration.{ Deadline, Duration, FiniteDuration }
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.control.NonFatal
import scala.util.{ Failure, Success }

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.annotation.InternalApi
import pekko.event.LoggingAdapter
import pekko.http.scaladsl.model.headers.Expect
import pekko.http.scaladsl.model.{ HttpRequest, HttpResponse, Uri }
import pekko.http.scaladsl.settings.ClientConnectionSettings
import pekko.http.scaladsl.{ ClientTransport, ConnectionContext, Http, HttpExt, HttpsConnectionContext }
import pekko.http.{ ExpectContinuePolicy, ExpectContinuePolicySupport }
import pekko.stream.scaladsl.{ BidiFlow, Flow, Keep, Sink, Source }
import pekko.stream.{ KillSwitches, Materializer }
import pekko.util.ByteString

/**
 * INTERNAL API
 *
 * A fixed-origin execution function with one optional HTTP/1.1 Expect fallback. Ordinary requests use the
 * supplied executor. Opted-in candidates use dedicated connections so a failed attempt cannot consume the
 * pool's independent retry budget or close another request's connection.
 *
 * The deadline limits how long the executor waits for a policy decision. It does not interrupt a callback
 * that has already begun, so callbacks must be non-blocking.
 */
@InternalApi
private[http] object Http1ExpectContinueExecutor {

  def wrap(
      ordinary: HttpRequest => Future[HttpResponse],
      origin: Uri,
      clientPolicy: ExpectContinuePolicy,
      budget: FiniteDuration,
      http: HttpExt,
      httpsContext: HttpsConnectionContext,
      settings: ClientConnectionSettings,
      log: LoggingAdapter)(implicit materializer: Materializer): HttpRequest => Future[HttpResponse] =
    if (clientPolicy eq ExpectContinuePolicy.disabled) ordinary
    else {
      val secure = origin.scheme.equalsIgnoreCase("https")
      val host = origin.authority.host
      require((secure || origin.scheme.equalsIgnoreCase("http")) && !host.isEmpty,
        "Expected an HTTP or HTTPS origin")
      require(budget > Duration.Zero, "Expected a positive HTTP/1.1 Expect retry budget")

      val context = if (secure) httpsContext else ConnectionContext.noEncryption()
      val port = origin.effectivePort
      val endpoint = new Endpoint(http, host.toString, port, context, settings, log)
      request => {
        val uri = request.uri
        if (!request.method.isIdempotent || !uri.scheme.equalsIgnoreCase(origin.scheme) ||
          uri.authority.host != host || uri.effectivePort != port ||
          request.headers.exists(header => header.is("host") || header.is("raw-request-uri")))
          // Do not retry a non-idempotent request or assume authority over an application-supplied target.
          ordinary(request)
        else Http1ExpectContinueAttempt.prepare(request, clientPolicy) match {
          case Some((prepared, attempt)) => endpoint.execute(request, prepared, attempt, Deadline.now + budget)
          case None                      => ordinary(request)
        }
      }
    }

  private final class Endpoint(
      http: HttpExt,
      host: String,
      port: Int,
      context: ConnectionContext,
      settings: ClientConnectionSettings,
      log: LoggingAdapter)(implicit materializer: Materializer) {
    private implicit val ec: ExecutionContext = materializer.executionContext

    def execute(
        request: HttpRequest,
        prepared: HttpRequest,
        attempt: Http1ExpectContinueAttempt,
        deadline: Deadline): Future[HttpResponse] =
      dispatch(prepared, deadline).transformWith {
        case Failure(cause) =>
          attempt.retire()
          Future.failed(cause)
        case Success((response, oldTransport)) =>
          if (!attempt.takeRejectionBeforeBody(response)) Future.successful(response)
          else {
            // The decision receives only immutable original-request metadata. It cannot own the old response.
            beforeDeadline(deadline, "while deciding") {
              Future {
                deadline.hasTimeLeft() &&
                ExpectContinuePolicySupport.retryContext(request, request.protocol, attempt.policy).exists { decision =>
                  // Time may have elapsed while the task waited in the dispatcher or prepared its metadata.
                  deadline.hasTimeLeft() && attempt.policy.shouldRetry(decision)
                }
              }
            }.recover {
              case NonFatal(cause) =>
                log.debug("HTTP/1.1 Expect retry decision not available: {}", cause.getClass.getSimpleName)
                false
            }.flatMap { allowed =>
              if (!allowed || !deadline.hasTimeLeft()) Future.successful(response)
              else {
                // Committed: this response is now ours to discard. Never hand out a partly consumed response.
                retireResponse(response, oldTransport, deadline).flatMap { _ =>
                  val replay = request.withHeaders(request.headers.filterNot(_ eq Expect.`100-continue`))
                  // Dispatch directly, not recursively: this is the last physical HTTP request.
                  dispatch(replay, deadline).map(_._1)
                }
              }
            }
          }
      }

    private def dispatch(
        request: HttpRequest,
        deadline: Deadline): Future[(HttpResponse, AttemptTransport)] = {
      val transport = new AttemptTransport(settings)
      transport.abortOnFailure {
        // Constructing the flow is inert. Check the remaining budget again before it can be materialized.
        budgeted(deadline, "before dispatch") { _ =>
          val flow = http.outgoingConnectionUsingContext(host, port, context, settings.withTransport(transport), log)
          budgeted(deadline, "before response headers") { remaining =>
            // Sink.head is the existing one-response client pattern. The native parser retains a streamed
            // response entity for its eventual subscriber; the header timeout must not govern that entity.
            Source.single(request.withUri(request.uri.toHttpRequestTargetOriginForm))
              .via(flow)
              .initialTimeout(remaining)
              .runWith(Sink.head)
              .map(_ -> transport)
          }
        }
      }
    }

    private def retireResponse(response: HttpResponse, transport: AttemptTransport, deadline: Deadline): Future[Unit] =
      transport.abortOnFailure {
        val consumed =
          if (response.entity.isStrict) Future.successful(())
          else budgeted(deadline, "while discarding the first response") { remaining =>
            // Keep the parser's limits and wait for the entity to complete, without buffering its data.
            response.entity.dataBytes.completionTimeout(remaining).runWith(Sink.ignore).map(_ => ())
          }
        consumed.flatMap { _ =>
          beforeDeadline(deadline, "while closing the first connection")(transport.retired)
        }
      }

    private def timedOut(phase: String): TimeoutException =
      new TimeoutException(s"HTTP/1.1 Expect retry budget expired $phase")

    private def budgeted[A](deadline: Deadline, phase: String)(start: FiniteDuration => Future[A]): Future[A] = {
      val remaining = deadline.timeLeft
      if (remaining > Duration.Zero) start(remaining) else Future.failed(timedOut(phase))
    }

    // Only used for the in-memory policy result or transport-port acknowledgement, not to cancel live I/O.
    private def beforeDeadline[A](deadline: Deadline, phase: String)(observe: => Future[A]): Future[A] =
      safely {
        budgeted(deadline, phase) { remaining =>
          val result = Promise[A]()
          val timer = materializer.scheduleOnce(remaining, () => { result.tryFailure(timedOut(phase)); () })
          safely(observe).onComplete { completion =>
            if (deadline.hasTimeLeft()) result.tryComplete(completion)
            else result.tryFailure(timedOut(phase))
            timer.cancel()
          }(ExecutionContext.parasitic)
          result.future
        }
      }
  }

  private def safely[A](start: => Future[A]): Future[A] =
    try start
    catch { case NonFatal(cause) => Future.failed(cause) }

  /** One physical attempt owns both raw stream ports, including a streamed response detached by Sink.head. */
  private final class AttemptTransport(settings: ClientConnectionSettings) extends ClientTransport {
    private val stop = KillSwitches.shared("http1-expect-attempt") // shared by its own two directions only
    private val closed = Promise[Unit]()

    // Both watched stream directions observed terminal signals, not a TCP or TLS close acknowledgement.
    def retired: Future[Unit] = closed.future

    def abortOnFailure[A](start: => Future[A]): Future[A] =
      safely(start).recoverWith {
        case NonFatal(cause) =>
          stop.abort(cause)
          Future.failed(cause)
      }(ExecutionContext.parasitic)

    override def connectTo(host: String, port: Int, _settings: ClientConnectionSettings)(
        implicit system: ActorSystem): Flow[ByteString, ByteString, Future[Http.OutgoingConnection]] = {
      val watched = BidiFlow.fromFlowsMat(
        Flow[ByteString].via(stop.flow[ByteString]).watchTermination(Keep.right),
        Flow[ByteString].watchTermination(Keep.right).via(stop.flow[ByteString]))(Keep.both)
      watched.joinMat(settings.transport.connectTo(host, port, settings)) { (ports, connected) =>
        // Only wait for both ports to be finished. Entity disposal still reports its own failure above.
        implicit val ec: ExecutionContext = ExecutionContext.parasitic
        val (outgoing, incoming) = ports
        closed.completeWith(outgoing.transform(_ => Success(())).flatMap(_ => incoming.transform(_ => Success(()))))
        connected
      }
    }
  }
}
