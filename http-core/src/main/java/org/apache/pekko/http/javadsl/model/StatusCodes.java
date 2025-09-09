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

package org.apache.pekko.http.javadsl.model;

import org.apache.pekko.annotation.ApiMayChange;
import org.apache.pekko.http.impl.util.Util;
import org.apache.pekko.http.scaladsl.model.StatusCodes$;

import java.util.Optional;

/**
 * Contains the set of predefined status-codes along with static methods to access and create custom
 * status-codes.
 */
public final class StatusCodes {
  private StatusCodes() {}

  public static final StatusCode CONTINUE =
      org.apache.pekko.http.scaladsl.model.StatusCodes.Continue();
  public static final StatusCode SWITCHING_PROTOCOLS =
      org.apache.pekko.http.scaladsl.model.StatusCodes.SwitchingProtocols();
  public static final StatusCode PROCESSING =
      org.apache.pekko.http.scaladsl.model.StatusCodes.Processing();

  @ApiMayChange
  public static final StatusCode EARLY_HINTS =
      org.apache.pekko.http.scaladsl.model.StatusCodes.EarlyHints();

  public static final StatusCode OK = org.apache.pekko.http.scaladsl.model.StatusCodes.OK();
  public static final StatusCode CREATED =
      org.apache.pekko.http.scaladsl.model.StatusCodes.Created();
  public static final StatusCode ACCEPTED =
      org.apache.pekko.http.scaladsl.model.StatusCodes.Accepted();
  public static final StatusCode NON_AUTHORITATIVE_INFORMATION =
      org.apache.pekko.http.scaladsl.model.StatusCodes.NonAuthoritativeInformation();
  public static final StatusCode NO_CONTENT =
      org.apache.pekko.http.scaladsl.model.StatusCodes.NoContent();
  public static final StatusCode RESET_CONTENT =
      org.apache.pekko.http.scaladsl.model.StatusCodes.ResetContent();
  public static final StatusCode PARTIAL_CONTENT =
      org.apache.pekko.http.scaladsl.model.StatusCodes.PartialContent();
  public static final StatusCode MULTI_STATUS =
      org.apache.pekko.http.scaladsl.model.StatusCodes.MultiStatus();
  public static final StatusCode ALREADY_REPORTED =
      org.apache.pekko.http.scaladsl.model.StatusCodes.AlreadyReported();
  public static final StatusCode IMUSED = org.apache.pekko.http.scaladsl.model.StatusCodes.IMUsed();

  public static final StatusCode MULTIPLE_CHOICES =
      org.apache.pekko.http.scaladsl.model.StatusCodes.MultipleChoices();
  public static final StatusCode MOVED_PERMANENTLY =
      org.apache.pekko.http.scaladsl.model.StatusCodes.MovedPermanently();
  public static final StatusCode FOUND = org.apache.pekko.http.scaladsl.model.StatusCodes.Found();
  public static final StatusCode SEE_OTHER =
      org.apache.pekko.http.scaladsl.model.StatusCodes.SeeOther();
  public static final StatusCode NOT_MODIFIED =
      org.apache.pekko.http.scaladsl.model.StatusCodes.NotModified();
  public static final StatusCode USE_PROXY =
      org.apache.pekko.http.scaladsl.model.StatusCodes.UseProxy();
  public static final StatusCode TEMPORARY_REDIRECT =
      org.apache.pekko.http.scaladsl.model.StatusCodes.TemporaryRedirect();
  public static final StatusCode PERMANENT_REDIRECT =
      org.apache.pekko.http.scaladsl.model.StatusCodes.PermanentRedirect();

