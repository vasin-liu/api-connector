# api-connector V2.7 绿场 Runtime 规格包

> 前提：**完全按** [`../api-connector-design-v2_7.md`](../api-connector-design-v2_7.md) 实现；**不考虑** 历史版本兼容性与旧代码复用。  
> 性质：编码前合同（评估 + Canonical 草稿 + 求值表 + 编译产物），**不是** 现有仓库的演进计划。  
> 日期：2026-09-14

## 结论摘要

按 V2.7 从零做 Phase 0 Runtime **技术上成立**，而且比在现有 11 模块仓库上「演进」更干净。但这不是同产品重写，而是换产品：

| 当前仓库 | V2.7 |
|---|---|
| 第三方接入中枢 + 管理台 + 代理 API | 纯 Outbound Flow / Protocol 执行引擎 |

正确路径：**新引擎按 V2.7 做 Phase 0**；现有中枢不当改造对象。

## 阅读顺序

| 序号 | 文件 | 内容 |
|---|---|---|
| 0 | [00-rewrite-evaluation.md](00-rewrite-evaluation.md) | 绿场重写评估、风险、工作量 |
| 1 | [01-encoding-prerequisites.md](01-encoding-prerequisites.md) | 编码前附件 A1–A3、预研门禁 |
| 2 | [02-phase0-increments.md](02-phase0-increments.md) | Phase 0 拆成 0a–0d |
| 3 | [03-canonical-yaml-mock-a-c.md](03-canonical-yaml-mock-a-c.md) | Mock A–C Canonical YAML 草稿 |
| 4 | [04-java-api-0a.md](04-java-api-0a.md) | 0a Java 类型与 ApiClient 草图 |
| 5 | [05-condition-transition-tables.md](05-condition-transition-tables.md) | Condition / Transition 求值表 |
| 6 | [06-attempt-sequences.md](06-attempt-sequences.md) | Mock B/C attempt 时序与 ID 约束 |
| 7 | [07-plan-compiler.md](07-plan-compiler.md) | PlanCompiler 产物、Validate 拒绝表 |
| 8 | [08-canonical-yaml-mock-d-i.md](08-canonical-yaml-mock-d-i.md) | Mock D/E/H/I YAML；F/G 复用说明 |

规格栈对应关系：A1–A3 → 0a–0d → YAML/Java → 求值表/时序 → Compiler。

## 开工最小闭环（建议）

1. 冻结 A1 schema 能表达 Mock A–I（A–C 见 03，D/E/H/I 见 08，F/G 复用）。  
2. 冻结 A2 `ApiClient` / `ExecutionResult` 与 A3 错误码。  
3. JSONPath / GraalVM / Cookie 三个 spike，结论写回 A1。  
4. 第一批测试：`TransitionEvaluator`（表 J2/J3）→ `DefinitionValidator`（表 U）→ `PlanCompiler(Mock A)` → 0a FakeTransport（A1/A2/A4）。

## 尚未落盘（有意留到 0b/0c 开工前）

- 内置 Pipeline 节点目录（每个 node 的 port、config、允许的 sink）——0c 前必须有。  
- SessionCoordinator 状态机表（CREATED/REFRESHING/VALID/… × 并发事件）——0b 前必须有。

## 与源设计的关系

本文档包**不替代** `api-connector-design-v2_7.md`。源设计是 Runtime Contract；本包把合同落到可测的 YAML、Java API、求值表和拒绝码。冲突时以源设计的原则为准，以本包的表为准做测试期望；若表与原则冲突，先改本包并记录。
