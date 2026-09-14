# 企业微信客服回调认证升级设计

## 1. 文档目的

本文定义 `g2rain-iam` 为企业微信智能客服回调提供渠道认证、短时授权码与 `SessionType=MEMBER` Token 能力所需的升级内容。本阶段以设计为准，实现时须与 Member / 客服模块联调。

关联会员流程：[`g2rain-member` 企业微信客户接入会员](../../../g2rain-member/docs/design/wechat-work-customer-member-onboarding.md)。
入口总览：[企业微信能力地图](./wecom-capability-map.md)。

## 2. 设计结论

`g2rain-iam` 负责：

1. 确认「回调密文确实来自企业微信，并属于哪个可信租户」（客服模块收回调，IAM 做验签解密）；
2. 在 `decrypt` 成功时签发一次性 `memberResolveCode`；
3. 在客服模块 `sync_msg` 之后，通过 `MemberAuthorizeController#token` 校验 code，并在受信服务网络内通过服务发现无鉴权直连 `g2rain-member` 的 `resolveOrCreate`；
4. 在解析成功后签发或复用 `SessionType=MEMBER` 短期 Token，**仅**供企业微信智能客服模块经 Gateway 调用下游业务。

企业微信智能客服模块位于 Gateway 外侧，负责客服协议与消息处理，**不**直连 Member。`g2rain-member` 仍是会员主数据所有者，不签发 Token。企微回调 URL 打在客服模块，**不是** IAM。

```text
企业微信客服回调（打到客服模块）
        ↓
企业微信智能客服模块
        ↓ 原始签名参数与密文
g2rain-iam WeComCustomerServiceController.decrypt
  POST /auth/wecom/customer_service/decrypt
        ↓ organId + plainBody + memberResolveCode
        （decrypt 明文无 external_userid，不能创建会员）
企业微信智能客服模块 sync_msg
        ↓ msgid + external_userid
g2rain-iam MemberAuthorizeController.token
  POST /auth/member/token（校验 code）
        ↓ 受信服务网络内无鉴权直连
g2rain-member resolveOrCreate
        ↓
g2rain-iam 签发/复用 SessionType=MEMBER Token
        ↓
企业微信智能客服模块持 Token 经 Gateway 调下游
```

对外命名对齐现有 `/auth/wecom/**` 与 `TokenController#token`（`POST /auth/token`），**不**使用 `/internal/wecom/callback/verify` 或对外 `resolve_or_create`。

IAM **不**调用 `/cgi-bin/kf/sync_msg`，**不**持久化 `member` / `member_identity`，**不**创建 `passport`，**不**签发员工/管理员登录 Session。

## 3. 现有能力

当前项目已经具备以下基础：

- [`WeComIamProperties`](../../src/main/java/com/g2rain/iam/config/WeComIamProperties.java) 配置内部应用、第三方服务商、Suite 和回调密钥。
- [`WeComCallbackCrypto`](../../src/main/java/com/g2rain/iam/wecom/WeComCallbackCrypto.java) 实现企业微信 SHA-1 签名校验、AES 解密、PKCS#7 去填充和接收方校验。
- [`WeComAuthorizationCallbackController`](../../src/main/java/com/g2rain/iam/controller/wecom/WeComAuthorizationCallbackController.java) 接收第三方应用授权回调。
- [`WeComAuthorizationService`](../../src/main/java/com/g2rain/iam/service/WeComAuthorizationService.java) 处理 `suite_ticket`、`create_auth`、`change_auth` 和 `cancel_auth`。
- [`TokenController`](../../src/main/java/com/g2rain/iam/controller/TokenController.java) / `TokenService`：既有令牌签发能力（需扩展 `SessionType=MEMBER`）。
- 企业微信授权信息可通过 Basis 的企业应用授权能力关联企业与租户。

现有实现必须继续兼容，不因新增客服能力而改变授权安装、更新、取消流程及现有 `POST /auth/token` 行为。

## 4. 当前差距

