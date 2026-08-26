# 配置

| 配置 | 用途 |
| --- | --- |
| `SERVER_PORT` | 服务端口，默认 `8082` |
| `SPRING_PROFILES_ACTIVE` | Spring Profile，默认 `dev` |
| `NACOS_SERVER_ADDR` | Nacos 地址 |
| `BASE_URL` | IAM 对外基础地址 |
| `PLATFORM_BASE_URL` | 平台回跳基础地址 |
| `IAM_SESSION_COOKIE_*` | Cookie SameSite 和有效期 |

Nacos 密码、Token 私钥、钉钉/企业微信 Secret、回调 Token/AES Key 和凭据加密 Key 都是 Secret，只能安全注入。

`BASE_URL`、代理外部地址、OAuth 回调和 DPoP `htu` 规范化必须一致。SameSite=None 时 Cookie 必须 Secure。生产环境应显式提供安全配置并在启动时失败校验。
