# Apache Pekko HTTP — Threat Model

**Status:** Reviewed by a Pekko maintainer. **All ten questions in §14 are answered** and are settled model, and §5b records the project's standing position on configuration defaults. One item is referred rather than settled: whether the upstream-coordination sentence in `security.md` should be promoted into `SECURITY.md` (§14 Q10). No claim in this document is now uncited: every assertion is either cited to Pekko HTTP's own source and configuration, or stated by a maintainer.

| | |
| --- | --- |
| **Project** | Apache Pekko HTTP |
| **Written against** | commit `85d7243`, `main` |
| **Date** | 2026-08-27 |
| **Authors** | ASF Security team, at the request of the Pekko PMC |
| **Version binding** | Versioned alongside the project. A report against version *N* is triaged against the model as it stood at *N*, not at `main`. |
| **Reporting** | Findings that violate a §8 property should be reported per [`SECURITY.md`](SECURITY.md). Findings under §3 or §9 will be closed citing this document. |
| **Companion model** | Pekko HTTP is built on Pekko Streams and Actors. The actor, remoting and cluster layers are modeled in [`apache/pekko`'s `THREAT_MODEL.md`](https://github.com/apache/pekko/blob/main/THREAT_MODEL.md); this document does not restate them. |

**Provenance legend.**
*(documented)* — stated in Pekko HTTP's own docs or `reference.conf` comments, cited.
*(maintainer)* — stated by a Pekko maintainer in review of this document.
*(inferred)* — reasoned from code or config defaults, **not yet confirmed**; each has a matching question in §14.

**Confidence:** 20 documented / 28 maintainer / 0 inferred — counting inline tags only. The §5a limits table and the §15 back-map carry a further ~35 documented facts under a single collective citation each, so the document is more evidence-backed than the bare ratio suggests. **Nothing in the document is inferred any more.** Q1-Q9 were answered by a maintainer in review; Q3, Q5 and Q9 were resolved against the source first, the last of these replacing §5's negative claims with a cited scan. Q3, Q4, Q5 and (in part) Q9 are worth noting as corrections rather than confirmations — in each, the draft's claim did not survive contact with the code: Q4 and Q5 had the facts backwards while the proposed triage disposition survived intact; Q3's review found a real defect in the containment check it examined (fixed in [#1218](https://github.com/apache/pekko-http/pull/1218)); and Q9's source scan had missed the one place the main sources write files (§5).

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

*(maintainer — the in/out split is confirmed by the Pekko maintainers; see §14 Q6)*

---

## §3 Out of scope (explicit non-goals)

- **Test kits, benchmarks, scalafix rules, build tooling and documentation sources.** A finding in `http-bench-jmh` or any `*-tests` module is `OUT-OF-MODEL: unsupported-component`. *(maintainer — §14 Q6)*
- **Pekko HTTP is not a WAF, and not an edge-hardened server.** The documentation says so plainly: applications *"should not be exposed to the public internet directly"* and an *"enterprise grade routing solution"* or a load balancer such as Apache HTTP Server or Nginx *"would be safer"* *(documented — `security.md`)*. See §4.
- **Pekko HTTP is not an authentication or authorization system.** It ships `authenticateBasic`, `authenticateOAuth2` and `authorize` directives, but these are *plumbing*: the credential check is a function the application supplies. Pekko HTTP has no user store, no session model, and no policy engine. *(maintainer — §14 Q7)*
- **The actor, stream, remoting and cluster layers** are out of scope here and covered by `apache/pekko`'s threat model.
- **Attackers who already control the embedding process** are out of scope. *(maintainer — §14 Q7)*

---

## §4 Trust boundaries and data flow

**The trust boundary is the inbound HTTP request.** Everything derived from wire bytes — request line, headers, cookies, entity, HTTP/2 frames — is attacker-controlled until the application validates it.

Pekko HTTP's documented posture is unusual and important enough to quote in full:

> "Pekko HTTP-based applications should not be exposed to the public internet directly. We believe Pekko HTTP behaves pretty well under most known Denial of Service attacks, but if you want the best security, you should use an enterprise grade routing solution. Even using a load balancing solution like an up-to-date version of Apache HTTP Server or Nginx would be safer than exposing Pekko HTTP-based applications directly to the public internet."
> — *(documented — `security.md`, "Security model")*

Read carefully, this makes a **graded** claim rather than a binary one: Pekko HTTP asserts it *"behaves pretty well under most known"* DoS attacks — not that it is DoS-proof, and not that DoS is out of scope. That hedge was the single most consequential ambiguity in this model for triage; §14 Q1 has now resolved it into the content-vs-volume line, which is the rule triage actually applies.

### Reachability preconditions per family

- **Core protocol / HTTP/2** — reachable from raw wire bytes. The strongest in-model surface: a parser defect here needs no application cooperation.
- **Routing DSL** — reachable from a request that the application's own routes expose. A finding must name the directive and show a route shape a reasonable application would write.
- **Marshalling** — reachable from an entity body **only where the application has bound that marshaller to a route**. Findings in the underlying JSON/XML library belong to that library.
- **CORS** — reachable only where the application has installed the `cors()` directive. It is opt-in, not on by default. See §5a.
- **Caching** — reachable only where the application has installed a caching directive; cache-key correctness is the sharp edge.

---

## §5 Assumptions about the environment

- **Runtime.** A conformant JVM. Pekko HTTP does not defend against a hostile JVM or in-process attacker. *(maintainer — §14 Q7)*
- **Fronting infrastructure.** The documented expectation is that something sits in front in production *(documented — `security.md`)*. Per §14 Q1 this is load-bearing for *volume* only: the proxy is relied on for flood and slow-loris defence, not for bounding a single request, which is P1's job.
- **TLS.** Pekko HTTP can terminate TLS itself (`HttpsConnectionContext`) and this is supported, though the documented recommendation to front the service (§4) means termination is commonly the proxy's job in production. Cipher and protocol selection come from the JSSE context the application supplies — Pekko HTTP pins nothing and overrides no JDK default, so cipher strength is a deployment property. *(maintainer — §14 Q8)*
- **Client IP.** `remote-address-attribute` ships `off` *(documented — `reference.conf`)*. When on, the attribute reflects the **socket** peer, which behind a proxy is the proxy; forwarding headers never feed it. Header-derived client IP is opt-in at the call site: `extractClientIP` reads `X-Forwarded-For` / `X-Real-Ip` and is therefore client-controllable, while `extractDirectClientIP` reads the attribute alone and is not. Choosing between them is the application's decision (§14 Q4). *(documented — `MiscDirectives.scala`)*

### What Pekko HTTP does not do to its host

Negative claims, rarely written down and therefore verified against the source rather than asserted. The scan below covers the main sources of `http-core`, `http`, `parsing`, `http-caching` and `http-cors` *(documented — source scan, §14 Q9)*:

- **Binds no port until the application calls a `bind*` method.** Binding is reachable only through the public `Http().bind` / `bindAndHandle*` entry points (`Http.scala:179-292`); nothing binds at class or extension initialization.
- **Installs no signal handlers and spawns no child processes.** No `sun.misc.Signal`/`SignalHandler`, `ProcessBuilder` or `Runtime.exec` in the main sources.
- **Registers no JVM shutdown hook.** There is no `addShutdownHook` anywhere in Pekko HTTP's main sources. Any hook an integrator observes comes from the actor system, not from here — see the caveat below.
- **Touches the file system only through directives the application installs.** The disk-serving directives (`getFromFile`, `getFromDirectory`) read. The upload directives **write**: `storeUploadedFile(s)` streams entity bytes to a destination the application's function chooses, and `fileUploadAll` buffers each part into a `Files.createTempFile` temp file registered with `deleteOnExit` (`FileUploadDirectives.scala:178-179`). Both are reachable only where the application binds them to a route; nothing writes at initialization or outside an installed directive. Beyond `FileUploadDirectives`, the main sources contain no `FileOutputStream`, `Files.write`, `FileWriter` or `createTempFile`. *(The draft claimed Pekko HTTP writes no files at all; the scan missed the upload directives — corrected in review, §14 Q9.)*
- **Does not mutate process-global state at initialization** — no `System.setProperty`, `Security.setProperty`, `Security.addProvider` or `setDefault(...)`.

**One inherited caveat, highlighted because it is easy to misattribute.** The claims above cover Pekko HTTP's own modules, not the `ActorSystem` it runs on. `apache/pekko` *does* register JVM shutdown hooks — one in `CoordinatedShutdown`, and a second in Artery when remoting is enabled — so an integrator running a Pekko HTTP service **will** observe shutdown hooks in the process. They arrive with the actor system and are modeled in the companion document. **Pekko HTTP itself adds none**, and that is the claim this section makes: *"a Pekko HTTP process has no shutdown hook"* is false, while *"Pekko HTTP registers no shutdown hook"* is true and is what §14 Q9 settles. *(maintainer — §14 Q9)*

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

**These limits are the model's quantitative spine.** A report that a request *within* every documented limit causes disproportionate resource use is `VALID`; one that simply exceeds a limit is P1 working, and one that needs a limit raised is `OUT-OF-MODEL: non-default-build` (§14 Q1).

### The CORS defaults — ruled (§5b, §14 Q2)

`http-cors` ships:

| Setting | Default |
| --- | --- |
| `allowed-origins` | `"*"` |
| `allow-credentials` | `yes` |
| `allowed-headers` | `"*"` |
| `allowed-methods` | `["GET", "POST", "HEAD", "OPTIONS"]` |
| `allow-generic-http-requests` | `yes` |

The module's own documentation states the interaction precisely: *"if parameter is `*` and credentials are not allowed, a `*` is set in `Access-Control-Allow-Origin`. Otherwise, the origins given in the `Origin` request header are echoed."* *(documented — `http-cors/reference.conf`)*

So with **both** defaults in force, the directive **echoes the requesting `Origin` and allows credentials** — the maximally permissive CORS posture. Two facts bound how alarming that is: the `cors()` directive is **opt-in**, so this affects only applications that chose to enable CORS; and it is a documented, deliberate default rather than an accident. It is nonetheless the most permissive default in the project. Its disposition follows §5b: the shipped value is a compatibility choice, and a request to change it is not a vulnerability report (§14 Q2). *(maintainer — §14 Q2)*

---

## §5b Security posture: hardening, not secure-by-default

Pekko HTTP is a long-lived toolkit whose deployment base is inherited from Akka HTTP, and `http-cors` carries a second inheritance on top of that — its defaults arrived with the code donated by Lomig Mégard (`legal/CorsNotice.txt`). Its configuration defaults are chosen for compatibility with those deployments, in which operators have already been tasked with fronting the service (§4, §10.1) and choosing limits appropriate to their traffic. Changing a default to a more restrictive value breaks those deployments on upgrade, sometimes without a clear signal as to why — a tightened parsing limit surfaces as requests that used to work now failing with a 4xx.

Pekko HTTP therefore takes the following position *(maintainer)*:

1. **Defaults are compatibility choices, not security claims.** §5a lists every setting whose default affects the security envelope; §10 lists what the operator must do as a result. Read together they are the contract: Pekko HTTP states what it does not provide, and states what it expects of the operator instead.
2. **A report that a default should be more restrictive is not a vulnerability report.** It is a change request, and is closed as `BY-DESIGN: default-configuration` (§13). This covers the recurring ones: the CORS pair in §5a, `server-header` disclosing a version, and every limit someone considers too generous.
3. **Proposals to change a default are welcome, and belong on the development list.** The PMC will weigh them in good faith on their merits — the compatibility cost, whether a migration path exists, and whether a major version is in flight. Defaults can and do change; they change through project discussion, not as the remediation of a security report.
4. **If an implementation is wrong, Pekko HTTP fixes it.** Where a control does not do what it is documented to do once enabled, that is a defect, in scope, at the severity §8 assigns. **This is the sharp end of the model.** The §5a limits and the §8 properties are only worth what their implementations deliver: a `max-header-list-size` that fails to bound CONTINUATION accumulation, an `illegal-response-header-value-processing-mode = error` that lets a CRLF through, a `safeDirectoryChildPath` that can be walked out of — those are the findings this project wants. This posture governs which value ships as the default — never whether the mechanism works.

**Users are free to strengthen any §5a setting**, and §10 says which ones matter most. What the project will not do is change the shipped value on their behalf.

---

## §6 Assumptions about inputs

| Surface | Input | Attacker-controllable? | Who must enforce what |
| --- | --- | --- | --- |
| Any bound route | Request line (method, URI, version) | **Yes** | Pekko HTTP: §5a length limits |
| Any bound route | Headers, incl. `Host`, `Cookie` | **Yes** | Pekko HTTP: count/length limits. App: semantic trust |
| Any bound route | `X-Forwarded-*`, `X-Real-Ip` | **Yes** — trivially spoofable | **App** — surfaced by `extractClientIP`, never validated; use `extractDirectClientIP` for access control or rate limiting (§14 Q4) |
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

- **The remote HTTP client.** Can send arbitrary bytes, malformed framing, oversized or deeply-nested input, many concurrent connections, and abusive HTTP/2 frame sequences. The primary adversary — though per §14 Q1 what this adversary achieves through sheer *volume* is the proxy's problem, not the library's. *(maintainer — §14 Q7)*
- **A malicious upstream server**, where the application uses the client API against an untrusted endpoint. *(maintainer — §14 Q7)*
- **A cross-origin web attacker**, where the application enables CORS. *(maintainer — §14 Q2)*

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

**P1-P7 are default-on properties** — a notable contrast with `apache/pekko`, where the strongest controls must be switched on. P8 is the exception: it holds only for a verifier that calls `Credentials.verify`, which is why §10.4 states it as a downstream responsibility. The boundary of the DoS claim, once this model's largest ambiguity, is now fixed by the §14 Q1 content-vs-volume line.

---

## §9 Security properties Pekko HTTP does **not** provide

- **No claim of complete DoS resistance.** The documented wording is *"behaves pretty well under most known Denial of Service attacks"*, immediately followed by a recommendation to front it with a load balancer or enterprise routing solution *(documented — `security.md`)*. Per §14 Q1 this disclaimer is **scoped to volume**: Pekko HTTP does not claim to withstand floods, but it *does* claim that one in-limits request cannot provoke disproportionate work — that part is P1, and a violation is `VALID`. *(maintainer — §14 Q1)*
- **No edge hardening.** Rate limiting, IP reputation, request scrubbing, connection-count throttling beyond `max-connections`, slow-loris mitigation beyond `idle-timeout` — none are provided, and none are planned. *(maintainer — §14 Q1)*
- **No authentication or authorization.** The security directives are plumbing; the credential check is the application's function. *(maintainer — §14 Q7)*
- **No CSRF protection.** No token issuance or verification is provided. *(maintainer — §14 Q7)*
- **No output encoding / XSS defence.** Pekko HTTP renders what the application marshals. *(maintainer — §14 Q7)*
- **No trusted client-IP derivation from headers.** `extractClientIP` surfaces `X-Forwarded-For` / `X-Real-Ip` without validating them, and Pekko HTTP has no trusted-proxy chain configuration. `extractDirectClientIP` is the trustworthy accessor, but it yields the last proxy rather than the client. See §5 and §14 Q4.

### False friends

- **The security directives are not a security *system*.** `authenticateBasic` and `authenticateOAuth2` route credentials to an application-supplied verifier; they impose no password policy, no rate limiting and no lockout. They *do* supply a constant-time comparison (§8 P8) — but only to a verifier that calls `Credentials.verify`; one that pattern-matches the secret out and uses `==` gets none of it.
- **`allowed-origins = "*"` does not mean "no credentials are exposed".** Combined with the shipped `allow-credentials = yes`, it echoes the caller's `Origin` (§5a).
- **`remote-address-attribute` is not the client IP behind a proxy.** It is the socket peer.
- **`max-content-length` is not a global memory bound.** It bounds one entity; concurrent connections multiply it.
- **A rejection is not a failure.** The routing DSL's rejection mechanism is control flow, not a security control — an unhandled rejection can fall through to a different route.

### Well-known attack classes left to the caller

- **Request smuggling / desync** between a fronting proxy and Pekko HTTP — inherently a two-party property; strict parsing (P4) helps but cannot settle it alone.
- **Slow-loris and connection exhaustion** — `idle-timeout` and `max-connections` (P5) bound what one connection holds and how many are accepted, but exhausting those bounds by volume is disclaimed (§14 Q1). A single connection that evades `idle-timeout` while holding resources is the in-scope version.
- **Decompression bombs** in request bodies, where the application enables decoding.
- **SSRF** via the client API, where the application takes a URL from a request.
- **Path traversal** in file-serving directives is *not* left to the caller — `safeDirectoryChildPath` claims containment, and a genuine escape from the configured root is `VALID` per §5b.4. Review of this model found exactly such a defect: the containment check compared canonical paths as **strings**, so a symbolic link resolving into a sibling directory whose name shares the served root as a string prefix (`/var/www` vs `/var/www-private`) escaped it — fixed by [#1218](https://github.com/apache/pekko-http/pull/1218), which compares path elements (§14 Q3). What remains the caller's is the surrounding choice: which root is served, and whether the tree under it contains symlinks at all.
- **XXE** in XML marshallers — a property of the underlying parser.

---

## §10 Downstream responsibilities

1. **Put an enterprise-grade proxy or load balancer in front** of an internet-facing service *(documented — `security.md`)*.
2. **Do not raise the §5a limits without understanding the memory cost** — each is multiplied by concurrent connections.
3. **If CORS is enabled, set `allowed-origins` explicitly.** Do not ship the `"*"` + `allow-credentials = yes` combination to a credentialed API (§5a).
4. **Compare credentials with `Credentials.verify`**, which is constant-time (§8 P8) — not with `==` on the secret, and not via `provideVerify` unless the supplied verifier is itself constant-time.
5. **Use `extractDirectClientIP`, not `extractClientIP`, for access control, rate limiting or audit logging.** The latter reads `X-Forwarded-For` / `X-Real-Ip`, which the client controls unless a trusted proxy overwrites them; it requires `remote-address-attribute = on` to fall back usefully. Derive identity from `X-Forwarded-For` only where a trusted proxy sets it and you validate the chain yourself.
6. **Validate and canonicalize any request-derived path** before passing it to a file-serving directive.
7. **Treat client-API responses from untrusted upstreams as untrusted input.**
8. **Consider `server-header = ""`** if product/version disclosure matters to your threat model.

---

## §11 Known misuse patterns

- **Exposing a Pekko HTTP service directly to the internet** with no fronting proxy, contrary to the documented recommendation.
- **Enabling `cors()` and leaving `allowed-origins = "*"`** on an API that uses cookies or bearer tokens.
- **Reaching for `extractClientIP`** — the more discoverable name — for rate limiting, audit logging or access control, where `extractDirectClientIP` is the one that cannot be chosen by the caller.
- **Raising `max-content-length` to `infinite`** to accept large uploads, without a concurrency bound.
- **Comparing credentials with `==`** inside an `authenticateBasic` verifier — or reaching for `provideVerify` with a non-constant-time verifier — instead of `Credentials.verify`.
- **Passing a request path segment straight to `getFromFile`.**
- **Turning `verbose-error-messages = on`** in production to aid debugging.

---

## §11a Known non-findings (recurring false positives)

- **"`Server` header discloses the product and version."** Documented default, configurable via `server-header` (§5a). Not a vulnerability under this model; a request to blank it by default is `BY-DESIGN: default-configuration` per §5b.
- **"No authentication on routes."** Authentication is the application's responsibility (§9). A scan of this library cannot conclude a route is unauthenticated.
- **"Request exceeding `max-uri-length` / `max-header-count` is rejected."** That is P1 working.
- **"N concurrent connections / requests exhaust CPU, memory or sockets."** Volume-based resource exhaustion is `BY-DESIGN: property-disclaimed` per §14 Q1 — defence belongs to the fronting proxy (§10.1). Reports must show *one* in-limits request doing disproportionate work, not many requests doing proportionate work. A load-generator result is not a finding.
- **"`extractClientIP` trusts a client-supplied header."** By design and documented at the directive, with `extractDirectClientIP` provided as the trustworthy alternative (§14 Q4). `BY-DESIGN: property-disclaimed`. A report that `extractDirectClientIP` can be influenced by a header *is* in scope.
- **"CORS allows any origin."** Reflects the shipped default and requires the application to have opted into `cors()`. A request to change the default is `BY-DESIGN: default-configuration` per §5b; a misconfigured deployment is a finding against the *application*, not the library.
- **"`allow-credentials = yes` with `allowed-origins = "*"` sends `Access-Control-Allow-Origin: *` with credentials."** It does not — the literal `*` is sent only when `allowCredentials` is false, otherwise the request `Origin` is echoed (`CorsSettingsImpl.scala:64`, covered by `CorsDirectivesSpec`). Reports asserting the literal `*`-with-credentials combination are factually wrong.
- **"Credential comparison is vulnerable to a timing attack."** Check which comparator the report exercises: `Credentials.verify` is constant-time (§8 P8), so the claim is wrong against it; against an application's own `provideVerify` comparator it is a finding in that application, not this library.
- **Findings in `*-tests`, `http-testkit*`, `http-bench-jmh`, `http-scalafix`, `docs`** — `OUT-OF-MODEL: unsupported-component` per §3.
- **"The process registers JVM shutdown hooks."** Pekko HTTP registers none (§5, §14 Q9). The hooks are `CoordinatedShutdown`'s and, with remoting enabled, Artery's — they belong to `apache/pekko`'s model. `OUT-OF-MODEL: unsupported-component`.
- **Findings in the actor or stream layer** — belongs to `apache/pekko`'s model, not this one.

---

## §12 Conditions that would change this model

- A change to any §5a **default**, especially a parsing limit, a timeout, or a CORS setting.
- A new protocol version or transport (HTTP/3).
- Taking on any authentication, authorization, or rate-limiting responsibility currently disclaimed in §9.
- A change to the documented "do not expose directly" posture in `security.md`.
- Promotion of a §3 module into the supported surface.
- A change to the §5 process-behaviour claims — for example, [#1217](https://github.com/apache/pekko-http/pull/1217) proposes replacing the per-upload `deleteOnExit` with a single JVM shutdown hook registered by Pekko HTTP itself, which on merge invalidates the "registers no shutdown hook" claim in §5 and the matching §11a non-finding.
- **A report that cannot be routed to exactly one §13 disposition** — evidence of a model gap; revise the model rather than making an ad-hoc call.

---

## §13 Triage dispositions

| Disposition | Meaning | Licensed by |
| --- | --- | --- |
| `VALID` | Violates a §8 property via an in-scope adversary and input. For resource exhaustion: a **single request within all §5a limits** causing super-linear CPU or memory | §6, §7, §8, §14 Q1 |
| `VALID-HARDENING` | No §8 property violated, but the API makes a §11 misuse easy enough to warrant hardening. Typically no CVE | §11 |
| `OUT-OF-MODEL: trusted-input` | Requires control of an input §6 marks trusted (configuration, application-supplied handlers) | §6 |
| `OUT-OF-MODEL: adversary-not-in-scope` | Requires in-JVM code execution, or a malicious embedding application | §7 |
| `OUT-OF-MODEL: unsupported-component` | Lands in a §3 module, or in the actor/stream layer | §3 |
| `OUT-OF-MODEL: non-default-build` | Only manifests under a non-default §5a setting — including any resource-exhaustion report that needs a limit **raised** from its default. Distinct from `default-configuration`: this is a real defect reachable only off-default, that one is no defect at all | §5a, §14 Q1 |
| `BY-DESIGN: default-configuration` | Asks that a §5a default be changed to a more restrictive value. Not a vulnerability; §5b.3 invites the proposal on the development list | §5b |
| `BY-DESIGN: property-disclaimed` | Concerns a §9 property — authentication, CSRF, XSS, edge hardening — or depends on request **volume** rather than request **content** | §9, §14 Q1 |
| `KNOWN-NON-FINDING` | Matches a §11a pattern | §11a |
| `MODEL-GAP` | Routable to none of the above — triggers §12 | §12 |

---

## §14 Open questions for the maintainers

Each states a **proposed answer**; confirming or correcting is enough.

**Q1 — Where exactly is the DoS line? — ANSWERED.** *(maintainer)*
The proposed split was accepted as written. The **DoS line** is now settled model, restated here as the canonical form and applied throughout §4, §5a, §8, §9, §11a and §13:

> A **single request within every §5a documented limit** that provokes super-linear CPU or memory is `VALID` — this is the P1 violation symptom.
> A finding that requires a §5a limit to be **raised** from its shipped default is `OUT-OF-MODEL: non-default-build`.
> A finding that depends on request **volume** rather than request **content** — connection floods, slow-loris at scale, aggregate bandwidth — is `BY-DESIGN: property-disclaimed`. Volume defence is the fronting proxy's job (§10.1).

The operative test is *content vs. volume*: one well-formed, in-limits request doing disproportionate work is a bug in Pekko HTTP; many requests doing proportionate work is a deployment concern.

**Q2 — The CORS defaults. ANSWERED *(maintainer)*.** **Answer:** a compatibility default under §5b. The values arrived with the donated `http-cors` code and existing users depend on them; `cors()` is opt-in, and an application that enables it is expected to configure it, with §10.3 stating what to set. A report that the shipped default should change is `BY-DESIGN: default-configuration` — welcome on the development list, not as a security report. What *is* in scope is the implementation: if `cors()` admits an origin its configuration should have rejected, or emits credentials for one it should not, that is a defect under §5b.4.

**Q3 — File-serving directives. ANSWERED *(maintainer)*.** *Resolved from code — and, like Q4 and Q5, a correction: the draft asserted containment held, and reviewing the check found a defect in it.* `safeDirectoryChildPath` claims containment by two measures: a path segment must not be `..` and must not contain `/` or `\\`; and the resolved file's `File.getCanonicalPath` must be prefixed by the base path's. The second measure compared the canonical paths as **strings** (`canonicalFinalPath.startsWith(...)`), and a string prefix is not a path prefix: with `/var/www` served, `/var/www-private/secret` satisfies it without being below the served root. The segment filter cannot catch this — the path only leaves the root when `getCanonicalPath` resolves a symbolic link into such a sibling, and no segment looks suspicious. **Answer:** containment is claimed, so the escape is `VALID` under §5b.4 — this is precisely the "walked out of" class that section names — and it is fixed by [#1218](https://github.com/apache/pekko-http/pull/1218), which compares canonical paths element by element. That fix also settles the draft's residual question: a symlink resolving out of the served root is *rejected by the containment check*, whatever its target is named, so a working escape is a `VALID` finding rather than an operator responsibility. The operator keeps the surrounding choices — which root is served, and whether symlinks exist under it. Passing an unvalidated path into `getFromFile` directly remains a §11 misuse. *(documented — `FileAndResourceDirectives.scala:229-274`; fix in [#1218](https://github.com/apache/pekko-http/pull/1218))*

**Q4 — `X-Forwarded-For` and client identity. ANSWERED *(maintainer)*.** The disposition is confirmed, but the draft's stated basis for it was wrong and is corrected here. Pekko HTTP **does** parse forwarding headers: `extractClientIP` resolves `X-Forwarded-For` (first address) → `X-Real-Ip` → the `remoteAddress` attribute, in that order (`MiscDirectives.scala:142-145`). What is true is that it never does so *implicitly* — `remote-address-attribute` populates the attribute from the socket peer only, and a route gets header-derived values solely because it called `extractClientIP`.

**Answer:** the spoofability is documented at the directive rather than defended against. `extractClientIP`'s own scaladoc warns that *"the headers are under the control of the client unless a trusted proxy in front of this server overwrites them"* and directs the reader to `extractDirectClientIP` *"where the address must not be chosen by the client, for example for access control or rate limiting"* — a directive added for exactly this purpose (#1219, `@since 2.0.0`), which reads the attribute alone and ignores headers. So "`extractClientIP` trusts a spoofable header" is `BY-DESIGN: property-disclaimed`: the library offers both a convenient and a trustworthy accessor, documents which is which, and leaves the choice to the application. A defect in `extractDirectClientIP` — anything client-controlled reaching it — would be `VALID`. *(documented — `MiscDirectives.scala:39-64`)*

**Q5 — Constant-time credential comparison. ANSWERED *(maintainer)*.** *Resolved from code — this document's earlier draft had it backwards.* `Credentials.Provided.verify` does compare, via `secure_==` (`EnhancedByteArray.scala:37`), which is constant-time; the library therefore **does** provide the guarantee, recorded as §8 P8. It is conditional on the verifier calling `verify` — `provideVerify` hands the raw secret to application code and waives it. **Answer *(maintainer)*:** the split is confirmed. A timing finding against `verify` — anything that makes the comparison data-dependent — is `VALID` at §8 P8's severity. One against an application's own `provideVerify` comparator is `BY-DESIGN: property-disclaimed`: passing the raw secret to application code waives the guarantee by construction, and §10.4 states the responsibility.

**Q6 — Module in/out split (§2 table). ANSWERED *(maintainer)*.** **Answer:** the split shown in §2 is confirmed as the maintainers' own, not merely the ASF Security team's proposal. `http-caching` is **in** model — cache-key confusion is a real class and the directive ships as part of the supported surface. `http-scalafix` is correctly **out**, along with the test kits, `http-bench-jmh`, `docs`, `project`, `scripts` and `legal`: a finding in any of them is `OUT-OF-MODEL: unsupported-component` per §3.

**Q7 — The §3/§7/§9 non-goals. ANSWERED *(maintainer)*.** **Answer:** the split is confirmed as stated. Pekko HTTP provides **no** authentication system, authorization policy, CSRF protection or XSS/output encoding — reports against those are `BY-DESIGN: property-disclaimed` per §9. **Out** of the adversary model: attackers with code execution in the embedding JVM, and a malicious embedding application (a route that deliberately leaks is an application bug). **In**: the remote HTTP client (primary), a malicious upstream server where the application drives the client API against an untrusted endpoint, and a cross-origin web attacker where the application has enabled `cors()`. This closes the §3, §7 and §9 non-goals as maintainer-stated rather than inferred.

**Q8 — TLS. ANSWERED *(maintainer)*.** **Answer:** confirmed. Where Pekko HTTP terminates TLS via `HttpsConnectionContext`, protocol and cipher selection come from the JSSE context the application supplies; Pekko HTTP neither pins a cipher suite nor overrides the JDK's defaults. "Weak cipher accepted" is therefore a deployment finding against that context or the JDK, not a library one — `OUT-OF-MODEL: trusted-input` per §6, which marks configuration as operator-supplied. A defect in how Pekko HTTP *drives* the context — failing to apply a supplied restriction, or continuing after a handshake failure — would be `VALID`.

On the second half: **in-process termination is supported**, and `HttpsConnectionContext` is a first-class API. It is not, however, the posture the documentation steers production deployments toward — §4's quoted recommendation to front the service with an enterprise-grade routing solution or load balancer applies to TLS as much as to volume defence (§14 Q1), and in such a deployment termination is commonly the proxy's job. Both are supported; the fronted one is what the docs recommend.

**Q9 — The negative claims in §5. ANSWERED *(maintainer)*, with one correction.** *Resolved from code, then ruled on.* Scanned the main sources of `http-core`, `http`, `parsing`, `http-caching` and `http-cors`: no `addShutdownHook`, no `ProcessBuilder`/`Runtime.exec`, no `Signal`/`SignalHandler`, no `System.setProperty`/`Security.*`/`setDefault`, and no bind outside the public `Http().bind*` entry points. **The file-writing claim did not survive review:** the scan missed `FileUploadDirectives.scala:178`, where `fileUploadAll` creates temp files with `Files.createTempFile` + `deleteOnExit` and `storeUploadedFile(s)` streams entity bytes to an application-chosen destination. §5 now states the accurate boundary — writes happen only through upload directives the application installs. The remaining claims hold, and §5 cites the scan rather than asserting them.

**Answer:** state the boundary here rather than deferring it. The `ActorSystem`'s shutdown hooks are worth **highlighting** in §5 so an integrator is not surprised by them, but the claim this document makes and stands behind is that **Pekko HTTP registers none of its own**. A report that Pekko HTTP installs a shutdown hook is factually wrong (§11a); one about the hooks `CoordinatedShutdown` or Artery register belongs to `apache/pekko`'s model, and is `OUT-OF-MODEL: unsupported-component` here.

**Q10 — Coexistence. ANSWERED *(maintainer)*.** Following the split adopted in [`apache/pekko#3478`](https://github.com/apache/pekko/pull/3478). Three documents carry security information, each canonical for one thing:

| Document | Canonical for | Reached by |
| --- | --- | --- |
| [`SECURITY.md`](SECURITY.md) | **The reporting policy.** The strongest and canonical statement of how to report and what the project undertakes | Anyone arriving via the repository, and every other document |
| `THREAT_MODEL.md` (this document) | **Scope** — what is and is not a vulnerability, and how a report is triaged | Reporters, triagers, scanning tools |
| `docs/src/main/paradox/security.md` | Security announcements, and the documentation-site index of security material | Readers of the documentation site |

Every other document **links** to `SECURITY.md` for the reporting policy and to this document for scope, rather than restating either. A change to the policy is therefore made in one place.

**Note the correction to the draft's proposal.** It suggested that `security.md`'s "Security model" section be reduced to a pointer here. It is *not*, and must not be: §4 quotes that section verbatim as the documented source of the "should not be exposed to the public internet directly" posture, and §15 back-maps four separate claims to it. Reducing it to a pointer would delete the evidence this document is built on and leave §4 citing a redirect. The section stays; `security.md` gains links to `SECURITY.md` and to this file, matching the minimal change made in `apache/pekko#3478`.

**One statement is referred, not migrated.** `security.md` carries an upstream-coordination sentence that `SECURITY.md` does not: *"Ideally, any issues affecting Apache Pekko and Akka should be reported to Apache team first. We will share the report with the Lightbend Akka team."* Per the same ruling, a reporting statement living outside `SECURITY.md` is referred to the maintainers for a decision — promoted verbatim or dropped, never silently moved. It is left untouched in `security.md` pending that decision. (`apache/pekko` resolved its equivalent by carrying a general sentence in `SECURITY.md`: *"The Pekko PMC will coordinate responsible disclosure with affected upstream maintainers where needed."*)

---

## §15 Appendix — back-map from existing docs

| Existing statement | Source | Lands in |
| --- | --- | --- |
| Applications should not be exposed to the public internet directly | `security.md` | §3, §4, §10.1 |
| Behaves "pretty well" under most known DoS attacks — scoped to *volume* by the §14 Q1 ruling | `security.md` + maintainer ruling | §4, §9, §11a, §13, §14 Q1 |
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
| `safeDirectoryChildPath` rejects `..`/separator segments and enforces canonical containment (its string-prefix comparison was found defective in review; fixed by [#1218](https://github.com/apache/pekko-http/pull/1218)) | `FileAndResourceDirectives.scala` | §9, §14 Q3 |
| `remote-address-attribute = off` | `http-core/reference.conf` | §5, §9, §14 Q4 |
| `extractClientIP` reads `X-Forwarded-For`/`X-Real-Ip`; `extractDirectClientIP` reads the attribute alone | `MiscDirectives.scala` | §5, §6, §9, §10.5, §11a, §14 Q4 |
| CORS: `*` + credentials echoes the request `Origin` | `http-cors/reference.conf` | §5a, §9, §11a, §14 Q2 |
| `http-cors` code donated by Lomig Mégard, defaults inherited with it | `legal/CorsNotice.txt`, `NOTICE` | §5b, §14 Q2 |
