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

package org.apache.pekko.http.javadsl.settings;

import org.apache.pekko.actor.ActorSystem;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

public class RoutingSettingsTest extends JUnitSuite {

  @Test
  public void testCreateWithActorSystem() {
    Config config = ConfigFactory.load().resolve();
    ActorSystem sys = ActorSystem.create("test", config);
    RoutingSettings settings = RoutingSettings.create(sys);
  }
}
