# Spec Delta

## ADDED Requirements

### Requirement: Dahua TCPL login password decrypts to the published digest
The system SHALL GET `/jg/AuthPlatform/accounts/publicKey` and then POST `/jg/AuthPlatform/accounts/login`. The login `password` field MUST be unchunked Base64 of RSA/ECB/PKCS1Padding over the UTF-8 of `s2`. `s1` MUST be the lowercase MD5 of the password, and `s2` MUST be the lowercase MD5 of `userName` concatenated with `s1`. Flow MUST NOT hash or encrypt. FakeTransport MUST NOT call the public internet.

#### Scenario: Login password decrypts to s2
- **WHEN** FakeTransport returns the published SPKI and `subjectSessionId` of `test-session-id`, and the credentials are `test-user` / `test-pass`
- **THEN** `s1` is `380e5dc89564f30713ad54bf06aacea8`, `s2` is `cb2cb0b9f11b040b2a169e768d266db8`, the login path is `/jg/AuthPlatform/accounts/login`, `Content-Type` is `application/json;charset=UTF-8`, header `X-Subject-SessionId` is `test-session-id`, and the JSON fields in order are `userName`, `password`, `publicKey`, `subjectSessionId` with `userName` `test-user`, `publicKey` equal to `MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxHBkVG6VHkLn9MZyY+/2agpxbCtJpyCo9K1tVVNXLGDS1Wtx6UyR66nsCUyJiC4pYxu4JDWm4/LsccBhupazJbIA78NV2tO1201dfJaRBfYekyQHjDONEKku6ns4c8pN4qIdUU/GwKlffWA2T3L3znAvJWozMXTy+ojiTNTrgWZDLGPr2Tg/AH6NiGGjFpQIaF/iozoU09YqNDz1/LHnPgtJsFOYI3eswvB0J6CCudjWA5jTxQ1khSqvlO5ZRI5EUogkgmb5FedMT9EJQwu7L5c99jGo2yvPwVKNVYe1Q9YslRZEpLD4W4VQNICNPt3JojQ+wMqhpWKu1uCNHZTquQIDAQAB`, and `subjectSessionId` `test-session-id`
- **THEN** Base64-decoding `password` and decrypting with RSA/ECB/PKCS1Padding and PKCS#8 private key `MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDEcGRUbpUeQuf0xnJj7/ZqCnFsK0mnIKj0rW1VU1csYNLVa3HpTJHrqewJTImILiljG7gkNabj8uxxwGG6lrMlsgDvw1Xa07XbTV18lpEF9h6TJAeMM40QqS7qezhzyk3ioh1RT8bAqV99YDZPcvfOcC8lajMxdPL6iOJM1OuBZkMsY+vZOD8Afo2IYaMWlAhoX+KjOhTT1io0PPX8sec+C0mwU5gjd6zC8HQnoIK52NYDmNPFDWSFKq+U7llEjkRSiCSCZvkV50xP0QlDC7svlz32MajbK8/BUo1Vh7VD1iyVFkSksPhbhVA0gI0+3cmiND7AyqGlYq7W4I0dlOq5AgMBAAECggEAQW+LDFEV3VG+QNyGKGI7/FDkPXsh+sk3XuIShJTRehf6w1saQ/Pcu9sLpIFZYwK1iK0oJu+0W0SFnNLP+z7OCGWGSFgSVmWls5rO5HynbIvy3WLoHLzjZcRlu3g2cuPWFAOaroZbl7wT8gjFrquGfr8CBX2d9gCWG/zTHXLjzGN135ldMrxboDGp3iG2njHBOdX0/OJGAk8aj5n2xHx5CtTr27lCnp1s+Pxhzmjr+2BE1G+2afaem++V2gMTAgnWleuterL1q/UOQkseqlN+lxU7byhHlollnFNv/M+LIJnEDtTV9MvzSQCw78z69fOoJawuxKyPGYqqD4rKxi3XPQKBgQDhIlm8xCdNN+aZLZwdthZRn/FG5Rn7KRKYIRDB2eTk/oUlmDDpnHdAtjhXL6HcpLvWsWTE16aKDUJQT0eiFhr32NKveCwXZ6Mfqk4yZgO1IPjEuUaBlNkQHSTn11wXt7jjy2PyQSxNDn9JhdiRbpYS635MdsevmEOewEzexQbJewKBgQDfXum6PSQB5qUiYh4C/d4Hs0CAhwgP82JuJgXLk+NIZ2TyctyQuF6UGjZQylLLCm1zN430ee5bguqjSki3VSNCKLMPcAiqIi92hN84gRgaAjztTT7BFLV1x5pz1BV0ArSGNzkJp2daIYfKqRlMkAwSZh9rrtTl/stdOOnj4KIkWwKBgDyDzzs+PpIzBHkIGX6jAzzNkT1NN76bO7RIrwhsWWp0P7XrQrqIpEVwtZQ56Xg1j4gWBtAiZ1GUXs7XgJnA917HmVoxIyjnYqGQVZSWd0iopDW814bdS7tZGiJ3cPmvGLOiaCsl+ZJ84xoQMDZnU0+inUBQbTx14Tc62W7H5xshAoGAGg2b/Vj8Rix8vlHrZjXuG7/Qmi0lx5Z/uJE+w8vwCpXktFKpX0fh4CxegXXXyk7hDTtBl25RWuMeMwWLDbckcacFm5tZp15tYsmEr7Z+9SmlqZgE5chgkehCH5hxGCLoo271fCbGa0DV8dFfXgq/HbZDm++8bUcWuTzWKvocb1kCgYEAkFxsYjhpIYpWX6uO9xK5Hu40Ra8mYd28OA2xjh2e4tRRmIAngnDkCTnb82tI+OQwbH1ze5mDbGvLu3DQ6JwBVDNToVphkFBtRFO1g239nZ1X0RNbSJ8ns1mK2JvAxrkCEDFunlxnTqV0FRJE05bPGwaH8DwGq2MvX3xg2IDA3+Q=` yields the UTF-8 of `s2`

