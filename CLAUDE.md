# API Connector (V2.7)

Outbound HTTP **Flow / Protocol Execution Engine** (JDK 21). In-process Runtime via `ApiClient` — not a `system-thirdpart` hub, Fat JAR console, or `/integrations/{code3rd}` proxy.

## Source of truth

| Area | Location |
|------|----------|
| Runtime contract | `docs/design/api-connector-design-v2_7.md` |
| Greenfield specs | `docs/design/v2.7-greenfield/` |
| Behavior specs | `openspec/specs/` |
| Active changes | `openspec/changes/` (archive under `openspec/changes/archive/`) |
| Protocol fixtures | `docs/design/v2.7-protocols/` |
| Platform notes | `api-connector-config/README.md` |
| Human entry | `README.md` |

Workflow: **OpenSpec** (`/opsx-explore`, `/opsx-propose`, `/opsx-apply`, `/opsx-archive`). Do not resurrect GSD `.planning/` trees for this product.

## Modules

- `api-connector-dependencies` — BOM
- `api-connector-parent` — compiler / plugins
- `api-connector-core` — `ApiClient`, snapshot / result (no Spring)
- `api-connector-runtime` — Flow compile/execute, Session, Pipeline, Script
- `api-connector-transport` — HTTP transport
- `api-connector-config` — Definition load + platform notes

## Constraints

- Gaps in Canonical YAML / built-in pipeline catalog → OpenSpec update, not implicit Runtime, Groovy, or Java Catalog
- FakeTransport protocol gates must not call the public internet; no production secrets in git
- Host / Admin UI / legacy Catalog / proxy API are out of Phase 0 Runtime scope

## Build

```powershell
.\mvnw-jdk21.ps1 -B -pl api-connector-core,api-connector-transport,api-connector-runtime,api-connector-config -am test
```

## Historical docs

`docs/README.md`, `docs/DEVELOPER.md`, and `docs/legacy-auth-inventory.md` describe the **pre-V2.7 hub** (deleted 11-module tree). Archaeology only.
