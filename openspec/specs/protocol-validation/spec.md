# protocol-validation Specification

## Purpose

Phase 0 follow-on gate for real vendor protocols: Canonical Definitions plus FakeTransport tests for Wenxin OAuth token-in-query and Gaode traffic sorted-query HMAC. Gaps must become OpenSpec updates, not implicit Runtime, Groovy, or Catalog code.

## Requirements

### Requirement: Wenxin OAuth token-in-query executes as a published definition
The system SHALL compile and execute a Canonical Definition for Baidu Wenxin client-credentials token exchange that stores the token in SESSION and attaches it as the `access_token` query parameter on the rebuilt business request. FakeTransport tests MUST NOT call the public internet. Secrets MUST be absent from DecisionTrace and exception messages.

#### Scenario: First business 401 then token then replay
- **WHEN** a Wenxin business request is sent without a session token and the FakeTransport returns 401
- **THEN** the authentication flow calls the token endpoint once, generation increments, and the replayed business request includes `access_token` in the query

#### Scenario: Valid session skips token endpoint
- **WHEN** a second execution reuses a still-valid Wenxin session
- **THEN** FakeTransport records zero token-endpoint calls and one business call

#### Scenario: Failed token exchange does not storm
- **WHEN** the token endpoint always fails
- **THEN** shared failure plus cooldown prevent a second token call from a follow-up execute during cooldown

### Requirement: Gaode traffic sorted-query HMAC rebuilds on replay
The system SHALL compile and execute a Canonical Definition for Gaode traffic HMAC over a canonical string of **sorted query parameters**. Replay and challenge (or timestamp refresh) MUST re-render the template and recompute HMAC; the second outbound query MUST NOT clone the first request bytes.

#### Scenario: Two sends differ in timestamp and digest
- **WHEN** FakeTransport returns a challenge or retry that triggers rebuild of the Gaode business request
- **THEN** the second outbound query string has a different timestamp (or equivalent freshness field) and a different HMAC digest than the first

#### Scenario: HMAC never runs in Flow
- **WHEN** the Gaode definition computes the digest
- **THEN** sorting, concat, and HMAC are pipeline nodes; Flow only assigns variables and chooses transitions

### Requirement: IDPS header AK/SK executes as a published definition
The system SHALL compile and execute a Canonical Definition for IDPS `aksk_hmac_sha256` that signs a seven-line envelope (algorithm, access key, ISO-offset timestamp, HTTP method, URI path, RFC3986 canonical query, client nonce) with HMAC-SHA256 and sends the digest plus freshness fields as `X-Auth-*` headers. FakeTransport tests MUST NOT call the public internet. Secret key material MUST be absent from DecisionTrace and exception messages. Production secrets MUST NOT appear in git. The outbound query string MAY remain unencoded even when the canonical query is percent-encoded.

#### Scenario: Two sends differ in timestamp, nonce, and digest
- **WHEN** FakeTransport returns a status that triggers `RETRY_FLOW` of the IDPS business request
- **THEN** the second outbound request is a new instance (not a clone of the first bytes) and MUST differ in `X-Auth-Timestamp`, `X-Auth-SnowflakeID`, and `X-Auth-Signature`

#### Scenario: Canonical query is RFC3986 then sorted
- **WHEN** the IDPS definition signs a query map whose values include a character that RFC3986 percent-encodes
- **THEN** the HMAC envelope's query line contains the encoded form (for example space as `%20`) while the outbound URL query MAY still carry the unencoded value

#### Scenario: HMAC signs the envelope not the query substring
- **WHEN** the IDPS definition computes the digest
- **THEN** HMAC input bytes are the full seven-line envelope joined by U+000A, not the canonical query string alone, and Flow MUST NOT sort, encode, or HMAC

#### Scenario: Unknown vendor node is rejected
- **WHEN** a definition references a pipeline node type that is not in the built-in catalog (including any `canonicalizer.aksk-*` vendor type that is not catalogued)
- **THEN** compile fails with an unknown-node validation code and zero HTTP

