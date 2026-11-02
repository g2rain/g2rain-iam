# 模块与职责

| 包/目录 | 职责 |
| --- | --- |
| `controller` | 公开认证端点、HTML/回调入口和受控内部接口 |
| `service` | 登录、授权、Token、会话、注册和 IdP 编排 |
| `service/idp` | 通用 IdP 主体、绑定、开户和路由抽象 |
| `service/idp/sync` | IdP 通讯录同步编排 |
| `dingtalk` | 钉钉适配、API、限流和身份解析 |
| `wecom` | 企业微信适配、回调密码学和凭据保护 |
| `client` | 与 Basis 等平台服务的 Feign 契约 |
| `filters` | Token 端点 DPoP 和请求上下文校验 |
| `config` | IAM、IdP、Token key、Redis、Web 和观测配置 |
| `dto` / `vo` | 边界输入输出模型 |
| `templates` / `static` | 登录、注册和授权确认页面资源 |

Controller 保持协议映射和输入校验；Service 编排安全流程；供应商差异保留在 IdP 适配层。敏感 DTO 不得默认写入日志或异常。
