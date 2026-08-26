# 测试策略

基础验证包括 `mvn test`、`mvn validate`、`mvn checkstyle:check`、`mvn pmd:check`、`mvn spotbugs:check` 和 `mvn test jacoco:report`。

安全测试覆盖授权 client/redirect/state/session，Token grant/授权码/刷新/claims，DPoP 签名/iat/jti 重放/htm/htu，Cookie 属性，IdP 内部/第三方/回调/重复投递，以及密钥轮换、撤销和回滚。

协议变化联合 Main Shell、Gateway、Basis 和真实测试 IdP 执行端到端验证。
