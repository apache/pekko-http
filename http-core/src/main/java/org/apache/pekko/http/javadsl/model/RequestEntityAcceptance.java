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

/**
 * @see RequestEntityAcceptances for convenience access to often used values. Do not extend this to
 *     a concrete Java class, as implementation of RequestEntityAcceptation should only exist in
 *     Scala
 */
public abstract class RequestEntityAcceptance {
  public abstract boolean isEntityAccepted();
}
