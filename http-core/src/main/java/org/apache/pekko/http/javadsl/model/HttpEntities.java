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

import java.io.File;
import java.nio.file.Path;
import org.apache.pekko.http.impl.util.JavaAccessors;
import org.apache.pekko.http.scaladsl.model.HttpEntity$;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.util.ByteString;

/** Constructors for HttpEntity instances */
public final class HttpEntities {
  private HttpEntities() {}

  public static final HttpEntity.Strict EMPTY = HttpEntity$.MODULE$.Empty();

  public static HttpEntity.Strict create(String string) {
    return HttpEntity$.MODULE$.apply(string);
  }

  public static HttpEntity.Strict create(byte[] bytes) {
    return HttpEntity$.MODULE$.apply(bytes);
  }

  public static HttpEntity.Strict create(ByteString bytes) {
    return HttpEntity$.MODULE$.apply(bytes);
  }

  public static HttpEntity.Strict create(ContentType.NonBinary contentType, String string) {
    return HttpEntity$.MODULE$.apply(
        (org.apache.pekko.http.scaladsl.model.ContentType.NonBinary) contentType, string);
  }

  public static HttpEntity.Strict create(ContentType contentType, byte[] bytes) {
    return HttpEntity$.MODULE$.apply(
        (org.apache.pekko.http.scaladsl.model.ContentType) contentType, bytes);
  }

  public static HttpEntity.Strict create(ContentType contentType, ByteString bytes) {
    return HttpEntity$.MODULE$.apply(
        (org.apache.pekko.http.scaladsl.model.ContentType) contentType, bytes);
  }

  public static UniversalEntity create(ContentType contentType, File file) {
    return JavaAccessors.HttpEntity(contentType, file);
  }

  public static UniversalEntity create(ContentType contentType, Path file) {
    return JavaAccessors.HttpEntity(contentType, file);
  }

  public static UniversalEntity create(ContentType contentType, File file, int chunkSize) {
    return create(contentType, file.toPath(), chunkSize);
  }

  public static UniversalEntity create(ContentType contentType, Path file, int chunkSize) {
    return HttpEntity$.MODULE$.fromPath(
        (org.apache.pekko.http.scaladsl.model.ContentType) contentType, file, chunkSize);
  }

  /**
   * Creates an entity with a known positive content length.
   *
   * <p>This overload currently returns {@link HttpEntity.Default}, which cannot represent an empty
   * entity, so a zero length is not supported. For an empty entity with this content type, use
   * {@link #create(ContentType, byte[])} with an empty byte array or {@link #create(ContentType,
   * ByteString)} with an empty ByteString instead.
   *
   * @throws IllegalArgumentException if {@code contentLength} is not positive
   */
  public static HttpEntity.Default create(
      ContentType contentType, long contentLength, Source<ByteString, ?> data) {
    return new org.apache.pekko.http.scaladsl.model.HttpEntity.Default(
        (org.apache.pekko.http.scaladsl.model.ContentType) contentType,
        contentLength,
        toScala(data));
  }

  public static HttpEntity.Chunked create(ContentType contentType, Source<ByteString, ?> data) {
    return org.apache.pekko.http.scaladsl.model.HttpEntity.Chunked$.MODULE$.fromData(
        (org.apache.pekko.http.scaladsl.model.ContentType) contentType, toScala(data));
  }

  public static HttpEntity.CloseDelimited createCloseDelimited(
      ContentType contentType, Source<ByteString, ?> data) {
    return new org.apache.pekko.http.scaladsl.model.HttpEntity.CloseDelimited(
        (org.apache.pekko.http.scaladsl.model.ContentType) contentType, toScala(data));
  }

  public static HttpEntity.IndefiniteLength createIndefiniteLength(
      ContentType contentType, Source<ByteString, ?> data) {
    return new org.apache.pekko.http.scaladsl.model.HttpEntity.IndefiniteLength(
        (org.apache.pekko.http.scaladsl.model.ContentType) contentType, toScala(data));
  }

  public static HttpEntity.Chunked createChunked(
      ContentType contentType, Source<ByteString, ?> data) {
    return org.apache.pekko.http.scaladsl.model.HttpEntity.Chunked$.MODULE$.fromData(
        (org.apache.pekko.http.scaladsl.model.ContentType) contentType, toScala(data));
  }

  private static org.apache.pekko.stream.scaladsl.Source<ByteString, Object> toScala(
      Source<ByteString, ?> javaSource) {
    return (org.apache.pekko.stream.scaladsl.Source<ByteString, Object>) javaSource.asScala();
  }
}
