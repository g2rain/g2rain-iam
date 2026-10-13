# IdP 员工扫码登录与租户开通闸门

本文定义钉钉 / 企业微信**员工**扫码登录收紧策略，以及仅 IdP 企业管理员可初始化租户的规则。

## 术语

| 名称 | 含义 | 会话 |
| --- | --- | --- |
| **USER / 员工** | 企业内员工 | 选中 organ User 后换 Token 为 `SessionType=USER` |
| **ADMIN（开通意图）** | 钉钉/企微企业管理员扫码，用于租户开通 | 登录 Session 标记 `idpAdmin`；开通前可能尚无 organ |
| **MEMBER / 会员** | 企微客服外部联系人 | `SessionType=MEMBER` —— **不在本文范围** |

企微 SSO 参数 `usertype=member|admin` 是企微 API 用语：`member` 映射本平台 **USER**，**不是** `SessionType.MEMBER`。

OAuth 查询参数：`loginRole=USER|ADMIN`（默认 `USER`）。

## 流程

```text
authorize?loginRole=USER|ADMIN
  → IdP 扫码
  → callback
  → （ADMIN）断言企业管理员 + Redis 开通资格
  → （USER）resolve idp_enterprise_organ；失败则拒绝
  → JIT passport/binding（如需）
  → （有 organ）ensure organ User（非 ADMIN）
  → IAM Session（idpLoginRole / idpAdmin）
  → consent 选 User → Token SessionType=USER
```

管理员开通：

```text
loginRole=ADMIN 扫码成功（可无 organ）
  → POST /tenant_provision/provision_account
  → Basis → IAM verify_create_organ（校验 Redis 资格）
  → 创建 organ + ADMIN User + 企业映射
```

## HTTP 入口

| 通道 | Controller 包 | 路径 |
| --- | --- | --- |
| 钉钉 OAuth | `controller.dingtalk` | `/auth/dingtalk/**` |
| 企微 OAuth | `controller.wecom` | `/auth/wecom/**` |
| ensure User | Basis 内部 | `POST /internal/idp/employee/ensure` |

## 与客服 MEMBER 分轨

客服链路使用 `/auth/wecom/customer_service/decrypt` 与 `/auth/member/token`，签发 `SessionType=MEMBER`，不走本文 organ User JIT。
