<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

## The URI model

Apache Pekko HTTP offers its own specialised @apidoc[Uri] model class which is tuned for both performance and idiomatic usage within
other types of the HTTP model. For example, an @apidoc[HttpRequest]'s target URI is parsed into this type, where all character
escaping and other URI specific semantics are applied.

### Parsing a URI string

We follow [RFC 3986](https://tools.ietf.org/html/rfc3986#section-1.1.2) to implement the URI parsing rules.
When you try to parse a URI string, Apache Pekko HTTP internally creates an instance of the @apidoc[Uri] class, which holds the modeled URI components inside.

For example, the following creates an instance of a simple valid URI:

Scala
:   
    ```
    Uri("http://localhost")
    ```
    
Java
:   
    ```
    Uri.create("http://localhost");
    ```


Below are some more examples of valid URI strings, and how you can construct a @apidoc[Uri] model class instances
@scala[,using `Uri.from()` method by passing `scheme`, `host`, `path` and `query` parameters].

Scala
:   @@snip [UriSpec.scala](/http-core/src/test/scala/org/apache/pekko/http/scaladsl/model/UriSpec.scala) { #valid-uri-examples }

Java
:   @@snip [UriTest.scala](/http-core/src/test/java/org/apache/pekko/http/javadsl/model/UriTest.java) { #valid-uri-examples }

For exact definitions of the parts of a URI, like `scheme`, `path` and `query` refer to [RFC 3986](https://tools.ietf.org/html/rfc3986#section-1.1.2).
Here's a little overview:

```
  foo://example.com:8042/over/there?name=ferret#nose
  \_/   \______________/\_________/ \_________/ \__/
   |           |            |            |        |
scheme     authority       path        query   fragment
   |   _____________________|__
  / \ /                        \
  urn:example:animal:ferret:nose
```

For "special" characters in URI, you typically use percent encoding like below.
Percent encoding is discussed in more detail in the @ref[Query String in URI](#query-string-in-uri) section.

Scala
:   @@snip [UriSpec.scala](/http-core/src/test/scala/org/apache/pekko/http/scaladsl/model/UriSpec.scala) { #dont-double-decode }

Java
:   @@snip [UriTest.java](/http-core/src/test/java/org/apache/pekko/http/javadsl/model/UriTest.java) { #dont-double-decode }


#### Invalid URI strings and IllegalUriException

When an invalid URI string is passed to `Uri()` as below, an `IllegalUriException` is thrown.

Scala
:   @@snip [UriSpec.scala](/http-core/src/test/scala/org/apache/pekko/http/scaladsl/model/UriSpec.scala) { #illegal-cases-immediate-exception }

Java
:   @@snip [UriTest.java](/http-core/src/test/java/org/apache/pekko/http/javadsl/model/UriTest.java) { #illegal-scheme #illegal-userinfo #illegal-percent-encoding #illegal-path #illegal-path-with-control-char }

#### Directives to extract URI components

To extract URI components with directives, see following references:

* @ref:[extractUri](../routing-dsl/directives/basic-directives/extractUri.md)
* @ref:[extractScheme](../routing-dsl/directives/scheme-directives/extractScheme.md)
* @ref:[scheme](../routing-dsl/directives/scheme-directives/scheme.md)
* @ref:[PathDirectives](../routing-dsl/directives/path-directives/index.md)
* @ref:[ParameterDirectives](../routing-dsl/directives/parameter-directives/index.md)

### Obtaining the raw request URI

For incoming HTTP/1.x requests, turn on `pekko.http.server.raw-request-uri-header` to obtain the
original request target in the synthetic `Raw-Request-URI` header.

### Server-wide HTTP/2 OPTIONS

To accept server-wide `OPTIONS *` over HTTP/2, set
`pekko.http.server.http2.accept-asterisk-options = on` or opt in through the server settings:

@scala[`settings.withHttp2Settings(settings.http2Settings.withAcceptAsteriskOptions(true))`]
@java[`settings.withHttp2Settings(settings.getHttp2Settings().withAcceptAsteriskOptions(true))`]

The HTTP/2 setting defaults to off. When enabled, the request's effective URI retains the scheme and any authority, with an empty path. The original `*` is available in a request attribute:

@scala[Read the attribute with `request.attribute(AttributeKeys.asteriskRequestTarget).contains("*")`.]
@java[Read the attribute with `request.getAttribute(AttributeKeys.asteriskRequestTarget).filter("*"::equals).isPresent()`.]

Check the attribute before dispatching to HTTP/2 resource-specific OPTIONS routes: an empty effective
path can also match root-path directives such as `pathEnd` and `pathEndOrSingleSlash`. An ordinary
target with a query, even an empty query (`/?`), does not get the asterisk attribute.

The HTTP/2 setting does not change HTTP/1.x handling. In a handler that serves both protocols,
use the HTTP/2 attribute or, for HTTP/1.x with raw target capture enabled, check that the request is
OPTIONS and the modeled `Raw-Request-URI` is `*`. The HTTP/2 attribute is independent of
`raw-request-uri-header`, is not rendered on the wire, and does not override an outbound request URI.

### Query string in URI

Although any part of URI can have special characters, it is more common for the query string in URI to have special characters,
which are typically [percent encoded](https://en.wikipedia.org/wiki/Percent-encoding).

@scala[@apidoc[Uri] class's `query()` method]@java[The method `Uri::query()`] returns the query string of the URI, which is modeled in an instance of the `Query` class.
When you instantiate a @apidoc[Uri] class by passing a URI string, the query string is stored in its raw string form.
Then, when you call the `query()` method, the query string is parsed from the raw string.

You can add query parameters with @apidoc[Uri] class's @scala[`withQuery()` method]@java[`Uri::query()` method]

Scala
:   @@snip [UriSpec.scala](/http-core/src/test/scala/org/apache/pekko/http/scaladsl/model/UriSpec.scala) { #create-uri-with-query }

Java
:   @@snip [UriTest.java](/http-core/src/test/java/org/apache/pekko/http/javadsl/model/UriTest.java) { #create-uri-with-query }

The below code illustrates how valid query strings are parsed.
Especially, you can check how percent encoding is used and how special characters like `+` and `;` are parsed.

@@@ note
The `mode` parameter to `Query()` and `Uri.query()` is discussed in @ref[Strict and Relaxed Mode](#strict-and-relaxed-mode).
@@@

Scala
:   @@snip [UriSpec.scala](/http-core/src/test/scala/org/apache/pekko/http/scaladsl/model/UriSpec.scala) { #query-strict-definition }

Java
:   @@snip [UriTest.java](/http-core/src/test/java/org/apache/pekko/http/javadsl/model/UriTest.java) { #query-strict-definition }


Scala
:   @@snip [UriSpec.scala](/http-core/src/test/scala/org/apache/pekko/http/scaladsl/model/UriSpec.scala) { #query-strict-mode }

Java
:   @@snip [UriTest.java](/http-core/src/test/java/org/apache/pekko/http/javadsl/model/UriTest.java) { #query-strict-mode }

Note that:

```
  Uri("http://localhost?a=b").query()
```

is equivalent to:

```
  Query("a=b")
```

As in the [section 3.4 of RFC 3986](https://tools.ietf.org/html/rfc3986#section-3.4),
some special characters like "/" and "?" are allowed inside a query string, without escaping them using ("%") signs.

> The characters slash ("/") and question mark ("?") may represent data within the query component.

"/" and "?" are commonly used when you have a URI whose query parameter has another URI.

Scala
:   @@snip [UriSpec.scala](/http-core/src/test/scala/org/apache/pekko/http/scaladsl/model/UriSpec.scala) { #query-strict-without-percent-encode }

Java
:   @@snip [UriTest.java](/http-core/src/test/java/org/apache/pekko/http/javadsl/model/UriTest.java) { #query-strict-without-percent-encode }

However, some other special characters can cause `IllegalUriException` without percent encoding as follows.

Scala
:   @@snip [UriSpec.scala](/http-core/src/test/scala/org/apache/pekko/http/scaladsl/model/UriSpec.scala) { #query-strict-mode-exception-1 }

Java
:   @@snip [UriTest.java](/http-core/src/test/java/org/apache/pekko/http/javadsl/model/UriTest.java) { #query-strict-mode-exception-1 }


Scala
:   @@snip [UriSpec.scala](/http-core/src/test/scala/org/apache/pekko/http/scaladsl/model/UriSpec.scala) { #query-strict-mode-exception-2 }

Java
:   @@snip [UriTest.java](/http-core/src/test/java/org/apache/pekko/http/javadsl/model/UriTest.java) { #query-strict-mode-exception-2 }

#### Strict and Relaxed Mode

The `Uri.query()` method and `Query()` take a parameter `mode`, which is either `Uri.ParsingMode.Strict` or `Uri.ParsingMode.Relaxed`.
Switching the mode gives different behavior on parsing some special characters in URI.

Scala
:   @@snip [UriSpec.scala](/http-core/src/test/scala/org/apache/pekko/http/scaladsl/model/UriSpec.scala) { #query-relaxed-mode }

Java
:   @@snip [UriTest.java](/http-core/src/test/java/org/apache/pekko/http/javadsl/model/UriTest.java) { #query-relaxed-definition }

The below two cases threw `IllegalUriException` when you specified the `Strict` mode,

Scala
:   @@snip [UriSpec.scala](/http-core/src/test/scala/org/apache/pekko/http/scaladsl/model/UriSpec.scala) { #query-strict-mode-exception-1 }

Java
:   @@snip [UriTest.java](/http-core/src/test/java/org/apache/pekko/http/javadsl/model/UriTest.java) { #query-strict-mode-exception-1 #query-strict-mode-exception-2 }

but the `Relaxed` mode parses them as they are.

Scala
:   @@snip [UriSpec.scala](/http-core/src/test/scala/org/apache/pekko/http/scaladsl/model/UriSpec.scala) { #query-relaxed-mode-success }

Java
:   @@snip [UriTest.java](/http-core/src/test/java/org/apache/pekko/http/javadsl/model/UriTest.java) { #query-relaxed-mode-success }

However, even with the `Relaxed` mode, there are still invalid special characters which require percent encoding.

Scala
:   @@snip [UriSpec.scala](/http-core/src/test/scala/org/apache/pekko/http/scaladsl/model/UriSpec.scala) { #query-relaxed-mode-exception }

Java
:   @@snip [UriTest.java](/http-core/src/test/java/org/apache/pekko/http/javadsl/model/UriTest.java) { #query-relaxed-mode-exception-1 }

Other than specifying the `mode` in the parameters, like when using directives, you can specify the `mode` in your configuration as follows.

```
    # Sets the strictness mode for parsing request target URIs.
    # The following values are defined:
    #
    # `strict`: RFC3986-compliant URIs are required,
    #     a 400 response is triggered on violations
    #
    # `relaxed`: all visible 7-Bit ASCII chars are allowed
    #
    uri-parsing-mode = strict
```

To access the raw, unparsed representation of the query part of a URI use the `rawQueryString` member of the @apidoc[Uri] class.

#### Directives to extract query parameters

If you want to use directives to extract query parameters, see below pages.

* @ref:[parameters](../routing-dsl/directives/parameter-directives/parameters.md)
* @ref:[parameter](../routing-dsl/directives/parameter-directives/parameter.md)
