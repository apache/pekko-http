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
  balancer. For denial of service the model draws an explicit line: a **single
  request within every documented limit** that causes disproportionate CPU or
  memory use is a vulnerability, and we want that report. Resource exhaustion
  that depends on request **volume** — connection floods, slow-loris at scale —
  is the fronting proxy's job and will be closed as by-design. Load-generator
  output is not a finding. See §5a for the limits and §14 Q1 for the ruling.
- **Pekko HTTP is a toolkit, not a security system.** It provides no
  authentication policy, no authorization model, no CSRF protection and no
  output encoding. The security directives route credentials to a verifier the
  application supplies. See §9.
- **Requests rejected for exceeding a documented limit are the limits working**,
  not a bug. The limits are listed in §5a.
- **Configuration defaults are compatibility choices, not security claims.** Pekko
  HTTP inherits a large deployment base from Akka HTTP, and tightening a shipped
  default breaks working deployments on upgrade. A report that a default *should*
  be stricter is a change request, not a vulnerability, and will be closed as
  by-design — but it is genuinely welcome on the development list, where the PMC
  will weigh it on its merits. You are free to strengthen any of these settings in
  your own configuration, and §10 says which ones matter most. See §5b.

**What we do want.** The flip side of the above is the report this project values
most: **an implementation that does not do what it is documented to do.** If a
parsing limit fails to bound what it claims to bound, if a control that is switched
on can be bypassed, if a containment check can be walked around — that is a defect,
it is in scope, and we will fix it. The defaults debate is about which value ships;
it never excuses a mechanism that does not work.

## Further Security Documentation

- [Pekko HTTP security announcements](https://pekko.apache.org/docs/pekko-http/current/security.html)
- [Apache Pekko threat model](https://github.com/apache/pekko/blob/main/THREAT_MODEL.md) — the actor, stream, remoting and cluster layers this project builds on
