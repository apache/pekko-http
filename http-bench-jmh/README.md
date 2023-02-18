# Apache Pekko Http Microbenchmarks

This subproject contains some microbenchmarks parts of Pekko Http.

You can run them like:

   project http-bench-jmh
   jmh:run -i 3 -wi 3 -f 1 .*LineParserBenchmark

Use 'jmh:run -h' to get an overview of the available options.