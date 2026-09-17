# Phase 0 platform notes

## JSONPath (task 1.1)

Choice: **self-written restricted parser** (`RestrictedJsonPath` in `api-connector-core`).

Jayway JsonPath default mode supports Filter expressions (`$.items[?(@.price < 10)]`) and is not used as the Condition engine.

Allowed: `$`, `.property`, `[index]`.
Forbidden: `..`, `?(`, `()`, `@`.

## GraalVM Polyglot (task 1.2)

Spike ran on the current JDK with `org.graalvm.polyglot:js` 24.2.1.

- `HostAccess.EXPLICIT` blocks `Java.type('java.io.File')`.
- `ResourceLimits.statementLimit` interrupts a tight JS loop (limit exceeded).
- Close the context with `ctx.close(true)` after a cancelled eval; a normal try-with-resources close can rethrow the cancel.
- Current JDK runs polyglot in interpreter-only mode unless `-XX:+EnableJVMCI` is set. Resource limits still work. 0d can stay on standard JDK 21 + polyglot JARs.

Conclusion: standard JDK 21 + GraalVM polyglot JARs is sufficient for Phase 0 script resource limits. GraalVM JDK is not required for the spike.

## CookieStore (as-implemented)

Phase 0 uses a hand-rolled store, not a third-party Cookie RFC library.

- Class: `com.suntek.apiconnector.runtime.session.CookieStore`
- Parse: `java.net.HttpCookie.parse` on `Set-Cookie` values against the request URI
- Blank Domain defaults to the request host; blank Path defaults to the parent path of the request URI
- Outbound match: `HttpCookie.domainMatches` (plus exact host), path prefix, Secure only on `https`
- Expired cookies are purged on read
- SameSite is not implemented (`java.net.HttpCookie` has no SameSite field)
- HttpOnly is stored by JDK parse but is not used as an outbound filter (this is a server-side client)

## planId (as-implemented)

`planId` is a stable hash of the compiled definition. The compiler does not put clock or random values into the hash.

Pipeline: `DefinitionNormalizer.normalize` → `CanonicalJson.stringify` → `PlanId.sha256Hex`

- `com.suntek.apiconnector.runtime.compile.DefinitionNormalizer` fills Normalize defaults
- `CanonicalJson.stringify` emits deterministic JSON (object keys sorted; array order preserved)
- `PlanId.sha256Hex` is SHA-256 of UTF-8 canonical JSON, lowercase hex (`planId = sha256(utf8(canonicalNormalizedJson))`)
