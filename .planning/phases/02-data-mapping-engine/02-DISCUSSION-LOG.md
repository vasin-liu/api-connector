# Phase 2: Data Mapping Engine - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-17
**Phase:** 2-data-mapping-engine
**Areas discussed:** 声明式映射 DSL, Groovy 边界, 错误响应映射, Transform 管道范围, Passthrough, JDBC 持久化

---

## 声明式映射 DSL

| 选项 | 描述 | 选中 |
|------|------|------|
| 端点为主 | endpoint 配置 mapping 三块 | |
| 连接器为主 | connector 级规则 | |
| 你来定（auth 对称） | connector 默认 + endpoint 覆盖 | ✓ |

| JSONPath + 操作符 | rename/coerce/nest/array_map/set | ✓ |
| 扁平字段表 | from/to/type | |

| 分块 request/response/error | 三块独立 | ✓ |
| 统一 rules + direction | |

| 发布时校验 | publish/load 拒绝无效规则 | ✓ |
| 运行时宽松 | |

| array_map 操作符 | 数组逐项映射 | ✓ |
| 纯 JSONPath [*] | |

| 缺失宽松 + 类型严格 | 缺字段 null/省略 | ✓ |
| 全严格 | |

| 顺序执行 rules | 后者覆盖 | ✓ |
| 并行合并 | |

| set 写常量 | 支持默认值 | ✓ |
| 仅 Groovy | |

---

## Groovy vs 声明式边界

| 镜像 auth | groovy_mapping_script + endpoint Groovy-only 覆盖 | ✓ |
| 仅 connector script | |

| 按方向互斥 | request/response/error 各自 rules 或 script | ✓ |
| 管道组合 | |

| 丰富绑定 | body + authSnapshot + direction + endpointMeta | ✓ |
| 仅 body | |

| MappingScript 函数式接口 | apply(MappingContext) → Object | ✓ |
| 绑定变量突变 | |

| 按方向分脚本 | requestScript/responseScript/errorScript | ✓ |
| 单脚本分支 | |

| MappingException 结构化 | 镜像 AuthException | ✓ |

---

## 错误响应映射

| HTTP 非 2xx + 业务失败 | 均走 error 映射 | ✓ |
| 仅 body | |

| Legacy 兼容 JSON | system-thirdpart 业务错误外形 | ✓ |
| 平台 + legacy 双层 | |

| connector + endpoint 覆盖 | Claude 定 | ✓ |
| 每 endpoint 独立 | |

| 无 error 映射透传 vendor | | ✓ |
| 通用包装 | |

| successWhen 门控 HTTP 200 业务失败 | | ✓ |

---

## Transform 管道范围

| SPI + SM4 实现，其余 stub | | ✓ |
| 仅 JSON mapping | |
| 含完整 envelope | |

| 请求：mapping → transform → auth | | ✓ |
| transform 先于 mapping | |

| 复用 transform[] | | ✓ |
| 新字段 transformPipeline | |

| 业务信封推迟 Wave 2 | | ✓ |

---

## Passthrough

| 默认 passthrough（无 mapping 块） | | ✓ |
| 显式 mode 字段 | |

| 隐式（空 = passthrough） | | ✓ |

| transform 独立执行 | passthrough 只跳过 JSON mapping | ✓ |
| 全跳过 | |

| 编排层短路 | 不调 MappingEngine | ✓ |

---

## JDBC 持久化

| mapping 嵌入 ConnectorSpec JSON blob | | ✓ |
| 独立 mapping_rules 表 | |

| Catalog + JDBC 并存 | | ✓ |

| 扩展 ConnectorPublishListener | compile + validate + reload | ✓ |

| publish 路径校验 | | ✓ |

---

## Claude's Discretion

- D-01：配置层级（用户选「你来定」→ auth 对称 connector+endpoint）
- D-17：厂商 error 模板组织（用户选「你来定」→ connector 默认 + endpoint 覆盖）

## Deferred Ideas

- 业务信封 transform（Wave 2+）
- MAP-06 管道顺序集成测试（Phase 3）
- Admin mapping 编辑器（Phase 4）
- invokeStream 映射快照（Phase 2/3）
- JOLT 导入（MAP-V2-01）
