# 部署

## 构建、镜像与部署边界

本地 Jar 构建使用 `mvn clean package`；本地容器构建使用 `mvn compile jib:dockerBuild`。二者均由根 `pom.xml` 的 Jib 配置确定运行时基础镜像 `eclipse-temurin:25.0.4.1_1-jre`、入口 `com.g2rain.iam.Application` 和容器端口 `8082`。Dockerfile 保留为手工部署兼容入口，并使用同一基础镜像、入口和端口；GHCR 发布不使用它。

GHCR 镜像为 `ghcr.io/g2rain/g2rain-iam`。CI 只会在 `main` 历史中的稳定标签（`vX.Y.Z`）触发，执行 Maven 构建与测试后以 Jib 推送 `<版本>` 与 `sha-<完整提交 SHA>`；不推送 `latest`、不创建 GitHub Release、也不部署。OCI 标签包含 source、Apache-2.0 license、title、version 和 description。

本仓库是单模块可执行服务，没有独立 API JAR、Startup 模块或 Reactor 父 POM，因此不发布 `g2rain-iam` API 到 GitHub Maven Packages，也不配置 `publish-maven.yml`。`mvn -pl <API模块> -am` 在本仓没有有效模块可选。构建依赖 `g2rain-common:1.0.9`、`g2rain-basis-api:1.0.0`、`g2rain-member-api:1.0.0` 及若干 g2rain starter；维护者需逐一确认这些外部坐标及其父 POM 是否在干净 CI 可访问的仓库发布。仅有本地 Maven 缓存不代表 CI 可解析，`packages: write` 也不授予跨仓库读取。若依赖来自 GitHub Packages，应在镜像工作流配置准确的仓库 URL、匹配 server ID 和读取权限；当前仓库 POM 未声明其来源，发布前必须确定来源并补齐。

首次成功推送后，在 GitHub Packages 的 **Package settings → Change visibility → Public** 将包设为公开；若组织管理员禁止修改，请联系管理员。已有同名 GHCR 包出现 `denied` 时，先检查 **Package settings → Actions access** 是否已授予 `g2rain/g2rain-iam` 写权限，以及组织 Packages 策略；`packages: write` 本身不能绕过这些设置。

匿名拉取仅在包已公开后可用：`docker pull ghcr.io/g2rain/g2rain-iam:1.0.0`。这不是在线演示，也不代表实际部署验证通过。

镜像含 Jib 生成的可执行应用层及编译后的 `src/main/resources`（包括页面模板、banner 和日志配置），不把源工作区整体复制进镜像。`application.yml` 已跟踪且含开发默认 Nacos 地址、用户名与密码，以及默认 `BASE_URL`；这些配置会进入 Jar/镜像，不能视为生产 Secret。生产环境必须通过 Nacos、Secret 管理和环境变量注入 Token 私钥、IdP Secret、回调 Token/AES Key、凭据加密 Key 和生产连接信息；维护者须在首次生产发布前评估并替换这些已跟踪默认值。

应用默认监听 `8082`；部署清单必须显式设置 `SERVER_PORT` 并正确映射探针和 Service。

发布前准备 Nacos/Secret、Redis、Basis、Gateway 和回调域名；部署后验证健康、登录、授权、Token、注销和真实 IdP。密钥轮换与普通镜像发布分开执行，并确保回滚版本能读取当前密钥和会话格式。
