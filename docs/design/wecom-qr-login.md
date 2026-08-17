# 企业微信扫码登录设计方案（IAM 子文档）

## 1. 文档状态

- 状态：已审核（IAM 实现子文档）
- 目标项目：`g2rain-iam`
- 身份源类型：`WECHAT_WORK`（沿用 Basis `IdpType` 定义）
- 目标模式：企业内部自建应用与服务商三方应用双模式
- 正式上线默认模式：`THIRD_PARTY`

**跨项目主文档**（数据模型、Basis API、登录与 Organ 规则、凭证脱敏）：  
[`g2rain-basis/docs/design/wechat-work-authorization.md`](../../../g2rain-basis/docs/design/wechat-work-authorization.md)

本文只描述 IAM 侧 OAuth、Adapter、配置与测试；与主文档冲突时**以主文档为准**。

说明：Java 组件名和配置前缀可以继续使用产品英文名 `WeCom`/`WECOM`；凡表示 Basis 身份源枚举或持久化 `idpType` 的字段，必须使用 `WECHAT_WORK`。

## 2. 背景与目标

`g2rain-iam` 已具备账号密码登录、钉钉身份源登录、通行证绑定、统一 Session、OAuth 授权码和 Token 签发能力。本次在不改变现有 IAM 主链路的前提下，增加企业微信扫码登录。

方案同时保留以下两种接入模式：

| 模式 | `bindMode` | 使用场景 | 优先级 |
|---|---|---|---|
| 企业内部自建应用 | `INTERNAL` | G2Rain 自有企业内部部署、联调和私有化部署 | 次要 |
| 服务商三方应用 | `THIRD_PARTY` | 正式 SaaS、多企业租户接入 | 主要 |

两种模式分别实现企业微信授权入口、凭证管理、换票接口和主体映射，只共用换取企业微信主体之后的 IAM 认证链路。

## 3. 总体设计

```text
企业微信扫码
  -> 企业微信回调
  -> 按 bindMode 选择换票适配器
  -> WeComPrincipal
  -> IdpPrincipal(WECHAT_WORK)
  -> 查询或创建 passport_idp_binding
  -> 获得 passportId
  -> 创建 IAM Session
  -> OAuth consent
  -> authorization_code
  -> token
```

现有通用能力继续复用：

- `IdpPrincipal`
- `IdpAuthServiceRouter`
- `IdpBindingSupport`
- `IdpPassportProvisioner`
- `SessionService`
- `AuthorizationService`
- `TokenService`

## 4. 服务商三方应用模式

### 4.1 扫码入口

正式开发以企业微信服务商单点登录入口为主：

```text
https://open.work.weixin.qq.com/wwopen/sso/3rd_qrConnect
    ?appid={服务商CorpID}
    &redirect_uri={IAM回调地址}
    &state={IAM一次性state}
    &usertype=member
```

参数说明：

| 参数 | 说明 |
|---|---|
| `appid` | 服务商 CorpID，不是 SuiteID |
| `redirect_uri` | IAM 三方应用回调地址，需要 URL 编码 |
| `state` | IAM 生成的一次性不透明状态 |
| `usertype=member` | 成员使用企业微信扫码 |
| `usertype=admin` | 管理员使用微信扫码，可用于后续租户开通或管理后台登录 |

三方登录成功后，企业微信向回调地址传递 `auth_code`。该参数与内部模式使用的 `code` 不同。

### 4.2 获取服务商凭证

调用：

```http
POST https://qyapi.weixin.qq.com/cgi-bin/service/get_provider_token
Content-Type: application/json
```

请求体：

```json
{
  "corpid": "服务商CorpID",
  "provider_secret": "服务商Secret"
}
```

返回的 `provider_access_token` 存入 Redis，缓存时间使用接口返回的 `expires_in` 减去安全窗口。当企业微信提前使 Token 失效时，服务端应清除缓存、重新获取并且只重试一次。

### 4.3 换取登录用户

调用：

```http
POST https://qyapi.weixin.qq.com/cgi-bin/service/get_login_info
    ?access_token={provider_access_token}
Content-Type: application/json
```

