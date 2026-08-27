# Security Policy

## Reporting a Vulnerability

**Do not report security vulnerabilities through public GitHub issues, pull
requests, or the mailing lists.**

Report them privately to the Apache Security team:

    security@apache.org

Apache Pekko does not operate a separate project security list; reports go to
the foundation-wide address above, which routes to the Pekko PMC.

Please follow the [guidelines laid down by the Apache Security
team](https://www.apache.org/security/).

To receive security announcements, subscribe to the [Apache Announce Mailing
List](https://lists.apache.org/list.html?announce@apache.org).

## Security Model

Before reporting, please read Apache Pekko HTTP's threat model:

[THREAT_MODEL.md](THREAT_MODEL.md)

It states what Pekko HTTP treats as a vulnerability and what it does not — the
documented parsing limits that bound untrusted input, which configuration
defaults change the security envelope, and which properties are deliberately
left to the application. Reports that fall outside the model will be closed
citing the relevant section, so checking first will save you time.

Three points catch most reporters:

- **Pekko HTTP is not meant to face the public internet unaided.** The project
  recommends fronting it with an enterprise-grade routing solution or a load
  balancer. Denial-of-service resistance is claimed only as "pretty well under
  most known attacks", not absolutely. See §4 and §14 Q1.
- **Pekko HTTP is a toolkit, not a security system.** It provides no
  authentication policy, no authorization model, no CSRF protection and no
  output encoding. The security directives route credentials to a verifier the
  application supplies. See §9.
- **Requests rejected for exceeding a documented limit are the limits working**,
  not a bug. The limits are listed in §5a.

## Further Security Documentation

- [Pekko HTTP security announcements](https://pekko.apache.org/docs/pekko-http/current/security.html)
- [Apache Pekko threat model](https://github.com/apache/pekko/blob/main/THREAT_MODEL.md) — the actor, stream, remoting and cluster layers this project builds on