| 项目 | 当前实现 | 客服回调需要 |
|---|---|---|
| 密钥选择 | 固定读取 `thirdParty.token` 和 `thirdParty.encodingAesKey` | 按回调场景和接入绑定选择凭据 |
| 接收方校验 | 固定要求接收方等于 `suiteId` | 按客服接入模式校验 CorpID、SuiteID 或配置的期望值 |
| 事件范围 | 第三方授权事件 | `kf_msg_or_event` 等客服通知 |
| 时间校验 | 签名计算包含时间戳，但未独立限制时间窗口 | 拒绝超出允许偏差的请求 |
| 防重放 | 无独立 nonce/签名重放记录 | 在短时间窗口内拒绝重复认证请求 |
| 调用方式 | 授权回调控制器内直接使用组件 | 向客服模块提供受保护的 `decrypt` 能力 |
| 租户上下文 | 授权服务处理企业授权 | 解密后返回可信 `organId` 和企业标识 |
| 会员换票 | 无 | `decrypt` 发 `memberResolveCode`；`token` 在受信服务网络内直连 Member |
| MEMBER Token | 无或未覆盖该 SessionType | 按 `organId + external_userid` 会话复用短期 Token |

微信客服的回调通知只表示存在新消息或事件。`external_userid` 和具体消息内容由企业微信智能客服模块调用 `/cgi-bin/kf/sync_msg` 后取得，**不属于** IAM `decrypt` 结果，也**不能**在 `decrypt` 阶段创建会员。

## 5. 职责边界

### 5.1 IAM 负责

- 管理或安全访问企业微信回调 `Token`、`EncodingAESKey`。
- 根据不可伪造的接入绑定标识选择回调配置。
- 校验签名、时间戳、随机串和密文结构。
- 解密回调，并校验明文接收方与配置一致。
- 根据已验证企业标识和有效授权关系解析 `organId`。
- 返回最小化的可信解密结果，并签发 `memberResolveCode`。
- 通过 `MemberAuthorizeController#token` 校验 code，在受信服务网络内无鉴权直连 Member `resolveOrCreate`。
- 在会员解析成功且状态允许时，签发或复用 `SessionType=MEMBER` Token。
- 记录不包含密钥和明文正文的安全审计日志。

### 5.2 IAM 不负责

- 不直接接收企业微信推送到公网的客服回调 URL（由客服模块接收）。
- 不解析或处理客服业务消息。
- 不调用 `sync_msg`、`send_msg` 等微信客服业务接口。
- 不持久化拉取游标、客服消息、对话或工单。
- 不持久化 `member` / `member_identity`（写入在 Member）。
- 不把微信客户注册成 `passport`。
- 不给微信客户签发员工/管理员登录 Session。
- 不在 `decrypt` 阶段创建会员或签发 MEMBER Token。

## 6. 回调凭据模型

现有 `WeComCallbackCrypto` 将配置读取、密码学处理和接收方规则绑定在一个组件中。升级后应拆分为：

```text
WeComCallbackCredentialResolver
    ↓
WeComCallbackCredential
    - callbackType
    - bindingCode
    - token
    - encodingAesKey
    - expectedReceiver
    - organId / authorization reference
    ↓
WeComCallbackVerifier
    - verifySignature
    - validateTimestamp
    - rejectReplay
    - decrypt
    - validateReceiver
```

建议回调类型至少区分：

```text
THIRD_PARTY_AUTHORIZATION
CUSTOMER_SERVICE
```

`bindingCode` 由平台在配置企业微信回调地址时生成，用于定位接入配置。外部请求不得直接指定 `organId`，也不得通过请求参数覆盖期望接收方。

凭据最终存放在 Nacos、密钥管理系统或持久化授权表，需要在实现前结合多租户接入方式确定。无论采用哪种存储，业务模块都不得获得明文 `Token` 和 `EncodingAESKey`。

## 7. 密码学组件改造

建议将现有组件改为显式传入凭据和期望接收方：

```text
verifyAndDecrypt(
    credential,
    msgSignature,
    timestamp,
    nonce,
    encryptedBody
) -> VerifiedWeComCallback
```