### Requirement: Huawei IVS cookie session executes as a published definition
The system SHALL compile and execute a Canonical Definition for Huawei IVS1800 login that posts JSON `userName` and `password` to the login path, treats JSONPath `$.resultCode` equal to `"0"` as authentication success, accepts `Set-Cookie` into the session cookie store, and attaches those cookies on the rebuilt business request. The business request MUST NOT send an Authorization header or a session token query/header. FakeTransport tests MUST NOT call the public internet. Cookie values and password material MUST be absent from DecisionTrace and exception messages. Production secrets MUST NOT appear in git.

#### Scenario: First business 401 then login then cookie replay
- **WHEN** a Huawei business request is sent without a session cookie and FakeTransport returns 401
- **THEN** the authentication flow calls the login endpoint once, generation increments, and the replayed business request includes `Cookie` with `JSESSIONID` from the cookie store and MUST NOT include `Authorization`

#### Scenario: Valid cookie session skips login
- **WHEN** a second execution reuses a still-valid Huawei session
- **THEN** FakeTransport records zero login-endpoint calls and one business call that still carries `JSESSIONID` from the store

#### Scenario: Failed login does not storm
- **WHEN** login always returns HTTP 200 with `resultCode` other than `"0"`
- **THEN** shared failure plus cooldown prevent a second login call from a follow-up execute during cooldown, and no `JSESSIONID` is stored

#### Scenario: Cookie is not copied from the raw login body
- **WHEN** login succeeds with `Set-Cookie: JSESSIONID=…` and a JSON body that also contains some session-looking field
- **THEN** the outbound business `Cookie` header is built only from the cookie store and MUST NOT take a cookie value from the JSON body

### Requirement: DaHua nested-MD5 authorize executes as a published definition
The system SHALL compile and execute a Canonical Definition for 大华交警视频云 authorize that: (1) posts `{userName, clientType}` with frozen `clientType=web` and login headers `Content-Type: application/json;charset=UTF-8` plus `X-Api-Version: V1.0` to `/videoService/accounts/authorize`, using two `type: secret` credentials for username and password, treats HTTP **401** when `$.randomKey` and `$.realm` exist and `$.encryptType` equals `MD5` as challenge hop (`CONTINUE`), then binds `realm` / `randomKey` / `encryptType` in **following EXTRACT steps**; (2) computes signature with nested MD5 for the **no-`method`-key** branch from `TrafficDaHuaUtils.calculateSign` inside Pipeline (`canonicalizer.concat` with `HASH` for both secrets + `hasher.md5` on bytes); (3) posts the signed body including `signature`, `randomKey`, `encryptType`, and `expiredTime=86400` to the same authorize path; (4) stores `$.token` in `session.token` and attaches it as header `X-Subject-Token` with sink `HEADER` on the rebuilt business request. FakeTransport tests MUST NOT call the public internet. Password, username, and token material MUST be absent from DecisionTrace and exception messages. Production secrets MUST NOT appear in git. RSA and `method=simple` / blank-method branches are out of scope. Challenge fixture `realm` MUST be non-blank.

#### Scenario: First business 401 then two-hop auth then token replay
- **WHEN** a DaHua business request is sent without a session token and FakeTransport returns 401
- **THEN** authentication performs exactly one challenge authorize (HTTP 401 body) and one token authorize (HTTP 200 with token), generation increments, and the replayed business request includes `X-Subject-Token` equal to the stored token

#### Scenario: Valid session skips authorize
- **WHEN** a second execution reuses a still-valid DaHua session
- **THEN** FakeTransport records zero authorize-endpoint calls and one business call that still carries `X-Subject-Token`

#### Scenario: Failed challenge hop does not storm
- **WHEN** the challenge authorize always returns 401 without `randomKey`
- **THEN** shared failure plus cooldown prevent a second authorize storm from a follow-up execute during cooldown, and no token is stored

#### Scenario: Signature matches nested MD5 fixture
- **WHEN** FakeTransport challenge returns fixed non-blank `realm` / `randomKey` / `encryptType=MD5` without a `method` key and credentials are the published fixtures
- **THEN** the token authorize request body `signature` equals the lowercase nested-MD5 digest published in the protocol table

