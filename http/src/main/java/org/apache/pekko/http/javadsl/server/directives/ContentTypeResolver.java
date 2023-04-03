/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.server.directives;

import org.apache.pekko.http.javadsl.model.ContentType;

/**
 * Implement this interface to provide a custom mapping from a file name to a [[org.apache.pekko.http.javadsl.model.ContentType]].
 */
@FunctionalInterface
public interface ContentTypeResolver {
    ContentType resolve(String fileName);

    /**
     * Returns a Scala DSL representation of this content type resolver
     */
    default org.apache.pekko.http.scaladsl.server.directives.ContentTypeResolver asScala() {
        ContentTypeResolver delegate = this;
        return new org.apache.pekko.http.scaladsl.server.directives.ContentTypeResolver() {
            @Override
            public ContentType resolve(String fileName) {
                return delegate.resolve(fileName);
            }
            
            @Override
            public org.apache.pekko.http.scaladsl.model.ContentType apply(String fileName) {
                return (org.apache.pekko.http.scaladsl.model.ContentType) delegate.resolve(fileName);
            }
        };
    }
}
