/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    OversizedSseStrategy strategy =
        OversizedSseStrategy.fromScala(
            org.apache.pekko.http.scaladsl.settings.OversizedSseStrategy.FailStream$.MODULE$);
    assertEquals("Should convert from Scala FailStream", OversizedSseStrategy.FailStream, strategy);
  }
}
