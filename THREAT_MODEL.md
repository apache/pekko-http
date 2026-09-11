# Apache Pekko HTTP — Threat Model

**Status:** Reviewed by a Pekko maintainer. Every question put to the maintainers (§14) is answered and is settled model; §5b records the project's standing position on configuration defaults. Every claim is either cited to Pekko HTTP's own source and configuration, or stated by a maintainer. One item is referred rather than settled (§14 Q10).

| | |
| --- | --- |
| **Project** | Apache Pekko HTTP |
| **Written against** | commit `40b07a2`, `main` |
| **Date** | 2026-08-27 |
| **Authors** | ASF Security team, at the request of the Pekko PMC |
| **Version binding** | Versioned alongside the project. A report against version *N* is triaged against the model as it stood at *N*, not at `main`. |
| **Reporting** | Findings that violate a §8 property should be reported per [`SECURITY.md`](SECURITY.md). Findings under §3 or §9 will be closed citing this document. |
| **Companion model** | Pekko HTTP is built on Pekko Streams and Actors. The actor, remoting and cluster layers are modeled in [`apache/pekko`'s `THREAT_MODEL.md`](https://github.com/apache/pekko/blob/main/THREAT_MODEL.md); this document does not restate them. |

**Provenance legend.** *(documented)* — stated in Pekko HTTP's own docs or `reference.conf`, cited. *(maintainer)* — stated by a Pekko maintainer in review. Nothing in this document is inferred: every claim first drafted from code or configuration was either confirmed by a maintainer or corrected against the source, and several were corrected — the review history is in the pull requests this document links to.

Apache Pekko HTTP is a Scala/Java toolkit for building HTTP-based services and clients on top of Pekko Streams. It provides a full HTTP/1.1 and HTTP/2 implementation — parsing, connection management, marshalling, and a routing DSL of composable "directives" — as an **embeddable library**, not a standalone server. The application supplies the routes, the authentication, and the deployment.

---

## §2 Scope and intended use

Pekko HTTP is a library the application embeds. There is no Pekko HTTP daemon to secure independently of the service built on it.

Caller roles:

- **The embedding application** — fully trusted. Defines routes, supplies handlers, chooses configuration.
- **The operator/deployer** — trusted for the instance. Chooses what sits in front of the service (§4), TLS termination, and limits.
- **The HTTP client** — **untrusted**. This is the adversary the model is mostly about.

### Component families

| Family | Modules | Entry point | In model |
| --- | --- | --- | --- |
| Core protocol | `http-core`, `parsing` | Wire bytes → `HttpRequest` / `HttpResponse` | **yes — primary surface** |
| HTTP/2 | `http-core` (`impl/engine/http2`) | HTTP/2 framing, HPACK, streams | **yes** |
| Routing DSL | `http` | `Route`, directives, rejection/exception handling | **yes** |
| Marshalling | `http-marshallers-scala`, `http-marshallers-java` | JSON/XML entity conversion | **yes** |
| CORS | `http-cors` | `cors()` directive | **yes — see §5a** |
| Caching | `http-caching` | response cache directives | **yes** |
| Test kits | `http-testkit`, `http-testkit-munit`, `http-tests`, `http-compatibility-tests`, `http2-tests` | — | **no** — §3 |
| Benchmarks | `http-bench-jmh` | — | **no** — §3 |
| Lint / build / docs | `http-scalafix`, `docs`, `project`, `scripts`, `legal` | — | **no** — §3 |

*(maintainer — §14 Q6)*

---

## §3 Out of scope (explicit non-goals)

- **Test kits, benchmarks, scalafix rules, build tooling and documentation sources.** A finding in `http-bench-jmh` or any `*-tests` module is `OUT-OF-MODEL: unsupported-component`. *(maintainer — §14 Q6)*
- **Pekko HTTP is not a WAF, and not an edge-hardened server.** Applications *"should not be exposed to the public internet directly"*; an *"enterprise grade routing solution"* or a load balancer such as Apache HTTP Server or Nginx *"would be safer"* *(documented — `security.md`)*. See §4.
- **Pekko HTTP is not an authentication or authorization system.** `authenticateBasic`, `authenticateOAuth2` and `authorize` are plumbing: the credential check is a function the application supplies. There is no user store, session model or policy engine. *(maintainer — §14 Q7)*
- **The actor, stream, remoting and cluster layers** are covered by `apache/pekko`'s threat model.
- **Attackers who already control the embedding process.** *(maintainer — §14 Q7)*

---

## §4 Trust boundaries and data flow

**The trust boundary is the inbound HTTP request.** Everything derived from wire bytes — request line, headers, cookies, entity, HTTP/2 frames — is attacker-controlled until the application validates it.

Pekko HTTP's documented posture:

> "Pekko HTTP-based applications should not be exposed to the public internet directly. We believe Pekko HTTP behaves pretty well under most known Denial of Service attacks, but if you want the best security, you should use an enterprise grade routing solution. Even using a load balancing solution like an up-to-date version of Apache HTTP Server or Nginx would be safer than exposing Pekko HTTP-based applications directly to the public internet."
> — *(documented — `security.md`, "Security model")*

This is a graded claim, not a disclaimer of DoS as a class. §14 Q1 resolves it into the content-vs-volume line that triage applies.

### Reachability preconditions per family

- **Core protocol / HTTP/2** — reachable from raw wire bytes. A parser defect here needs no application cooperation.
- **Routing DSL** — reachable from a request the application's own routes expose. A finding must name the directive and show a route shape a reasonable application would write.
- **Marshalling** — reachable from an entity body **only where the application has bound that marshaller to a route**. Findings in the underlying JSON/XML library belong to that library.
- **CORS** — reachable only where the application has installed `cors()`. Opt-in, not on by default.
- **Caching** — reachable only where the application has installed a caching directive; cache-key correctness is the sharp edge.

---

## §5 Assumptions about the environment

- **Runtime.** A conformant JVM. Pekko HTTP does not defend against a hostile JVM or in-process attacker. *(maintainer — §14 Q7)*
- **Fronting infrastructure.** Something sits in front in production *(documented — `security.md`)*. Per §14 Q1 this is load-bearing for *volume* only: the proxy is relied on for flood and slow-loris defence, not for bounding a single request, which is P1's job.
- **TLS.** Pekko HTTP can terminate TLS itself (`HttpsConnectionContext`); in a fronted deployment termination is commonly the proxy's job. Cipher and protocol selection come from the JSSE context the application supplies — Pekko HTTP pins nothing and overrides no JDK default. *(maintainer — §14 Q8)*
- **Client IP.** `remote-address-attribute` ships `off` *(documented — `reference.conf`)*. When on, the attribute is the **socket** peer — behind a proxy, the proxy. `extractClientIP` reads `X-Forwarded-For` / `X-Real-Ip` and is client-controllable; `extractDirectClientIP` reads the attribute alone and is not (§14 Q4). *(documented — `MiscDirectives.scala`)*

### What Pekko HTTP does not do to its host

Verified by scanning the main sources of `http-core`, `http`, `parsing`, `http-caching` and `http-cors` *(documented — source scan, §14 Q9)*:

- **Binds no port until the application asks for one.** Binding is reachable only through `Http().newServerAt(...)` and the `ServerBuilder` it returns; nothing binds at class or extension initialization.
- **Installs no signal handlers and spawns no child processes.** No `sun.misc.Signal`, `ProcessBuilder` or `Runtime.exec` in the main sources.
- **Registers no JVM shutdown hook.** No `addShutdownHook` in the main sources. The `ActorSystem` it runs on *does* register hooks — `CoordinatedShutdown`'s, and Artery's when remoting is enabled — so an integrator will observe hooks in the process; they belong to `apache/pekko`'s model. *"Pekko HTTP registers no shutdown hook"* is the claim; *"a Pekko HTTP process has no shutdown hook"* is false. *(maintainer — §14 Q9)*
- **Touches the file system only through directives the application installs.** `getFromFile` / `getFromDirectory` read. `storeUploadedFile(s)` writes to a destination the application chooses; `fileUploadAll` buffers parts into temp files in one directory per actor system, removed by a `CoordinatedShutdown` task ([#1217](https://github.com/apache/pekko-http/pull/1217)). Nothing writes at initialization or outside an installed directive; beyond `FileUploadDirectives` the main sources contain no `FileOutputStream`, `Files.write`, `FileWriter` or `createTempFile`.
- **Does not mutate process-global state at initialization** — no `System.setProperty`, `Security.setProperty`, `Security.addProvider` or `setDefault(...)`.

---

## §5a Configuration variants that change the security envelope

Pekko HTTP's resistance to malformed and abusive input is a function of `pekko.http.server.parsing.*`, plus two `pekko.http.routing.*` entries bounding what the parsing limits cannot see — an entity's size *after* decompression. These are the **documented, shipped** limits *(all documented — `http-core` and `http` `reference.conf`)*:

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
| `max-part-count` | `10000` | Parts in one multipart entity — the amplification `max-content-length` does not bound |
| `max-comment-parsing-depth` | `5` | Nested comment recursion |
| `max-to-strict-bytes` | `8m` | `toStrict` materialization |
| `max-concurrent-streams` | `256` | HTTP/2 concurrent streams |
| `max-header-list-size` | `64 KiB` | HTTP/2 decompressed header list, **and** the accumulated HEADERS + CONTINUATION fragments for one header block |
| `incoming-connection-level-buffer-size` | `10 MB` | HTTP/2 incoming data buffered across one connection |
| `incoming-stream-level-buffer-size` | `512kB` | HTTP/2 incoming data buffered for one stream |
| `outgoing-control-frame-buffer-size` | `1024` | HTTP/2 outgoing control frames queued before the connection fails |
| `frame-type-throttle` | `frame-types = ["reset"]`, `cost = 100`, `burst = 100`, `interval = 1 s` | HTTP/2 frames rate-limited — `RST_STREAM` by default, against Rapid Reset (CVE-2023-44487) — **secure default** |
| `max-connections` | `1024` | Server connections |
| `pipelining-limit` | `1` | In-flight pipelined requests |
| `idle-timeout` | `60 s` | Connection inactivity, in either direction (§9) |
| `request-timeout` | `20 s` | Per-request handling |
| `uri-parsing-mode` | `strict` | URI leniency |
| `cookie-parsing-mode` | `rfc6265` | Cookie leniency |
| `verbose-error-messages` | `off` | Whether parse errors leak detail to the client — **secure default** |
| `error-logging-verbosity` | `full` | How much of a rejected request reaches the *server log* — with `full`, the raw request target (§9) |
| `illegal-response-header-name-processing-mode` | `error` | Blocks response-splitting via header names — **secure default** |
| `illegal-response-header-value-processing-mode` | `error` | Blocks response-splitting via header values — **secure default** |
| `server-header` | `pekko-http/${version}` | Advertises product and version |
| `remote-address-attribute` | `off` | Exposes socket peer address to routes |
| `transparent-head-requests` | `off` | HEAD handled as GET |
| `routing.decode-max-bytes-per-chunk` | `1m` | Single `ByteString` a decoding directive emits |
| `routing.decode-max-size` | `8m` | Entity size **after** decoding (§9) |

**These limits are the model's quantitative spine.** A request *within* every documented limit that causes disproportionate resource use is `VALID`; one that simply exceeds a limit is P1 working; one that needs a limit raised is `OUT-OF-MODEL: non-default-build` (§14 Q1).

### The CORS defaults — ruled (§5b, §14 Q2)

`http-cors` ships:

| Setting | Default |
| --- | --- |
| `allowed-origins` | `"*"` |
| `allow-credentials` | `yes` |
| `allowed-headers` | `"*"` |
| `allowed-methods` | `["GET", "POST", "HEAD", "OPTIONS"]` |
| `allow-generic-http-requests` | `yes` |

*"If parameter is `*` and credentials are not allowed, a `*` is set in `Access-Control-Allow-Origin`. Otherwise, the origins given in the `Origin` request header are echoed."* *(documented — `http-cors/reference.conf`)* With both defaults in force the directive **echoes the requesting `Origin` and allows credentials** — the most permissive default in the project, bounded by `cors()` being opt-in and the default being deliberate. Its disposition follows §5b: a request to change it is not a vulnerability report. *(maintainer — §14 Q2)*

That ruling covers the default, never the enforcement. Where an operator has restricted `allowed-origins`, the directive must honour it: a request pairing an allowed origin with a disallowed one used to pass while every origin was echoed back ([#1262](https://github.com/apache/pekko-http/pull/1262)) — `VALID` under §5b.4. *(maintainer)*

---

## §5b Security posture: hardening, not secure-by-default

Pekko HTTP's deployment base is inherited from Akka HTTP, and `http-cors`'s defaults arrived with the code donated by Lomig Mégard (`legal/CorsNotice.txt`). Defaults are chosen for compatibility with those deployments, whose operators already front the service (§4, §10.1) and choose limits for their traffic; tightening a default breaks them on upgrade, often as requests that used to work failing with a 4xx.

Pekko HTTP therefore takes the following position *(maintainer)*:

1. **Defaults are compatibility choices, not security claims.** §5a lists every setting whose default affects the security envelope; §10 lists what the operator must do as a result. Together they are the contract.
2. **A report that a default should be more restrictive is not a vulnerability report.** It is a change request, closed as `BY-DESIGN: default-configuration` (§13). This covers the CORS pair, `server-header`, and every limit someone considers too generous.
3. **Proposals to change a default are welcome, and belong on the development list.** The PMC weighs compatibility cost, migration path, and whether a major version is in flight. Defaults change through project discussion, not as the remediation of a security report.
4. **If an implementation is wrong, Pekko HTTP fixes it.** A control that does not do what it is documented to do once enabled is a defect, in scope, at the severity §8 assigns. **This is the sharp end of the model:** a `max-header-list-size` that fails to bound CONTINUATION accumulation, an `illegal-response-header-value-processing-mode = error` that lets a CRLF through, a `safeDirectoryChildPath` that can be walked out of — those are the findings this project wants, and the second and third were real and fixed during this review ([#1256](https://github.com/apache/pekko-http/pull/1256), [#1218](https://github.com/apache/pekko-http/pull/1218)). This posture governs which value ships as the default, never whether the mechanism works.

**Users are free to strengthen any §5a setting**, and §10 says which ones matter most. What the project will not do is change the shipped value on their behalf.

---

## §6 Assumptions about inputs

| Surface | Input | Attacker-controllable? | Who must enforce what |
| --- | --- | --- | --- |
| Any bound route | Request line (method, URI, version) | **Yes** | Pekko HTTP: §5a length limits |
| Any bound route | Headers, incl. `Host`, `Cookie` | **Yes** | Pekko HTTP: count/length limits. App: semantic trust |
| Any bound route | `X-Forwarded-*`, `X-Real-Ip` | **Yes** — trivially spoofable | **App** — surfaced by `extractClientIP`, never validated; use `extractDirectClientIP` for access control or rate limiting (§14 Q4) |
| Any bound route | Entity body (fixed, chunked, streamed) | **Yes** | Pekko HTTP: size/chunk limits. App: content validation |
| Route with a multipart unmarshaller | Body parts — boundaries, per-part headers, part count | **Yes** | Pekko HTTP: `max-part-count`, header limits per part, each part carrying only its own headers ([#1279](https://github.com/apache/pekko-http/pull/1279)). App: per-part validation |
| Custom `ParsingErrorHandler` | `IllegalRequestContext.rawRequestTarget` | **Yes** — the bytes that failed to parse | **App** — escape before logging or echoing; documented at the class |
| HTTP/2 | Frames, HPACK table, stream IDs | **Yes** | Pekko HTTP: `max-concurrent-streams`, `frame-type-throttle`, P9 |
| Route with marshaller | Entity parsed to a domain type | **Yes** | Underlying JSON/XML library + app |
| `cors()` | `Origin`, `Access-Control-Request-*` | **Yes** | Operator: §5a CORS config |
| File-serving directives | Path segments | **Yes** | Pekko HTTP + app — see §9, §14 Q3 |
| Client API | Response from an upstream server | **Yes** if the upstream is untrusted | App: treat responses as untrusted |
| Config | `application.conf` | **No** — trusted deployment input | Operator |

---

## §7 Adversary model

**In scope:**

- **The remote HTTP client.** Arbitrary bytes, malformed framing, oversized or deeply-nested input, many concurrent connections, abusive HTTP/2 frame sequences. The primary adversary — though what it achieves through sheer *volume* is the proxy's problem (§14 Q1). *(maintainer — §14 Q7)*
- **A malicious upstream server**, where the application uses the client API against an untrusted endpoint. *(maintainer — §14 Q7)*
- **A cross-origin web attacker**, where the application enables CORS. *(maintainer — §14 Q2)*

**Explicitly out of scope:**

- **Attackers with code execution in the embedding JVM.**
- **The embedding application itself.** A route that deliberately leaks data is an application bug.
- **Side-channel observers.** No general timing guarantees are made about routing or parsing. Credential comparison is the exception (P8): a timing finding against `Credentials.verify` is in scope; one against an application-supplied `provideVerify` is not. *(documented — `SecurityDirectives.scala`, `EnhancedByteArray.scala`)*

---

## §8 Security properties Pekko HTTP provides

| # | Property & conditions | Violation symptom | Severity | Provenance |
| --- | --- | --- | --- | --- |
| P1 | **Inbound messages are bounded** by the §5a limits: reaching a limit stops the parse, every header a message carries counts towards the count limits, and buffered bytes are released from the accounting when the buffer is discarded | OOM or unbounded buffering from input *within* documented limits; a limit reached without the parse stopping | **Critical** | *(documented — `reference.conf`, `HttpMessageParser.scala`, `Http2StreamHandling.scala`)* |
| P2 | **Response splitting is blocked**: illegal response header names and values are `error` by default, and the renderers drop any header whose rendered bytes contain CR, LF or NUL — on the HTTP/1.1 header block, chunked-response trailers and chunk extensions, and the HTTP/2 HPACK path alike | CRLF or NUL in an application-supplied header, trailer or chunk extension reaching the wire | **Critical** | *(documented — `reference.conf`, `Rendering.isIllegalHeaderChar`)* |
| P3 | **Parse errors do not leak detail to the client** — `verbose-error-messages = off` | Internal parse state or stack detail in a 400 response under defaults | High | *(documented — `reference.conf`)* |
| P4 | **Strict URI and RFC6265 cookie parsing by default**, rather than lenient normalization that invites smuggling | Two components disagreeing on a URI or cookie under `strict` | High | *(documented — `reference.conf`)* |
| P5 | **Connection and request lifetimes are bounded** — `idle-timeout 60s` closes a connection with no traffic in *either* direction for that long; `request-timeout 20s` bounds handling once a request has been received; `max-connections 1024`; `pipelining-limit 1`. `idle-timeout` is an inactivity timeout, not a receive deadline (§9) | A connection with no traffic outliving `idle-timeout`; a received request outliving `request-timeout` with no response; resources held past either bound after the connection is gone | High | *(documented — `reference.conf`, `timeouts.md`)* |
| P6 | **HTTP/2 concurrency and stream churn are bounded** — `max-concurrent-streams = 256` caps open streams, and `frame-type-throttle` rate-limits `RST_STREAM` (100/s, burst 100) so a peer cannot cycle through that cap for free (Rapid Reset, CVE-2023-44487) | Unbounded stream/state growth on one connection, or unbounded churn through a bounded concurrency limit | High | *(documented — `reference.conf`)* |
| P7 | **HTTP/2 header blocks are bounded** — `max-header-list-size = 64 KiB` caps the decompressed header list *and* the accumulated HEADERS + CONTINUATION fragments; over-limit blocks get `GOAWAY(ENHANCE_YOUR_CALM)` rather than being buffered | Unbounded buffering from a CONTINUATION flood or an oversized header list | **Critical** | *(documented — `reference.conf`)* |
| P8 | **Credential comparison is constant-time** where the verifier calls `Credentials.verify` — `secure_==` XOR-accumulates over the full length after a length check | Secret recoverable byte-by-byte from response timing against a `verify`-based verifier | High | *(documented — `SecurityDirectives.scala`, `EnhancedByteArray.scala`)* |
| P9 | **HTTP/2 streams are isolated from each other's malformed requests**: a request that fails to parse is answered with a `400` on its own stream, and the connection — its HPACK state and every other stream — carries on. A connection a proxy multiplexes for many clients is what this protects | One stream's malformed request failing, stalling or desynchronising the connection for the other streams | High | *(documented — `RequestErrorFlow.scala`)* |

**P1–P7 and P9 are default-on.** P8 holds only for a verifier that calls `Credentials.verify` (§10.4). The DoS boundary is the §14 Q1 content-vs-volume line.

**How §8 was verified.** P1, P2 and P9 were first asserted from configuration and design, then checked against the code; each had defects, all fixed at or before the pinned commit. P1: `max-chunk-count` reported the limit without stopping the parse ([#1220](https://github.com/apache/pekko-http/pull/1220)); repeated `Connection` headers escaped `max-header-count` ([#1255](https://github.com/apache/pekko-http/pull/1255)); HTTP/2 connection-level buffer accounting leaked on discarded streams until the connection window drained and every stream stalled ([#1259](https://github.com/apache/pekko-http/pull/1259), [#1281](https://github.com/apache/pekko-http/pull/1281)); nothing bounded multipart part count ([#1266](https://github.com/apache/pekko-http/pull/1266)). P2: the CR/LF guard missed chunked trailers and extensions ([#1256](https://github.com/apache/pekko-http/pull/1256)), NUL ([#1260](https://github.com/apache/pekko-http/pull/1260)) and the HTTP/2 header path ([#1258](https://github.com/apache/pekko-http/pull/1258)). P9: a header that failed to parse desynchronised the HPACK dynamic table for every later frame ([#1252](https://github.com/apache/pekko-http/pull/1252)), and a field the HTTP/1.1 parser rejected failed the connection instead of the stream ([#1297](https://github.com/apache/pekko-http/pull/1297)). The properties as stated are claims about the merged code, not about the settings. *(maintainer)*

**One P1 gap is open at the pinned commit.** The HTTP/2 frame parser buffers a frame of any declared length — up to 16 MiB − 1 — before HPACK decoding and before any §5a buffer bound applies, multiplied by connection count (`Http2FrameParsing.scala`). A `max-frame-size` setting is in flight ([#1264](https://github.com/apache/pekko-http/pull/1264)); until it lands, an oversized-frame report is `VALID` against P1, not a model gap. *(maintainer)*

---

## §9 Security properties Pekko HTTP does **not** provide

- **No claim of complete DoS resistance.** *"Behaves pretty well under most known Denial of Service attacks"* *(documented — `security.md`)* is scoped to **volume** (§14 Q1): Pekko HTTP does not claim to withstand floods, but does claim that one in-limits request cannot provoke disproportionate work — that is P1, and a violation is `VALID`.
- **No edge hardening.** Rate limiting, IP reputation, request scrubbing, connection throttling beyond `max-connections`, slow-loris mitigation beyond `idle-timeout` — none provided, none planned. *(maintainer — §14 Q1)*
- **No authentication or authorization.** The security directives are plumbing. *(maintainer — §14 Q7)*
- **No CSRF protection.** *(maintainer — §14 Q7)*
- **No output encoding / XSS defence.** Pekko HTTP renders what the application marshals. *(maintainer — §14 Q7)*
- **No trusted client-IP derivation from headers.** `extractClientIP` surfaces `X-Forwarded-For` / `X-Real-Ip` unvalidated; there is no trusted-proxy chain configuration. `extractDirectClientIP` is trustworthy but yields the last proxy, not the client (§14 Q4).

### False friends

- **The security directives are not a security *system*.** `authenticateBasic` and `authenticateOAuth2` route credentials to an application-supplied verifier; no password policy, rate limiting or lockout. They supply a constant-time comparison (P8) only to a verifier that calls `Credentials.verify`.
- **`allowed-origins = "*"` does not mean "no credentials are exposed".** With the shipped `allow-credentials = yes` it echoes the caller's `Origin` (§5a).
- **`remote-address-attribute` is not the client IP behind a proxy.** It is the socket peer.
- **`max-content-length` is not a global memory bound.** It bounds one entity; concurrent connections multiply it.
- **`verbose-error-messages = off` is not a log-hygiene setting.** It governs what reaches the *client*. What reaches the *server log* is `error-logging-verbosity`, which ships `full`: a request whose target fails to parse has that target written to the log at warning level (`UriParser.fail`, `logParsingError`). Attacker-chosen bytes in the log at an alerting level are a log-injection and log-flooding consideration the operator owns (§10.9). *(documented — `reference.conf`, `ErrorInfo.scala`)*
- **A rejection is not a failure.** The routing DSL's rejection mechanism is control flow, not a security control — an unhandled rejection can fall through to a different route.

### Well-known attack classes left to the caller

- **Request smuggling / desync** between a fronting proxy and Pekko HTTP is inherently two-party; strict parsing (P4) helps but cannot settle it alone. What Pekko HTTP does own is its own framing decision: framing a message by a different rule than a conformant upstream would is a defect here, not a stalemate. An unparseable `Transfer-Encoding` framed by `Content-Length` ([#1267](https://github.com/apache/pekko-http/pull/1267)) and whitespace tolerated *inside* a chunk size ([#1257](https://github.com/apache/pekko-http/pull/1257)) were both such cases; both are now rejected.
- **Slow-loris and connection exhaustion.** `idle-timeout` bounds *inactivity* in either direction: a client sending a byte inside every window keeps the connection alive by design, and a hard receive deadline needs a reverse proxy or an application-level entity timeout *(documented — `reference.conf`, `timeouts.md`)*. A trickling client is `BY-DESIGN: property-disclaimed`; exhausting the bounds by volume is disclaimed (§14 Q1). In scope: a connection holding resources with **no** traffic that outlives the timeout, or a bound whose own cleanup leaks — as the request-timeout path did, keeping one scheduled task per open request alive after the connection was gone ([#1284](https://github.com/apache/pekko-http/pull/1284)).
- **Decompression bombs** are bounded where decoding goes through `decodeRequest` / `decodeRequestWith`: per-chunk output by `decode-max-bytes-per-chunk`, decoded size by `decode-max-size` (§5a, `CodingDirectives.scala`). Left to the caller: decoding outside those directives — `Coders.Gzip.decodeMessage`, or client-side response decoding — and raising `decode-max-size`, which may be set to `infinite`.
- **SSRF** via the client API, where the application takes a URL from a request.
- **Path traversal** — three tiers (§14 Q3). `getFromDirectory`, `listDirectoryContents` and the `getFromBrowseableDirector*` pair run the unmatched path through `safeDirectoryChildPath`: segment filter plus canonical containment against the served root. Containment is claimed there, so an escape is `VALID` — one was found and fixed, a symlink into a sibling directory whose name shared the root as a string prefix ([#1218](https://github.com/apache/pekko-http/pull/1218)). `getFromResourceDirectory` applies the segment filter only, since a class-loader resource name has no canonical form. `getFromFile` and `getFromResource` filter nothing; handing either request input is a §11 misuse. Residual: on Windows `File.getCanonicalPath` does not resolve NTFS symlinks or junctions, so a link out of the root is not detected there — serving a link-free tree is the operator's.
- **XXE** in XML marshallers — a property of the underlying parser.

---

## §10 Downstream responsibilities

1. **Put an enterprise-grade proxy or load balancer in front** of an internet-facing service *(documented — `security.md`)*.
2. **Do not raise the §5a limits without understanding the memory cost** — each is multiplied by concurrent connections.
3. **If CORS is enabled, set `allowed-origins` explicitly.** Do not ship `"*"` + `allow-credentials = yes` to a credentialed API (§5a).
4. **Compare credentials with `Credentials.verify`** (P8) — not `==` on the secret, and not `provideVerify` unless the supplied verifier is itself constant-time.
5. **Use `extractDirectClientIP`, not `extractClientIP`, for access control, rate limiting or audit logging.** Derive identity from `X-Forwarded-For` only where a trusted proxy sets it and you validate the chain yourself.
6. **Validate and canonicalize any request-derived path** before passing it to a file-serving directive.
7. **Treat client-API responses from untrusted upstreams as untrusted input.**
8. **Consider `server-header = ""`** if product/version disclosure matters to your threat model.
9. **Consider `error-logging-verbosity = simple`** where logs are shipped or alerted on (§9).

---

## §11 Known misuse patterns

- **Exposing a Pekko HTTP service directly to the internet** with no fronting proxy.
- **Enabling `cors()` and leaving `allowed-origins = "*"`** on an API that uses cookies or bearer tokens.
- **Reaching for `extractClientIP`** for rate limiting, audit logging or access control, where `extractDirectClientIP` is the one the caller cannot choose.
- **Raising `max-content-length` to `infinite`** to accept large uploads, without a concurrency bound.
- **Comparing credentials with `==`** inside an `authenticateBasic` verifier, or `provideVerify` with a non-constant-time verifier.
- **Passing a request path segment straight to `getFromFile` or `getFromResource`.** Neither filters what it is handed. `getFromResource` looks like the sibling of `getFromResourceDirectory` but skips the segment filter that directive applies, so `getFromResource(s"public/$name")` against a directory-backed class loader can be walked into `application.conf` or `logback.xml`.
- **Turning `verbose-error-messages = on`** in production.
- **Building a `Raw-Request-URI` header from request input.** It renders the request target verbatim — into the HTTP/1.1 request line, and since [#1280](https://github.com/apache/pekko-http/pull/1280) as the HTTP/2 `:path`. Under §6 it is application-supplied and trusted, which is exactly why it must not carry client bytes.
- **Echoing `IllegalRequestContext.rawRequestTarget` from a custom `ParsingErrorHandler`** into the response or log without escaping it. The class documents it as attacker-controlled; the default handler never reads it.

---

## §11a Known non-findings (recurring false positives)

- **"`Server` header discloses the product and version."** Documented default, configurable via `server-header`. `BY-DESIGN: default-configuration` per §5b.
- **"No authentication on routes."** Authentication is the application's responsibility (§9). A scan of this library cannot conclude a route is unauthenticated.
- **"Request exceeding `max-uri-length` / `max-header-count` is rejected."** That is P1 working.
- **"N concurrent connections / requests exhaust CPU, memory or sockets."** Volume-based exhaustion is `BY-DESIGN: property-disclaimed` per §14 Q1 — defence belongs to the fronting proxy (§10.1). A report must show *one* in-limits request doing disproportionate work. A load-generator result is not a finding.
- **"`extractClientIP` trusts a client-supplied header."** By design and documented at the directive, with `extractDirectClientIP` as the trustworthy alternative (§14 Q4). `BY-DESIGN: property-disclaimed`. A report that `extractDirectClientIP` can be influenced by a header *is* in scope.
- **"CORS allows any origin."** The shipped default, reachable only where the application opted into `cors()`. `BY-DESIGN: default-configuration` per §5b; a misconfigured deployment is a finding against the *application*.
- **"`allow-credentials = yes` with `allowed-origins = "*"` sends `Access-Control-Allow-Origin: *` with credentials."** It does not — the literal `*` is sent only when credentials are not allowed; otherwise the request `Origin` is echoed (`CorsSettingsImpl.scala`, covered by `CorsDirectivesSpec`). Factually wrong.
- **"Credential comparison is vulnerable to a timing attack."** Wrong against `Credentials.verify` (P8); against an application's own `provideVerify` comparator it is a finding in that application.
- **Findings in `*-tests`, `http-testkit*`, `http-bench-jmh`, `http-scalafix`, `docs`** — `OUT-OF-MODEL: unsupported-component` per §3.
- **"The process registers JVM shutdown hooks."** Pekko HTTP registers none (§5). The hooks are `CoordinatedShutdown`'s and, with remoting, Artery's — `apache/pekko`'s model. `OUT-OF-MODEL: unsupported-component`.
- **Findings in the actor or stream layer** — `apache/pekko`'s model, not this one.

---

## §12 Conditions that would change this model

- A change to any §5a **default**, especially a parsing limit, a timeout, or a CORS setting.
- A new protocol version or transport (HTTP/3).
- Taking on any authentication, authorization, or rate-limiting responsibility currently disclaimed in §9.
- A change to the documented "do not expose directly" posture in `security.md`.
- Promotion of a §3 module into the supported surface.
- A change to the §5 process-behaviour claims. One near-miss is on record: [#1217](https://github.com/apache/pekko-http/pull/1217) originally registered a JVM shutdown hook from Pekko HTTP itself and was reworked to a `CoordinatedShutdown` task before merging, so the claims stand.
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
| `OUT-OF-MODEL: non-default-build` | Only manifests under a non-default §5a setting — including any resource-exhaustion report that needs a limit **raised** from its default. A real defect reachable only off-default; contrast `default-configuration`, which is no defect at all | §5a, §14 Q1 |
| `BY-DESIGN: default-configuration` | Asks that a §5a default be changed to a more restrictive value. Not a vulnerability; §5b.3 invites the proposal on the development list | §5b |
| `BY-DESIGN: property-disclaimed` | Concerns a §9 property — authentication, CSRF, XSS, edge hardening — or depends on request **volume** rather than request **content** | §9, §14 Q1 |
| `KNOWN-NON-FINDING` | Matches a §11a pattern | §11a |
| `MODEL-GAP` | Routable to none of the above — triggers §12 | §12 |

---

## §14 Maintainer rulings

Questions the draft put to the maintainers, with the ruling each received. All are settled model *(maintainer)*.

**Q1 — Where is the DoS line?** Content vs. volume:

> A **single request within every §5a documented limit** that provokes super-linear CPU or memory is `VALID` — the P1 violation symptom.
> A finding that requires a §5a limit to be **raised** from its shipped default is `OUT-OF-MODEL: non-default-build`.
> A finding that depends on request **volume** rather than request **content** — connection floods, slow-loris at scale, aggregate bandwidth — is `BY-DESIGN: property-disclaimed`. Volume defence is the fronting proxy's job (§10.1).

**Q2 — The CORS defaults.** A compatibility default under §5b: the values arrived with the donated code and existing users depend on them; `cors()` is opt-in and an application that enables it is expected to configure it (§10.3). Changing the default is `BY-DESIGN: default-configuration`. The *implementation* is in scope: `cors()` admitting an origin its configuration should have rejected, or emitting credentials for one it should not, is a defect under §5b.4.

**Q3 — File-serving directives.** Containment is claimed by `safeDirectoryChildPath` — segment filter (`..`, `/`, `\`) plus canonical containment of the resolved file under the served root — for `getFromDirectory`, `listDirectoryContents` and the `getFromBrowseableDirector*` pair. A genuine escape is `VALID` under §5b.4; review found one (string-prefix comparison of canonical paths, [#1218](https://github.com/apache/pekko-http/pull/1218)). `getFromResourceDirectory` gets the segment filter only. `getFromFile` and `getFromResource` get neither and passing them request input is a §11 misuse. Residual: on Windows `File.getCanonicalPath` does not resolve NTFS symlinks or junctions; serving a link-free tree there is the operator's. *(documented — `FileAndResourceDirectives.scala`)*

**Q4 — `X-Forwarded-For` and client identity.** `extractClientIP` resolves `X-Forwarded-For` (first address) → `X-Real-Ip` → the `remoteAddress` attribute, and its scaladoc says the headers are under the client's control unless a trusted proxy overwrites them. `extractDirectClientIP` reads the attribute alone. "`extractClientIP` trusts a spoofable header" is `BY-DESIGN: property-disclaimed`; anything client-controlled reaching `extractDirectClientIP` is `VALID`. *(documented — `MiscDirectives.scala`)*

**Q5 — Constant-time credential comparison.** `Credentials.Provided.verify` compares via constant-time `secure_==` — recorded as P8. A timing finding against `verify` is `VALID`; one against an application's own `provideVerify` comparator is `BY-DESIGN: property-disclaimed` — handing the raw secret to application code waives the guarantee (§10.4).

**Q6 — Module in/out split.** The §2 table is the maintainers' own. `http-caching` is **in** — cache-key confusion is a real class. `http-scalafix`, the test kits, `http-bench-jmh`, `docs`, `project`, `scripts` and `legal` are **out**: `OUT-OF-MODEL: unsupported-component`.

**Q7 — Non-goals.** Confirmed as stated in §3, §7 and §9: no authentication system, authorization policy, CSRF protection or XSS/output encoding (`BY-DESIGN: property-disclaimed`). Out of the adversary model: in-JVM code execution, and a malicious embedding application. In: the remote client, a malicious upstream server behind the client API, a cross-origin attacker where `cors()` is enabled.

**Q8 — TLS.** Where Pekko HTTP terminates TLS via `HttpsConnectionContext`, protocol and cipher selection come from the JSSE context the application supplies; nothing is pinned or overridden. "Weak cipher accepted" is `OUT-OF-MODEL: trusted-input`. A defect in how Pekko HTTP *drives* the context — failing to apply a supplied restriction, continuing after a handshake failure — is `VALID`. In-process termination is supported; fronting is what the docs recommend.

**Q9 — The §5 negative claims.** Verified by source scan, with one correction: the draft said Pekko HTTP writes no files, and the upload directives do — §5 states the accurate boundary. Pekko HTTP registers no shutdown hook of its own; the `ActorSystem`'s hooks belong to `apache/pekko`'s model. A report that Pekko HTTP installs a hook is factually wrong (§11a).

**Q10 — Coexistence of the security documents.** Following [`apache/pekko#3478`](https://github.com/apache/pekko/pull/3478):

| Document | Canonical for | Reached by |
| --- | --- | --- |
| [`SECURITY.md`](SECURITY.md) | **The reporting policy** | Anyone arriving via the repository, and every other document |
| `THREAT_MODEL.md` (this document) | **Scope** — what is and is not a vulnerability, and how a report is triaged | Reporters, triagers, scanning tools |
| `docs/src/main/paradox/security.md` | Security announcements, and the documentation-site index of security material | Readers of the documentation site |

Each links to the others rather than restating them. `security.md`'s "Security model" section stays as the documented source §4 quotes. **One statement is referred:** `security.md`'s upstream-coordination sentence (*"any issues affecting Apache Pekko and Akka should be reported to Apache team first…"*) is a reporting statement outside `SECURITY.md`; it is left in place pending a maintainer decision to promote it verbatim or drop it.

---

## §15 Appendix — back-map from existing docs

| Existing statement | Source | Lands in |
| --- | --- | --- |
| Applications should not be exposed to the public internet directly | `security.md` | §3, §4, §10.1 |
| Behaves "pretty well" under most known DoS attacks — scoped to *volume* by §14 Q1 | `security.md` + maintainer ruling | §4, §9, §11a, §13, §14 Q1 |
| An enterprise-grade routing solution or LB (httpd, Nginx) is safer | `security.md` | §5, §10.1 |
| Report privately per ASF guidelines; subscribe to announce@ | `security.md` | `SECURITY.md`, §1 |
| Parsing limits (`max-uri-length`, `max-header-*`, `max-chunk-*`, `max-part-count`, …) | `http-core/reference.conf` | §5a, §8 P1 |
| `idle-timeout` is a bidirectional inactivity timeout, not a request-receive deadline | `http-core/reference.conf`, `timeouts.md` | §8 P5, §9 |
| `error-logging-verbosity = full` logs the failing request target at warning level | `http-core/reference.conf`, `ErrorInfo.scala` | §5a, §9, §10.9 |
| `IllegalRequestContext.rawRequestTarget` is unvalidated, attacker-controlled input | `ParsingErrorHandler.scala` | §6, §11 |
| `verbose-error-messages = off` | `http-core/reference.conf` | §5a, §8 P3 |
| Illegal response header name/value processing = `error` | `http-core/reference.conf` | §5a, §8 P2 |
| `uri-parsing-mode = strict`, `cookie-parsing-mode = rfc6265` | `http-core/reference.conf` | §5a, §8 P4 |
| Timeouts and connection caps | `http-core/reference.conf` | §5a, §8 P5 |
| `max-concurrent-streams = 256` | `http-core/reference.conf` | §5a, §8 P6 |
| `max-header-list-size = 64 KiB`, bounding HEADERS + CONTINUATION accumulation | `http-core/reference.conf` | §5a, §8 P7 |
| `frame-type-throttle` charging `RST_STREAM` by default, against Rapid Reset (CVE-2023-44487) | `http-core/reference.conf` | §5a, §8 P6 |
| HTTP/2 incoming buffer bounds and `outgoing-control-frame-buffer-size` | `http-core/reference.conf` | §5a, §8 P1 |
| Decoding limits (`decode-max-bytes-per-chunk`, `decode-max-size`), applied by the `decodeRequest*` directives | `http/reference.conf`, `CodingDirectives.scala` | §5a, §9 |
| `Credentials.verify` compares via constant-time `secure_==` | `SecurityDirectives.scala`, `EnhancedByteArray.scala` | §7, §8 P8, §9, §10.4, §14 Q5 |
| A request that fails to parse is answered with a `400` on its own HTTP/2 stream | `RequestErrorFlow.scala` | §8 P9 |
| `safeDirectoryChildPath` rejects `..`/separator segments and enforces canonical containment | `FileAndResourceDirectives.scala` | §9, §14 Q3 |
| `remote-address-attribute = off` | `http-core/reference.conf` | §5, §9, §14 Q4 |
| `extractClientIP` reads `X-Forwarded-For`/`X-Real-Ip`; `extractDirectClientIP` reads the attribute alone | `MiscDirectives.scala` | §5, §6, §9, §10.5, §11a, §14 Q4 |
| CORS: `*` + credentials echoes the request `Origin` | `http-cors/reference.conf` | §5a, §9, §11a, §14 Q2 |
| `http-cors` code donated by Lomig Mégard, defaults inherited with it | `legal/CorsNotice.txt`, `NOTICE` | §5b, §14 Q2 |
