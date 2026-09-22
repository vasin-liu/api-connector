## ADDED Requirements

### Requirement: Cookie-only session is sufficient
Authentication SHALL be allowed to mark a session VALID after committing cookies to the session cookie store without also storing a companion token. Outbound business requests that declare cookies from the store MUST send those cookies and MUST NOT be required to send Authorization or a token query parameter.

#### Scenario: Cookie without bearer
- **WHEN** authentication accepts `Set-Cookie` into the store and does not extract a token
- **THEN** the replayed business request includes the store-built `Cookie` header and MUST NOT include `Authorization`
