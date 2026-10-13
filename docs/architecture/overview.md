# 架构概览

`g2rain-iam` 是 G2rain 平台唯一身份安全服务，向浏览器和可信客户端提供登录、授权码、Token、会话及 IdP 接入，并通过 Basis API 使用 Passport、用户、应用和绑定事实。

```text
Main Shell / Client
  → 登录与授权
  → IAM 会话、授权码、Token、DPoP
  → Basis 主数据与绑定事实
  → Gateway / 领域服务执行业务鉴权
```

IAM 包含 REST/HTML Controller、认证编排、Token/会话服务、IdP 适配器、Feign 客户端和 Redis/Nacos 基础设施。它不是普通 CRUD 领域服务，也不是研发期工具。
