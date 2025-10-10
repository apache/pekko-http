# 6. Examples and Extensions

## Examples
* https://github.com/jrudolph/pekko-http-scala-js-websocket-chat
* https://github.com/theiterators/pekko-http-microservice

If you are aware of any interesting examples that use Apache Pekko HTTP, please notify us or create a PR to modify this page.

## Extensions
There are several third party libraries that expand the functionality of Apache Pekko HTTP including:

- [aws-spi-pekko-http](https://github.com/pjfanning/aws-spi-pekko-http): Provides a non-blocking HTTP Client that works with the [AWS Java SDK v2](https://github.com/aws/aws-sdk-java-v2). This project is now part of [Pekko Connectors](https://pekko.apache.org/docs/pekko-connectors/current/) starting with v1.1.0.
- [pekko-http-json](https://github.com/pjfanning/pekko-http-json): Integrate some of the best JSON libs in Scala with Pekko HTTP.
- [pekko-streams-circe/pekko-http-json](https://github.com/mdedetrich/pekko-streams-circe): Includes streaming [Circe](https://circe.github.io/circe/) support that can be used with Pekko HTTP.
- [swagger-pekko-http](https://github.com/swagger-akka-http/swagger-pekko-http): A Scala/Java library for generating Open API (a.k.a. Swagger) from annotated Pekko HTTP code.
- [Guardrail](https://github.com/guardrail-dev/guardrail): Guardrail is a code generation tool, capable of reading from OpenAPI/Swagger specification files and generating Pekko HTTP code
- [akka-http-session](https://github.com/softwaremill/akka-http-session): Web & mobile client-side pekko-http sessions, with optional JWT support (supports Akka and Pekko).
- [sttp](https://github.com/softwaremill/sttp): Library that provides a clean, programmer-friendly API to define HTTP requests and execute them using one of the wrapped backends, pekko-http among them.
- [tapir](https://github.com/softwaremill/tapir): Tapir is a library to describe HTTP APIs, expose them as a server, consume as a client, and automatically document using open standards. Pekko HTTP is one supported backend.
- [pekko-http-metrics](https://github.com/rustedbones/pekko-http-metrics) Easily collect and expose metrics in your pekko-http server.

@ref:[pekko-http-cors](common/cors.md) was previously an independent library that has now been included in Pekko HTTP.

If you are aware of any extensions that support Apache Pekko HTTP, please notify us or create a PR to modify this page.

See the Scala Index for a more in-depth list of them. [Modules with the topic pekko-http](https://index.scala-lang.org/search?topics=pekko-http)