  public static final StatusCode BAD_REQUEST =
      org.apache.pekko.http.scaladsl.model.StatusCodes.BadRequest();
  public static final StatusCode UNAUTHORIZED =
      org.apache.pekko.http.scaladsl.model.StatusCodes.Unauthorized();
  public static final StatusCode PAYMENT_REQUIRED =
      org.apache.pekko.http.scaladsl.model.StatusCodes.PaymentRequired();
  public static final StatusCode FORBIDDEN =
      org.apache.pekko.http.scaladsl.model.StatusCodes.Forbidden();
  public static final StatusCode NOT_FOUND =
      org.apache.pekko.http.scaladsl.model.StatusCodes.NotFound();
  public static final StatusCode METHOD_NOT_ALLOWED =
      org.apache.pekko.http.scaladsl.model.StatusCodes.MethodNotAllowed();
  public static final StatusCode NOT_ACCEPTABLE =
      org.apache.pekko.http.scaladsl.model.StatusCodes.NotAcceptable();
  public static final StatusCode PROXY_AUTHENTICATION_REQUIRED =
      org.apache.pekko.http.scaladsl.model.StatusCodes.ProxyAuthenticationRequired();
  public static final StatusCode REQUEST_TIMEOUT =
      org.apache.pekko.http.scaladsl.model.StatusCodes.RequestTimeout();
  public static final StatusCode CONFLICT =
      org.apache.pekko.http.scaladsl.model.StatusCodes.Conflict();
  public static final StatusCode GONE = org.apache.pekko.http.scaladsl.model.StatusCodes.Gone();
  public static final StatusCode LENGTH_REQUIRED =
      org.apache.pekko.http.scaladsl.model.StatusCodes.LengthRequired();
  public static final StatusCode PRECONDITION_FAILED =
      org.apache.pekko.http.scaladsl.model.StatusCodes.PreconditionFailed();

  public static final StatusCode CONTENT_TOO_LARGE =
      org.apache.pekko.http.scaladsl.model.StatusCodes.ContentTooLarge();

  /** @deprecated deprecated in favor of CONTENT_TOO_LARGE since 1.1.0 */
  @Deprecated
  public static final StatusCode PAYLOAD_TOO_LARGE =
      org.apache.pekko.http.scaladsl.model.StatusCodes.PayloadTooLarge();

  public static final StatusCode URI_TOO_LONG =
      org.apache.pekko.http.scaladsl.model.StatusCodes.UriTooLong();

  public static final StatusCode UNSUPPORTED_MEDIA_TYPE =
      org.apache.pekko.http.scaladsl.model.StatusCodes.UnsupportedMediaType();
  public static final StatusCode RANGE_NOT_SATISFIABLE =
      org.apache.pekko.http.scaladsl.model.StatusCodes.RangeNotSatisfiable();

  public static final StatusCode EXPECTATION_FAILED =
      org.apache.pekko.http.scaladsl.model.StatusCodes.ExpectationFailed();
  public static final StatusCode IM_A_TEAPOT =
      org.apache.pekko.http.scaladsl.model.StatusCodes.ImATeapot();
  public static final StatusCode ENHANCE_YOUR_CALM =
      org.apache.pekko.http.scaladsl.model.StatusCodes.EnhanceYourCalm();
  public static final StatusCode MISDIRECTED_REQUEST =
      org.apache.pekko.http.scaladsl.model.StatusCodes.MisdirectedRequest();
  public static final StatusCode UNPROCESSABLE_CONTENT =
      org.apache.pekko.http.scaladsl.model.StatusCodes.UnprocessableContent();

  /** @deprecated deprecated in favor of UNPROCESSABLE_CONTENT since 1.1.0 */
  @Deprecated
  public static final StatusCode UNPROCESSABLE_ENTITY =
      org.apache.pekko.http.scaladsl.model.StatusCodes.UnprocessableEntity();

  public static final StatusCode LOCKED = org.apache.pekko.http.scaladsl.model.StatusCodes.Locked();
  public static final StatusCode FAILED_DEPENDENCY =
      org.apache.pekko.http.scaladsl.model.StatusCodes.FailedDependency();

