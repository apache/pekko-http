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

package org.apache.pekko.http.impl.util;

import scala.collection.immutable.Map$;
import scala.collection.immutable.Seq;

import org.apache.pekko.stream.scaladsl.Source;
import org.apache.pekko.http.ccompat.MapHelpers;
import org.apache.pekko.util.OptionalUtil;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

/** Contains internal helper methods. */
public abstract class Util {

  @SuppressWarnings("unchecked") // no support for covariance of Publisher in Java
  // needed to provide covariant conversions that the Java interfaces don't provide automatically.
  // The alternative would be having to cast around everywhere instead of doing it here in a central
  // place.
  public static <U, T extends U> Source<U, scala.Unit> convertPublisher(Source<T, scala.Unit> p) {
    return (Source<U, scala.Unit>) (Object) p;
  }

  @SuppressWarnings("unchecked")
  public static <T, U extends T> Source<U, scala.Unit> upcastSource(Source<T, scala.Unit> p) {
    return (Source<U, scala.Unit>) (Object) p;
  }

  public static scala.collection.immutable.Map<String, String> convertMapToScala(
      Map<String, String> map) {
    return MapHelpers.convertMapToScala(map);
  }

  public static final scala.collection.immutable.Map<String, String> emptyMap =
      Map$.MODULE$.<String, String>empty();

  @SuppressWarnings("unchecked")
  public static <T, U extends T> Seq<U> convertIterable(Iterable<T> els) {
    return scala.collection.JavaConverters.iterableAsScalaIterableConverter((Iterable<U>) els)
        .asScala()
        .toVector();
  }

  public static <T, U extends T> Seq<U> convertArray(T[] els) {
    return Util.<T, U>convertIterable(Arrays.asList(els));
  }

  public static <J, V extends J> Optional<J> lookupInRegistry(
      ObjectRegistry<Object, V> registry, int key) {
    return OptionalUtil.<J, V>convertOption(registry.getForKey(key));
  }

  public static <J, V extends J> Optional<J> lookupInRegistry(
      ObjectRegistry<String, V> registry, String key) {
    return Util.<String, J, V>lookupInRegistry(registry, key);
  }

  public static <K, J, V extends J> Optional<J> lookupInRegistry(
      ObjectRegistry<K, V> registry, K key) {
    return OptionalUtil.<J, V>convertOption(registry.getForKey(key));
  }
}
