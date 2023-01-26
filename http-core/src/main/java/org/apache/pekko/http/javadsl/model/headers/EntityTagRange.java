/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

import org.apache.pekko.http.impl.util.Util;

/**
 * @see EntityTagRanges for convenience access to often used values.
 */
public abstract class EntityTagRange {
    public static EntityTagRange create(EntityTag... tags) {
        return org.apache.pekko.http.scaladsl.model.headers.EntityTagRange.apply(Util.<EntityTag, org.apache.pekko.http.scaladsl.model.headers.EntityTag>convertArray(tags));
    }
}
