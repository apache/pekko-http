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

package org.apache.pekko.http.javadsl.unmarshalling;

public interface Unmarshallers {
  /** Creates an unmarshaller from an asynchronous Java function. */
  // #unmarshaller-creation
  <A, B> Unmarshaller<A, B> async(
      java.util.function.Function<A, java.util.concurrent.CompletionStage<B>> f);
  // #unmarshaller-creation

  /** Creates an unmarshaller from a Java function. */
  // #unmarshaller-creation
  <A, B> Unmarshaller<A, B> sync(java.util.function.Function<A, B> f);
  // #unmarshaller-creation

  // #unmarshaller-creation
  <A, B> Unmarshaller<A, B> firstOf(Unmarshaller<A, B> u1, Unmarshaller<A, B> u2);

  <A, B> Unmarshaller<A, B> firstOf(
      Unmarshaller<A, B> u1, Unmarshaller<A, B> u2, Unmarshaller<A, B> u3);

  <A, B> Unmarshaller<A, B> firstOf(
      Unmarshaller<A, B> u1, Unmarshaller<A, B> u2, Unmarshaller<A, B> u3, Unmarshaller<A, B> u4);

  <A, B> Unmarshaller<A, B> firstOf(
      Unmarshaller<A, B> u1,
      Unmarshaller<A, B> u2,
      Unmarshaller<A, B> u3,
      Unmarshaller<A, B> u4,
      Unmarshaller<A, B> u5);
  // #unmarshaller-creation
}
