/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.http.javadsl;

//#seal-handler-example
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.ExceptionHandler;
import org.apache.pekko.http.javadsl.server.PathMatchers;
import org.apache.pekko.http.javadsl.server.RejectionHandler;
import org.apache.pekko.http.javadsl.server.Route;

import static org.apache.pekko.http.javadsl.server.PathMatchers.integerSegment;

public class ExceptionHandlerInSealExample extends AllDirectives {

  public Route createRoute() {
    final ExceptionHandler divByZeroHandler = ExceptionHandler.newBuilder()
      .match(ArithmeticException.class, x ->
        complete(StatusCodes.BAD_REQUEST, "You've got your arithmetic wrong, fool!"))
      .build();

    final RejectionHandler defaultHandler = RejectionHandler.defaultHandler();

    return path(PathMatchers.segment("divide").slash(integerSegment()).slash(integerSegment()), (a, b) ->
      complete("The result is " + (a / b))
    ).seal(defaultHandler, divByZeroHandler);
  }
}
//#seal-handler-example
