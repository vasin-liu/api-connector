## 1. Protocol table

- [x] 1.1 Write `docs/design/v2.7-protocols/idps-aksk.md` with algorithm, five `X-Auth-*` headers, seven-line envelope, RFC3986 encode-then-sort, unencoded wire query, UTC ISO timestamp fixture, scripted nonce, and a known HMAC vector that includes a percent-encoded query value, and verify the file contains no production secrets and cites `IdpsUtils`/`IdpsClient` (not gateway / `IdpsUtils2`)

## 2. Sorted-query RFC3986

- [x] 2.1 Add `encoding: none | rfc3986` to `canonicalizer.sorted-query` (default `none`) and reject unknown encoding at validate, and verify `PipelineGraphValidator` still rejects unknown node types and that omitted encoding still produces the Gaode plaintext VECTOR
- [x] 2.2 Add a unit test that RFC3986 encode-then-sort of the table’s query map matches the published canonical query line (space → `%20`, and `*` / `~` if listed), and verify shuffling encoded item order without sorting fails that assertion

## 3. ISO timestamp and nonce assign

- [x] 3.1 Implement Flow assign `{ now: isoOffset }` using the injected clock instant and zone (`yyyy-MM-dd'T'HH:mm:ssXXX`) and reject unknown `now` forms at validate, and verify millis `1000` in UTC assigns `1970-01-01T00:00:01Z` while `{ now: epochMillis }` still yields a number
- [x] 3.2 Add injectable `NonceSource` (test `ScriptedNonce`) and Flow assign `{ generate: nonce }` with an additive `Phase0ApiClient` constructor so existing `(transport, clock)` tests compile, and verify two successive generates differ and `{ now: epochSeconds }` fails validate

## 4. IDPS definition and tests

- [x] 4.1 Add Canonical YAML `docs/design/v2.7-protocols/idps-aksk.yaml` citing the table: two pipelines (RFC3986 sorted-query then concat LF + HMAC), GLOBAL algorithm/method/uri, `RETRY_FLOW` on 503, headers `X-Auth-*`, and verify compile succeeds, concat envelope contains U+000A (not the two characters `\` `n`), and no `canonicalizer.aksk-*` node is referenced
- [x] 4.2 Add FakeTransport tests: first GET already signed; 503 → `RETRY_FLOW` rebuilds timestamp, nonce, and signature on a new request instance; canonical query has `%20` while wire query may be unencoded; HMAC digest ≠ HMAC of the query line alone; SK absent from trace, and verify Gaode and Wenxin protocol tests still pass

## 5. Gap discipline

- [x] 5.1 If YAML cannot express a table rule (LF separator, seven concat parts, or encoding), stop and update this change’s specs/design rather than adding AuthProvider, Catalog, Hutool snowflake, or a vendor envelope node, and verify no new `AuthProvider` / `*ConnectorCatalog` types were introduced