#### Scenario: Two logins do not share a ciphertext
- **WHEN** authentication runs twice with the same plaintext `s2` and the same public key
- **THEN** each `password` decrypts to the same `s2`, and the two `password` strings are not required to be equal

### Requirement: Dahua TCPL RSA stays inside the pipeline catalog
The login ciphertext MUST come from `cipher.rsa-pkcs1` with encoding `base64`. Its bytes input MUST be a concat of the `s2` hex. Its key MUST be a concat of the extracted SPKI Base64 and MUST NOT be a `secretRef`. Nested MD5 MUST be `canonicalizer.concat` plus `hasher.md5`. Flow MUST only assign, extract, and choose transitions.

#### Scenario: Catalog nodes produce the ciphertext
- **WHEN** the Dahua TCPL definition builds the login password
- **THEN** the ciphertext comes from `cipher.rsa-pkcs1` and the digests come from `hasher.md5`

#### Scenario: Unknown vendor RSA node is rejected
- **WHEN** a definition references `cipher.dahua-rsa` or another pipeline node type that is not in the built-in catalog
- **THEN** compile fails with an unknown-node validation code and zero HTTP

### Requirement: Dahua TCPL password stays off the wire
The password secret MUST be absent from outbound headers, body, and query, and from DecisionTrace and exception messages. The username MUST be absent from DecisionTrace and exception messages and MUST appear as the login JSON field `userName`. Production secrets MUST NOT appear in git. The definition MUST NOT depend on a vendor SDK or a vendor pipeline node.

#### Scenario: Password never appears on the wire
- **WHEN** the Dahua TCPL login is sent
- **THEN** no query, header, or body field contains `test-pass`, DecisionTrace MUST NOT contain `test-pass` or `test-user`, and the body field `userName` is `test-user`

### Requirement: Dahua TCPL session replays the subject token
A business GET without a token MUST authenticate, store `$.token` as `session.token`, and replay with header `X-Subject-Token` and sink `HEADER`. A later execute with a valid session MUST NOT call the public-key or login paths. A public-key response without `publicKey` MUST fail the session and MUST NOT storm login.

#### Scenario: First business 401 then public key, login, and token replay
- **WHEN** a Dahua TCPL business GET to `/jg/tcpl/stub` is sent without a session token and FakeTransport returns 401
- **THEN** authentication performs exactly one public-key GET and one login POST, the login response token is `test-token`, generation increments, and the replayed business request includes `X-Subject-Token` equal to `test-token`

#### Scenario: Valid session skips public key and login
- **WHEN** a second execution reuses a still-valid Dahua TCPL session
- **THEN** FakeTransport records zero public-key calls, zero login calls, and one business call that still carries `X-Subject-Token`

#### Scenario: Missing public key does not storm
- **WHEN** the public-key response is HTTP 200 without a `publicKey` field
- **THEN** shared failure plus cooldown prevent a login storm from a follow-up execute during cooldown, and no token is stored
