# g2rain-iam

## 1. 徽标与状态标识

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-437291?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1.1-586069?logo=spring&logoColor=white)](https://spring.io/projects/spring-cloud)
[![Maven](https://img.shields.io/badge/build-Maven-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)

## 2. 项目简介

`g2rain-iam` 是 G2rain 平台中的统一身份认证、授权与令牌服务，负责承载浏览器侧登录与授权页面、授权码流程、会话管理、令牌签发，以及钉钉等第三方身份接入能力。

## 3. 平台定位

在 G2rain“企业级 AI 原生开源 SaaS 平台”体系中，`g2rain-iam` 位于平台核心服务层，承担统一身份入口与安全令牌中心的角色。

它主要服务以下场景：
- 为主壳、子应用与平台控制台提供统一登录与授权确认入口
- 为前端应用、网关与后端服务提供授权码换令牌、刷新令牌与身份切换能力
- 为平台第三方身份接入场景提供统一会话承载与身份编排能力
- 为平台企业级安全链路提供 DPoP、JWT、公钥校验与会话控制能力

它与 `g2rain-basis`、`g2rain-main-shell`、`g2rain-gateway-webmvc`、`g2rain-gateway-webflux` 等仓库协同，共同构成平台统一身份、统一安全与统一应用接入体系。

## 4. 核心能力

本章回答“这个仓库在平台里提供什么能力、解决什么问题”。

- 浏览器侧认证页面与授权入口：解决登录、注册、授权确认、退出、错误提示等统一认证体验问题，通过 `PageController`、`LoginController`、`AuthorizeController` 与 `templates/*.html` 把浏览器侧认证入口沉淀为平台标准能力。
- 标准授权码与令牌中心能力：解决前端应用、网关或外部接入方如何获得统一身份令牌的问题，通过 `AuthorizationService`、`TokenController`、`TokenService` 提供 `authorization_code`、`refresh_token`、`exchange_token` 三类令牌流程。
- 企业级双重持有证明安全链路：解决“令牌请求是否真的来自合法客户端与合法应用”的问题，通过客户端 DPoP、应用 DPoP、JWT ECDSA 签发、授权码原子消费与公钥绑定校验，提升整个平台身份链路的防重放与防盗用能力。
- 平台统一会话管理能力：解决浏览器登录态与第三方身份登录态的统一托管问题，通过 `SessionService`、`IamSessionCookieService` 与 Redis 托管 Session，让主壳、授权页与第三方登录回调都能回到同一会话模型。
- 第三方身份接入与平台主体绑定：解决钉钉等外部身份如何接入平台并回到统一身份体系的问题，通过 `DingTalkOAuthController`、`DingTalkOAuthService`、`service/idp` 路由层与钉钉适配器把外部身份纳入平台授权码与令牌链路。
- 密钥与签发策略管理：解决 JWT 签发密钥切换、历史兼容与运行期动态刷新的问题，通过 `TokenKeyProperties` 与 `TokenKeyManager` 支持多套 EC 密钥配置、激活密钥切换与 Nacos 动态刷新。

## 5. 技术栈

- 语言与运行时：`Java 25`
- 后端框架：`Spring Boot 4.0.5`、`Spring Cloud 2025.1.1`
- 服务治理：`Nacos Discovery`、`Nacos Config`
- 数据与缓存：`Redis`
- 服务调用：`OpenFeign`、`LoadBalancer`
- 安全与签名：`Nimbus JOSE JWT`、ECDSA、DPoP
- 页面渲染：`Thymeleaf`
- 可观测：`Actuator`、`OpenTelemetry`、`Micrometer Tracing`
- 构建与交付：`Maven`、`Jib`、`Dockerfile`、`build.sh`

## 6. 快速开始

### 环境要求

- `JDK 25`
- `Maven 3.9+`
- 可用的 `Nacos`
- 可用的 `Redis`
- 可访问的 `g2rain-basis` 相关接口服务

### 关键配置

当前仓库的关键运行配置主要来自 `src/main/resources/application.yml` 与 Nacos 配置中心。

| 变量名 | 说明 | 典型用途 |
| --- | --- | --- |
| `SERVER_PORT` | 服务端口 | 默认 `8082` |
| `SPRING_PROFILES_ACTIVE` | 启动环境 | 区分 `dev` 等 profile |
| `NACOS_SERVER_ADDR` | Nacos 地址 | 服务发现与配置中心 |
| `SPRING_CLOUD_NACOS_DISCOVERY_*` | 注册中心认证与命名空间 | 服务注册 |
| `SPRING_CLOUD_NACOS_CONFIG_*` | 配置中心认证与命名空间 | 外部配置拉取 |
| `BASE_URL` | IAM 对外根地址 | 回调、Cookie 安全策略推导 |
| `PLATFORM_BASE_URL` | 平台控制台根地址 | 无显式回跳地址时的默认跳转 |
| `IAM_SESSION_COOKIE_SAME_SITE` | Session Cookie SameSite 策略 | 跨域登录态控制 |
| `IAM_SESSION_COOKIE_MAX_AGE_SECONDS` | Session Cookie 有效期 | 浏览器登录态时长 |
| `IAM_ANONYMOUS_ENABLED` | 是否启用匿名授权 | 匿名授权码开关 |
| `IAM_ANONYMOUS_ORGAN_ID` | 匿名授权组织 | 匿名主体上下文 |
| `IAM_ANONYMOUS_ROLE_IDS` | 匿名授权角色 | 匿名权限上下文 |
| `IAM_DINGTALK_LOGIN_BIND_MODE` | 钉钉绑定模式 | 登录页钉钉入口策略 |
| `DINGTALK_INTERNAL_*` | 内部企业钉钉接入配置 | 内部钉钉登录 |
| `DINGTALK_THIRD_PARTY_*` | 第三方钉钉接入配置 | 第三方钉钉登录 |

建议：
- JWT 密钥、公私钥材料与第三方密钥统一放在 Nacos 或密钥管理系统，不写入仓库示例。
- 生产环境重点验证 `BASE_URL`、`PLATFORM_BASE_URL` 与 `IAM_SESSION_COOKIE_*` 的组合行为。
- 钉钉接入与匿名授权能力依赖外部配置，部署前需确认是否正式启用。

### 本地构建

```bash
mvn clean package -DskipTests
```

### 本地运行

```bash
mvn spring-boot:run
```

或：

```bash
java -jar target/g2rain-iam-1.0.0.jar
```

### 镜像构建

```bash
./build.sh
./build.sh 1.0.0
```

或：

```bash
mvn clean compile jib:dockerBuild -DskipTests=true
```

## 7. 项目结构

本章回答“代码与模块是如何组织的、排查和扩展时应该先看哪里”。

```text
g2rain-iam/
├── Dockerfile
├── build.sh
├── pom.xml
└── src/
    └── main/
        ├── java/com/g2rain/iam/
        │   ├── controller
        │   ├── service
        │   ├── service/idp
        │   ├── client
        │   ├── config
        │   ├── dingtalk
        │   ├── filters
        │   ├── dto / vo
        │   └── enums / utils
        └── resources/
            ├── application.yml
            ├── logback-spring.xml
            ├── templates/
            └── static/
```

### 结构说明

- `controller`：承载登录、授权、Token、页面、验证码、钉钉入口等 HTTP 入口。
- `service`：承载授权码、令牌、会话、认证、页面编排等核心业务实现。
- `service/idp`：承载身份提供方接入抽象与路由能力，适合后续扩展更多 IdP。
- `client`：承载对 `g2rain-basis-api` 的 Feign 调用，是 IAM 与平台业务域协作的主要接口层。
- `config`：承载 WebMVC、Nacos、Redis、OpenAPI、访问参数、令牌密钥等运行配置。
- `dingtalk`：承载钉钉接入适配器与主体信息对象。
- `filters`：承载 `/auth/token` 前置安全校验逻辑，重点关注 `ClientDPoPAuthFilter`。
- `resources/templates`：承载 `login`、`register`、`consent`、`logout`、`error`、`index` 等认证页面模板。
- `Dockerfile` 与 `build.sh`：承载仓库默认交付入口。

### 代码查阅指引

- 查看登录、授权页与授权确认入口时，优先看 `controller/AuthorizeController`、`controller/LoginController`、`service/ModelAndViewService`。
- 查看授权码换 Token、刷新 Token、身份切换时，优先看 `controller/TokenController`、`service/TokenService`。
- 查看 Session 与 Cookie 策略时，优先看 `service/SessionService`、`service/IamSessionCookieService`、`config/IamAccessProperties`。
- 查看客户端 DPoP、应用 DPoP 与 JWT 签发时，优先看 `filters/ClientDPoPAuthFilter`、`service/TokenService`、`service/TokenKeyManager`。
- 查看钉钉接入时，优先看 `controller/DingTalkOAuthController`、`service/DingTalkOAuthService`、`dingtalk/*`、`service/idp/*`。
- 查看外部平台协作接口时，优先看 `client/*`。

## 8. 核心业务流程

本章回答“这些能力在运行时是如何串起来工作的”。

#### 1. 标准登录与授权码主线

- 客户端先访问 `GET /auth/authorize`，携带 `clientId`、`redirectUri`、`state`。
- `AuthorizeController` 先校验必要参数，再检查本地是否已有会话。
- 如果没有会话或会话已过期，`ModelAndViewService` 跳转到 `login.html`。
- 用户登录成功后，`AuthService` 完成认证，`SessionService` 在 Redis 中创建会话，并由 `IamSessionCookieService` 写回浏览器 Cookie。
- 回到授权阶段后，系统根据可用身份数量决定直接发码还是进入 `consent.html`。
- `AuthorizationService` 生成授权码并回调业务应用，形成完整授权码入口主线。

#### 2. 授权码换 Token 与双重持有证明主线

- 客户端调用 `POST /auth/token` 时，`ClientDPoPAuthFilter` 会先校验客户端 `DPoP`。
- `TokenService` 再解析客户端身份、原子消费授权码，并读取登录上下文。
- 系统通过 `ApplicationClient` 获取平台登记的应用公钥，继续校验 `application-DPoP`。
- 在客户端 DPoP 与应用 DPoP 都通过后，`TokenKeyManager` 才会加载激活密钥并签发 JWT。
- 这一主线解决的是“请求来自哪个客户端实例、属于哪个应用、能否安全签发令牌”的企业级安全问题。

#### 3. Refresh / Exchange Token 主线

- Refresh Token 流程会校验旧 Token、客户端公钥绑定关系与刷新时效，再重新签发访问令牌。
- Exchange Token 流程会在同一客户端绑定下切换目标身份，再签发新的 Token。
- 这一主线解决的是多身份切换与令牌续期场景下的安全连续性问题。

#### 4. 会话与浏览器安全主线

- 浏览器侧只持有 `HttpOnly` 的 Session Cookie，真实会话实体保存在 Redis 中。
- Cookie 的 `SameSite`、`Secure` 与有效期策略由 `g2rain.iam.session-cookie.*` 与 `BASE_URL` 协同决定。
- 退出登录时，服务端删除 Redis Session，同时清理浏览器 Cookie。
- 这一主线保证了浏览器认证体验、跨域登录态与服务端会话控制的一致性。

#### 5. 钉钉 OAuth 与第三方身份接入主线

- 前端先发起钉钉授权或扫码引导请求，系统把 OAuth 上下文序列化后写入 Redis state。
- 钉钉回调后，`DingTalkOAuthService` 恢复 state、换取主体信息，并根据 `bindMode` 路由适配器。
- `AuthService.authenticateDingTalk` 最终仍然回到平台统一 Session 模型，写入标准会话 Cookie。
- 然后系统继续回到授权页与授权码流程，而不是绕开平台统一身份链路。

## 9. 常用命令

```bash
mvn clean package
mvn spring-boot:run
mvn test
mvn jacoco:report
mvn checkstyle:check
mvn pmd:check
mvn spotbugs:check
./build.sh
./build.sh 1.0.0
mvn clean compile jib:dockerBuild -DskipTests=true
```

## 10. 质量与测试

- `pom.xml` 已集成 `maven-enforcer-plugin`、`checkstyle`、`pmd`、`spotbugs`、`jacoco`。
- 当前扫描未发现 `src/test/java` 测试源码，说明质量插件已接入，但自动化测试仍需补齐。
- 建议后续优先补齐授权码流程、DPoP 校验、JWT 签发、Session/Cookie 策略、钉钉登录回调等关键链路测试。

## 11. 相关仓库

- `g2rain-basis`：平台应用、资源、角色、权限与主体治理底座
- `g2rain-main-shell`：主壳与统一交互入口
- `g2rain-gateway-webmvc`：网关与接入安全协同实现之一
- `g2rain-gateway-webflux`：网关与接入安全协同实现之一

## 12. 使用建议

- 适合作为平台统一身份认证与令牌服务独立部署，而不是与业务服务混合承载。
- 适合与主壳、子应用、网关、业务服务协同组成完整身份链路。
- 生产环境请将 JWT 密钥、Nacos 凭据、钉钉密钥等敏感配置统一托管在安全配置中心。
- 跨域登录态场景请重点验证 `BASE_URL`、`PLATFORM_BASE_URL` 与 `IAM_SESSION_COOKIE_*` 的组合行为。

## 13. 贡献指南

欢迎通过文档改进、Issue 反馈、测试补充、代码优化、功能增强等形式参与贡献。

建议流程：
1. Fork 本仓库
2. 创建特性分支
3. 提交修改
4. 推送分支
5. 提交 Pull Request

提交前请尽量确保：
- 遵循现有技术栈与代码规范
- 补充必要测试
- 更新相关文档
- 确保测试通过

## 14. 许可证

本项目基于 [Apache 2.0许可证](LICENSE) 开源。

## 15. 联系我们

- **站点**: https://www.g2rain.com/
- **Issues**: [GitHub Issues](https://github.com/g2rain/g2rain/issues)
- **讨论**: [GitHub Discussions](https://github.com/g2rain/g2rain/discussions)
- **邮箱**: g2rain_developer@163.com

## 16. 致谢

感谢所有为这个项目做出贡献的开发者们。

如果这个项目对您有帮助，欢迎 Star 支持。
