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

package docs.http.javadsl.server.testkit;

import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.junit.Test;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.TimeUnit;

public class WithTimeoutTest extends JUnitRouteTest {
  // #timeout-setting
  @Override
  public FiniteDuration awaitDuration() {
    return FiniteDuration.create(5, TimeUnit.SECONDS);
  }
  // #timeout-setting

  @Test
  public void dummy() {}
}
