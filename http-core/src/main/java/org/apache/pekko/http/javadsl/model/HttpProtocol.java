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
 * Represents an Http protocol (currently only HTTP/1.0 or HTTP/1.1). See {@link HttpProtocols} for
 * the predefined constants for the supported protocols.
 *
 * @see HttpProtocols for convenience access to often used values.
 */
public abstract class HttpProtocol {
  /** Returns the String representation of this protocol. */
  public abstract String value();
}
