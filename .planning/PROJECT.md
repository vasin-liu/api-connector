# API Connector

> **SUPERSEDED (pre-V2.7 hub).** This file describes the former `system-thirdpart` replacement (11-module Spring hub, Groovy, Vue/React console, drop-in URL compat). Current product is the V2.7 outbound Flow Runtime: `docs/design/api-connector-design-v2_7.md`, `openspec/specs/`. Do not treat Phase 4 Admin BFF as next work. `CLAUDE.md` is generated from this file — do not hand-edit `CLAUDE.md`.

## What This Is

API Connector（`api-connector`）是 ITS 平台的第三方 API 集成中枢，用于替代旧模块 `system-thirdpart`。它通过声明式 connector 规格、可插拔认证、数据映射与可视化管理台，将 60+ 厂商/域的第三方 HTTP 接口统一接入并对外暴露。

调用方通过上游 API 网关访问本服务；本服务负责出站第三方认证、请求编排、数据映射与可观测性。目标是**完全兼容**旧模块的 URL、请求/响应格式与错误语义，在全部接口迁移完成后一次性切换，调用方无感知。

## Core Value

**在零感知替换 `system-thirdpart` 的前提下，让任意第三方 API 的接入、认证、映射与运维可通过配置（及必要时的 Groovy 扩展）完成，而无需为每个厂商手写 Controller。**

## Requirements

### Validated

- ✓ 六边形多模块 Maven 架构（domain / spec / auth / engine / api / persistence / connectors / app / ui）— 现有代码库
- ✓ 声明式 ConnectorSpec + Java Catalog 扫描端点定义 — 现有代码库
- ✓ 统一代理调用 API：`POST /api/v1/integrations/{code3rd}/endpoints/{endpointId}/invoke` — 现有代码库
- ✓ AuthProvider SPI 出站认证（none、api_key_query、aksk_hmac_sha256_v1、oauth2 等）— 现有代码库
- ✓ JDBC 持久化 connector 配置与发布同步 — 现有代码库
- ✓ 部分内置厂商 connector（IDPS、高德、百度等）— 现有代码库
- ✓ 旧 URL 前缀兼容过滤器（`LegacyCompatFilter`，可配置启用）— 现有代码库
- ✓ 单端口 Fat JAR 部署，管理台静态资源内嵌于同一服务 — 现有代码库
- ✓ 调用审计日志（`InvokeAuditLogger`）— 现有代码库

### Active

- [ ] 认证模块抽象为独立可插拔插件体系：内置 Java 插件覆盖旧模块实际使用的认证方式，非标准流程支持 Groovy 脚本扩展；认证上下文可供后续出站请求与映射使用
- [ ] 数据映射引擎：支持入参/出参复杂映射、类型转换、嵌套结构变换；支持 Groovy 脚本扩展处理
- [ ] 可视化配置与管理台：独立 `api-connector-ui` 模块，技术栈为 **Vite + React + Ant Design**（替换现有 Vue）；开发与发布时与后端共用同一 HTTP 端口
- [ ] 对外 endpoint 元数据：向 API 网关提供「哪些路径需要/不需要调用方鉴权」的配置能力（调用方鉴权由上游网关执行，本服务不实现消费者认证）
- [ ] 监控能力：调用日志查询、Prometheus 指标、连接器健康检查与告警
- [ ] 完全重写 `system-thirdpart` 全部对外接口（60+ Controller 域），不依赖旧模块任何代码或依赖
- [ ] 对外接口**完全兼容**旧模块：URL 路径、请求/响应结构、错误码语义与调用方行为保持一致
- [ ] 全部旧接口迁移完成后一次性切换，替换旧 `system-thirdpart` 服务

### Out of Scope

- 依赖或复用 `system-thirdpart` 源码及旧 Maven 模块 — 必须独立重写
- 在本服务内实现调用方（消费者）身份认证 — 由上游 API 网关负责
- 继续使用 Vue 作为管理台框架 — 统一迁移至 React
- v1 覆盖所有「理论上的」标准认证协议 — 先覆盖旧模块实际用到的认证方式，其余后续迭代
- 分布式多实例状态共享（OAuth token、registry）— 当前保持单实例内存模型，除非后续里程碑明确要求

## Context

**现状（brownfield）：**
- 新代码库 `api-connector` 已具备六边形架构、部分 connector、AuthProvider SPI、Vue 管理台雏形、H2/MySQL 持久化
- 旧代码库 `D:\Work\99_Code\ITS\suntek-system\system-thirdpart` 为单体 Spring 集成服务，约 60+ `@RestController`，每厂商一套 Client + InvokeService + Controller 模式，难以扩展与统一运维
- 已有 codebase map：`.planning/codebase/`（ARCHITECTURE、STACK、INTEGRATIONS 等）

**目标用户：**
- ITS 平台内部业务系统（原 `system-thirdpart` 的 HTTP 调用方）
- 平台运维与集成开发人员（通过 React 管理台配置 connector、认证、映射、监控）

**迁移约束：**
- **Drop-in 兼容**：URL、契约、错误语义与旧版一致
- **All-at-once 切换**：全部接口在新模块实现并验证后才替换旧服务
- **零旧代码依赖**：新实现仅引用 `api-connector` 自身模块

**技术环境：**
- Java 21、Spring Boot 4、Maven 多模块
- 管理台：Vite + React + Ant Design（替换 Vue 3）
- 脚本扩展：Groovy（与 Java 互操作，用于非标准认证与映射）
- 默认端口 19090；管理台路径 `/console/`（与后端同端口）

## Constraints

- **Tech stack**: Java 21 + Spring Boot 4 后端；React + Ant Design 前端 — 团队指定，Vue 不继续使用
- **Compatibility**: 旧 `system-thirdpart` 对外 HTTP 契约必须完全兼容 — 生产切换硬性要求
- **Migration**: 全部接口就绪后一次性切换 — 不接受长期双轨并行作为终态
- **Auth boundary**: 调用方鉴权由上游 API 网关处理；本服务提供 endpoint 鉴权元数据并专注出站第三方认证
- **Deployment**: 单 Fat JAR、单端口同时提供 API 与管理台 — 延续现有部署模式
- **Independence**: 禁止依赖 `system-thirdpart` 及 suntek-system 旧模块代码

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| 完全替代 `system-thirdpart`，非增量修补 | 旧模块每厂商一套代码，维护成本高，无法支撑插件化与映射 | — Pending |
| 对外接口 drop-in 兼容 | 60+ 调用方无法同步改造 | — Pending |
| 全部迁移后一次性切换 | 避免长期双轨运维与数据不一致 | — Pending |
| 调用方鉴权交给上游 API 网关 | 平台已有统一网关；本服务专注集成编排 | — Pending |
| 内置 Java 认证插件 + Groovy 脚本兜底 | 标准认证性能与可测试性；非标流程灵活扩展 | — Pending |
| 管理台使用 Vite + React + Ant Design | 团队技术栈要求，替换现有 Vue 实现 | — Pending |
| v1 内置认证覆盖旧模块实际用到的类型即可 | 降低首期范围，避免过度设计未使用的协议 | — Pending |
| 监控 v1 包含日志 + Prometheus + 健康检查 | 生产替换旧服务必须具备可观测性 | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-06-17 after initialization*
