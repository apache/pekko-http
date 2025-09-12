The setup of the scalafix module roughly follows the example in https://github.com/scalacenter/scalafix.g8.

## Adding new rules

 * Add before/after test file in scalafix-test-input / scalafix-test-output
 * Add rule in scalafix-rules
 * run test in `http-scalafix-tests`

## Applying locally defined rules to docs examples

 * run `scalafixEnable` on the sbt shell (this will unfortunately require a complete rebuild afterwards)
 * run `set scalacOptions in ThisBuild += "-P:semanticdb:synthetics:on"` to allow access to synthetics
 * e.g. run `docs/scalafixAll MigrateToServerBuilder`
