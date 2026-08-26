# 安全边界

- 浏览器、客户端请求、回调参数和外部 IdP 响应默认不可信。
- `/internal/**` 仍需网络隔离和应用身份授权。
- Basis 提供主数据，IAM 负责认证编排；Gateway/领域服务执行最终授权。
- 私钥、IdP Secret、回调 Token/AES Key、Nacos 密码和加密 Key 只能安全注入。
- redirect URI 精确匹配；state、nonce、授权码短期且一次性。
- DPoP 校验签名、typ、JWK、iat、jti 防重放、htm 和规范化外部 htu。
- JWT 明确 issuer、audience、算法、kid 和时间 claims，拒绝降级。
- Session Cookie 使用 HttpOnly、Secure、合适 SameSite/Path/Max-Age，注销清理服务端状态。
- IdP 回调校验签名、时间窗、企业、应用、模式和绑定上下文。
- 登录、验证码、Token、绑定和回调配置限流、审计和告警。

当前未完全满足的要求见[架构与安全偏差](../architecture/deviations.md)。