#### Scenario: MD5 never runs in Flow
- **WHEN** the DaHua definition computes the signature
- **THEN** nested MD5 steps are pipeline concat + `hasher.md5` nodes; Flow only assigns, extracts, and chooses transitions

### Requirement: Hikvision Artemis GET AK/SK executes as a published definition
The system SHALL compile and execute a Canonical Definition for Hikvision Artemis GET that signs `SignUtil` StringToSign with HMAC-SHA256 and sends the Base64 digest plus freshness fields as `X-Ca-*` headers. StringToSign for this gate is the frozen GET shape: `METHOD` uppercase, then `Accept` value, then `Content-Type` value (no `Content-MD5`, no `Date`), then signed-header lines `X-Ca-Key:{ak}`, `X-Ca-Nonce:{nonce}`, `X-Ca-Timestamp:{epochMillis}` each `Name:value` plus U+000A, then resource `path?` plus query pairs sorted by key with encoding `none`. HMAC MUST use encoding `base64`. Canonicalization and HMAC MUST be pipeline nodes (`canonicalizer.sorted-query`, `canonicalizer.concat`, `signer.hmac-sha256`); Flow only assigns, binds credentials, and chooses transitions. FakeTransport tests MUST NOT call the public internet. Secret **key** material MUST be absent from DecisionTrace, exception messages, and outbound query/headers. Production secrets MUST NOT appear in git. The definition MUST NOT depend on `artemis-http-client` or a vendor `canonicalizer.artemis-*` node.

#### Scenario: Two sends differ in timestamp, nonce, and signature
- **WHEN** FakeTransport returns a status that triggers `RETRY_FLOW` of the Artemis GET
- **THEN** the second outbound request is a new instance (not a clone of the first bytes) and MUST differ in `X-Ca-Timestamp`, `X-Ca-Nonce`, and `X-Ca-Signature`

#### Scenario: Signature matches the published Base64 vector
- **WHEN** FakeTransport first GET uses the published fixtures (AK/SK, nonce, timestamp, path, query)
- **THEN** `X-Ca-Signature` equals the Base64 digest published in the protocol table and `X-Ca-Key` equals the fixture AK

#### Scenario: HMAC signs StringToSign not the query substring
- **WHEN** the Artemis definition computes the digest
- **THEN** HMAC input bytes are the full StringToSign (method, Accept, Content-Type, signed `X-Ca-*` lines, resource), not the canonical query string alone, and the digest is Base64 not hex

#### Scenario: SK never appears on the wire
- **WHEN** the Artemis GET is sent
- **THEN** no query, header, or body field contains the secret key, and DecisionTrace MUST NOT contain SK plaintext

#### Scenario: Unknown vendor Artemis node is rejected
- **WHEN** a definition references `canonicalizer.artemis-string-to-sign` or another pipeline node type that is not in the built-in catalog
- **THEN** compile fails with an unknown-node validation code and zero HTTP

### Requirement: Hikvision Artemis POST Content-MD5 executes as a published definition
The system SHALL compile and execute a Canonical Definition, distinct from the Artemis GET definition, for Hikvision Artemis POST that signs `SignUtil` StringToSign with HMAC-SHA256 and sends the Base64 digest plus freshness fields as `X-Ca-*` headers. The HTTP body MUST be the UTF-8 bytes of one published JSON literal. `Content-MD5` MUST be RFC 4648 Base64 of the raw MD5 of those same body bytes. StringToSign for this gate is the frozen POST shape: `METHOD` uppercase, then `Accept` value, then the `Content-MD5` value, then `Content-Type` value (no `Date`), then signed-header lines `X-Ca-Key:{ak}`, `X-Ca-Nonce:{nonce}`, `X-Ca-Timestamp:{epochMillis}` each `Name:value` plus U+000A, then resource `path?` plus query pairs sorted by key with encoding `none`. The body MUST NOT be merged into that resource. HMAC MUST use encoding `base64`. Canonicalization, MD5, and HMAC MUST be pipeline nodes (`canonicalizer.sorted-query`, `canonicalizer.concat`, `hasher.md5`, `signer.hmac-sha256`); Flow only assigns, binds credentials, and chooses transitions. The signed body MUST NOT be the JSON encoding of the execution-variable object. FakeTransport tests MUST NOT call the public internet. Secret **key** material MUST be absent from DecisionTrace, exception messages, and outbound query, headers, or body. Production secrets MUST NOT appear in git. The definition MUST NOT depend on `artemis-http-client` or a vendor `canonicalizer.artemis-*` node. The existing Artemis GET definition MUST keep compiling and MUST keep its published GET signature.