  public static final StatusCode TOO_EARLY =
      org.apache.pekko.http.scaladsl.model.StatusCodes.TooEarly();
  public static final StatusCode UPGRADE_REQUIRED =
      org.apache.pekko.http.scaladsl.model.StatusCodes.UpgradeRequired();
  public static final StatusCode PRECONDITION_REQUIRED =
      org.apache.pekko.http.scaladsl.model.StatusCodes.PreconditionRequired();
  public static final StatusCode TOO_MANY_REQUESTS =
      org.apache.pekko.http.scaladsl.model.StatusCodes.TooManyRequests();
  public static final StatusCode REQUEST_HEADER_FIELDS_TOO_LARGE =
      org.apache.pekko.http.scaladsl.model.StatusCodes.RequestHeaderFieldsTooLarge();
  public static final StatusCode RETRY_WITH =
      org.apache.pekko.http.scaladsl.model.StatusCodes.RetryWith();
  public static final StatusCode BLOCKED_BY_PARENTAL_CONTROLS =
      org.apache.pekko.http.scaladsl.model.StatusCodes.BlockedByParentalControls();
  public static final StatusCode UNAVAILABLE_FOR_LEGAL_REASONS =
      org.apache.pekko.http.scaladsl.model.StatusCodes.UnavailableForLegalReasons();

  public static final StatusCode INTERNAL_SERVER_ERROR =
      org.apache.pekko.http.scaladsl.model.StatusCodes.InternalServerError();
  public static final StatusCode NOT_IMPLEMENTED =
      org.apache.pekko.http.scaladsl.model.StatusCodes.NotImplemented();
  public static final StatusCode BAD_GATEWAY =
      org.apache.pekko.http.scaladsl.model.StatusCodes.BadGateway();
  public static final StatusCode SERVICE_UNAVAILABLE =
      org.apache.pekko.http.scaladsl.model.StatusCodes.ServiceUnavailable();
  public static final StatusCode GATEWAY_TIMEOUT =
      org.apache.pekko.http.scaladsl.model.StatusCodes.GatewayTimeout();
  public static final StatusCode HTTPVERSION_NOT_SUPPORTED =
      org.apache.pekko.http.scaladsl.model.StatusCodes.HttpVersionNotSupported();
  public static final StatusCode VARIANT_ALSO_NEGOTIATES =
      org.apache.pekko.http.scaladsl.model.StatusCodes.VariantAlsoNegotiates();
  public static final StatusCode INSUFFICIENT_STORAGE =
      org.apache.pekko.http.scaladsl.model.StatusCodes.InsufficientStorage();
  public static final StatusCode LOOP_DETECTED =
      org.apache.pekko.http.scaladsl.model.StatusCodes.LoopDetected();
  public static final StatusCode BANDWIDTH_LIMIT_EXCEEDED =
      org.apache.pekko.http.scaladsl.model.StatusCodes.BandwidthLimitExceeded();
  public static final StatusCode NOT_EXTENDED =
      org.apache.pekko.http.scaladsl.model.StatusCodes.NotExtended();
  public static final StatusCode NETWORK_AUTHENTICATION_REQUIRED =
      org.apache.pekko.http.scaladsl.model.StatusCodes.NetworkAuthenticationRequired();
  public static final StatusCode NETWORK_READ_TIMEOUT =
      org.apache.pekko.http.scaladsl.model.StatusCodes.NetworkReadTimeout();
  public static final StatusCode NETWORK_CONNECT_TIMEOUT =
      org.apache.pekko.http.scaladsl.model.StatusCodes.NetworkConnectTimeout();

  /** Create a custom status code. */
  public static StatusCode custom(
      int intValue, String reason, String defaultMessage, boolean isSuccess, boolean allowsEntity) {
    return org.apache.pekko.http.scaladsl.model.StatusCodes.custom(
        intValue, reason, defaultMessage, isSuccess, allowsEntity);
  }

  /** Create a custom status code. */
  public static StatusCode custom(int intValue, String reason, String defaultMessage) {
    return org.apache.pekko.http.scaladsl.model.StatusCodes.custom(
        intValue, reason, defaultMessage);
  }

  /**
   * Looks up a status-code by numeric code. Throws an exception if no such status-code is found.
   */
  public static StatusCode get(int intValue) {
    return org.apache.pekko.http.scaladsl.model.StatusCode.int2StatusCode(intValue);
  }

  /** Looks up a status-code by numeric code and returns Some(code). Returns None otherwise. */
  public static Optional<StatusCode> lookup(int intValue) {
    return Util.<StatusCode, org.apache.pekko.http.scaladsl.model.StatusCode>lookupInRegistry(
        StatusCodes$.MODULE$, intValue);
  }
}
