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

package org.apache.pekko.http.javadsl.common;

import java.util.regex.Pattern;

import scala.collection.immutable.Seq;
import scala.collection.immutable.VectorBuilder;
import scala.util.matching.Regex;

public final class RegexConverters {
  private static final Seq<String> empty = new VectorBuilder<String>().result();

  /** Converts the given Java Pattern into a scala Regex, without recompiling it. */
  public static Regex toScala(Pattern p) {
    return new Regex(p, empty);
  }
}