请求体：

```json
{
  "auth_code": "企业微信回调中的auth_code"
}
```

关键响应字段：

```json
{
  "usertype": 1,
  "user_info": {
    "userid": "...",
    "open_userid": "...",
    "name": "...",
    "avatar": "..."
  },
  "corp_info": {
    "corpid": "..."
  },
  "agent": []
}
```

服务端必须校验：

- 企业微信业务错误码为成功。
- 登录用户类型符合当前请求要求。
- `corp_info.corpid` 存在。
- `user_info.open_userid` 或兼容字段 `userid` 存在。
- `auth_code` 只使用一次。

### 4.4 三方身份映射

与主文档 §12.1 及 `WeComPrincipal` 实现一致：

| IAM 字段 | 三方应用取值 |
|---|---|
| `idpType` | `WECHAT_WORK` |
| `bindMode` | `THIRD_PARTY` |
| `idpSubject` | 优先 `open_userid`；缺失时 `lowercaseHex(SHA-256(enterpriseId + "\0" + encryptedUserId))` |
| `idpUserId` | 优先 `open_userid`；缺失时保存企业微信返回的加密 UserID |
| `corpId` | `corp_info.corpid` |
| `displayName` | `user_info.name`，为空时使用「企业微信用户」 |
| `idpApplicationCode` | `suiteId` |
| `installedApplicationId` | 登录响应中的 AgentID，用于与 Basis 授权记录比对 |
| `rawProfile` | 脱敏后的企业微信登录响应 |

### 4.5 企业授权校验

`3rd_qrConnect` 是服务商级登录入口，扫码成功不等于成员所属企业已经安装并授权 G2Rain 对应 Suite。创建 IAM Session 前必须执行（**相对钉钉的额外门禁**）：

```text
corpId + suiteId
  -> Feign 调用 Basis POST /internal/idp/enterprise-application-authorization/resolve
  -> authorizationStatus 必须为 ACTIVE
  -> 登录响应 AgentID 与 Basis installedApplicationId 一致
  -> 允许进入通行证绑定和 Session 创建
```

授权记录的持久化、状态机、凭证加密与 Basis CRUD 脱敏规则见主文档 §4–§6、§5.1。IAM 通过 `IdpEnterpriseApplicationAuthorizationClient` 调用 `upsert` / `revoke` / `resolve`。

处理策略：

- 普通成员所属企业未授权：拒绝登录（`WECOM_ENTERPRISE_NOT_AUTHORIZED`）。
- AgentID 不一致：拒绝登录（`WECOM_AGENT_MISMATCH`）。
- 已取消授权：Basis 返回非 ACTIVE，拒绝登录。
- PermanentCode 仅经 IAM 加密后由 internal `upsert` 写入 Basis，不进入浏览器或管理端 list/page。

## 5. 企业内部自建应用模式

### 5.1 扫码入口

内部模式使用企业微信自建应用扫码入口：

```text
https://open.work.weixin.qq.com/wwopen/sso/qrConnect
    ?appid={企业CorpID}
    &agentid={应用AgentID}
    &redirect_uri={IAM回调地址}
    &state={IAM一次性state}
```

登录页面可以使用企业微信官方 `WwLogin` 组件内嵌二维码，同时保留整页跳转作为加载失败后的兜底。

### 5.2 换取身份

流程：

1. 使用 `CorpID + CorpSecret` 获取企业应用 `access_token`。
2. 使用回调中的 `code` 调用 `/cgi-bin/user/getuserinfo`。
3. 获取企业成员 `UserId`。
4. 必要时调用成员详情接口补充姓名；姓名获取失败不能阻断登录。

### 5.3 内部身份映射

与主文档 §12.2 一致：

| IAM 字段 | 内部应用取值 |
|---|---|
| `idpType` | `WECHAT_WORK` |
| `bindMode` | `INTERNAL` |
| `idpSubject` | `lowercaseHex(SHA-256(corpId + "\0" + userId))` |
| `idpUserId` | `userId` |
| `corpId` | 配置的企业 CorpID |
| `displayName` | 成员姓名，获取不到时使用「企业微信用户」 |
| `idpApplicationCode` | `agentId` |

