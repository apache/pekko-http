# 0. Release Notes (1.4.x)

## 1.4.0

Release notes for Apache Pekko HTTP 1.4.0. See [GitHub Milestone for 1.4.0](https://github.com/apache/pekko-http/milestone/13?closed=1) for a fuller list of changes.

It is strongly recommended that you avoid using Pekko 1.0.x jars with this release, you should use Pekko 1.1.x jars (or later 1.x releases) where possible. We don't expect there to be problems running with Pekko 1.0.x jars but Pekko HTTP 1.4 jars are built with Pekko 1.1 jars.

### Changes
* Don't rely on Pekko Core ccompat ([PR890](https://github.com/apache/pekko-http/pull/890))
* Correct Content-Length rendering based on method+status ([PR968](https://github.com/apache/pekko-http/pull/968))
* Ensure end() is called on Inflaters and Deflaters to allow earlier tidy up of resources ([#1133](https://github.com/apache/pekko-http/issue/1133))

### Dependency Changes

Most of the dependency changes are small patch level upgrades. Some exceptions include:

* Jackson 2.21.5
