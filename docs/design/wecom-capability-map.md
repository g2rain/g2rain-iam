# 企业微信能力地图（`/auth/wecom`）

本文是 IAM 企业微信 HTTP 入口的总览：按信任边界拆分 Controller，**不合并**不同协议与调用方。专题设计见扫码登录与客服回调文档。

## 1. 包与入口

| Controller | 包 | 路径前缀 | 调用方 | 协议 |
| --- | --- | --- | --- | --- |
| [`WeComOAuthController`](../../src/main/java/com/g2rain/iam/controller/wecom/WeComOAuthController.java) | `controller.wecom` | `/auth/wecom` | 浏览器 / Stream 客户端 | 302、`ModelAndView`、JSON |
| [`WeComAuthorizationCallbackController`](../../src/main/java/com/g2rain/iam/controller/wecom/WeComAuthorizationCallbackController.java) | `controller.wecom` | `/auth/wecom/third-party/authorization/callback` | **企业微信公网** | GET echo / POST XML → `text/plain` |
| [`WeComCustomerServiceController`](../../src/main/java/com/g2rain/iam/controller/wecom/WeComCustomerServiceController.java) | `controller.wecom` | `/auth/wecom/customer_service` | **客服模块**（经 Gateway） | JSON `Result` |

共享实现位于 `com.g2rain.iam.wecom`（凭据解析、验签解密、登录 Adapter），不抬到 Controller 合并。

员工扫码闸门与钉钉对称，见 [IdP 员工扫码与租户开通闸门](./idp-employee-login-tenant-gate.md)。

关联但不在本前缀内：

| 接口 | Controller | 说明 |
| --- | --- | --- |
| `POST /auth/member/token` | `MemberAuthorizeController` | 客服持 `memberResolveCode` 换 `SessionType=MEMBER` Token |

## 2. 能力分流图

```text
                    /auth/wecom/**
                           │
       ┌───────────────────┼───────────────────┐
       ▼                   ▼                   ▼
  扫码 / Stream      第三方安装授权回调      客服 decrypt
  WeComOAuth*        AuthorizationCallback   CustomerService*
       │                   │                   │
  浏览器 / 应用端       企微公网直连          受信客服模块
  OAuth + Session      suite 签名/AES        binding 凭据 + code
       │                   │                   │
       ▼                   ▼                   ▼
  员工登录编排         授权安装/变更/取消    organId + memberResolveCode
                                               │
                                               ▼
                                    POST /auth/member/token
                                    → SessionType=MEMBER
```

## 3. HTTP 能力表

### 3.1 扫码登录与 Stream（`WeComOAuthController`）

| 方法 | 路径 | 用途 | 对外可见性 |
| --- | --- | --- | --- |
| GET | `/auth/wecom/authorize` | 跳转企微扫码授权页 | hidden |
| GET | `/auth/wecom/callback` | 扫码登录回调，写 Session Cookie | hidden |
| POST | `/auth/wecom/authorize_code` | Stream / 消息应用发 OAuth 授权码 | 公开 JSON |

细节：[企业微信扫码登录](./wecom-qr-login.md)。

### 3.2 第三方应用授权回调（`WeComAuthorizationCallbackController`）

| 方法 | 路径 | 用途 | 对外可见性 |
| --- | --- | --- | --- |
| GET | `/auth/wecom/third-party/authorization/callback` | URL 验证（echostr）或安装完成（`auth_code`） | hidden |
| POST | 同上 | `suite_ticket` / `create_auth` / `change_auth` / `cancel_auth` 等事件 | hidden |

细节：扫码登录设计中的授权安装章节；凭据类型 `WeComCallbackType.THIRD_PARTY_AUTHORIZATION`。

### 3.3 客服回调解密（`WeComCustomerServiceController`）

| 方法 | 路径 | 用途 | 对外可见性 |
| --- | --- | --- | --- |
| POST | `/auth/wecom/customer_service/decrypt` | 验签解密、定租户、签发 `memberResolveCode` | 受信服务调用 |

**不是**企微公网回调 URL；企微客服回调打在客服模块，再由客服调本接口。

细节：[企业微信客服回调认证升级](./wecom-customer-service-callback-verification.md)。

## 4. 信任边界（禁止合并的原因）

| 边界 | 规则 |
| --- | --- |
| 公网企微回调 | 仅授权安装 callback；只回 `success` / 明文 echo |
| 浏览器 OAuth | 仅扫码登录；可写 Cookie、可 302 |
| 受信服务 | 仅 `decrypt`；需服务身份；返回 JSON，可含短时 code |
| 会员换票 | 独立 `/auth/member/token`；不得塞进 `/auth/wecom` 回调类 |

白名单、限流与 Gateway 策略必须按上表分轨配置，避免把 `decrypt` 配成公网匿名，或把授权 callback 配成需登录 Session。

## 5. 下层复用（可共享）

- `WeComCallbackCredentialResolver` / `WeComCallbackVerifier` / `WeComCallbackCrypto`
- `WeComIamProperties`（internal / thirdParty / customer-service.bindings）
- Basis 企业应用授权与 organ 映射查询

Controller 只做协议适配；领域规则在对应 Service。

## 6. 相关文档

- [企业微信扫码登录](./wecom-qr-login.md)
- [企业微信客服回调认证升级](./wecom-customer-service-callback-verification.md)
- Member：[企业微信客户接入会员](../../../g2rain-member/docs/design/wechat-work-customer-member-onboarding.md)（跨仓相对路径，以本机多仓布局为准）
