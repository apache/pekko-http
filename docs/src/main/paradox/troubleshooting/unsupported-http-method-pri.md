<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# Unsupported HTTP method: PRI

```
Illegal request, responding with status '501 Not Implemented': Unsupported HTTP method: PRI
```

This indicates that an HTTP/2 request was received, but the server was not
correctly set up to handle those. You may have to:

* Make sure the @ref[`pekko.http.server.enable-http2` option](../server-side/http2.md#enable-http-2-support) is enabled
* Make sure you are not using @apidoc[Http().bindAndHandle()](Http$) or @apidoc[Http().newServerAt().bindFlow()](ServerBuilder), but @apidoc[Http().newServerAt().bind()](ServerBuilder).