验证顺序：

1. 校验所有必要参数存在且长度在限制内。
2. 解析并校验时间戳，与服务器时间偏差不得超过配置窗口。
3. 使用 `Token + timestamp + nonce + Encrypt` 计算并常量时间比较签名。
4. 在短期缓存中登记回调指纹，拒绝窗口内完全相同的重放请求。
5. 使用 `EncodingAESKey` 解密并验证 PKCS#7 填充、消息长度和 UTF-8 内容。
6. 校验明文尾部接收方等于凭据配置的 `expectedReceiver`。
7. 从明文读取企业标识，并解析有效的租户授权关系。
8. 签发 `memberResolveCode` 并返回最小化的已解密结果。

现有 `decryptMessage`、`decryptEcho` 可以保留为兼容入口，但内部应委托新的通用验证器，并继续使用 `THIRD_PARTY_AUTHORIZATION` 凭据。URL 验证（echostr）可不签发 `memberResolveCode`。

## 8. 对外接口契约

### 8.1 客服回调解密 — `WeComCustomerServiceController#decrypt`

```text
POST /auth/wecom/customer_service/decrypt
```

挂在 `/auth/wecom/**` 下，与扫码 OAuth、授权 callback 同前缀。语义是**验签解密 + 定租户 + 签发短时 code**，不是企微直连回调入口，也不是仅返回布尔的「verify」。

请求：

```json
{
  "callbackType": "CUSTOMER_SERVICE",
  "bindingCode": "平台生成的接入绑定标识",
  "msgSignature": "企业微信签名",
  "timestamp": "时间戳",
  "nonce": "随机串",
  "encryptedBody": "原始加密 XML"
}
```

响应：

```json
{
  "verified": true,
  "organId": 10001,
  "enterpriseId": "ww...",
  "callbackType": "CUSTOMER_SERVICE",
  "plainBody": "已解密 XML",
  "memberResolveCode": "一次性短时票据",
  "codeExpiresAt": "2026-08-20T10:05:00+08:00",
  "verifiedAt": "2026-08-20T10:00:00+08:00"
}
```

约束：

- 接口须经平台对客服/内部应用的调用方认证，不能公开匿名调用，也不是终端用户登录。
- `verified` 为 `false` 时不返回明文、租户信息和 code。
- 响应不得返回回调密钥、企业访问令牌或永久授权码。
- `organId` 必须由 IAM 根据已验证配置和授权关系产生，不能原样回显请求参数。
- `plainBody` 仅用于接入模块解析通知，不得在 IAM 日志中输出。
- `kf_msg_or_event` 明文通常仅含拉取令牌与 `OpenKfId`，**不含** `external_userid`。

### 8.2 会员授权换票 — `MemberAuthorizeController#token`

```text
POST /auth/member/token
```

对齐 [`TokenController#token`](../../src/main/java/com/g2rain/iam/controller/TokenController.java)（`POST /auth/token`）的「获取 Token」语义；对客服暴露的是换票，不是 Member 领域的 `resolve_or_create`。

请求：

```json
{
  "memberResolveCode": "decrypt 返回的票据",
  "externalUserId": "企业微信 external_userid",
  "msgid": "可选，用于消息级幂等关联",
  "externalProfile": {
    "name": "可选昵称",
    "avatar": "可选头像 URL"
  }
}
```

响应：

```json
{
  "accessToken": "SessionType=MEMBER 的访问令牌",
  "tokenExpiresAt": "2026-08-20T10:30:00+08:00",
  "memberId": 10001,
  "memberNo": "...",
  "memberStatus": "NORMAL",
  "newMember": true,
  "identityVerified": true
}
```

约束：

