## Context

See `proposal.md` for why. Runtime on `main` already has FakeTransport protocol tests for Wenxin and Gaode (`canonicalizer.sorted-query` with plaintext `k=v`, `{ now: epochMillis }` only, no client nonce generator). HMAC executor takes the first bytes port unless an explicit `*.in` edge is set. `RequestRenderer.appendQuery` joins unencoded `name=value`. Production formula is `system-thirdpart` `IdpsUtils.authenticate` + `IdpsClient.getRequest` (sign RFC3986, send `MapUtil.joinIgnoreNull`). `BrainApiTool` signs and sends the encoded string — not this change's wire behavior. Specs: `specs/protocol-validation`, `pipeline-graph`, `flow-runtime`.

## Goals / Non-Goals

**Goals:**

- Table-first IDPS GET with a value that RFC3986 encodes; two-pipeline YAML; FakeTransport 503 → `RETRY_FLOW`.
- Keep Gaode green: omitted encoding = `none`.
- Inject clock + nonce without breaking existing `Phase0ApiClient(transport, clock)` tests.

**Non-Goals:**

- New pipeline node types (`canonicalizer.aksk-*`).
- Hutool snowflake worker/bits; concat `value:` / `from: node.port` unless apply proves GLOBAL parts cannot compile.
- Changing wire query encoding; Host constructor contract beyond an additive nonce parameter.

## Decisions

### D1. Source of truth is IdpsUtils + IdpsClient

**Choice:** Protocol table copies algorithm `aksk_hmac_sha256`, five `X-Auth-*` headers, seven-line `\n` envelope (no trailing newline), RFC3986 then sort, unencoded wire query.

**Why:** That is production `system-thirdpart`. Gateway SHA256 and `IdpsUtils2` are different protocols.

**Alternatives:** Follow `BrainApiTool` encoded wire query — rejected. Follow gateway inbound — rejected.

### D2. RFC3986 is a sorted-query encoding flag

**Choice:** `config.encoding`: `none` (default) | `rfc3986`. Encode each key/value (`URLEncoder` UTF-8, `+`→`%20`, `*`→`%2A`, `%7E`→`~`), then sort encoded `k=v` items, join with separator.

**Why:** Same node Gaode already uses; encode-then-sort is not plaintext key sort. Flow must not encode.

**Alternatives:** New `canonicalizer.rfc3986-query` — extra catalog type. Vendor envelope node — rejected in review. Groovy — rejected.

### D3. Two pipelines, not one vendor envelope

**Choice:** Flow: `rfc3986Query` → `execution.canonicalQuery`; then `envelopeHmac` (`canonicalizer.concat` separator LF + `signer.hmac-sha256` with edge `concat.out` → `hmac.in`). Constants (algorithm, `GET`, path) are GLOBAL vars.

**Why:** Concat today reads `config.parts` only (var / secretRef), not inbound node edges. Two `output:` bindings already work (Gaode). Splitting also avoids HMAC `findBytes` grabbing sorted-query bytes.

**Alternatives:** Same-pipeline concat `from: node.port` — extra executor work, not needed. `canonicalizer.aksk-envelope` — vendor-shaped catalog.

### D4. `{ now: isoOffset }` on the injected Clock

**Choice:** Format `yyyy-MM-dd'T'HH:mm:ssXXX` with `clock.instant()` + `clock.getZone()`. Tests use `ScriptedClock` (already UTC). Fixture at millis `1000` is `1970-01-01T00:00:01Z`.

**Why:** `epochMillis` stringify is `"1000"`, not the production timestamp.

**Alternatives:** Pipeline clock node — clock belongs in Flow assign (Gaode stamp). Hardcoded YAML timestamp — cannot prove RETRY_FLOW freshness.

### D5. `{ generate: nonce }` + injectable NonceSource

**Choice:** Assign pulls `NonceSource.next()`. Tests inject `ScriptedNonce("1","2",...)`. Default implementation may be unique strings; **not** Hutool snowflake. Header name stays `X-Auth-SnowflakeID`.

**Why:** IDPS has no challenge nonce to extract. Bit-identical snowflake is untestable in FakeTransport and not required by `BrainApiTool` (UUID).

**Alternatives:** Extract from 403 — IDPS does not send it. Copy Hutool — worker id / clock coupling, out of scope.

### D6. Additive client constructor

**Choice:** Keep `Phase0ApiClient(HttpTransport, Clock)`. Add an overload (or last-arg default) that accepts `NonceSource`. Existing Gaode/Wenxin/Mock tests stay source-compatible.

**Why:** Nonce is the only new host-side injection.

### D7. Wire query stays unencoded

**Choice:** Do not percent-encode `RequestRenderer.appendQuery`. Table and tests assert canonical `%20` vs wire space (or `*`).

**Why:** Matches `IdpsClient`. Changing renderer would break Gaode and silently diverge from production IDPS.

### D8. Secrets and fixtures

**Choice:** `test-ak` / `test-sk` via SecretRef. AK may appear as header `X-Auth-Key` after credential bind (same pattern as Gaode query `key`). SK only HMAC sink. Never commit `BrainApiTool` keys.

### D9. Gap discipline

**Choice:** If concat LF is two characters (`\` `n`) instead of U+000A, stop and spec a named separator (e.g. `lf`) rather than signing in Flow. If concat's Mock C three-port defaults reject seven `parts`, relax ports — do not add a vendor node.

## Risks / Trade-offs

- [HMAC signs query not envelope] → Two pipelines + explicit `concat.out` → `hmac.in`; unit test digest ≠ HMAC(canonicalQuery).
- [YAML `"\n"` is not LF] → Node/protocol test asserts `0x0A` in envelope bytes; fallback named separator via spec update.
- [Live IDPS rejects non-Hutool nonce] → Table assumption; FakeTransport does not claim live auth.
- [Live JVM zone ≠ UTC] → Tests lock UTC; table lists `XXX` offset as assumption vs `ZonedDateTime.now()`.
- [Gaode regression] → Default encoding `none`; keep existing VECTOR test.
- [POST body in canonical string] → Out of scope; GET query only.

## Migration Plan

1. Protocol table (+ encoding / dual-query / UTC assumptions).
2. RFC3986 unit vector; `isoOffset` + `NonceSource`; unknown encoding / `now` fail validate.
3. YAML + FakeTransport (RED then GREEN). Re-run Gaode/Wenxin tests.
4. No production deploy.

## Open Questions

None that change specs or tasks. Live snowflake/timezone acceptance is documented as table assumptions, not apply blockers.
