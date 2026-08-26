# 企业微信客服回调认证升级设计

## 1. 文档目的

本文定义 `g2rain-iam` 为企业微信智能客服回调提供渠道认证能力所需的升级内容。本阶段只形成设计，不修改代码和运行配置。

关联会员流程：[`g2rain-member` 企业微信智能客服会员识别流程](../../../../codes/g2rain/g2rain-member/docs/design/wechat-work-smart-customer-service-member-identification.md)。

## 2. 设计结论

`g2rain-iam` 负责确认“回调确实来自企业微信，并属于哪个可信租户”；企业微信接入模块负责客服协议和消息处理；`g2rain-member` 负责会员身份绑定。

```text
企业微信客服回调
        ↓
企业微信接入模块
        ↓ 原始签名参数与密文
g2rain-iam 回调认证能力
        ↓ 已验证的回调明文与租户上下文
企业微信接入模块解析 kf_msg_or_event
        ↓
调用 sync_msg 拉取完整消息
        ↓
g2rain-member 识别会员
```

IAM 不读取或保存客服消息正文，不调用 `/cgi-bin/kf/sync_msg`，不创建 `member`、`passport` 或会员 Session。

## 3. 现有能力

当前项目已经具备以下基础：

- [`WeComIamProperties`](../../src/main/java/com/g2rain/iam/config/WeComIamProperties.java) 配置内部应用、第三方服务商、Suite 和回调密钥。
- [`WeComCallbackCrypto`](../../src/main/java/com/g2rain/iam/wecom/WeComCallbackCrypto.java) 实现企业微信 SHA-1 签名校验、AES 解密、PKCS#7 去填充和接收方校验。
- [`WeComAuthorizationCallbackController`](../../src/main/java/com/g2rain/iam/controller/WeComAuthorizationCallbackController.java) 接收第三方应用授权回调。
- [`WeComAuthorizationService`](../../src/main/java/com/g2rain/iam/service/WeComAuthorizationService.java) 处理 `suite_ticket`、`create_auth`、`change_auth` 和 `cancel_auth`。
- 企业微信授权信息可通过 Basis 的企业应用授权能力关联企业与租户。

现有实现必须继续兼容，不因新增客服回调认证而改变授权安装、更新和取消流程。

## 4. 当前差距

| 项目 | 当前实现 | 客服回调需要 |
|---|---|---|
| 密钥选择 | 固定读取 `thirdParty.token` 和 `thirdParty.encodingAesKey` | 按回调场景和接入绑定选择凭据 |
| 接收方校验 | 固定要求接收方等于 `suiteId` | 按客服接入模式校验 CorpID、SuiteID 或配置的期望值 |
| 事件范围 | 第三方授权事件 | `kf_msg_or_event` 等客服通知 |
| 时间校验 | 签名计算包含时间戳，但未独立限制时间窗口 | 拒绝超出允许偏差的请求 |
| 防重放 | 无独立 nonce/签名重放记录 | 在短时间窗口内拒绝重复认证请求 |
| 调用方式 | 授权回调控制器内直接使用组件 | 为企业微信接入模块提供受保护的内部能力 |
| 租户上下文 | 授权服务处理企业授权 | 验证后返回可信 `organId` 和企业标识 |

微信客服的回调通知只表示存在新消息或事件。`external_userid` 和具体消息内容由企业微信接入模块调用 `/cgi-bin/kf/sync_msg` 后取得，不属于 IAM 回调认证结果。

## 5. 职责边界

### 5.1 IAM 负责

- 管理或安全访问企业微信回调 `Token`、`EncodingAESKey`。
- 根据不可伪造的接入绑定标识选择回调配置。
- 校验签名、时间戳、随机串和密文结构。
- 解密回调，并校验明文接收方与配置一致。
- 根据已验证企业标识和有效授权关系解析 `organId`。
- 返回最小化的可信回调结果。
- 记录不包含密钥和明文正文的安全审计日志。

### 5.2 IAM 不负责

- 不解析或处理客服业务消息。
- 不调用 `sync_msg`、`send_msg` 等微信客服业务接口。
- 不持久化拉取游标、客服消息、对话或工单。
- 不根据 `external_userid` 查询或创建会员。
- 不把微信客户注册成 `passport`。
- 不给微信客户签发 IAM 登录 Session 或用户 Token。

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
8. 返回最小化的已验证结果。

现有 `decryptMessage`、`decryptEcho` 可以保留为兼容入口，但内部应委托新的通用验证器，并继续使用 `THIRD_PARTY_AUTHORIZATION` 凭据。

## 8. 内部接口契约

如果企业微信接入模块独立部署，IAM 可提供仅限内部调用的接口。接口名称遵循项目最终规范，建议语义为：

