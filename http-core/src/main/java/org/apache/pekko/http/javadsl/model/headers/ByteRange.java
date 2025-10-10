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

package org.apache.pekko.http.javadsl.model.headers;

import org.apache.pekko.http.scaladsl.model.headers.ByteRange$;

import java.util.OptionalLong;

public abstract class ByteRange {
  public abstract boolean isSlice();

  public abstract boolean isFromOffset();

  public abstract boolean isSuffix();

  public abstract OptionalLong getSliceFirst();

  public abstract OptionalLong getSliceLast();

  public abstract OptionalLong getOffset();

  public abstract OptionalLong getSuffixLength();

  public static ByteRange createSlice(long first, long last) {
    return ByteRange$.MODULE$.apply(first, last);
  }

  public static ByteRange createFromOffset(long offset) {
    return ByteRange$.MODULE$.fromOffset(offset);
  }

  public static ByteRange createSuffix(long length) {
    return ByteRange$.MODULE$.suffix(length);
  }
}
