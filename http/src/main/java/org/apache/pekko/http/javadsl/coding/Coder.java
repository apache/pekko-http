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

package org.apache.pekko.http.javadsl.coding;

import java.util.concurrent.CompletionStage;

import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.scaladsl.coding.Deflate$;
import org.apache.pekko.http.scaladsl.coding.Gzip$;
import org.apache.pekko.http.scaladsl.coding.NoCoding$;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.util.ByteString;

import scala.jdk.javaapi.FutureConverters;

/** A coder is an implementation of the predefined encoders/decoders defined for HTTP. */
public enum Coder {
  NoCoding(NoCoding$.MODULE$),
  Deflate(Deflate$.MODULE$),
  Gzip(Gzip$.MODULE$),
  DeflateLevel1(Deflate$.MODULE$.withLevel(1)),
  DeflateLevel9(Deflate$.MODULE$.withLevel(9)),
  GzipLevel1(Gzip$.MODULE$.withLevel(1)),
  GzipLevel9(Gzip$.MODULE$.withLevel(9));

  private org.apache.pekko.http.scaladsl.coding.Coder underlying;

  Coder(org.apache.pekko.http.scaladsl.coding.Coder underlying) {
    this.underlying = underlying;
  }

  public HttpResponse encodeMessage(HttpResponse message) {
    return (HttpResponse)
        underlying.encodeMessage((org.apache.pekko.http.scaladsl.model.HttpMessage) message);
  }

  public HttpRequest encodeMessage(HttpRequest message) {
    return (HttpRequest)
        underlying.encodeMessage((org.apache.pekko.http.scaladsl.model.HttpMessage) message);
  }

  /** @deprecated Synchronous encoding is deprecated since Akka HTTP 10.2.0 */
  @Deprecated
  public ByteString encode(ByteString input) {
    return underlying.encode(input);
  }

  public HttpResponse decodeMessage(HttpResponse message) {
    return (HttpResponse)
        underlying.decodeMessage((org.apache.pekko.http.scaladsl.model.HttpMessage) message);
  }

  public HttpRequest decodeMessage(HttpRequest message) {
    return (HttpRequest)
        underlying.decodeMessage((org.apache.pekko.http.scaladsl.model.HttpMessage) message);
  }

  public CompletionStage<ByteString> decode(ByteString input, Materializer mat) {
    return FutureConverters.asJava(underlying.decode(input, mat));
  }

  public org.apache.pekko.http.scaladsl.coding.Coder _underlyingScalaCoder() {
    return underlying;
  }
}
