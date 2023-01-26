/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

import org.apache.pekko.http.impl.util.Util;
import org.apache.pekko.http.scaladsl.model.headers.Language$;

public abstract class Language {
    public static Language create(String primaryTag, String... subTags) {
        return Language$.MODULE$.apply(primaryTag, Util.<String, String>convertArray(subTags));
    }

    public abstract String primaryTag();
    public abstract Iterable<String> getSubTags();
    public abstract LanguageRange withQValue(float qValue);
}
