# G2rain IAM

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-437291?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1.1-586069?logo=spring&logoColor=white)](https://spring.io/projects/spring-cloud)
[![Maven](https://img.shields.io/badge/build-Maven-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)

统一**身份认证与授权**的微服务：在浏览器侧提供登录、注册、授权确认等 **Thymeleaf** 页面，在协议侧提供类 **OAuth2** 的授权码换令牌、`/auth/token` 发放 **JWT**（结合 **DPoP** 请求头校验），并通过 **OpenFeign** 调用 **g2rain-basis**（`g2rain-basis-api`）完成通行证、用户、应用、登录令牌等域数据读写。

本项目由 **[谷雨开源](https://g2rain.com)**（G2Rain）社区维护，采用 **Apache License 2.0** 开源协议发布。

---

## 目录

- [功能概览](#功能概览)
- [技术栈](#技术栈)
- [与 g2rain-basis 的关系](#与-g2rain-basis-的关系)
- [主要 HTTP 路径](#主要-http-路径)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [构建与镜像](#构建与镜像)
- [代码质量与测试](#代码质量与测试)
- [参与贡献](#参与贡献)
- [许可证](#许可证)

---

## 功能概览

| 能力 | 说明（以当前代码为准） |
|------|------------------------|
| **授权码流程** | `GET /auth/authorize` 校验会话，未登录跳转登录页；已登录进入授权确认流程；`POST /auth/authorize_selected` 确认后发放授权码并重定向回调。 |
| **令牌端点** | `POST /auth/token`，`TokenGrantType` 支持 `authorization_code`、`refresh_token`、`exchange_token`（切换身份等场景）。 |
| **DPoP** | `ClientDPoPAuthFilter` 对 **`/auth/token`** 请求校验客户端/应用 DPoP 相关头（见 `TokenController` 与过滤器实现）。 |
| **JWT** | 基于 **Nimbus JOSE JWT**；`TokenKeyManager` / `TokenKeyProperties` 管理密钥；`ESAlgorithm` 支持 ES256/ES384/ES512。 |
| **会话** | `AuthService` 等结合 **Redis** 维护会话与登录态（键规则见 `RedisKeyRule`）。 |
| **页面** | **Thymeleaf** 模板（`templates/*.html`），`PageController` 提供 `/auth/register.html` 与 `/auth/{filename}.html` 等入口。 |
| **注册与验证码** | `PassportController`：`POST /auth/passport_register`；`CaptchaController`：`GET /auth/captcha/register`。 |
| **服务发现与配置** | **Nacos** Discovery + Config；除本服务配置外，可选导入 **`g2rain-token-keypair.yml`**（`group=g2rain`）承载令牌密钥等材料。 |
| **可观测性** | **Actuator**（`health`、`info`）；引入 **OpenTelemetry** / Micrometer Tracing（`application.yml` 中默认关闭 OTLP 导出，可按环境打开）。 |

> 说明：本服务**不是** Spring Authorization Server 的默认自动配置形态，而是自研控制器与服务组合实现的授权与发牌流程；对接字段名（如 `clientId`、`redirectUri`）以代码及模板为准。

---

## 技术栈

| 类别 | 说明 |
|------|------|
| 运行时 | Java **25**（`maven-enforcer-plugin` 要求 `[25,)`） |
| 框架 | **Spring Boot** 4.0.5、**Spring Cloud** 2025.1.1、**Nacos**、**OpenFeign** + **LoadBalancer** |
| Web 视图 | **Thymeleaf**、**thymeleaf-layout-dialect** |
| 安全与令牌 | **Nimbus JOSE JWT**、`g2rain-starter-aegis-core` |
| 数据 | **Spring Data Redis**、`g2rain-starter-data-redis` |
| 内部 API | **g2rain-basis-api**（与 basis 服务联调） |
| 其他 | **Lombok**、**Micrometer Tracing**（OTel 桥接） |

---

## 与 g2rain-basis 的关系

本仓库通过 Feign 客户端（如 `PassportClient`、`UserClient`、`ApplicationClient`、`LoginClient`、`LoginTokenClient`）访问 **g2rain-basis** 暴露的 API：**IAM 不直接承载** basis 中的组织/用户/通行证等表模型，而是作为**认证体验与令牌发放**的专用服务，与 basis **分工协作**。

---

## 主要 HTTP 路径

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/auth/authorize` | 授权入口（查询参数含 `clientId`、`redirectUri`、`state` 等） |
| POST | `/auth/authorize_selected` | 用户确认授权后回调 |
| POST | `/auth/login` | 登录提交 |
| GET/POST | `/auth/logout` | 登出 |
| POST | `/auth/token` | 换取/刷新 JWT（需满足 DPoP 等校验） |
| POST | `/auth/passport_register` | 通行证注册 |
| GET | `/auth/captcha/register` | 注册用验证码 |
| GET | `/auth/register.html`、`/auth/{filename}.html` | Thymeleaf 页面渲染 |

---

## 环境要求

- **JDK 25+**
- **Maven 3.9+**（推荐，与质量插件版本匹配）
- **Redis**、**Nacos**
- 可访问的 **g2rain-basis** 服务（与 Feign 配置一致）

---

## 快速开始

```bash
git clone <你的仓库克隆地址>
cd g2rain-iam
mvn clean package -DskipTests
java -jar target/g2rain-iam-1.0.0.jar
```

或使用：

```bash
mvn spring-boot:run
```

默认 HTTP 端口为 **8082**（`SERVER_PORT`，见 `src/main/resources/application.yml`）。版本号以根 `pom.xml` 的 `<version>` 为准（当前为 **1.0.0**）。

---

## 配置说明

| 项 | 说明 |
|----|------|
| `SERVER_PORT` | 默认 **8082** |
| `SPRING_PROFILES_ACTIVE` | 如 `dev` |
| `NACOS_SERVER_ADDR` 及 `SPRING_CLOUD_NACOS_*` | 注册与配置中心；生产环境请用安全凭据 |
| `spring.config.import` | 可选 Nacos：`g2rain-iam.yml`、`g2rain-token-keypair.yml`（见 `application.yml`） |
| Redis | 连接信息通常在 Nacos 或 profile 中补全（本仓库默认 `application.yml` 未展开完整 Redis 块时，以你方配置为准） |
| `g2rain.web.*` | Web 层全局行为开关（与 `g2rain-basis` 等共用约定时对齐） |

**密钥**：JWT 公私钥等敏感配置请放在 **Nacos**（如 `g2rain-token-keypair.yml`）或密钥管理系统，**不要**将真实私钥写入公开仓库示例。

---

## 构建与镜像

- **可执行 Jar**：`mvn clean package`，产物 `target/g2rain-iam-${version}.jar`。
- **Jib**：`pom.xml` 中已配置 `g2rain/g2rain-iam:${project.version}`，基础镜像 `eclipse-temurin:25-jre`；示例：`mvn compile jib:dockerBuild`。
- 仓库根目录另提供 **Dockerfile**、**build.sh**，可按组织规范选用。

---

## 代码质量与测试

`pom.xml` 中已集成（需时手动或在 CI 中执行）：

- **maven-enforcer-plugin**：JDK 版本、依赖上界等
- **checkstyle**（Google 风格）、**PMD**、**SpotBugs**、**JaCoCo**

```bash
mvn test
mvn jacoco:report
# 按需：mvn checkstyle:check pmd:check spotbugs:check enforcer:enforce
```

---

## 参与贡献

欢迎 Issue 与 Pull Request。提交前请保证本地 `mvn test` 与项目约定的静态检查可通过，并在 PR 中说明与安全相关行为的变更。

---

## 许可证

本仓库适用 **Apache License, Version 2.0**，见 [LICENSE](LICENSE)。

```
Copyright © 2025 g2rain.com
```

---

## 链接

- **组织**：谷雨开源（G2Rain）
- **官网**：<https://www.g2rain.com>
- 将上文 `<你的仓库克隆地址>` 替换为实际 Git 托管地址；Issues/Discussions 链接请与托管平台保持一致。
