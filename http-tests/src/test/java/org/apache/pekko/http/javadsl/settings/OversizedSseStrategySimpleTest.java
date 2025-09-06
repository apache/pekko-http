/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.http.javadsl.settings;

import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;
import static org.junit.Assert.*;

public class OversizedSseStrategySimpleTest extends JUnitSuite {

  @Test
  public void testEnumValues() {
    // Simple test that the enum values exist and can be converted
    OversizedSseStrategy failStream = OversizedSseStrategy.FailStream;
    OversizedSseStrategy logAndSkip = OversizedSseStrategy.LogAndSkip;
    OversizedSseStrategy truncate = OversizedSseStrategy.Truncate;
    OversizedSseStrategy deadLetter = OversizedSseStrategy.DeadLetter;
    
    assertNotNull("FailStream should not be null", failStream);
    assertNotNull("LogAndSkip should not be null", logAndSkip);
    assertNotNull("Truncate should not be null", truncate);
    assertNotNull("DeadLetter should not be null", deadLetter);
  }

  @Test
  public void testFromScala() {
    // Test that fromScala method works
    OversizedSseStrategy strategy = OversizedSseStrategy.fromScala(
        org.apache.pekko.http.scaladsl.settings.OversizedSseStrategy.FailStream$.MODULE$
    );
    assertEquals("Should convert from Scala FailStream", OversizedSseStrategy.FailStream, strategy);
  }
}