#### Scenario: Two sends differ in timestamp, nonce, and signature only
- **WHEN** FakeTransport returns a status that triggers `RETRY_FLOW` of the Artemis POST
- **THEN** the second outbound request is a new instance (not a clone of the first bytes) and MUST differ in `X-Ca-Timestamp`, `X-Ca-Nonce`, and `X-Ca-Signature`, while the body bytes and `Content-MD5` stay equal to the first send

#### Scenario: Signature and Content-MD5 match the published vectors
- **WHEN** FakeTransport first POST uses the published fixtures (AK/SK, nonce, timestamp, path, query, body)
- **THEN** `X-Ca-Signature` equals the Base64 digest published in the protocol table, `Content-MD5` equals the published Base64 MD5, and `X-Ca-Key` equals the fixture AK

#### Scenario: Content-MD5 is the MD5 of the wire body
- **WHEN** the Artemis POST is sent
- **THEN** the outbound body equals the published JSON literal, `Content-MD5` equals RFC 4648 Base64 of the raw MD5 of those body bytes, and the lowercase hex MD5 MUST NOT be sent as `Content-MD5`

#### Scenario: HMAC signs StringToSign including Content-MD5
- **WHEN** the Artemis POST definition computes the digest
- **THEN** HMAC input bytes are the full StringToSign (method, Accept, Content-MD5, Content-Type, signed `X-Ca-*` lines, resource) and the digest is Base64 not hex; the digest MUST NOT equal HMAC of the query string alone or of the body alone

#### Scenario: MD5 never runs in Flow
- **WHEN** the Artemis POST definition computes `Content-MD5`
- **THEN** the hash is a `hasher.md5` node with encoding `base64` over concat bytes of the body literal; Flow MUST NOT compute MD5

#### Scenario: SK never appears on the wire
- **WHEN** the Artemis POST is sent
- **THEN** no query, header, or body field contains the secret key, and DecisionTrace MUST NOT contain SK plaintext

#### Scenario: Unknown vendor Artemis node is rejected
- **WHEN** a definition references `canonicalizer.artemis-string-to-sign` or another pipeline node type that is not in the built-in catalog
- **THEN** compile fails with an unknown-node validation code and zero HTTP

### Requirement: Hikvision PMS download token executes as a published definition
The system SHALL compile and execute a Canonical Definition, distinct from the Artemis definitions, for Hikvision PMS 下行 token: the `token` query parameter is the **uppercase** hexadecimal MD5 over the concatenation of the request URI path (no host, no query), the exact wire body bytes, and the secret key. The hashed body bytes MUST be the bytes actually sent. The MD5 MUST be a `hasher.md5` node with encoding `hex-upper` fed by `canonicalizer.concat`; Flow MUST NOT compute MD5 or uppercase strings. The body MUST be the UTF-8 bytes of compact JSON `{"appkey":"<appkey>"}`, where `<appkey>` is credential material for this api and MUST NOT be stored as a definition literal. FakeTransport tests MUST NOT call the public internet. Secret key material MUST be absent from DecisionTrace, exception messages, and outbound query, headers, or body. The appkey credential MUST be absent from DecisionTrace and exception messages. Production secrets MUST NOT appear in git. The definition MUST NOT depend on a vendor SDK or a vendor `canonicalizer.*` node. The existing Artemis GET and POST definitions MUST keep compiling and MUST keep their published signatures.

#### Scenario: Token and body match the published vectors
- **WHEN** FakeTransport first POST uses the published fixtures (appkey credential, secret key, path)
- **THEN** the outbound query `token` equals `EDAA4B3136EA75DF9A435C95B920DD9E` and the outbound body equals `{"appkey":"test-ak"}`

