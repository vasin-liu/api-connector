# ADR-002: 认证引擎与载荷变换

**状态:** Accepted  
**日期:** 2026-06-03  
**决策:** 认证与载荷变换分离；支持 Auth Pipeline；国密为一等公民。

## 上下文

现有 thirdpart 认证模式约 28 类（见 `docs/profile-registry.md`）。单一 `authType` 枚举无法表达美亚（OAuth+零信任）、禹好办（Bearer+签）、CETC（SM4 加密）等组合。

## 决策

1. **AuthEngine** 执行 `auth` 或 `auth.pipeline[]`，输出 `AuthOutcome`（headers/query/body）。
2. **Transform Pipeline**（独立配置）处理 SM4 加密、业务信封，与 Auth 解耦。
3. **AuthProvider SPI** 用于 `custom_spi` 与 L3 厂家插件。
4. **Token 缓存** 按 `code3rd + profile + scope` 键（engine 层实现，P1）。
5. **BouncyCastle** 提供 SM3/SM4。

## SPI 准入

仅当满足：多步登录、厂商 SDK、不可模板化签名、Cookie/WebSocket 专用。

## 实现状态（当前骨架）

- 已实现：`none`、`AuthEngine` pipeline 骨架、`NoneAuthProvider`
- 待实现：P1 Profile 清单；P2 Transform；P3 大华/海康/公服/讯飞

## 后果

- 新增厂家时先查注册表选 L1/L2，避免直接写 SPI。
- UI 需支持 Pipeline 步骤编排（P2）。
