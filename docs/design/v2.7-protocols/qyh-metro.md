# Qyh metro SHA-256 header sign — protocol table

Canonical Definition: [`qyh-metro.yaml`](qyh-metro.yaml).  
Sources: `system-thirdpart` `com.suntek.system.thirdpart.client.qyh.QyhMetroClient.inOutRecord` and `getSHA256`. Old Java Catalog/AuthProvider is gone.

**No production secrets.** Tests use `test-metro-token` / `test-pass` only. Do not copy the host, pass id, or token from `QyhMetroExtendParam`.

## Endpoint

| Field | Value |
|-------|--------|
| Base URL | `https://qyh-metro.example` (FakeTransport only) |
| Method | `GET` |
| Path | `/jtj/in-out` |
| Auth profile (YAML) | `qyh-metro-sha256-v1` |
| Query | `start_time=2026-01-01T00:00:00&end_time=2026-01-01T00:10:00` |
| Body | none |

## Headers

| Name | In SHA-256 material? | Source | Fixture r1 / r2 |
|------|----------------------|--------|-----------------|
| `x-tif-paasid` | **no** | literal | `test-pass` |
| `timestamp` | **yes** | `{ now: epochSeconds }` | `1700000000` then `1700000002` |
| `nonce` | **yes** | `{ generate: nonce }` | `1` then `2` |
| `signature` | **NO — digest** | `hasher.sha256` uppercase hex | see vector |

`token` is HASH material only. It MUST NOT appear as a header, query, or body field.

## Signed material

1. Concatenate UTF-8 strings with an **empty** separator: `timestamp` + `token` + `nonce` + `timestamp`.
2. SHA-256 over those bytes (JDK `MessageDigest` SHA-256, same call shape as `QyhMetroClient.getSHA256`).
3. Emit **uppercase** hexadecimal (32 bytes → 64 hex chars).

Pipeline: `canonicalizer.concat` (`separator: ""`, secret part sink `HASH`) then `hasher.sha256` (`encoding: hex-upper`). Flow MUST NOT hash.

### Known test vector (FakeTransport only)

| Send | Preimage | `signature` |
|------|----------|-------------|
| r1 | `1700000000test-metro-token11700000000` | `F299939FA500E157E6EBD2899F6CE9FF926D0DF0996AC73D6643C501E62317E5` |
| r2 | `1700000002test-metro-token21700000002` | `0B3FF5CB28670D23AA4266EF97AF9512DBB7463BE874501E10800789CF45033E` |

Lowercase of the r1 digest MUST NOT be `signature`.

Clock: `1700000000000` then `1700000002000` (epoch millis). Seconds are those values divided by 1000, truncated toward zero. Nonce: `1` then `2`.

## Replay / freshness

No Session login hop. YAML uses **timestamp + nonce refresh + `RETRY_FLOW`**:

1. Business: stamp → nonce → sign pipeline → GET (already signed).
2. FakeTransport returns **503**.
3. `RETRY_FLOW` runs authentication, which re-stamps, generates a new nonce, and re-SHA-256s with **zero** authentication HTTP; the business template is rebuilt (not a byte clone of send 1).
4. Second GET differs in `timestamp`, `nonce`, and `signature`. Query stays unchanged.

## Assumptions

| Topic | Frozen here | Unverified vs live metro |
|-------|-------------|--------------------------|
| Timestamp unit | Epoch seconds | Same as `now / 1000L` |
| Nonce | Injectable `generate: nonce` | `Math.random().substring(2)` |
| Query times | `T`-separated literals | Live `yyyy-MM-dd HH:mm:ss` with a space |
| Routes | `in-out` only | Other paths share the same headers |
| Response | Body bytes unchanged | Live `success` / empty list parsing |

## Out of scope

Yuhaoban (same digest formula, no definition here), support-vehicle / support-task, `timeOut` renewal, `Math.random()` nonce, space-separated query times, copying `QyhMetroExtendParam` credentials, Host / Catalog / Groovy, 讯飞 WebSocket, public internet, production secrets.

## FakeTransport script (acceptance)

| Send | Freshness | SHA-256 |
|------|-----------|---------|
| r1 | `timestamp=1700000000`, `nonce=1` | r1 digest above |
| r2 (after 503 + `RETRY_FLOW`) | `timestamp=1700000002`, `nonce=2` | r2 digest above |

r2 is a new `RawHttpRequest` instance; r1 bytes are not cloned. Authentication HTTP count is zero. HTTP 200 with body `{"ok":true}` returns SUCCESS and those same bytes.
