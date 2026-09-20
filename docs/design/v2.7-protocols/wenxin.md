# Baidu Wenxin / ERNIE — protocol table

Canonical Definition: [`wenxin.yaml`](wenxin.yaml).  
Sources: `docs/legacy-auth-inventory.md` (`BAIDU_WENXIN`, `oauth2_token_in_query`), `docs/profile-registry.md`, public Baidu AI Cloud OAuth docs. Old Java Catalog/AuthProvider is gone; this table freezes the YAML + FakeTransport contract.

**No production secrets.** Git and tests use only fixture material (`test-wenxin-id`, `test-wenxin-secret`, `test-wenxin-token`). Do not check in API Key / Secret Key / live `access_token`.

## Endpoints

| Role | Method | URL | Notes |
|------|--------|-----|--------|
| Token | `GET` | `https://aip.baidubce.com/oauth/2.0/token` | Client-credentials exchange |
| Business (non-stream) | `POST` | `https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/eb-instant` | Smallest JSON chat path; SSE/stream is out of scope |

## Token request

| Query | Source | Fixture (tests only) |
|-------|--------|----------------------|
| `grant_type` | literal `client_credentials` | `client_credentials` |
| `client_id` | SecretRef `secret/baidu-wenxin/client-id` | `test-wenxin-id` |
| `client_secret` | SecretRef `secret/baidu-wenxin/client-secret` | `test-wenxin-secret` |

## Token response (FakeTransport shape)

```json
{"access_token":"test-wenxin-token","expires_in":2592000}
```

| Field | JsonPath | Binding |
|-------|----------|---------|
| access token | `$.access_token` | `session.token` (secret) |
| TTL | `$.expires_in` | **Not** mapped; SESSION TTL is the definition `session.ttl` (`30m`) |

## Business request

| Query | Source |
|-------|--------|
| `access_token` | `session.token` |

Body is JSON (`codec.json`). FakeTransport does not assert vendor body shape. Stream/`stream=true` is not used.

## Session flow (Mock B shape)

1. First business send has no session → query omits `access_token`.
2. FakeTransport returns **HTTP 401** with `{"error":"UNAUTHORIZED"}`.
3. Authentication calls the token URL **once**, extracts `$.access_token`, generation increments.
4. Replay rebuilds the business template; query includes `access_token=test-wenxin-token`.
5. A second execute with a still-valid session skips the token URL.

## Assumptions (inventory vs live API)

| Topic | Frozen here | Live API (not a test gate) |
|-------|-------------|----------------------------|
| Missing/expired token | HTTP **401** + `$.error=UNAUTHORIZED` so existing Session transitions apply | Baidu often returns HTTP 200 with `error_code` (e.g. 110) in the body |
| Token HTTP method | `GET` + query | POST form body is also documented |
| Chat path | `eb-instant` non-stream | Other ERNIE model paths exist |
| `expires_in` | Ignored; YAML `session.ttl` | Could drive TTL later without changing query names |

## FakeTransport script (acceptance)

| Execute | Outbound | Scripted response |
|---------|----------|-------------------|
| First, no session | `POST .../chat/eb-instant` (no `access_token`) | 401 `{"error":"UNAUTHORIZED"}` |
| First, auth | `GET .../oauth/2.0/token` | 200 token JSON above |
| First, replay | `POST .../chat/eb-instant?access_token=test-wenxin-token` | 200 `{"result":"ok"}` |
| Second, valid session | one chat POST only | 200 |
| Token always fails | token GET 4xx | shared failure + cooldown; follow-up execute does not storm |

Secret material (`test-wenxin-id`, `test-wenxin-secret`, `test-wenxin-token`) MUST be absent from DecisionTrace and exception messages.