内部模式**不**调用 Basis 企业应用授权表；凭证来自 `WeComIamProperties.internal`（与钉钉 `DingTalkIamProperties` 相同模式）。

## 6. 适配器设计

企业微信登录采用与现有钉钉相似的路由结构：

```text
WeComLoginAdapter
├── InternalWeComLoginAdapter
└── ThirdPartyWeComLoginAdapter
```

建议接口：

```java
public interface WeComLoginAdapter {

    IdpBindMode bindMode();

    String buildAuthorizeUrl(String state, String callbackUrl);

    WeComPrincipal exchangeCodeForPrincipal(String code);
}
```

控制器负责将内部回调的 `code` 和三方回调的 `auth_code` 归一化后交给对应适配器。

建议新增：

```text
config/
  WeComIamProperties.java
  WeComIamConfiguration.java

wecom/
  WeComLoginAdapter.java
  WeComLoginAdapterRouter.java
  AbstractWeComLoginAdapter.java
  InternalWeComLoginAdapter.java
  ThirdPartyWeComLoginAdapter.java
  WeComSuiteApiClient.java
  WeComPrincipal.java
  WeComOAuthResult.java
  WeComCallbackCrypto.java
  WeComCredentialCipher.java

service/
  WeComOAuthStateService.java
  WeComOAuthService.java
  WeComAuthorizationService.java

controller/
  WeComOAuthController.java
  WeComAuthorizationCallbackController.java

dto/
  WeComOAuthStateDto.java
```

## 7. HTTP 接口

| 接口 | 用途 |
|---|---|
| `GET /auth/wecom/authorize?bindMode=...` | 根据模式进入内部或三方扫码页 |
| `POST /auth/wecom/qr/bootstrap` | 内部应用内嵌二维码初始化 |
| `GET /auth/wecom/callback/internal` | 接收内部应用 `code` 回调 |
| `GET /auth/wecom/callback/third-party` | 接收三方应用 `auth_code` 回调 |

内部和三方使用不同回调地址，以降低参数混淆和错误路由风险。

## 8. OAuth State

State 中保存以下服务端上下文：

```text
bindMode
clientId
redirectUri
业务state
suiteId/agentId
userType
创建时间
```

安全要求：

- 使用密码学安全随机值，至少 128 bit。
- Redis TTL 为 10 分钟。
- 回调时原子读取并删除，只能消费一次。
- 回调中的业务 `clientId`、`redirectUri`、SuiteID 和模式只从服务端 State 恢复。
- State 不包含 Secret、Token、永久授权码等敏感信息。
- 企业微信回调失败时可以只读 State 恢复错误跳转上下文，但不能重新完成登录。

Redis Key 建议：

```text
auth:wecom:oauth:state:{state}
auth:wecom:token:internal:{corpId}:{agentId}
auth:wecom:token:provider:{providerCorpId}
```

## 9. 配置设计

```yaml
g2rain:
  iam:
    wecom:
      login-page-bind-mode: ${IAM_WECOM_LOGIN_BIND_MODE:THIRD_PARTY}

      internal:
        enabled: ${WECOM_INTERNAL_ENABLED:false}
        corp-id: ${WECOM_INTERNAL_CORP_ID:}
        agent-id: ${WECOM_INTERNAL_AGENT_ID:}
        corp-secret: ${WECOM_INTERNAL_CORP_SECRET:}
        callback-path: /auth/wecom/callback/internal

      third-party:
        enabled: ${WECOM_THIRD_PARTY_ENABLED:false}
        provider-corp-id: ${WECOM_PROVIDER_CORP_ID:}
        provider-secret: ${WECOM_PROVIDER_SECRET:}
        suite-id: ${WECOM_SUITE_ID:}
        suite-secret: ${WECOM_SUITE_SECRET:}
        callback-path: /auth/wecom/callback/third-party
        user-type: member
```

配置约束：

