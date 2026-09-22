# 应用授权确认（OAuth 统一确认）

状态：**已实施（统一确认，无勾选分叉）**  
范围：复用现有 `clientId + Client DPoP + Application DPoP + authorization_code` 链路；所有应用共用同一套确认发码流程；Basis 复用 `application_authorization` 做目标应用开通事实；Gateway 保持现有 Token/DPoP 消费职责。

本轮明确不做回调地址白名单：`redirectUri` 接受任意地址。P2 其余项（DPoP `jti`/`htu` 重放保护、撤销/审计/配额）仍未做。

## 1. 背景

当前 IAM 已有浏览器授权码主线：`GET /auth/authorize` → 授权确认 → `POST /auth/authorize_selected` → `POST /auth/token`。授权码存入 Redis，默认有效期十分钟；换票时原子读取并删除，并校验发码时绑定的 `clientId`。

该链路服务于平台与外部应用登录。`applicationCode` 表示本次授权的目标应用（不再区分「是否开放平台」）；有该参数时确认后自动开通目标应用全部 `SELF` 控制域，再发码。

## 2. 目标与边界

目标：用户在统一 `consent` 页确认或拒绝；带 `applicationCode` 时展示应用名称与描述，确认后自动开通全部 `SELF` 并发放 code；无 `applicationCode`（Main Shell）同样需确认，但不写开通。

非目标：

- IAM 不拥有应用、组织、套餐、租户开通、API 能力或订单主数据。
- IAM 不替代 Gateway 或领域服务的最终 API 权限判定。
- 本方案不新增 `oauth_client`、`tenant_oauth_client_grant`、`client_credentials` 或 PKCE。
- 确认页不勾选控制域；不做回调白名单。

## 3. 领域协作与数据归属

| 事实 | 所属 | IAM 使用方式 |
| --- | --- | --- |
| 应用、能力域、租户开通状态 | Basis：`application`、`control_domain`、`application_authorization` | 发码、换票和刷新时经受信 API 查询并校验 |
| 客户端实例与持钥证明 | 现有调用方生成 `clientId` 和 DPoP 密钥对 | IAM 将 code 绑定到 `clientId`，Token 绑定 Client DPoP 公钥 |
| 会话、授权交互、短期 code、DPoP、Token | IAM | Redis 保存短期授权事务；JWT 由 IAM 签发 |
| API 路由、请求 DPoP、接口级最终授权与限流 | Gateway / 领域服务 | 消费 JWT 与受信主体上下文 |

### 3.1 自助开通控制域（SELF）

`ControlDomainType.SELF` 表示允许租户管理员在确认授权时由系统自动开通。确认时：

1. Basis 校验当前用户属于目标租户且为管理员；
2. 查询该应用全部 `SELF` 控制域；
3. 逐个调用既有 `ApplicationAuthorizationService.save`（幂等）；
4. 无 `SELF` 时成功返回且 `authorizationIds` 为空，IAM 仍发码。

机构创建时仍跳过 `SELF`；`fetchTokenContext` 对非默认主应用继续校验目标应用开通；主应用 `application_suite` 展开规则不变。

## 4. 目标授权码流程

```text
Client → GET /auth/authorize
IAM → 登录后一律进入统一 consent（禁止单用户自动发码）
  有 applicationCode → Basis preview（名称/描述）→ 用户确认或拒绝
  无 applicationCode → 仅选用户并确认或拒绝
拒绝 → 错误回调，不写开通、不发 code
确认 + applicationCode → Basis 自动开通全部 SELF → 发码（5 分钟 TTL，绑定 applicationCode）
确认无 applicationCode → 按既有逻辑发码（10 分钟 TTL）
```

## 5. 协议要点

| 参数 | 规则 |
| --- | --- |
| `clientId` / `redirectUri` / `state` | 既有契约；`redirectUri` 接受任意地址 |
| `applicationCode` | 可选；有则展示应用描述、确认时自动开通 SELF，换票时 Client DPoP `acd` 必须匹配 |

Basis 受信 API（仅开通，无独立 preview）：
`POST /application_authorization/activate_self`（自动开通全部 SELF）。
consent 页预览由 IAM 调用既有 `/application/list`、`/organ/id_name_map` 本地拼装。

## 6. 安全控制

1. 回调地址本轮接受任意 `redirectUri`。
2. 换票须通过 Client DPoP 与 Application DPoP；code 绑定的 `applicationCode` 须等于 `acd`。
3. 有 `applicationCode` 时将 OAuth 参数绑定到 IAM `SessionDto`（`oauthClientId` 等），确认时校验一致，拒绝跨会话篡改；无独立授权事务 Redis key。
4. 用户拒绝不写 `application_authorization`、不发 code。
5. 管理员校验在确认开通（`activate_self`）时执行，预览阶段不强制。

## 7. 验收清单

- 单用户有/无 `applicationCode` 均不自动发码，须点确认。
- 确认页不勾选控制域；有 `applicationCode` 时仅展示名称与描述。
- 确认自动开通全部 `SELF`；无 `SELF` 仍可发码。
- 拒绝不发码、不写开通；`acd` 不匹配失败；无 `applicationCode` 路径保持兼容。
