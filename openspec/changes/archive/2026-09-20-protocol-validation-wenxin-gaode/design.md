## Context

See `proposal.md`. Runtime is on `main` (Phase 0 Mock A–I). Host is still in-process `ApiClient`. Inventory leftovers (`docs/legacy-auth-inventory.md`, `docs/profile-registry.md`) record Wenxin as `oauth2_token_in_query` (`aip.baidubce.com`) and Gaode traffic as `gaode_traffic_hmac_v1` (`et-api.amap.com`, HMAC on sorted query). Old Java providers were deleted with the hub; this change re-encodes those protocols as Canonical YAML + FakeTransport, not Catalog/AuthProvider.

Existing nodes: `passthrough`, `codec.json`, `canonicalizer.concat`, `signer.hmac-sha256`. Mock B covers 401 → login → Bearer replay. Mock C covers concat HMAC + rebuild. Wenxin is Mock B with **query** token. Gaode is Mock C-shaped signing over **sorted query**, which concat-of-named-parts cannot do unless Flow pre-sorts (forbidden by pipeline-graph).

## Goals / Non-Goals

**Goals:**

- Freeze protocol tables, then YAML, then tests that fail until green.
- Prove session reuse (Wenxin) and template rebuild HMAC (Gaode) without the public internet.
- Add sorted-query canonicalizer to the built-in catalog.

**Non-Goals:**

- Host, admin UI, gateway export, Vue/React console.
- Live vendor calls or checking in real keys.
- Streaming SSE chat as a product path (non-stream chat or a stub endpoint is enough).
- IDPS header AKSK, Hikvision SDK, Cookie session vendors.

## Decisions

### D1. FakeTransport only

**Choice:** Tests inject FakeTransport with scripted status/body. Optional checked-in **redacted** recorded JSON later; not required to close the change.

**Why:** CI cannot hold vendor credentials or depend on Baidu/Amap availability.

**Alternatives:** WireMock to public URL — rejected. Manual curl as gate — rejected.

### D2. Protocol tables before YAML

**Choice:** First task writes `docs/design/v2.7-protocols/wenxin.md` and `gaode-traffic.md` (token URL, grant, query names, HMAC field order, timestamp field). YAML must cite the table. If `system-thirdpart` is unavailable, use inventory + public API docs and mark remaining fields as assumptions in the table.

**Why:** Guessing Canonical YAML will fail Gaode digest.

**Alternatives:** YAML-first — rejected.

### D3. Sorted-query canonicalizer node

**Choice:** Add built-in node (name in apply, e.g. `canonicalizer.sorted-query`) that sorts object/map keys and joins `k=v` with a documented separator (Gaode table decides `&` vs empty). HMAC key remains a secret port with sink HMAC.

**Why:** Keeps sort in Pipeline. `canonicalizer.concat` stays for Mock C.

**Alternatives:** Groovy/script sort — rejected (Phase 0 script not in Mock path; would skip type checks). Sort in Flow assign — rejected (data flow in control flow).

### D4. Wenxin maps to existing Session flow

**Choice:** No new SessionCoordinator semantics. Token extract to `session.token`; business query `access_token` from that secret. First attempt is business (SESSION_MISSING), 401 → AUTHENTICATE → REPLAY_REQUEST, matching Mock B / D7.

**Why:** Proves the existing spec on a real shape.

**Alternatives:** Always pre-auth — rejected unless YAML adds an explicit step.

### D5. Secrets

**Choice:** Test SecretProvider returns fixture values (`test-ak` / `test-sk`). Trace/redaction tests already in 0d; protocol tests assert `access_token` and digest key material are redacted.

**Why:** Align with `secret-script`.

### D6. Runtime gaps

**Choice:** If token-in-query or sorted HMAC cannot be expressed after D3, stop apply and `/opsx-update` specs. Do not add AuthProvider or Java Catalog.

## Risks / Trade-offs

- [Gaode canonical string wrong] → Table must quote key order, excluded fields (e.g. `sig`), encoding. Tests lock a known HMAC vector from the table, not live Amap.
- [Wenxin token JSON path drift] → Table freezes jsonpath for access_token / expires; FakeTransport body matches the table.
- [Streaming] → Use a non-stream endpoint in YAML.
- [Inventory vs live API] → Document assumption rows; do not block on deleted hub source if tables are internally consistent.

## Migration Plan

1. Protocol tables.
2. Sorted-query node + unit vector (if Gaode needs it — it does under D3).
3. YAML + FakeTransport tests (RED then GREEN).
4. No production deploy; Host remains later.

## Open Questions

- Exact Gaode digest charset and whether `sig` is excluded from the sorted set (fill in table from audit/public doc; do not guess in YAML).
- Wenxin chat vs a simpler `ernie` HTTP path for the business request (choose the smallest non-stream JSON call in the table).