#### Scenario: Token is the uppercase MD5 of the wire material
- **WHEN** the PMS request is sent
- **THEN** `token` equals uppercase hex MD5 of URI-path + wire body bytes + secret, and MUST NOT be the lowercase hex digest or any Base64 form

#### Scenario: Deterministic replay keeps token and body
- **WHEN** FakeTransport returns 503 and the replay policy re-sends the PMS request
- **THEN** the second outbound request is a new instance whose body bytes and `token` equal the first send, with no freshness rebuild (the token is a deterministic function of URI path and body)

#### Scenario: MD5 never runs in Flow
- **WHEN** the PMS definition computes the token
- **THEN** the digest is a `hasher.md5` `encoding: hex-upper` node over concat bytes of path + the exact wire body bytes + secret key, the secret key is admitted via sink `HASH`, the appkey is admitted via sink `BODY` on the body producer, and Flow only assigns variables and chooses transitions

#### Scenario: Appkey is a credential, not a literal
- **WHEN** the PMS body is built
- **THEN** the `appkey` field equals the resolved credential `test-ak` and the definition does not contain that material as a plain JSON literal

#### Scenario: SK never appears on the wire
- **WHEN** the PMS request is sent
- **THEN** no query, header, or body field contains the secret key, and DecisionTrace MUST NOT contain SK plaintext

#### Scenario: Unknown vendor PMS node is rejected
- **WHEN** a definition references `canonicalizer.pms-token` or another pipeline node type that is not in the built-in catalog
- **THEN** compile fails with an unknown-node validation code and zero HTTP

### Requirement: Keytop body key matches the published MD5
The system SHALL send the published Keytop POST so that JSON field `key` is the uppercase hexadecimal MD5 of the signed material. That material is the published fields as unescaped `key=value` pairs, sorted by key, joined with `&`, omitting `appId` and `key`, then `&` and the secret. Flow MUST NOT sort or hash. FakeTransport MUST NOT call the public internet.

#### Scenario: Body key matches the published vector
- **WHEN** FakeTransport receives the first Keytop POST
- **THEN** the path is `/api/wec/GetFreeSpaceNum`, the body contains the published fields including `appId`, and JSON field `key` equals `B19F7863ADCC8B5442A757AC7B90F6AC`

#### Scenario: Signed material excludes appId and key
- **WHEN** the Keytop signature is computed
- **THEN** the signed material is `amount=100&freeDetail=[{"code":"","money":100,"time":0,"type":0}]&freeMoney=100&freeTime=0&orderNo=闽C12345&outOrderNo=T20200306124536001&parkId=1000001&payMethod=6&paySource=85d15350778b11e9bbaa506b4b2f6421&payTime=2020-03-06 10:57:22&payType=1006&payableAmount=200&paymentExt={"deviceNo":"123456"}&reqId=5be4e3e6d5704a7d91ccbd9731d970f5&ts=1583744086841&EED96C219E83450A` and `key` MUST NOT be the lowercase hex digest

#### Scenario: Fixed fields replay with the same body
- **WHEN** FakeTransport returns 503 and the replay policy re-sends the Keytop request
- **THEN** the second body bytes equal the first, including the same `key`

### Requirement: Keytop MD5 stays inside the pipeline catalog
The MD5 MUST be `hasher.md5` with encoding `hex-upper`, over `canonicalizer.concat` of a `canonicalizer.sorted-query` result (`encoding: none`, excluding `appId` and `key`) and the secret admitted with sink HASH.

#### Scenario: Catalog nodes produce the digest
- **WHEN** the Keytop definition computes `key`
- **THEN** the digest is that `hasher.md5` node and Flow only assigns variables and chooses transitions

#### Scenario: Unknown vendor Keytop node is rejected
- **WHEN** a definition references `canonicalizer.keytop-sign` or another pipeline node type that is not in the built-in catalog
- **THEN** compile fails with an unknown-node validation code and zero HTTP

### Requirement: Keytop secret stays off the wire
The Keytop secret MUST be absent from the outbound body, headers, and query, and from DecisionTrace and exception messages. Production secrets MUST NOT appear in git. The definition MUST NOT depend on a vendor SDK or a vendor pipeline node.

