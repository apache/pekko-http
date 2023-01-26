/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model;

import org.apache.pekko.annotation.DoNotInherit;

@DoNotInherit
public abstract class AttributeKey<T> {
    public static <U> AttributeKey<U> create(String name, Class<U> clazz) {
        return new org.apache.pekko.http.scaladsl.model.AttributeKey<U>(name, clazz);
    }
}