- `organId` 以 `memberResolveCode` 绑定为准，拒绝请求体伪造租户。
- IAM 通过服务发现无鉴权直连 Member `POST /internal/wechat_work_member/resolve_or_create`。约定调用方仅 IAM，Member 信任 IAM 传入的 `organId`；Member 服务不得暴露到公网、客户端网络或其他非受信网络。
- 同一 `memberResolveCode` 可多次调用 `token`（一次回调多消息）；按 `msgid` 与 `(organId, externalUserId)` 幂等；同一会话复用未过期 MEMBER Token。
- 解析失败（身份已删、会员冻结/删除等）时不签发 MEMBER Token，透传或映射业务错误。

### 8.3 `memberResolveCode` 规则

| 项 | 规则 |
|---|---|
| 签发时机 | `CUSTOMER_SERVICE` 的 `decrypt` 成功时（echostr 验证除外） |
| 绑定 | `organId` + `bindingCode` / `enterpriseId` + 回调实例 |
| 能力 | **仅**授权 `POST /auth/member/token` |
| TTL | 短时（建议 1–5 分钟） |
| 使用 | 同一 code 允许多次换票；过期或伪造一律拒绝 |

### 8.4 `SessionType=MEMBER` Token 规则

| 项 | 规则 |
|---|---|
| 主体 | JWT claim `memberId`（写入 `BasePrincipal.memberId`；**不得**写入 `userId`）；不创建 `passport` |
| 租户 | claims 含 code 绑定的可信 `organId` |
| 使用方 | **仅**企业微信智能客服模块 |
| 禁止 | 不下发终端微信用户；不作员工登录；不可用 code 冒充 |
| 粒度 | 每个 `organId + external_userid` 会话复用一个短期 Token；未过期则复用，不每条消息新签 |
| 签发时机 | Member resolve 成功且状态允许之后 |

两类凭证分轨：`memberResolveCode` 与 MEMBER Token。IAM→Member 直连不使用这两类凭证，也不额外使用服务凭证。

如果接入模块与 IAM 同进程部署，`decrypt` 可用内部 Java Service；换票与 Token 签发职责仍不变。

## 9. 客服回调处理链路

1. 客服模块调用 IAM `POST /auth/wecom/customer_service/decrypt`，取得 `organId`、`plainBody`、`memberResolveCode`。
2. 解析解密 XML，确认 `MsgType=event`、`Event=kf_msg_or_event`。
3. 读取回调中的拉取令牌和 `OpenKfId`。
4. 使用对应企业的微信客服访问凭据调用 `/cgi-bin/kf/sync_msg`。
5. 维护拉取游标并处理企业微信要求的重试。
6. 按 `msgid` 幂等消费完整消息。
7. 对每条需识别会员的消息，调用 IAM `POST /auth/member/token`。
8. 持返回的 MEMBER Token 经 Gateway 处理咨询 / 下游业务。

步骤 2–6 属于客服模块；步骤 7–8 的会员数据与 Token 由 IAM / Member 分工完成。

## 10. 租户解析规则

租户解析必须同时满足：

- 回调凭据绑定处于启用状态。
- 解密后的企业标识与绑定配置一致。
- 企业微信应用授权状态有效。
- 授权记录能够唯一映射到一个 `organId`。
- 绑定的应用或客服能力与本次回调类型一致。

无法唯一确定租户时应拒绝 `decrypt`，不允许回退到默认租户；不得签发 `memberResolveCode`。

## 11. 安全要求

- 限制请求体大小，拒绝畸形 XML、缺失 `Encrypt` 或超长参数。
- 保持 XML 解析器禁用 DTD、外部实体、XInclude 和实体展开。
- 签名继续使用常量时间比较。
- 对时间窗口、重放缓存过期时间和最大时钟偏差提供配置项。
- 日志仅记录请求追踪标识、回调类型、绑定标识、验证结果和脱敏企业标识。
- 禁止记录回调 `Token`、`EncodingAESKey`、Secret、永久授权码、访问令牌、`memberResolveCode` 全文、MEMBER Token 全文和解密正文。
- `decrypt` 与 `token` 接口实施限流、调用方认证和超时保护。
- IAM 不长期保存解密后的回调正文。
- Gateway 只处理客服模块持 `SessionType=MEMBER` Token 调用下游的鉴权；IAM→Member 内部解析不经过 Gateway。
- Member 必须部署在受信服务网络，禁止暴露到公网、客户端网络或其他非受信网络；若该边界变化，须先引入调用方鉴权。