```text
POST /internal/wecom/callback/verify
```

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
  "verifiedAt": "2026-08-20T10:00:00+08:00"
}
```

约束：

- 接口必须经过平台现有的内部应用认证，不能公开匿名调用。
- `verified` 为 `false` 时不返回明文和租户信息；也可以直接返回统一业务错误。
- 响应不得返回回调密钥、企业访问令牌或永久授权码。
- `organId` 必须由 IAM 根据已验证配置和授权关系产生，不能原样回显请求参数。
- `plainBody` 仅用于接入模块解析通知，不得在 IAM 日志中输出。

如果接入模块与 IAM 同进程部署，可使用等价的内部 Java Service，避免不必要的 HTTP 调用，但职责和返回契约保持一致。

## 9. 客服回调处理链路

IAM 返回可信结果后，企业微信接入模块执行：

1. 解析解密 XML，确认 `MsgType=event`、`Event=kf_msg_or_event`。
2. 读取回调中的拉取令牌和 `OpenKfId`。
3. 使用对应企业的微信客服访问凭据调用 `/cgi-bin/kf/sync_msg`。
4. 维护拉取游标并处理企业微信要求的重试。
5. 按 `msgid` 幂等消费完整消息。
6. 将可信 `organId`、`external_userid` 和脱敏资料交给 `g2rain-member`。

该链路的第 1 至 6 步均不扩展 IAM 的会员认证职责。

## 10. 租户解析规则

租户解析必须同时满足：

- 回调凭据绑定处于启用状态。
- 解密后的企业标识与绑定配置一致。
- 企业微信应用授权状态有效。
- 授权记录能够唯一映射到一个 `organId`。
- 绑定的应用或客服能力与本次回调类型一致。

无法唯一确定租户时应拒绝回调认证，不允许回退到默认租户。

## 11. 安全要求

- 限制请求体大小，拒绝畸形 XML、缺失 `Encrypt` 或超长参数。
- 保持 XML 解析器禁用 DTD、外部实体、XInclude 和实体展开。
- 签名继续使用常量时间比较。
- 对时间窗口、重放缓存过期时间和最大时钟偏差提供配置项。
- 日志仅记录请求追踪标识、回调类型、绑定标识、验证结果和脱敏企业标识。
- 禁止记录 `Token`、`EncodingAESKey`、Secret、永久授权码、访问令牌和解密正文。
- 内部验证接口实施限流、调用方认证和超时保护。
- IAM 不长期保存解密后的回调正文。

## 12. 兼容与迁移

- 第三方授权回调 URL 和响应格式保持不变。
- `suite_ticket`、授权安装、授权变更和取消授权逻辑保持不变。
- 先用新验证器替换现有授权回调内部实现并完成回归测试，再开放客服回调验证能力。
- 新增客服凭据配置时不得复用名称含义不一致的 `thirdParty.token`，除非已经确认两个回调场景在企业微信后台确实共享同一组配置。
- 配置缺失时只禁用对应回调类型，不影响其他企业微信登录和授权能力。

## 13. 测试要求

### 13.1 单元测试

- 正确签名和密文能够解密。
- 签名错误、AES Key 错误、填充错误、接收方错误均被拒绝。
- 时间戳过期或超前超出窗口时被拒绝。
- 相同回调指纹在防重放窗口内再次出现时被拒绝。
- 不同 `callbackType` 选择不同凭据。
- `CUSTOMER_SERVICE` 不会误用 Suite 授权凭据。
- XML 外部实体与 DTD 被拒绝。

### 13.2 集成测试

- 现有第三方授权回调完整回归。
- 客服 URL 验证请求能够返回正确明文。
- 客服事件回调能够返回可信 `organId` 和明文通知。
- 无效、停用或跨租户绑定不能获得可信上下文。
- 内部接口未认证调用被拒绝。
- 响应和日志中不出现任何密钥材料。

## 14. 实现清单

- [ ] 明确微信客服接入模式、回调接收方及凭据来源。
- [ ] 定义 `callbackType`、`bindingCode` 和凭据解析接口。
- [ ] 将密码学处理从固定配置读取中解耦。
- [ ] 增加独立时间窗口校验和防重放能力。
- [ ] 实现客服回调凭据解析及可信 `organId` 映射。
- [ ] 定义内部验证 API/Service、DTO、VO 和错误码。
- [ ] 使用平台内部应用认证保护验证接口。
- [ ] 让现有授权回调委托通用验证器并完成回归测试。
- [ ] 增加客服回调认证单元与集成测试。
- [ ] 与企业微信接入模块联调 `kf_msg_or_event` 通知。
- [ ] 与 `g2rain-member` 联调可信会员解析请求。

## 15. 参考资料

- [企业微信：接收消息和事件、读取微信客服消息](https://developer.work.weixin.qq.com/document/path/94670)
- [现有企业微信扫码登录设计](./wecom-qr-login.md)
