# ! Security Announcements !

## Security model

Pekko HTTP-based applications should not be exposed to the public internet directly.
We believe Pekko HTTP behaves pretty well under most known Denial of Service attacks, but if you want the best security, you should use an enterprise grade routing solution.
Even using a load balancing solution like an up-to-date version of [Apache HTTP Server](https://httpd.apache.org/) or [Nginx](https://nginx.org/) would be safer than exposing Pekko HTTP-based applications directly to the public internet.

## Receiving Security Advisories
The best way to receive any and all security announcements is to subscribe to the [Apache Announce Mailing List](https://lists.apache.org/list.html?announce@apache.org).

This mailing list has a reasonable level of traffic, and receives notifications only after security reports have been managed by the core Apache teams and fixes are publicly available.

This mailing list also has announcements of releases for Apache projects.

## Reporting Vulnerabilities

We strongly encourage people to report such problems to our private security mailing list first, before disclosing them in a public forum.

Please follow the [guidelines](https://www.apache.org/security/) laid down by the Apache Security team.

Ideally, any issues affecting Apache Pekko and Akka should be reported to Apache team first. We will share the
report with the Lightbend Akka team.

## References

 * [Akka HTTP security fixes](https://doc.akka.io/docs/akka-http/10.2/security.html)
