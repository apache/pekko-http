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

package org.apache.pekko.http.javadsl.marshallers.jackson;

import java.io.IOException;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.pekko.http.javadsl.model.HttpEntity;
import org.apache.pekko.http.javadsl.model.MediaTypes;
import org.apache.pekko.http.javadsl.model.RequestEntity;
import org.apache.pekko.http.javadsl.marshalling.Marshaller;
import org.apache.pekko.http.javadsl.unmarshalling.Unmarshaller;
import org.apache.pekko.http.scaladsl.model.ExceptionWithErrorInfo;
import org.apache.pekko.http.scaladsl.model.ErrorInfo;
import org.apache.pekko.util.ByteString;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamWriteConstraints;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

public class Jackson {
  private static final ObjectMapper defaultObjectMapper =
      new ObjectMapper().enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);

  /** INTERNAL API */
  public static class JacksonUnmarshallingException extends ExceptionWithErrorInfo {
    public JacksonUnmarshallingException(Class<?> expectedType, IOException cause) {
      super(
          new ErrorInfo(
              "Cannot unmarshal JSON as " + expectedType.getSimpleName(), cause.getMessage()),
          cause);
    }
  }

  public static <T> Marshaller<T, RequestEntity> marshaller() {
    return marshaller(defaultObjectMapper);
  }

  public static <T> Marshaller<T, RequestEntity> marshaller(ObjectMapper mapper) {
    return Marshaller.wrapEntity(
        u -> toJSON(mapper, u), Marshaller.stringToEntity(), MediaTypes.APPLICATION_JSON);
  }

  public static <T> Unmarshaller<ByteString, T> byteStringUnmarshaller(Class<T> expectedType) {
    return byteStringUnmarshaller(defaultObjectMapper, expectedType);
  }

  public static <T> Unmarshaller<HttpEntity, T> unmarshaller(Class<T> expectedType) {
    return unmarshaller(defaultObjectMapper, expectedType);
  }

  public static <T> Unmarshaller<HttpEntity, T> unmarshaller(
      ObjectMapper mapper, Class<T> expectedType) {
    return Unmarshaller.forMediaType(MediaTypes.APPLICATION_JSON, Unmarshaller.entityToString())
        .thenApply(s -> fromJSON(mapper, s, expectedType));
  }

  public static <T> Unmarshaller<ByteString, T> byteStringUnmarshaller(
      ObjectMapper mapper, Class<T> expectedType) {
    return Unmarshaller.sync(s -> fromJSON(mapper, s.utf8String(), expectedType));
  }

  private static String toJSON(ObjectMapper mapper, Object object) {
    try {
      return mapper.writeValueAsString(object);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Cannot marshal to JSON: " + object, e);
    }
  }

  private static <T> T fromJSON(ObjectMapper mapper, String json, Class<T> expectedType) {
    try {
      return mapper.readerFor(expectedType).readValue(json);
    } catch (IOException e) {
      throw new JacksonUnmarshallingException(expectedType, e);
    }
  }

  private static ObjectMapper createMapper() {
    Config config = ConfigFactory.load().getConfig("pekko.http.jackson");
    StreamReadConstraints streamReadConstraints = StreamReadConstraints.builder()
            .maxNestingDepth(config.getInt("read.max-nesting-depth"))
            .maxNumberLength(config.getInt("read.max-number-length"))
            .maxStringLength(config.getInt("read.max-string-length"))
            .maxNameLength(config.getInt("read.max-name-length"))
            .maxDocumentLength(config.getLong("read.max-document-length"))
            .build();
    StreamWriteConstraints streamWriteConstraints = StreamWriteConstraints.builder()
            .maxNestingDepth(config.getInt("write.max-nesting-depth"))
            .build();
    JsonFactory jsonFactory = JsonFactory.builder()
            .streamReadConstraints(streamReadConstraints)
            .streamWriteConstraints(streamWriteConstraints)
            .build();
    return new JsonMapper(jsonFactory);
  }
}
