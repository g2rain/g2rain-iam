# 依赖与协作

- Basis 是 Passport、用户、应用、组织和 IdP 绑定主数据来源；IAM 不复制其所有权。
- Redis 保存会话、授权码、OAuth state 和临时凭据；TTL 和一次性消费属于安全契约。
- Nacos 提供发现和配置；生产 Secret 由安全配置来源注入。
- Main Shell 负责浏览器入口；Gateway/领域服务负责业务请求最终鉴权。
- 外部 IdP 是不可信网络依赖，必须处理超时、限流、伪造和重复投递。

Controller 不绕过 Service 直接操作 Redis、密钥或供应商凭据。Feign DTO、Token claims 和错误码是跨仓库契约。`/internal/**` 仍需网络隔离和应用身份授权，路径名称不是安全控制。
