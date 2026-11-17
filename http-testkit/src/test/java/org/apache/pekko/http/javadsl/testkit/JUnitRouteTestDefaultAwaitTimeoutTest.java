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

package org.apache.pekko.http.javadsl.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

public class JUnitRouteTestDefaultAwaitTimeoutTest extends JUnitJupiterRouteTest {

  @Override
  public Duration defaultAwaitTimeout() {
    return Duration.ofSeconds(7);
  }

  @Test
  public void testDefaultAwaitTimeoutOverrideDrivesAwaitDurations() {
    Duration awaitTimeout = awaitTimeout();
    // the default is scaled by pekko.test.timefactor, which is at least 1
    assertTrue(
        awaitTimeout.compareTo(Duration.ofSeconds(7)) >= 0,
        "expected awaitTimeout to be based on the overridden 7s default, but was " + awaitTimeout);
    assertEquals(
        awaitTimeout.toMillis(),
        awaitDuration().toMillis(),
        "awaitTimeout and awaitDuration should agree");
  }
}