## 12. 兼容与迁移

- 第三方授权回调 URL 和响应格式保持不变。
- `suite_ticket`、授权安装、授权变更和取消授权逻辑保持不变。
- 现有 `POST /auth/token` 行为保持不变。
- 先用新验证器替换现有授权回调内部实现并完成回归测试，再开放客服 `decrypt` 与 `MemberAuthorizeController#token`。
- 新增客服凭据配置时不得复用名称含义不一致的 `thirdParty.token`，除非已经确认两个回调场景在企业微信后台确实共享同一组配置。
- 配置缺失时只禁用对应回调类型，不影响其他企业微信登录和授权能力。
- 旧表述「`/internal/wecom/callback/verify`」「对外 `resolve_or_create`」「客服直连 Member」以本文为准：改为 `decrypt` + `token` + IAM 编排。

## 13. 测试要求

### 13.1 单元测试

- 正确签名和密文能够解密。
- 签名错误、AES Key 错误、填充错误、接收方错误均被拒绝。
- 时间戳过期或超前超出窗口时被拒绝。
- 相同回调指纹在防重放窗口内再次出现时被拒绝。
- 不同 `callbackType` 选择不同凭据。
- `CUSTOMER_SERVICE` 不会误用 Suite 授权凭据。
- XML 外部实体与 DTD 被拒绝。
- `memberResolveCode` 过期、伪造、错绑 `organId` 时 `token` 被拒绝。
- MEMBER Token 按 `organId + external_userid` 复用；失败路径不签发。

### 13.2 集成测试

- 现有第三方授权回调完整回归。
- 现有 `POST /auth/token` 回归。
- 客服 URL 验证请求能够返回正确明文（可不发 code）。
- 客服事件回调经 `decrypt` 能够返回可信 `organId`、明文通知与 code。
- IAM 在受信服务网络内直连 Member，成功完成首次创建与再次命中，并由 `token` 返回 MEMBER Token。
- 无效、停用或跨租户绑定不能获得可信上下文。
- 未认证调用被拒绝。
- 响应和日志中不出现任何密钥材料。

## 14. 实现清单

- [ ] 明确微信客服接入模式、回调接收方及凭据来源。
- [ ] 定义 `callbackType`、`bindingCode` 和凭据解析接口。
- [ ] 将密码学处理从固定配置读取中解耦。
- [ ] 增加独立时间窗口校验和防重放能力。
- [ ] 实现客服回调凭据解析及可信 `organId` 映射。
- [ ] 实现 `WeComCustomerServiceController#decrypt`（`POST /auth/wecom/customer_service/decrypt`）及 DTO/VO/错误码。
- [ ] 实现 `MemberAuthorizeController#token`（`POST /auth/member/token`）及 DTO/VO/错误码。
- [ ] 实现 `SessionType=MEMBER` Token 签发与按会话复用。
- [ ] 实现 IAM→Member 服务发现直连及超时/重试策略，并验证 Member 仅在受信服务网络可达。
- [ ] 使用平台调用方认证保护 `decrypt` 与 `token`。
- [ ] 让现有授权回调委托通用验证器并完成回归测试。
- [ ] 增加客服 `decrypt` / `token` 单元与集成测试。
- [ ] 与企业微信智能客服模块联调：`decrypt` → `sync_msg` → `token` → MEMBER Token。
- [ ] 与 `g2rain-member` 联调受信网络内无鉴权直连的 `resolveOrCreate`（约定调用方仅 IAM）。

## 15. 参考资料

- [企业微信：接收消息和事件、读取微信客服消息](https://developer.work.weixin.qq.com/document/path/94670)
- [现有企业微信扫码登录设计](./wecom-qr-login.md)
- [g2rain-member 企业微信客户接入会员](../../../g2rain-member/docs/design/wechat-work-customer-member-onboarding.md)
