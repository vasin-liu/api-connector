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
