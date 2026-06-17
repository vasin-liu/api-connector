---
last_mapped_commit: 9b5f5ce02dc1eb222a9a437d18f44861be53ccb6
last_mapped_at: 2026-06-17
---
# External Integrations

**Analysis Date:** 2026-06-17

## APIs & External Services

**Built-in vendor connectors (outbound HTTP):**

Registered in `api-connector-connectors/src/main/java/com/suntek/apiconnector/connectors/BuiltinConnectorCatalogs.java`:

| code3rd | Vendor | Base URL | Auth profile |
|---------|--------|----------|--------------|
| `IDPS` | IDPS traffic brain | `https://idps.example.com` | `aksk_hmac_sha256_v1` |
| `GAODE_OPEN_PLATFORM` | Amap REST | `https://restapi.amap.com` | `api_key_query` |
| `GAODE_TRAFFIC` | Amap traffic | `https://et-api.amap.com` | `gaode_traffic_hmac_v1` |
| `BAIDU_MAP` | Baidu Maps | `https://api.map.baidu.com` | `api_key_query` |
| `BAIDU_WENXIN` | Baidu Wenxin / ERNIE | `https://aip.baidubce.com` | `oauth2_token_in_query` |
| `DEMO_NONE` | httpbin.org (demo) | `https://httpbin.org` | `none` |
| `DEMO_AKSK` | httpbin.org (demo) | `https://httpbin.org` | `aksk_hmac_sha256_v1` |

- Alias: `BaiduGpt` → `BAIDU_WENXIN` in `BuiltinConnectorCatalogs.java`
- HTTP outbound via JDK `HttpClient` in `JdkHttpTransport.java` — no Feign/WebClient

**Runtime invoke API (this platform exposes):**
- `POST /api/v1/integrations/{code3rd}/endpoints/{endpointId}/invoke` — `IntegrationProxyController.java`
- Streaming variant: `POST .../endpoints/{endpointId}/stream` — same controller
- Legacy aliases: `/{code3rd}/invoke`, deprecated `/{code3rd}/proxy`

**Admin BFF API:**
- `/api/v1/admin/connectors` — CRUD, publish — `IntegrationAdminController.java`
- `/api/v1/admin/sync` — JDBC sync trigger — `IntegrationSyncController.java`
- Console metadata — `ConsoleInfoController.java`

**Legacy thirdpart URL compat (inbound):**
- `LegacyCompatFilter.java` maps old prefixes when `integration.legacy.enabled: true`:
  - `/idps` → `IDPS`
  - `/gaode/traffic` → `GAODE_TRAFFIC`
  - `/gaode` → `GAODE_OPEN_PLATFORM`
  - `/baiduJiaotong` → `BAIDU_MAP`
  - `/BaiduGpt` → `BAIDU_WENXIN`

## Data Storage

**Databases:**
- H2 (embedded file, default) — `spring.datasource.url: jdbc:h2:file:./data/integration` in `application.yml`
- MySQL / PostgreSQL — Supported by changing `spring.datasource.*` per `docs/CONNECTOR-PERSISTENCE.md` (driver not bundled; add at deploy time)
- Schema: `api-connector-persistence/src/main/resources/db/schema.sql`
  - `IT_CONNECTOR_CLIENT` — credentials, host, proxy settings
  - `IT_CONNECTOR_SPEC` — `SPEC_JSON`, publish status
- Client: Spring JDBC via `JdbcConnectorConfigStore.java`
- Migrations: SQL schema file applied on startup (no Flyway/Liquibase)

**File Storage:**
- None — connector specs stored in DB (`SPEC_JSON`) or Java Catalog interfaces

**Caching:**
- In-memory OAuth token cache per `code3rd` — `OAuth2ClientCredentialsAuthProvider.java`, `OAuth2TokenInQueryAuthProvider.java`
- In-memory `ConnectorRegistry` — `ConnectorRegistry.java`
- No Redis or distributed cache

## Authentication & Identity

**Platform inbound auth (optional):**
- Spring Security + custom API key filter — `IntegrationSecurityConfiguration.java`, `IntegrationApiKeyFilter.java`
- Header: `X-Integration-Api-Key` (configurable via `integration.security.api-key-header`)
- Key lists: `runtime-api-keys`, `admin-api-keys` in `application.yml`
- Default: **disabled** (`integration.security.enabled: false`); enabled in `application-prod.yml`

**Outbound auth profiles (implemented):**
Wired in `IntegrationEngineConfiguration.java`:

| Profile | Implementation |
|---------|----------------|
| `none` | `NoneAuthProvider.java` |
| `aksk_hmac_sha256_v1` | `AkskHmacSha256V1AuthProvider.java` |
| `api_key_query` | `ApiKeyQueryAuthProvider.java` |
| `bearer_static` | `BearerStaticAuthProvider.java` |
| `oauth2_client_credentials` | `OAuth2ClientCredentialsAuthProvider.java` |
| `oauth2_token_in_query` | `OAuth2TokenInQueryAuthProvider.java` |
| `gaode_traffic_hmac_v1` | `GaodeTrafficHmacAuthProvider.java` |

- Full planned registry (~25+ profiles): `docs/profile-registry.md` — most not yet implemented
- Credentials injected at runtime via env vars or JDBC store, separate from ConnectorSpec

## Monitoring & Observability

**Health & metrics:**
- Spring Boot Actuator — `health`, `info`, `metrics` exposed in `application.yml`
- Invoke audit logging — `InvokeAuditLogger.java` (`integration.invoke.audit` logger)

**Analytics:**
- None

**Logs:**
- SLF4J stdout (default Spring Boot logging)
- Structured audit: `integration.invoke.audit` key=value format
- Planned: Micrometer/OTLP per `docs/ARCHITECTURE.md` §6

## CI/CD & Deployment

**Hosting:**
- Single fat JAR deployment — `api-connector-app` Spring Boot repackage
- Vue console bundled as static resources at `/console/`

**CI Pipeline:**
- No `.github/workflows/` found in repository
- Local verify: `.\mvnw-jdk21.ps1 clean verify`

## Environment Configuration

**Development:**
- Required: JDK 21; optional per-connector `{CODE3RD}_APP_ID` / `_APP_SECRET` env vars
- Default H2 file DB at `./data/integration` (username `sa`, empty password)
- Security disabled by default; legacy compat enabled
- Vue dev server proxies to `:19090` via `vite.config.js`

**Staging/Production:**
- Set `integration.security.enabled: true` and configure API keys
- Replace H2 with MySQL/PostgreSQL datasource
- Rate limit example: `rate-limit-per-code3rd-per-minute: 120` in `application-prod.yml`
- Override vendor base URLs via `{CODE3RD}_BASE_URL` env vars

## Webhooks & Callbacks

**Incoming:**
- None — platform is request/response proxy, not webhook receiver

**Outgoing:**
- All vendor communication is synchronous HTTP (or streaming HTTP) initiated by invoke API
- No message queue or async callback infrastructure

## Connector Spec Sources

**Composite mode (default):**
1. Java Catalog interfaces — `api-connector-connectors/` (primary, production)
2. Classpath YAML — `classpath:connectors/*.yaml` via `ConnectorSpecLoader.java` (no YAML files currently in repo)
3. JDBC published configs — overrides same `code3rd` from DB

**Persistence sync:**
- Startup sync: `integration.persistence.sync-on-startup: true`
- Periodic sync: `sync-interval-ms: 0` (disabled); job in `ConnectorConfigPeriodicSyncJob.java`

---

*Integration audit: 2026-06-17*
*Update when adding/removing external services*
