---
last_mapped_commit: 9b5f5ce02dc1eb222a9a437d18f44861be53ccb6
last_mapped_at: 2026-06-17
---
# Technology Stack

**Analysis Date:** 2026-06-17

## Languages

**Primary:**
- Java 21 — All application code across 11 Maven modules (`pom.xml`, `api-connector-parent/pom.xml`)

**Secondary:**
- JavaScript / Vue 3 — Admin console in `api-connector-ui/frontend/`
- SQL — Schema in `api-connector-persistence/src/main/resources/db/schema.sql`
- YAML — Optional connector specs via `ConnectorSpecLoader` (`api-connector-spec/`)

## Runtime

**Environment:**
- JDK 21 (Zulu recommended) — `mvnw-jdk21.ps1` pins `JAVA_HOME` and delegates to `mvnw.cmd`
- Spring Boot 4.0.6 — Runnable fat JAR from `api-connector-app`
- Virtual threads enabled — `spring.threads.virtual.enabled: true` in `api-connector-app/src/main/resources/application.yml`
- Default HTTP port: `19090`

**Package Manager:**
- Maven 3.9.11 — `.mvn/wrapper/maven-wrapper.properties`
- npm 10.8.2 / Node v20.18.1 — `api-connector-ui/pom.xml` via `frontend-maven-plugin`
- Lockfile: `api-connector-ui/frontend/package-lock.json`

## Frameworks

**Core:**
- Spring Boot 4.0.6 — Web, JSON, Validation, Actuator, Security, JDBC (`api-connector-dependencies/pom.xml`)
- Spring Cloud 2025.1.0 (Oakwood) — BOM imported only; no Spring Cloud starters in use yet
- Vue 3.5.13 + Vue Router 4.5.0 — `api-connector-ui/frontend/package.json`
- Vite 6.0.5 — `api-connector-ui/frontend/vite.config.js` (`base: '/console/'`)

**Testing:**
- JUnit 5 (Jupiter) — All `*Test.java` files
- Spring Boot Test — `api-connector-app`, `api-connector-api` integration tests
- WireMock 3.13.1 (standalone, test scope) — `api-connector-app/pom.xml`
- AssertJ — Used alongside JUnit in spec and connector tests

**Build/Dev:**
- Maven Wrapper (`mvnw`, `mvnw.cmd`, `mvnw-jdk21.ps1`)
- flatten-maven-plugin 1.7.3 — CI-friendly `${revision}` versioning in root `pom.xml`
- frontend-maven-plugin 1.15.1 — Builds Vue assets into `api-connector-ui` JAR
- spring-boot-maven-plugin 4.0.6 — Fat JAR repackage in `api-connector-app`
- springdoc-openapi 3.0.3 — OpenAPI/Swagger UI at `/v3/api-docs`, `/swagger-ui.html`

## Key Dependencies

**Critical:**
- SnakeYAML 2.4 — Connector spec YAML parsing (`api-connector-spec/pom.xml`)
- Jayway JsonPath 2.10.0 — Response evaluation (`api-connector-engine/pom.xml`)
- BouncyCastle bcprov-jdk18on 1.80 — Gaode traffic HMAC and planned 国密 profiles (`api-connector-auth/pom.xml`)
- Jackson databind — JSON serialization in auth and persistence modules
- Lombok 1.18.44 — API DTOs (`api-connector-dependencies/pom.xml`)

**Infrastructure:**
- JDK HttpClient — Default HTTP transport via `JdkHttpTransport` (`api-connector-engine/.../transport/JdkHttpTransport.java`); no Feign/WebClient
- H2 Database (runtime) — Embedded file DB default in `application.yml`
- Spring JDBC — `JdbcConnectorConfigStore` in `api-connector-persistence`

## Configuration

**Environment:**
- `application.yml` — Core app config in `api-connector-app/src/main/resources/`
- `application-prod.yml` — Production overrides (security enabled, rate limits)
- Per-connector env vars: `{CODE3RD}_APP_ID`, `{CODE3RD}_APP_SECRET`, `{CODE3RD}_PUBLIC_KEY`, `{CODE3RD}_BASE_URL` — `ConnectorBootstrapConfiguration.java`
- `integration.persistence.mode` — `classpath` | `memory` | `jdbc` | `composite` (default)
- `integration.security.*` — Optional API key auth (`integration.security.enabled`, key lists)
- `integration.invoke.*` — Platform HTTP status mapping, audit, rate limits

**Build:**
- `api-connector-dependencies/pom.xml` — Central BOM for all dependency versions
- `api-connector-parent/pom.xml` — Compiler (`release 21`), Surefire, resource plugin conventions
- `.mvn/jvm.config` — `--enable-native-access=ALL-UNNAMED`
- `skip.ui` property — Skip Vue build in `api-connector-ui/pom.xml`

## Platform Requirements

**Development:**
- Windows/Linux/macOS with JDK 21
- Node.js 20.x for UI development (or let Maven `frontend-maven-plugin` install it)
- Build: `.\mvnw-jdk21.ps1 clean verify` (with tests) or `.\mvnw-jdk21.ps1 clean package -DskipTests`

**Production:**
- JDK 21 runtime
- Deployable as single Spring Boot fat JAR (`api-connector-app`)
- Default embedded H2 suitable for dev only; production should use MySQL/PostgreSQL via `spring.datasource.*` (driver added at deploy time)
- Vue console served as static assets from `/console/` inside the same JAR

---

*Stack analysis: 2026-06-17*
*Update after major dependency changes*
