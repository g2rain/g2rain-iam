# 排障

| 现象 | 优先检查 |
| --- | --- |
| 授权循环登录 | Cookie、Redis TTL、BASE_URL、回调和代理 HTTPS |
| Token 拒绝 DPoP | typ/JWK/签名、iat、jti、htm、htu |
| 签发或验签失败 | 活动 key、kid、算法、PEM、Nacos 刷新和 Gateway 缓存 |
| IdP 回调失败 | state TTL、签名、企业/应用模式、Secret 和供应商错误 |
| 无法映射用户 | Basis、Passport/绑定、企业授权和 Feign 错误 |
| 注销后仍有效 | Redis 会话、Cookie、刷新凭据撤销和缓存 |
| 容器探针失败 | 默认 8082 与 EXPOSE 8080 的映射差异 |

日志只记录 requestId、traceId 和脱敏主体，不输出 Token、授权码、Cookie、IdP access token、Secret 或私钥。
