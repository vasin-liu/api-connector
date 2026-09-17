## 1. Platform notes (Open Questions)

- [x] 1.1 Add a CookieStore section to `api-connector-config/README.md` naming `com.suntek.apiconnector.runtime.session.CookieStore` and `java.net.HttpCookie` (Domain/Path defaults, `domainMatches`, Secure, no SameSite) and verify the README names that class
- [x] 1.2 Add a `planId` section to `api-connector-config/README.md` describing `DefinitionNormalizer` → `CanonicalJson.stringify` (sorted object keys) → `PlanId.sha256Hex` and verify it states no clock/random in the hash
- [x] 1.3 Confirm the existing GraalVM section still says standard JDK 21 + polyglot JARs (GraalVM JDK not required) and verify no contradictory “GraalVM JDK required” wording in that README or the rewritten root `README.md`

## 2. Product narrative alignment

- [x] 2.1 Rewrite root `README.md` for the six-module V2.7 engine + in-process `ApiClient` and verify it no longer lists deleted modules (`domain`/`spec`/`auth`/`engine`/`api`/`app`/`ui`) or Fat JAR run instructions for missing `api-connector-app`
- [x] 2.2 Add a superseded banner to `.planning/PROJECT.md` and `.planning/ROADMAP.md` pointing at V2.7 / OpenSpec baseline and verify Phase 4 “next” language is marked historical; do not hand-edit `CLAUDE.md`
- [x] 2.3 Add the same historical banner to `.planning/codebase/STACK.md` and `.planning/codebase/ARCHITECTURE.md` (GSD injects these) and verify both files state they describe the pre-V2.7 hub
- [x] 2.4 Add a historical / pre-V2.7 banner to README-linked `docs/README.md` and `docs/DEVELOPER.md` (no full rewrite) and verify a new reader is not directed to build Vue console or `api-connector-app` as current work

## 3. Verify engine baseline locally

- [x] 3.1 Run `.\mvnw-jdk21.ps1 -B -pl api-connector-core,api-connector-transport,api-connector-runtime,api-connector-config -am test` (Unix: `./mvnw` with `MAVEN_OPTS=--enable-native-access=ALL-UNNAMED`) and verify all tests pass

## 4. Merge to main

- [x] 4.1 Open one PR from `feat/v2.7-greenfield-runtime` (engine + this change’s doc commits) into `main` and verify GitHub shows the PR URL with workflow `v2-7-runtime` attached
- [ ] 4.2 Merge the PR after review and `v2-7-runtime` green, and verify `main` tip contains the greenfield modules and updated root README
- [ ] 4.3 If a later docs-only commit skips path filters, record the last green engine SHA from Actions (do not treat a skipped workflow as failure) and verify that SHA still points at the four engine modules

## 5. Handoff

- [x] 5.1 State in the PR body that the next OpenSpec change is `protocol-validation-wenxin-gaode` (文心 OAuth + 高德交通 HMAC) with no Host/admin in that follow-up, and verify the PR description contains that sentence
