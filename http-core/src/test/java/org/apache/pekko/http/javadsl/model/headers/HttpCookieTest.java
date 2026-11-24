/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.http.javadsl.model.headers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.pekko.http.javadsl.model.HttpHeader;
import org.junit.jupiter.api.Test;

public class HttpCookieTest {
  @Test
  public void testPartitionedCookie() {
    HttpCookie cookie = HttpCookie.create("theme", "light");
    assertFalse(cookie.partitioned());

    HttpCookie partitioned = cookie.withPartitioned(true);
    assertTrue(partitioned.partitioned());
    assertFalse(partitioned.secure());
    assertFalse(partitioned.withPartitioned(false).partitioned());

    SetCookie header = SetCookie.create(partitioned.withSecure(true));
    assertEquals("theme=light; Secure; Partitioned", header.value());
    SetCookie parsed = (SetCookie) HttpHeader.parse("Set-Cookie", header.value());
    assertTrue(parsed.cookie().partitioned());
    assertTrue(parsed.cookie().secure());
    assertTrue(parsed.cookie().getExtension().isEmpty());
  }
}
