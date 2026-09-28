# g2rain-iam 文档

本目录是 IAM 项目事实、Agent 工作规则和安全治理入口。中央平台身份与测试验证固定在 g2rain 架构库提交 `ec69e77`。

## 导航

- [项目元数据](project.yaml)
- [架构概览](architecture/overview.md)
- [模块与职责](architecture/modules.md)
- [依赖与协作](architecture/dependencies.md)
- [运行流程](architecture/runtime-flows.md)
- [已知偏差](architecture/deviations.md)
- [本地开发](development/local-development.md)
- [代码约定](development/code-conventions.md)
- [测试策略](development/testing.md)
- [完成定义](development/definition-of-done.md)
- [配置](operations/configuration.md)
- [部署](operations/deployment.md)
- [排障](operations/troubleshooting.md)
- [安全边界](security/security-boundaries.md)
- [网关接入手册](gateway-integration-guide.md)
- [需求入口](requirements/README.md)

## 设计资料

- [企业微信能力地图（`/auth/wecom`）](design/wecom-capability-map.md)
- [IdP 员工扫码与租户开通闸门](design/idp-employee-login-tenant-gate.md)
- [企业微信扫码登录](design/wecom-qr-login.md)
- [企业微信客服回调认证升级](design/wecom-customer-service-callback-verification.md)
- [Token 签发双模式对齐（MEMBER 应用上下文）](design/member-token-issuance-alignment.md)（已完成实施）
- [应用授权确认（OAuth 统一确认）](design/open-platform-oauth-upgrade.md)（已实施：有 applicationCode 须确认并开通 SELF；无则原发码逻辑）
- [IAM 页面与授权流程解耦升级方案](design/iam-page-flow-upgrade.md)（已收敛：站内页面只认 `tid`）

设计文档不自动等于开发指令；是否实施由 `aiCoding.activeRequirement` 或唯一 `开发中` 需求决定。