- 启用某种模式时，对应凭证必须完整。
- 正式环境回调地址必须使用 HTTPS。
- 日志可以显示 CorpID、AgentID、SuiteID 和 Secret 长度，不能显示 Secret 内容。
- 三方模式需要在服务商后台配置登录授权发起域名和授权完成回调域名。
- 内部模式需要在企业微信应用后台配置完全匹配的授权回调域。
- Suite Secret、Provider Secret 和永久授权码应由 Nacos Secret 或部署环境的密钥管理机制提供。

## 10. 登录页面

登录页面保留：

- 账号登录
- 钉钉登录
- 企业微信登录

企业微信标签的默认模式由 `login-page-bind-mode` 决定：

- 正式 SaaS 环境默认 `THIRD_PARTY`。
- 内部或私有化环境可以使用 `INTERNAL`。

两种模式同时启用时，可以在企业微信标签内展示：

- “企业成员登录”：三方应用模式，主入口。
- “内部员工登录”：内部自建应用模式，次级入口。

三方模式首期使用企业微信官方整页扫码页，不通过 iframe 强行内嵌。内部模式使用官方 `WwLogin` 内嵌组件并提供整页跳转兜底。

## 11. Basis 侧依赖

以下项已在主文档定义并由 `g2rain-basis-api` 提供，IAM 通过 Feign 消费：

- `IdpType.WECHAT_WORK`、`IdpBindMode`
- `IdpEnterpriseApplicationAuthorizationApi`（`upsert` / `revoke` / `resolve`）
- `passport_idp_binding` 通用绑定与 Resolve

开发 checklist：

1. IAM 依赖新版 `g2rain-basis-api` 并注册 `IdpEnterpriseApplicationAuthorizationClient`。
2. 三方登录前调用 `resolve`；安装/取消回调调用 `upsert` / `revoke`。
3. 不在 IAM 重复实现 Basis 授权表 CRUD 或管理端凭证展示。

## 12. 自动开户与 Organ 映射（与钉钉一致）

与 `DingTalkOAuthService` / `DingTalkIdpAuthService` 相同：

| 场景 | 行为 |
|---|---|
| IdP 扫码登录 | **不**校验 `idp_enterprise_organ`；默认 `autoProvisionMissingPassport=true` |
| 已绑定 | 直接获得 `passportId` |
| 未绑定 | 创建不可密码登录的 Passport 并写入 `passport_idp_binding` |
| 三方额外条件 | 仅在企业 Suite 授权为 `ACTIVE` 且 AgentID 一致后才允许进入绑定/Session |

`idp_enterprise_organ` 仅在已登录用户执行 `/passport_idp_binding/bind`、租户同步、租户开通时使用，详见主文档 §10.2。

建议配置（与钉钉对称）：

```yaml
g2rain.iam.wecom.internal.auto-provision-missing-passport: true
g2rain.iam.wecom.third-party.auto-provision-missing-passport: true
```

## 13. 错误码

建议增加独立错误码：

| 场景 | 建议错误 |
|---|---|
| State 无效或过期 | `WECOM_OAUTH_INVALID_STATE` |
| 内部应用凭证缺失 | `WECOM_INTERNAL_CREDENTIAL_MISSING` |
| 服务商凭证缺失 | `WECOM_PROVIDER_CREDENTIAL_MISSING` |
| 获取 Token 失败 | `WECOM_TOKEN_EXCHANGE_FAILED` |
| 获取登录用户失败 | `WECOM_USERINFO_FAILED` |
| 用户类型不匹配 | `WECOM_USER_TYPE_INVALID` |
| 企业尚未授权 Suite | `WECOM_ENTERPRISE_NOT_AUTHORIZED` |
| AgentID 与授权记录不一致 | `WECOM_AGENT_MISMATCH` |
| 企业微信账号未绑定 | `WECOM_STREAM_USER_NOT_BOUND` |

对用户展示稳定、简洁的中文提示；企业微信原始 `errcode` 只写入脱敏日志。

## 14. 安全要求

