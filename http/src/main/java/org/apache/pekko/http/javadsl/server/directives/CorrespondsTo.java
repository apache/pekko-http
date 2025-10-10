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

package org.apache.pekko.http.javadsl.server.directives;

import org.apache.pekko.annotation.InternalApi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * INTERNAL API – used for consistency specs
 *
 * <p>Used to hint at consistency spec implementations that a given JavaDSL method corresponds to a
 * method of given name in ScalaDSL.
 *
 * <p>E.g. a Java method paramsList could be hinted using <code>@CorrespondsTo("paramsSeq")</code>.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@InternalApi
public @interface CorrespondsTo {
  String value();
}
