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
import org.apache.pekko.http.scaladsl.coding.Coders$;
import org.apache.pekko.http.scaladsl.coding.Deflate$;
import org.apache.pekko.http.scaladsl.coding.Gzip$;
import org.apache.pekko.http.scaladsl.coding.NoCoding$;
import org.apache.pekko.http.scaladsl.coding.Zstd$;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.util.ByteString;
import scala.jdk.javaapi.FutureConverters;

/** A coder is an implementation of the predefined encoders/decoders defined for HTTP. */
@SuppressWarnings("deprecation")
public enum Coder {
  NoCoding(NoCoding$.MODULE$),
  Deflate(Deflate$.MODULE$),
  Gzip(Gzip$.MODULE$),
  DeflateLevel1(Deflate$.MODULE$.withLevel(1)),
  DeflateLevel9(Deflate$.MODULE$.withLevel(9)),
  GzipLevel1(Gzip$.MODULE$.withLevel(1)),
  GzipLevel9(Gzip$.MODULE$.withLevel(9)),
  /**
   * The 'zstd' encoding, which needs the optional {@code com.github.luben:zstd-jni} dependency on
   * the classpath.
   *
   * @since 2.0.0
   */
  Zstd(Zstd$.MODULE$.Default());

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

  /**
   * Decodes the response with the one of {@code coders} whose encoding matches the response's
   * {@code Content-Encoding} header, without having to check its encoding first. If no coders are
   * given, {@link #Gzip}, {@link #Deflate} and {@link #NoCoding} are used; {@link #Zstd} has to be
   * given explicitly.
   *
   * <p>The response is returned unchanged if it has no {@code Content-Encoding} header, if none of
   * {@code coders} matches its encoding, or if the header lists more than one encoding. In the last
   * two cases the {@code Content-Encoding} header is kept, so {@code response.encoding()} shows
   * that the entity is still encoded.
   *
   * @since 2.0.0
   */
  public static HttpResponse decodeResponse(HttpResponse response, Coder... coders) {
    return (HttpResponse)
        Coders$.MODULE$.decodeMessage(
            (org.apache.pekko.http.scaladsl.model.HttpMessage) response, scalaDecoders(coders));
  }

  /**
   * Decodes the request with the one of {@code coders} whose encoding matches the request's {@code
   * Content-Encoding} header, see {@link #decodeResponse(HttpResponse, Coder...)}.
   *
   * @since 2.0.0
   */
  public static HttpRequest decodeRequest(HttpRequest request, Coder... coders) {
    return (HttpRequest)
        Coders$.MODULE$.decodeMessage(
            (org.apache.pekko.http.scaladsl.model.HttpMessage) request, scalaDecoders(coders));
  }

  private static scala.collection.immutable.Seq<org.apache.pekko.http.scaladsl.coding.Decoder>
      scalaDecoders(Coder[] coders) {
    if (coders.length == 0) coders = new Coder[] {Gzip, Deflate, NoCoding};
    final java.util.List<org.apache.pekko.http.scaladsl.coding.Decoder> decoders =
        new java.util.ArrayList<>(coders.length);
    for (Coder coder : coders) decoders.add(coder.underlying);
    return scala.jdk.javaapi.CollectionConverters.asScala(decoders).toList();
  }
}