- Provider Secret、Suite Secret、Corp Secret、Access Token、永久授权码不得进入浏览器。
- 日志不得记录完整授权码、Token、Secret 或原始敏感用户资料。
- State 必须一次性原子消费。
- Token 失效重试最多一次，防止错误响应导致无限重试。
- 三方扫码必须校验授权企业，不能把服务商级扫码结果直接视为应用授权。
- `redirectUri` 和 OAuth 客户端必须继续经过现有 IAM 客户端校验。
- `rawProfile` 仅保留必要字段并进行脱敏。
- 生产环境通过应用可见范围限制可登录人员。
- 三方身份匹配以服务商主体下的加密 ID 为基准，不依赖可能变化的姓名、手机号或邮箱。

## 15. 测试方案

### 15.1 单元测试

- 两种模式授权 URL 构造及 URL 编码。
- Adapter Router 对 `INTERNAL`、`THIRD_PARTY` 的路由。
- Provider Token 和内部 Token 的缓存、刷新及提前失效重试。
- 企业微信成功响应和各类 `errcode`。
- 内部 UserId 与三方 OpenUserId 的主体映射。
- State 过期、伪造、重复消费和回调参数缺失。
- 三方企业授权有效、未授权、取消授权。
- 已绑定、未绑定自动开户、禁止自动开户。
- 并发首次登录的唯一绑定处理。

### 15.2 集成测试

- 三方成员扫码后建立 IAM Session。
- 内部成员扫码后建立 IAM Session。
- Session Cookie 正确写入。
- 扫码登录后进入现有 consent 页面。
- 授权码能够正常换取 Token。
- 不同企业相同 UserId 不会串号。
- 三方应用不同 Suite 不会错误复用绑定。
- 企业微信登录失败后能够返回原登录上下文。

### 15.3 回归测试

- 账号密码登录。
- 钉钉扫码登录。
- 注册、退出和 Session 过期。
- OAuth authorization code 与 refresh token。

## 16. 实施顺序

1. 升级 `g2rain-basis-api` 依赖并接入 `IdpEnterpriseApplicationAuthorizationClient`（主文档 §13 步骤 1–3）。
2. 实现 Suite 回调（`suite_ticket`、`create_auth`、`cancel_auth`）与 `WeComAuthorizationService`。
3. 实现 `THIRD_PARTY` 扫码、`get_login_info`、Basis 授权校验与 Session（`WeComOAuthService`）。
4. 实现 `INTERNAL` 自建应用扫码（`InternalWeComLoginAdapter`）。
5. 登录页双模式入口、错误码、配置校验与测试。
6. 通讯录同步单独规划（主文档 §7.4：当前仍拒绝 `WECHAT_WORK` 同步）。

## 17. 验收标准

- 正式环境默认使用 `THIRD_PARTY + member`。
- 已授权企业成员能够通过企业微信扫码进入现有 OAuth 授权链路。
- 未授权或已取消授权企业的成员无法创建 IAM Session。
- IdP 扫码登录不要求 `idp_enterprise_organ`（与钉钉一致）。
- 三方身份使用稳定 `idpSubject`（`open_userid` 或 SHA-256 主体）。
- 内部模式可以独立启用并完成扫码登录。
- 任一模式关闭或配置不完整时，登录页面不展示对应入口。
- 账号密码和钉钉登录行为不受影响。
- Secret、Token、PermanentCode 不出现在浏览器、管理端 list/page 及普通日志中（主文档 §5.1）。

## 18. 参考资料

- [企业微信三方扫码登录接入指引](https://s.apifox.cn/apidoc/docs-site/406014/doc-417881)
- [企业微信获取登录用户信息](https://apifox.com/apidoc/docs-site/406014/api-10061611)
- [企业微信获取服务商凭证](https://s.apifox.cn/apidoc/docs-site/406014/api-10061532)
- [企业微信第三方获取访问用户身份](https://s.apifox.cn/apidoc/docs-site/406014/api-10061609)
- [企业微信扫码登录链接](https://s.apifox.cn/apidoc/docs-site/406014/doc-417799)
- [企业微信 ID 转换说明](https://s.apifox.cn/apidoc/docs-site/406014/doc-1794365)
