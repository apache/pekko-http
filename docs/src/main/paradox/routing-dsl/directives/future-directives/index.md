<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# FuturesDirectives

Future directives can be used to run inner routes once the provided @scala[`Future[T]`]@java[`CompletionStage<T>`] has been completed.

@@toc { depth=1 }

@@@ index

* [onComplete](onComplete.md)
* [onCompleteWithBreaker](onCompleteWithBreaker.md)
* [onSuccess](onSuccess.md)
* [completeOrRecoverWith](completeOrRecoverWith.md)

@@@