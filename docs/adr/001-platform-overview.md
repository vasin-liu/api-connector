# ADR-001: ITS 第三方通用对接平台

**状态:** Accepted  
**日期:** 2026-06-03  
**决策:** 在 `D:\Work\99_Code\01_Java\api-connector` 新建 JDK 21 多模块工程，采用 Connector Spec + Auth Profile + 统一代理 API。

## 上下文

ITS 现有 `system-thirdpart` 以「每厂家一套 Client/Controller」扩展，认证协议分散在 50+ 包内。新厂家接入成本高。需在**不绑定旧 thirdpart 代码**前提下，按协议与认证能力重建平台。

## 决策

1. **技术栈:** JDK 21、Spring Boot 4.0.6、Spring Cloud 2025.1.0、JDK HttpClient、虚拟线程（版本见 [DEPENDENCIES.md](../DEPENDENCIES.md)）。
2. **结构:** Maven 多模块（dependencies BOM + domain/spec/auth/engine/api/app），对齐 `traffic-brain-framework` 的 BOM 实践。
3. **扩展策略:** 声明式 Spec 为主（L1/L2），SPI 为辅（L3，控制在 ≤10 个插件）。
4. **对外 API:** `POST /api/v1/integrations/{code3rd}/endpoints/{endpointId}/invoke`（推荐）；`/proxy` 已废弃。
5. **文档:** 全部置于 `api-connector/docs/`。

## 后果

- **正面:** 新厂家以配置为主；认证可复用；与 `ClientInfoDTO` 字段语义对齐。
- **负面:** 需逐步实现 Profile 注册表；Persistence/UI 在 P1/P2。
- **风险:** 复杂厂家（大华、海康）短期内仍依赖 SPI。

## 备选方案

| 方案 | 否决原因 |
|------|----------|
| 继续堆 thirdpart Client | 无法快速配置化 |
| 仅网关 Kong/APISIX | 国内私有签名支持弱 |
| 全量 Low-code | 可控性与国密支持不足 |
