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

import scala.None$;
import scala.collection.immutable.Map$;
import scala.collection.immutable.Seq;
import scala.jdk.javaapi.OptionConverters;

import org.apache.pekko.stream.scaladsl.Source;
import org.apache.pekko.http.ccompat.MapHelpers;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** Contains internal helper methods. */
public abstract class Util {
  @SuppressWarnings("unchecked") // no support for covariance of option in Java
  // needed to provide covariant conversions that the Java interfaces don't provide automatically.
  // The alternative would be having to cast around everywhere instead of doing it here in a central
  // place.
  public static <U, T extends U> Optional<U> convertOption(scala.Option<T> o) {
    return (Optional<U>) (Object) OptionConverters.toJava(o);
  }

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

  @SuppressWarnings("unchecked") // contains an upcast
  public static <T, U extends T> scala.Option<U> convertOptionalToScala(Optional<T> o) {
    return OptionConverters.toScala((Optional<U>) o);
  }

  // This is needed to be used in Java source code that calls Scala code which expects scala.Long
  // since an implicit cast from java.lang.Long to scala.Long is not available in Java source
  public static scala.Option<Object> convertOptionalToScala(OptionalLong o) {
    if (o.isPresent()) {
      return new scala.Some(o.getAsLong());
    } else {
      return scala.Option.empty();
    }
  }

  // This is needed to be used in Java source code that calls Scala code which expects scala.Int
  // since an implicit cast from java.lang.Int to scala.Int is not available in Java source
  public static scala.Option<Object> convertOptionalToScala(OptionalInt o) {
    if (o.isPresent()) {
      return new scala.Some(o.getAsInt());
    } else {
      return scala.Option.empty();
    }
  }

  public static final scala.collection.immutable.Map<String, String> emptyMap =
      Map$.MODULE$.<String, String>empty();

  private static final scala.Option<?> noneValue = None$.MODULE$;

  @SuppressWarnings("unchecked")
  public static <T> scala.Option<T> scalaNone() {
    return (scala.Option<T>) noneValue;
  }

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
    return Util.<J, V>convertOption(registry.getForKey(key));
  }

  public static <J, V extends J> Optional<J> lookupInRegistry(
      ObjectRegistry<String, V> registry, String key) {
    return Util.<String, J, V>lookupInRegistry(registry, key);
  }

  public static <K, J, V extends J> Optional<J> lookupInRegistry(
      ObjectRegistry<K, V> registry, K key) {
    return Util.<J, V>convertOption(registry.getForKey(key));
  }
}