#### Scenario: Secret never appears on the wire
- **WHEN** the Keytop request is sent
- **THEN** no query, header, or body field contains the secret, and DecisionTrace MUST NOT contain the secret plaintext

### Requirement: Shuyun list signature matches the published SM3
The system SHALL send the published Shuyun POST so that header `signature` is the lowercase hexadecimal SM3 of the UTF-8 concatenation of app id, app secret, timestamp, and nonce with an empty separator. Flow MUST NOT hash. FakeTransport MUST NOT call the public internet.

#### Scenario: First send matches the published vector
- **WHEN** FakeTransport receives the first Shuyun POST
- **THEN** the path is `/sznszhddpt/rest/event/list`, `x-app-id` is `test-app-id`, `timestamp` is `1000`, `nonce` is `n1`, and `signature` equals `bce0b91e9ba1e45aae2ccd3341c7f5bcf0889e2b08d12ed7b3cd6dc9744c05cb`

#### Scenario: Signature is lowercase hex of the four-part string
- **WHEN** the signature is computed for timestamp `1000` and nonce `n1`
- **THEN** the hashed UTF-8 string is `test-app-idtest-app-secret1000n1` and `signature` MUST NOT equal `BCE0B91E9BA1E45AAE2CCD3341C7F5BCF0889E2B08D12ED7B3CD6DC9744C05CB`

#### Scenario: Operation headers are present and unsigned
- **WHEN** the first Shuyun POST is sent
- **THEN** the headers include `operationUserId` `1`, `operationUserName` `idps`, `operationUserCode` `idps`, and `operationAppCode` `jtdn`, and `signature` remains the four-part digest above

### Requirement: Shuyun SM3 stays inside the pipeline catalog
The SM3 digest MUST be `hasher.sm3` with encoding `hex`, over `canonicalizer.concat` with an empty separator whose parts are the app id, the app secret admitted with sink `HASH`, the timestamp, and the nonce.

#### Scenario: Catalog nodes produce the digest
- **WHEN** the Shuyun definition computes `signature`
- **THEN** the digest is that `hasher.sm3` node and Flow only assigns variables and chooses transitions

#### Scenario: Unknown vendor Shuyun node is rejected
- **WHEN** a definition references `hasher.shuyun-sm3` or another pipeline node type that is not in the built-in catalog
- **THEN** compile fails with an unknown-node validation code and zero HTTP

### Requirement: Shuyun retry rebuilds timestamp, nonce, and signature
HTTP 503 MUST select `RETRY_FLOW`. The authentication flow MUST assign a new timestamp and a new nonce and MUST NOT send HTTP. The following business request MUST be a new instance whose `timestamp`, `nonce`, and `signature` differ from the first.

#### Scenario: Second send uses the next timestamp and nonce
- **WHEN** FakeTransport returns 503 and then 200
- **THEN** authentication performs zero HTTP calls, and the second business request has `timestamp` `1001`, `nonce` `n2`, and `signature` `c6261c5b5b0eb59b92993bcb8954aec24e5436e1c50b8246d80765e9733aa882`

#### Scenario: Body bytes stay fixed across the rebuild
- **WHEN** the second business request is sent
- **THEN** its body bytes equal the first body and the request instance is not the first request

### Requirement: Shuyun secret stays off the wire
The Shuyun app secret MUST be absent from the outbound headers, body, and query, and from DecisionTrace and exception messages. Production secrets MUST NOT appear in git. The definition MUST NOT depend on a vendor SDK or a vendor pipeline node.

#### Scenario: Secret never appears on the wire
- **WHEN** the Shuyun request is sent
- **THEN** no query, header, or body field contains `test-app-secret`, and DecisionTrace MUST NOT contain that plaintext

### Requirement: Protocol gaps become spec changes
If a vendor rule cannot be expressed with Canonical YAML plus the built-in pipeline catalog, implementation MUST stop and update OpenSpec rather than adding implicit orchestrator behavior, Groovy, or Java Catalog endpoints.

#### Scenario: Missing node type fails compile
- **WHEN** a definition references a pipeline node type that is not in the built-in catalog
- **THEN** compile fails with an unknown-node validation code and zero HTTP
