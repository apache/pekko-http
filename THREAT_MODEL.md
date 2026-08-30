# Apache Pekko HTTP — Threat Model

**Status:** DRAFT — awaiting Pekko PMC review. Not yet maintainer-ratified.

| | |
| --- | --- |
| **Project** | Apache Pekko HTTP |
| **Written against** | commit `444d939`, `main` |
| **Date** | 2026-08-27 |
| **Authors** | ASF Security team, at the request of the Pekko PMC |
| **Version binding** | Versioned alongside the project. A report against version *N* is triaged against the model as it stood at *N*, not at `main`. |
| **Reporting** | Findings that violate a §8 property should be reported per [`SECURITY.md`](SECURITY.md). Findings under §3 or §9 will be closed citing this document. |
| **Companion model** | Pekko HTTP is built on Pekko Streams and Actors. The actor, remoting and cluster layers are modeled in [`apache/pekko`'s `THREAT_MODEL.md`](https://github.com/apache/pekko/blob/main/THREAT_MODEL.md); this document does not restate them. |

**Provenance legend.**
*(documented)* — stated in Pekko HTTP's own docs or `reference.conf` comments, cited.
*(maintainer)* — stated by a Pekko maintainer in review of this document.
*(inferred)* — reasoned from code or config defaults, **not yet confirmed**; each has a matching question in §14.

**Draft confidence:** 17 documented / 0 maintainer / 15 inferred — counting inline tags only. The §5a limits table and the §15 back-map carry a further ~35 documented facts under a single collective citation each, so the document is more evidence-backed than the bare ratio suggests. What is genuinely inferred clusters in three places: the §3/§7/§9 non-goals, the negative claims in §5, and the exact boundary of the DoS claim — §14 Q1, Q7 and Q9 respectively. §14 Q3 and Q5 were originally inferred and have since been resolved against the source; they remain listed so the PMC can confirm the *disposition*, not the fact.

Apache Pekko HTTP is a Scala/Java toolkit for building HTTP-based services and clients on top of Pekko Streams. It provides a full HTTP/1.1 and HTTP/2 implementation — parsing, connection management, marshalling, and a routing DSL of composable "directives" — as an **embeddable library**, not a standalone server. The application supplies the routes, the authentication, and the deployment.

---

## §2 Scope and intended use

Pekko HTTP is a library the application embeds. There is no Pekko HTTP daemon to secure independently of the service built on it.

Caller roles:

- **The embedding application** — fully trusted. Defines routes, supplies handlers, chooses configuration.
- **The operator/deployer** — trusted for the instance. Chooses what sits in front of the service (see §4), TLS termination, and limits.
- **The HTTP client** — **untrusted**. This is the adversary the model is mostly about.

### Component families

| Family | Modules | Entry point | In model |
| --- | --- | --- | --- |
| Core protocol | `http-core`, `parsing` | Wire bytes → `HttpRequest` / `HttpResponse` | **yes — primary surface** |
| HTTP/2 | `http2-tests` support in `http-core` | HTTP/2 framing, HPACK, streams | **yes** |
| Routing DSL | `http` | `Route`, directives, rejection/exception handling | **yes** |
| Marshalling | `http-marshallers-scala`, `http-marshallers-java` | JSON/XML entity conversion | **yes** |
| CORS | `http-cors` | `cors()` directive | **yes — see §5a** |
| Caching | `http-caching` | response cache directives | **yes** |
| Test kits | `http-testkit`, `http-testkit-munit`, `http-tests`, `http-compatibility-tests`, `http2-tests` | — | **no** — §3 |
| Benchmarks | `http-bench-jmh` | — | **no** — §3 |
| Lint / build / docs | `http-scalafix`, `docs`, `project`, `scripts`, `legal` | — | **no** — §3 |

*(inferred — the in/out split is the ASF Security team's proposal; see §14 Q6)*

---

## §3 Out of scope (explicit non-goals)

- **Test kits, benchmarks, scalafix rules, build tooling and documentation sources.** A finding in `http-bench-jmh` or any `*-tests` module is `OUT-OF-MODEL: unsupported-component`. *(inferred — §14 Q6)*
- **Pekko HTTP is not a WAF, and not an edge-hardened server.** The documentation says so plainly: applications *"should not be exposed to the public internet directly"* and an *"enterprise grade routing solution"* or a load balancer such as Apache HTTP Server or Nginx *"would be safer"* *(documented — `security.md`)*. See §4.
- **Pekko HTTP is not an authentication or authorization system.** It ships `authenticateBasic`, `authenticateOAuth2` and `authorize` directives, but these are *plumbing*: the credential check is a function the application supplies. Pekko HTTP has no user store, no session model, and no policy engine. *(inferred — §14 Q7)*
- **The actor, stream, remoting and cluster layers** are out of scope here and covered by `apache/pekko`'s threat model.
- **Attackers who already control the embedding process** are out of scope. *(inferred — §14 Q7)*

---

## §4 Trust boundaries and data flow

**The trust boundary is the inbound HTTP request.** Everything derived from wire bytes — request line, headers, cookies, entity, HTTP/2 frames — is attacker-controlled until the application validates it.

Pekko HTTP's documented posture is unusual and important enough to quote in full:

> "Pekko HTTP-based applications should not be exposed to the public internet directly. We believe Pekko HTTP behaves pretty well under most known Denial of Service attacks, but if you want the best security, you should use an enterprise grade routing solution. Even using a load balancing solution like an up-to-date version of Apache HTTP Server or Nginx would be safer than exposing Pekko HTTP-based applications directly to the public internet."
> — *(documented — `security.md`, "Security model")*

Read carefully, this makes a **graded** claim rather than a binary one: Pekko HTTP asserts it *"behaves pretty well under most known"* DoS attacks — not that it is DoS-proof, and not that DoS is out of scope. That hedge is the single most consequential ambiguity in this model for triage, and §14 Q1 asks the PMC to resolve it.

### Reachability preconditions per family

- **Core protocol / HTTP/2** — reachable from raw wire bytes. The strongest in-model surface: a parser defect here needs no application cooperation.
- **Routing DSL** — reachable from a request that the application's own routes expose. A finding must name the directive and show a route shape a reasonable application would write.
- **Marshalling** — reachable from an entity body **only where the application has bound that marshaller to a route**. Findings in the underlying JSON/XML library belong to that library.
- **CORS** — reachable only where the application has installed the `cors()` directive. It is opt-in, not on by default. See §5a.
- **Caching** — reachable only where the application has installed a caching directive; cache-key correctness is the sharp edge.

---

## §5 Assumptions about the environment

- **Runtime.** A conformant JVM. Pekko HTTP does not defend against a hostile JVM or in-process attacker. *(inferred — §14 Q7)*
- **Fronting infrastructure.** The documented expectation is that something sits in front in production *(documented — `security.md`)*. §14 Q1 asks how load-bearing this is.
- **TLS.** Pekko HTTP can terminate TLS itself (`HttpsConnectionContext`), but where a reverse proxy is used, termination is commonly the proxy's job. Cipher and protocol selection come from the JSSE context the application supplies. *(inferred — §14 Q8)*
- **Client IP.** `remote-address-attribute` ships `off` *(documented — `reference.conf`)*. When on, the attribute reflects the **socket** peer, which behind a proxy is the proxy. `X-Forwarded-For` is not trusted or parsed into it automatically — deriving client IP from headers is the application's decision. *(inferred — §14 Q4)*

### What Pekko HTTP does not do to its host

Negative claims, rarely written down and therefore high-priority confirmation targets *(all inferred — §14 Q9)*:

- Binds no port until the application calls a `bind*` method.
- Installs no signal handlers, spawns no child processes.
- Writes no files of its own accord; serves from disk only via directives the application installs (`getFromFile`, `getFromDirectory`).
- Does not mutate process-global state at initialization.

---

## §5a Configuration variants that change the security envelope

Pekko HTTP's resistance to malformed and abusive input is almost entirely a function of `pekko.http.server.parsing.*`. These are the **documented, shipped** limits *(all documented — `http-core/src/main/resources/reference.conf`)*:

| Setting | Default | What it bounds |
| --- | --- | --- |
| `max-uri-length` | `2k` | Request-line URI |
| `max-method-length` | `16` | Method token |
| `max-header-name-length` | `64` | Single header name |
| `max-header-value-length` | `8k` | Single header value |
| `max-header-count` | `64` | Headers per message |
| `max-content-length` | `8m` (server) | Entity size |
| `max-chunk-size` | `1m` | Single chunk |
| `max-chunk-count` | `100000` | Chunks per message |
| `max-chunk-ext-length` | `256` | Chunk extension |
| `max-comment-parsing-depth` | `5` | Nested comment recursion |
| `max-to-strict-bytes` | `8m` | `toStrict` materialization |
| `max-concurrent-streams` | `256` | HTTP/2 concurrent streams |
| `max-header-list-size` | `64 KiB` | HTTP/2 decompressed header list, **and** the accumulated HEADERS + CONTINUATION fragments for one header block |
| `max-connections` | `1024` | Server connections |
| `pipelining-limit` | `1` | In-flight pipelined requests |
| `idle-timeout` | `60 s` | Connection idle |
| `request-timeout` | `20 s` | Per-request handling |
| `uri-parsing-mode` | `strict` | URI leniency |
| `cookie-parsing-mode` | `rfc6265` | Cookie leniency |
| `verbose-error-messages` | `off` | Whether parse errors leak detail to the client — **secure default** |
| `illegal-response-header-name-processing-mode` | `error` | Blocks response-splitting via header names — **secure default** |
| `illegal-response-header-value-processing-mode` | `error` | Blocks response-splitting via header values — **secure default** |
| `server-header` | `pekko-http/${version}` | Advertises product and version |
| `remote-address-attribute` | `off` | Exposes socket peer address to routes |
| `transparent-head-requests` | `off` | HEAD handled as GET |

**These limits are the model's quantitative spine.** A report that a request *within* every documented limit causes disproportionate resource use is meaningfully different from one that simply exceeds a limit — see §14 Q1.

### The CORS defaults — needs a ruling

`http-cors` ships:

| Setting | Default |
| --- | --- |
| `allowed-origins` | `"*"` |
| `allow-credentials` | `yes` |
| `allowed-headers` | `"*"` |
| `allowed-methods` | `["GET", "POST", "HEAD", "OPTIONS"]` |
| `allow-generic-http-requests` | `yes` |

The module's own documentation states the interaction precisely: *"if parameter is `*` and credentials are not allowed, a `*` is set in `Access-Control-Allow-Origin`. Otherwise, the origins given in the `Origin` request header are echoed."* *(documented — `http-cors/reference.conf`)*

So with **both** defaults in force, the directive **echoes the requesting `Origin` and allows credentials** — the maximally permissive CORS posture. Two facts bound how alarming that is: the `cors()` directive is **opt-in**, so this affects only applications that chose to enable CORS; and it is a documented, deliberate default rather than an accident. It is nonetheless the clearest insecure-default in the project, and §14 Q2 asks for the ruling. *(inferred that this is deliberate — §14 Q2)*

---

## §6 Assumptions about inputs

| Surface | Input | Attacker-controllable? | Who must enforce what |
| --- | --- | --- | --- |
| Any bound route | Request line (method, URI, version) | **Yes** | Pekko HTTP: §5a length limits |
| Any bound route | Headers, incl. `Host`, `Cookie` | **Yes** | Pekko HTTP: count/length limits. App: semantic trust |
| Any bound route | `X-Forwarded-*` | **Yes** — trivially spoofable | **App/operator** — not validated by Pekko HTTP (§14 Q4) |
| Any bound route | Entity body (fixed, chunked, streamed) | **Yes** | Pekko HTTP: size/chunk limits. App: content validation |
| HTTP/2 | Frames, HPACK table, stream IDs | **Yes** | Pekko HTTP: `max-concurrent-streams` |
| Route with marshaller | Entity parsed to a domain type | **Yes** | Underlying JSON/XML library + app |
| `cors()` | `Origin`, `Access-Control-Request-*` | **Yes** | Operator: §5a CORS config |
| File-serving directives | Path segments | **Yes** | Pekko HTTP + app — see §14 Q3 |
| Client API | Response from an upstream server | **Yes** if the upstream is untrusted | App: treat responses as untrusted |
| Config | `application.conf` | **No** — trusted deployment input | Operator |

---

## §7 Adversary model

**In scope:**

- **The remote HTTP client.** Can send arbitrary bytes, malformed framing, oversized or deeply-nested input, many concurrent connections, and abusive HTTP/2 frame sequences. The primary adversary. *(inferred — §14 Q1, Q7)*
- **A malicious upstream server**, where the application uses the client API against an untrusted endpoint. *(inferred — §14 Q7)*
- **A cross-origin web attacker**, where the application enables CORS. *(inferred — §14 Q2)*

**Explicitly out of scope:**

- **Attackers with code execution in the embedding JVM.** Already inside.
- **The embedding application itself.** A route that deliberately leaks data is an application bug.
- **Side-channel observers.** No general timing guarantees are made about routing or parsing. Credential comparison is the exception: `Credentials.Provided.verify` compares in constant time (see §8 P8), so a timing finding there is in scope, while one against an application-supplied `provideVerify` is not. *(documented — `SecurityDirectives.scala`, `EnhancedByteArray.scala`)*

---

## §8 Security properties Pekko HTTP provides

| # | Property & conditions | Violation symptom | Severity | Provenance |
| --- | --- | --- | --- | --- |
| P1 | **Inbound messages are bounded** by the §5a limits; input exceeding them is rejected rather than buffered | OOM or unbounded buffering from input *within* documented limits | **Critical** | *(documented — `reference.conf`)* |
| P2 | **Response splitting is blocked**: illegal response header names and values are `error` by default, not passed through | CRLF in an application-supplied header reaching the wire | **Critical** | *(documented — `reference.conf`)* |
| P3 | **Parse errors do not leak detail to the client** — `verbose-error-messages = off` | Internal parse state or stack detail in a 400 response under defaults | High | *(documented — `reference.conf`)* |
| P4 | **Strict URI and RFC6265 cookie parsing by default**, rather than lenient normalization that invites smuggling | Two components disagreeing on a URI or cookie under `strict` | High | *(documented — `reference.conf`)* |
| P5 | **Connection and request lifetimes are bounded** — `idle-timeout 60s`, `request-timeout 20s`, `max-connections 1024`, `pipelining-limit 1` | A client holding resources indefinitely under defaults | High | *(documented — `reference.conf`)* |
| P6 | **HTTP/2 concurrency is bounded** — `max-concurrent-streams = 256` | Unbounded stream/state growth on one connection | High | *(documented — `reference.conf`)* |
| P7 | **HTTP/2 header blocks are bounded** — `max-header-list-size = 64 KiB` caps the decompressed header list *and* the accumulated HEADERS + CONTINUATION fragments, so a header block the peer never terminates with `END_HEADERS` cannot grow without bound; over-limit blocks get `GOAWAY(ENHANCE_YOUR_CALM)` rather than being buffered | Unbounded buffering from a CONTINUATION flood or an oversized header list | **Critical** | *(documented — `reference.conf`)* |
| P8 | **Credential comparison is constant-time** where the verifier calls `Credentials.verify` — it compares via `secure_==`, which XOR-accumulates over the full length after a length check, rather than short-circuiting on the first differing byte | Secret recoverable byte-by-byte from response timing against a `verify`-based verifier | High | *(documented — `SecurityDirectives.scala`, `EnhancedByteArray.scala:37`)* |

**P1-P7 are default-on properties** — a notable contrast with `apache/pekko`, where the strongest controls must be switched on. P8 is the exception: it holds only for a verifier that calls `Credentials.verify`, which is why §10.4 states it as a downstream responsibility. Pekko HTTP's weak spot is not its defaults but the boundary of its DoS claim (§9, §14 Q1).

---

## §9 Security properties Pekko HTTP does **not** provide

- **No claim of complete DoS resistance.** The documented wording is *"behaves pretty well under most known Denial of Service attacks"*, immediately followed by a recommendation to front it with a load balancer or enterprise routing solution *(documented — `security.md`)*. This is a hedge, not a guarantee.
- **No edge hardening.** Rate limiting, IP reputation, request scrubbing, slow-loris mitigation beyond `idle-timeout` — none are provided. *(inferred — §14 Q1)*
- **No authentication or authorization.** The security directives are plumbing; the credential check is the application's function. *(inferred — §14 Q7)*
- **No CSRF protection.** No token issuance or verification is provided. *(inferred — §14 Q7)*
- **No output encoding / XSS defence.** Pekko HTTP renders what the application marshals. *(inferred — §14 Q7)*
- **No trusted client-IP derivation.** See §5 and §14 Q4.

### False friends

- **The security directives are not a security *system*.** `authenticateBasic` and `authenticateOAuth2` route credentials to an application-supplied verifier; they impose no password policy, no rate limiting and no lockout. They *do* supply a constant-time comparison (§8 P8) — but only to a verifier that calls `Credentials.verify`; one that pattern-matches the secret out and uses `==` gets none of it.
- **`allowed-origins = "*"` does not mean "no credentials are exposed".** Combined with the shipped `allow-credentials = yes`, it echoes the caller's `Origin` (§5a).
- **`remote-address-attribute` is not the client IP behind a proxy.** It is the socket peer.
- **`max-content-length` is not a global memory bound.** It bounds one entity; concurrent connections multiply it.
- **A rejection is not a failure.** The routing DSL's rejection mechanism is control flow, not a security control — an unhandled rejection can fall through to a different route.

### Well-known attack classes left to the caller

- **Request smuggling / desync** between a fronting proxy and Pekko HTTP — inherently a two-party property; strict parsing (P4) helps but cannot settle it alone.
- **Slow-loris and connection exhaustion** — partially bounded by P5, explicitly not fully claimed (§14 Q1).
- **Decompression bombs** in request bodies, where the application enables decoding.
- **SSRF** via the client API, where the application takes a URL from a request.
- **Path traversal** in file-serving directives is *not* left to the caller — `safeDirectoryChildPath` contains it (§14 Q3). What remains the caller's is the surrounding choice: which root is served, and whether a symlink may point out of it.
- **XXE** in XML marshallers — a property of the underlying parser.

---

## §10 Downstream responsibilities

1. **Put an enterprise-grade proxy or load balancer in front** of an internet-facing service *(documented — `security.md`)*.
2. **Do not raise the §5a limits without understanding the memory cost** — each is multiplied by concurrent connections.
3. **If CORS is enabled, set `allowed-origins` explicitly.** Do not ship the `"*"` + `allow-credentials = yes` combination to a credentialed API (§5a).
4. **Compare credentials with `Credentials.verify`**, which is constant-time (§8 P8) — not with `==` on the secret, and not via `provideVerify` unless the supplied verifier is itself constant-time.
5. **Do not derive client identity from `X-Forwarded-For`** unless a trusted proxy sets it and the application validates the chain.
6. **Validate and canonicalize any request-derived path** before passing it to a file-serving directive.
7. **Treat client-API responses from untrusted upstreams as untrusted input.**
8. **Consider `server-header = ""`** if product/version disclosure matters to your threat model.

---

## §11 Known misuse patterns

- **Exposing a Pekko HTTP service directly to the internet** with no fronting proxy, contrary to the documented recommendation.
- **Enabling `cors()` and leaving `allowed-origins = "*"`** on an API that uses cookies or bearer tokens.
- **Trusting `X-Forwarded-For`** for rate limiting, audit logging, or access control without a trusted-proxy chain.
- **Raising `max-content-length` to `infinite`** to accept large uploads, without a concurrency bound.
- **Comparing credentials with `==`** inside an `authenticateBasic` verifier — or reaching for `provideVerify` with a non-constant-time verifier — instead of `Credentials.verify`.
- **Passing a request path segment straight to `getFromFile`.**
- **Turning `verbose-error-messages = on`** in production to aid debugging.

---

## §11a Known non-findings (recurring false positives)

- **"`Server` header discloses the product and version."** Documented default, configurable via `server-header` (§5a). Not a vulnerability under this model.
- **"No authentication on routes."** Authentication is the application's responsibility (§9). A scan of this library cannot conclude a route is unauthenticated.
- **"Request exceeding `max-uri-length` / `max-header-count` is rejected."** That is P1 working.
- **"CORS allows any origin."** Reflects the shipped default and requires the application to have opted into `cors()`; pending §14 Q2, report against the *application's* configuration, not the library.
- **"`allow-credentials = yes` with `allowed-origins = "*"` sends `Access-Control-Allow-Origin: *` with credentials."** It does not — the literal `*` is sent only when `allowCredentials` is false, otherwise the request `Origin` is echoed (`CorsSettingsImpl.scala:64`, covered by `CorsDirectivesSpec`). Reports asserting the literal `*`-with-credentials combination are factually wrong.
- **"Credential comparison is vulnerable to a timing attack."** Check which comparator the report exercises: `Credentials.verify` is constant-time (§8 P8), so the claim is wrong against it; against an application's own `provideVerify` comparator it is a finding in that application, not this library.
- **Findings in `*-tests`, `http-testkit*`, `http-bench-jmh`, `http-scalafix`, `docs`** — `OUT-OF-MODEL: unsupported-component` per §3.
- **Findings in the actor or stream layer** — belongs to `apache/pekko`'s model, not this one.

---

## §12 Conditions that would change this model

- A change to any §5a **default**, especially a parsing limit, a timeout, or a CORS setting.
- A new protocol version or transport (HTTP/3).
- Taking on any authentication, authorization, or rate-limiting responsibility currently disclaimed in §9.
- A change to the documented "do not expose directly" posture in `security.md`.
- Promotion of a §3 module into the supported surface.
- **A report that cannot be routed to exactly one §13 disposition** — evidence of a model gap; revise the model rather than making an ad-hoc call.

---

## §13 Triage dispositions

| Disposition | Meaning | Licensed by |
| --- | --- | --- |
| `VALID` | Violates a §8 property via an in-scope adversary and input | §6, §7, §8 |
| `VALID-HARDENING` | No §8 property violated, but the API makes a §11 misuse easy enough to warrant hardening. Typically no CVE | §11 |
| `OUT-OF-MODEL: trusted-input` | Requires control of an input §6 marks trusted (configuration, application-supplied handlers) | §6 |
| `OUT-OF-MODEL: adversary-not-in-scope` | Requires in-JVM code execution, or a malicious embedding application | §7 |
| `OUT-OF-MODEL: unsupported-component` | Lands in a §3 module, or in the actor/stream layer | §3 |
| `OUT-OF-MODEL: non-default-build` | Only manifests under a non-default §5a setting | §5a |
| `BY-DESIGN: property-disclaimed` | Concerns a §9 property — authentication, CSRF, XSS, edge hardening | §9 |
| `KNOWN-NON-FINDING` | Matches a §11a pattern | §11a |
| `MODEL-GAP` | Routable to none of the above — triggers §12 | §12 |

---

## §14 Open questions for the maintainers

Each states a **proposed answer**; confirming or correcting is enough.

**Q1 — Where exactly is the DoS line? (highest value — reshapes §8, §9, §11a, §13.)**
`security.md` says Pekko HTTP *"behaves pretty well under most known Denial of Service attacks"* while recommending a fronting proxy. That is deliberately graded, and triage needs a line. *Proposed:* a request **within** all §5a documented limits that causes super-linear CPU or memory is `VALID`; anything requiring limits to be raised, or requiring request *volume* rather than request *content*, is `BY-DESIGN: property-disclaimed` — volume defence is the proxy's job. Is that the intended split?

**Q2 — The CORS defaults.** `allowed-origins = "*"` with `allow-credentials = yes` echoes the caller's `Origin` with credentials allowed. *Proposed:* deliberate, on the grounds that `cors()` is opt-in and an application enabling it is expected to configure it — so a report against the default is `VALID-HARDENING` at most, and the §10.3 guidance covers it. Confirm — or should the shipped default change?

**Q3 — File-serving directives.** *Resolved from code — confirm the disposition only.* `safeDirectoryChildPath` contains traversal by two stated measures: a path segment must not be `..` and must not contain `/` or `\\`; and the resolved file's `File.getCanonicalPath` must be prefixed by the base path's. So containment **is** claimed, and a genuine escape from the configured root is `VALID`; passing an unvalidated path in is a §11 misuse. One residual the code comment itself flags: containment rests on `getCanonicalPath`, whose symlink resolution is platform-dependent — *is a symlink out of the served root a `VALID` finding, or an operator responsibility?* *(documented — `FileAndResourceDirectives.scala:229-274`)*

**Q4 — `X-Forwarded-For` and client identity.** *Proposed:* Pekko HTTP neither parses nor trusts forwarding headers; `remote-address-attribute` is strictly the socket peer, and deriving client IP is entirely the application's job — so "forwarding header is spoofable" is `BY-DESIGN: property-disclaimed`. Confirm?

**Q5 — Constant-time credential comparison.** *Resolved from code — this document's earlier draft had it backwards.* `Credentials.Provided.verify` does compare, via `secure_==` (`EnhancedByteArray.scala:37`), which is constant-time; the library therefore **does** provide the guarantee, recorded as §8 P8. It is conditional on the verifier calling `verify` — `provideVerify` hands the raw secret to application code and waives it. *Proposed:* a timing finding against `verify` is `VALID`; one against an application's own `provideVerify` comparator is `BY-DESIGN: property-disclaimed`. Confirm the split?

**Q6 — Module in/out split (§2 table).** *Proposed:* the split shown. Specifically: should `http-caching` be in model (cache-key confusion is a real class), and is `http-scalafix` correctly out?

**Q7 — The §3/§7/§9 non-goals.** *Proposed:* Pekko HTTP provides no authentication system, no authorization policy, no CSRF protection and no XSS/output encoding, and in-JVM attackers plus a malicious embedding application are out of the adversary model — while the remote HTTP client, a malicious upstream (client API), and a cross-origin web attacker (where CORS is on) are all **in**. Confirm the split?

**Q8 — TLS.** *Proposed:* where Pekko HTTP terminates TLS, protocol and cipher selection come from the application-supplied JSSE context, so "weak cipher accepted" is a deployment finding, not a library one. Confirm — and is in-process termination a supported production posture, or is proxy termination the expectation?

**Q9 — The negative claims in §5.** These are inferred and hard to cite. Are any wrong — does Pekko HTTP bind ports, write files, or mutate process-global state in ways an integrator would not expect?

**Q10 — Coexistence (meta).** `docs/src/main/paradox/security.md` has a "Security model" section that this document expands considerably. *Proposed:* this file becomes canonical for **scope and triage**, `security.md` stays canonical for **announcements and reporting**, and its "Security model" section becomes a short pointer here. Agree?

---

## §15 Appendix — back-map from existing docs

| Existing statement | Source | Lands in |
| --- | --- | --- |
| Applications should not be exposed to the public internet directly | `security.md` | §3, §4, §10.1 |
| Behaves "pretty well" under most known DoS attacks | `security.md` | §4, §9, §14 Q1 |
| An enterprise-grade routing solution or LB (httpd, Nginx) is safer | `security.md` | §5, §10.1 |
| Report privately per ASF guidelines; subscribe to announce@ | `security.md` | `SECURITY.md`, §1 |
| Parsing limits (`max-uri-length`, `max-header-*`, `max-chunk-*`, …) | `http-core/reference.conf` | §5a, §8 P1 |
| `verbose-error-messages = off` | `http-core/reference.conf` | §5a, §8 P3 |
| Illegal response header name/value processing = `error` | `http-core/reference.conf` | §5a, §8 P2 |
| `uri-parsing-mode = strict`, `cookie-parsing-mode = rfc6265` | `http-core/reference.conf` | §5a, §8 P4 |
| Timeouts and connection caps | `http-core/reference.conf` | §5a, §8 P5 |
| `max-concurrent-streams = 256` | `http-core/reference.conf` | §5a, §8 P6 |
| `max-header-list-size = 64 KiB`, bounding HEADERS + CONTINUATION accumulation | `http-core/reference.conf` | §5a, §8 P7 |
| `Credentials.verify` compares via constant-time `secure_==` | `SecurityDirectives.scala`, `EnhancedByteArray.scala` | §7, §8 P8, §9, §10.4, §14 Q5 |
| `safeDirectoryChildPath` rejects `..`/separator segments and enforces a canonical-path prefix | `FileAndResourceDirectives.scala` | §9, §14 Q3 |
| `remote-address-attribute = off` | `http-core/reference.conf` | §5, §9, §14 Q4 |
| CORS: `*` + credentials echoes the request `Origin` | `http-cors/reference.conf` | §5a, §9, §11a, §14 Q2 |
