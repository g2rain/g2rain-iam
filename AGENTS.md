# AGENTS.md

执行评审或开发前，依次读取 `docs/project.yaml`、`docs/architecture/deviations.md`、`docs/security/security-boundaries.md`、当前需求、中央 `platform-services/g2rain-iam.md` 登记，以及相关设计、源码、配置、测试和 Git Diff。

## 强制边界

- IAM 负责认证、授权、Token、会话和 IdP 编排，不拥有 Passport、用户、组织和应用主数据。
- 不把 Main Shell 的前端治理、Gateway 的转发或业务服务的最终领域授权搬入 IAM。
- Token claims、错误码、Cookie、授权码、回调、签名算法和密钥配置属于跨仓库契约。
- 私钥、IdP Secret、Nacos 密码和回调凭据不得进入 Git、日志、异常、测试快照或镜像层。
- 新增 IdP 必须覆盖 state、回调签名、重复回调、绑定和供应商异常。
- 不覆盖用户已有改动；未经需求授权不得改写 `docs/design/wecom-customer-service-callback-verification.md`。

至少执行 `mvn test`。协议、安全或配置变化还要执行适用的集成测试和联调。需求只认 `docs/project.yaml` 的 `aiCoding.activeRequirement` 或唯一 `开发中` 文档；没有或不唯一时停止开发。
