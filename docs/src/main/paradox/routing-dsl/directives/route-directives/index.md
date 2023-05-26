# RouteDirectives

The @apidoc[RouteDirectives] have a special role in pekko-http's routing DSL. Contrary to all other directives (except most
@ref[FileAndResourceDirectives](../file-and-resource-directives/index.md)) they do not produce instances of type `Directive[L <: HList]` but rather "plain"
routes of type @scala[@scaladoc[Route](org.apache.pekko.http.scaladsl.server.index#Route=org.apache.pekko.http.scaladsl.server.RequestContext=%3Escala.concurrent.Future[org.apache.pekko.http.scaladsl.server.RouteResult])]@java[@javadoc[Route](org.apache.pekko.http.javadsl.server.Route)].
The reason is that the @apidoc[RouteDirectives] are not meant for wrapping an inner route (like most other directives, as
intermediate-level elements of a route structure, do) but rather form the leaves of the actual route structure **leaves**.

So in most cases the inner-most element of a route structure branch is one of the @apidoc[RouteDirectives] (or
@ref[FileAndResourceDirectives](../file-and-resource-directives/index.md)):

@@toc { depth=1 }

@@@ index

* [complete](complete.md)
* [failWith](failWith.md)
* [handle](handle.md)
* [handleSync](handleSync.md)
* [redirect](redirect.md)
* [reject](reject.md)

@@@
