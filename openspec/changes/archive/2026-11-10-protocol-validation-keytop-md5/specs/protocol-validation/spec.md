# Spec Delta

## ADDED Requirements

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
