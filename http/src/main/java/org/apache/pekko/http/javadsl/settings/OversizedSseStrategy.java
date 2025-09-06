/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.http.javadsl.settings;

/**
 * Strategy for handling oversized SSE messages that exceed the configured max-line-size.
 */
public enum OversizedSseStrategy {
  /**
   * Fail the stream with an IllegalStateException when an oversized message is encountered.
   * This is the default behavior to maintain backward compatibility.
   */
  FailStream,
  
  /**
   * Log a warning and skip the oversized message, continuing with stream processing.
   */
  LogAndSkip,
  
  /**
   * Log an info message and truncate the oversized message to the configured max-line-size,
   * continuing with stream processing.
   */
  Truncate,
  
  /**
   * Send the oversized message to dead letters, continuing with stream processing.
   */
  DeadLetter;

  /**
   * Convert this Java enum to the corresponding Scala enum value.
   */
  public org.apache.pekko.http.scaladsl.settings.OversizedSseStrategy asScala() {
    switch (this) {
      case FailStream:
        return org.apache.pekko.http.scaladsl.settings.OversizedSseStrategy.FailStream$.MODULE$;
      case LogAndSkip:
        return org.apache.pekko.http.scaladsl.settings.OversizedSseStrategy.LogAndSkip$.MODULE$;
      case Truncate:
        return org.apache.pekko.http.scaladsl.settings.OversizedSseStrategy.Truncate$.MODULE$;
      case DeadLetter:
        return org.apache.pekko.http.scaladsl.settings.OversizedSseStrategy.DeadLetter$.MODULE$;
      default:
        throw new IllegalArgumentException("Unknown OversizedSseStrategy: " + this);
    }
  }

  /**
   * Convert from a Scala enum value to the corresponding Java enum value.
   */
  public static OversizedSseStrategy fromScala(org.apache.pekko.http.scaladsl.settings.OversizedSseStrategy scalaStrategy) {
    if (scalaStrategy == org.apache.pekko.http.scaladsl.settings.OversizedSseStrategy.FailStream$.MODULE$) {
      return FailStream;
    } else if (scalaStrategy == org.apache.pekko.http.scaladsl.settings.OversizedSseStrategy.LogAndSkip$.MODULE$) {
      return LogAndSkip;
    } else if (scalaStrategy == org.apache.pekko.http.scaladsl.settings.OversizedSseStrategy.Truncate$.MODULE$) {
      return Truncate;
    } else if (scalaStrategy == org.apache.pekko.http.scaladsl.settings.OversizedSseStrategy.DeadLetter$.MODULE$) {
      return DeadLetter;
    } else {
      throw new IllegalArgumentException("Unknown Scala OversizedSseStrategy: " + scalaStrategy);
    }
  }
}
