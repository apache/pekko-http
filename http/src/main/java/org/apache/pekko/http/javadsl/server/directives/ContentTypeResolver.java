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

package org.apache.pekko.http.javadsl.server.directives;

import java.util.Map;
import org.apache.pekko.http.javadsl.model.ContentType;
import org.apache.pekko.http.javadsl.model.HttpCharset;
import org.apache.pekko.http.javadsl.model.HttpCharsets;
import org.apache.pekko.http.javadsl.model.MediaType;

/**
 * Implement this interface to provide a custom mapping from a file name to a {@link
 * org.apache.pekko.http.javadsl.model.ContentType}.
 */
@FunctionalInterface
public interface ContentTypeResolver {
  ContentType resolve(String fileName);

  /**
   * Returns a resolver that looks up the given file extensions before the built-in media type
   * registry, and otherwise resolves file names like the default resolver used by the file and
   * resource directives. This lets applications serve types that Pekko HTTP does not predefine, or
   * override a predefined mapping.
   *
   * <p>Extensions are matched ignoring case and must not be empty, contain a {@code .} or be {@code
   * gz}, which is reserved for precompressed files: {@code file.ext.gz} resolves to the media type
   * for {@code ext} with gzip compression. Non-binary media types that do not fix their own charset
   * get UTF-8.
   *
   * @throws IllegalArgumentException if an extension is invalid
   * @since 2.1.0
   */
  static ContentTypeResolver withExtensions(Map<String, MediaType> extensions) {
    return withExtensions(extensions, HttpCharsets.UTF_8);
  }

  /**
   * Like {@link #withExtensions(Map)}, using the given charset for non-binary media types that do
   * not fix their own charset.
   *
   * @throws IllegalArgumentException if an extension is invalid
   * @since 2.1.0
   */
  static ContentTypeResolver withExtensions(
      Map<String, MediaType> extensions, HttpCharset defaultCharset) {
    scala.collection.immutable.Map<String, org.apache.pekko.http.scaladsl.model.MediaType>
        scalaMap = scala.collection.immutable.Map$.MODULE$.empty();
    for (Map.Entry<String, MediaType> entry : extensions.entrySet()) {
      scalaMap =
          scalaMap.updated(
              entry.getKey(), (org.apache.pekko.http.scaladsl.model.MediaType) entry.getValue());
    }
    return org.apache.pekko.http.scaladsl.server.directives.ContentTypeResolver$.MODULE$
        .withExtensions(
            scalaMap, (org.apache.pekko.http.scaladsl.model.HttpCharset) defaultCharset);
  }

  /** Returns a Scala DSL representation of this content type resolver */
  default org.apache.pekko.http.scaladsl.server.directives.ContentTypeResolver asScala() {
    ContentTypeResolver delegate = this;
    return new org.apache.pekko.http.scaladsl.server.directives.ContentTypeResolver() {
      @Override
      public ContentType resolve(String fileName) {
        return delegate.resolve(fileName);
      }

      @Override
      public org.apache.pekko.http.scaladsl.model.ContentType apply(String fileName) {
        return (org.apache.pekko.http.scaladsl.model.ContentType) delegate.resolve(fileName);
      }
    };
  }
}
