## ADDED Requirements

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
