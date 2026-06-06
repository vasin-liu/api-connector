# 依赖版本清单

> 更新时间：2026-06-03  
> 策略：Spring 平台 BOM 对齐最新稳定版；其余库取 Maven Central 当前最新稳定版。

## Spring 平台

| 组件 | 版本 | 说明 |
|------|------|------|
| Spring Boot | **4.0.6** | 当前 4.0.x 稳定线（需 JDK 17+，本项目 JDK 21） |
| Spring Cloud | **2025.1.0** (Oakwood) | 与 Boot 4.0.x 配套 |

兼容性参考：[Spring Cloud 发布列车](https://spring.io/projects/spring-cloud)

## 第三方库

| 组件 | 版本 |
|------|------|
| Lombok | 1.18.38 |
| BouncyCastle bcprov-jdk18on | 1.80 |
| SnakeYAML | 2.4 |
| Jayway JsonPath | 2.10.0 |
| springdoc-openapi (WebMVC UI) | 3.0.3 |

## Maven 插件

| 插件 | 版本 |
|------|------|
| flatten-maven-plugin | 1.7.3 |
| maven-compiler-plugin | 3.14.1 |
| maven-surefire-plugin | 3.5.4 |
| maven-resources-plugin | 3.3.1 |
| spring-boot-maven-plugin | 4.0.6（随 BOM） |

## 升级说明（相对初版骨架）

| 组件 | 原版本 | 现版本 |
|------|--------|--------|
| Spring Boot | 3.3.6 | 4.0.6 |
| Spring Cloud | 2023.0.3 | 2025.1.0 |
| springdoc | 2.6.0 | 3.0.3（Boot 4 需 3.x 线） |
| BouncyCastle | 1.78.1 | 1.80 |
| SnakeYAML | 2.3 | 2.4 |
| JsonPath | 2.9.0 | 2.10.0 |
| Lombok | 1.18.36 | 1.18.38 |
| maven-compiler-plugin | 3.13.0 | 3.14.1 |
| maven-surefire-plugin | 3.5.2 | 3.5.4 |

## 维护

版本集中在 `its-integration-dependencies/pom.xml` 的 `<properties>` 中维护。升级后执行：

```bash
mvn -q clean verify -DskipTests
```
