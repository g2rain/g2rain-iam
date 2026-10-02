# 应用授权确认（OAuth 按应用类型分流）

状态：**已实施（有 applicationCode 时查 Basis `applicationType`；仅 PUBLIC/PRIVATE 须确认并开通 SELF）**  
范围：复用现有 `clientId + Client DPoP + Application DPoP + authorization_code` 链路；Basis 复用 `application` / `application_authorization`；Gateway 保持现有 Token/DPoP 消费职责。

本轮明确不做回调地址白名单：`redirectUri` 接受任意地址。P2 其余项（DPoP `jti`/`htu` 重放保护、撤销/审计/配额）仍未做。

## 1. 背景

当前 IAM 已有浏览器授权码主线：`GET /auth/authorize` →（按参数分流）→ `POST /auth/token`。授权码存入 Redis；换票时原子读取并删除，并校验发码时绑定的 `clientId`。

此前分流仅看有无 `applicationCode`。本升级改为：有 `applicationCode` 时经 Basis `/application/list` 读取 `applicationType`，再决定是否进入开放平台确认。

| `applicationType` | 含义 | 授权行为 |
| --- | --- | --- |
| `PUBLIC` / `PRIVATE` | 开放平台应用 | consent + `activate_self` + 绑定发码（5 分钟 TTL） |
| `SUPPORT` / `SYSTEM` | 微前端子应用（页面应用） | **完全忽略** `applicationCode`，等同无参 Main Shell 路径 |
| 查不到 / 调用失败 / 类型非法 | — | **失败关闭**（错误页，不发码） |

类型判定复用 Basis `ApplicationType.nonMicroApp`（`PUBLIC`/`PRIVATE` 为 true）。

## 2. 目标与边界

目标：

- 无 `applicationCode`：单用户直接选中并发码回调；多用户先选用户，选定后发码（不强制确认页；10 分钟 TTL，码不绑定应用）。
- 有 `applicationCode` 且类型为 `PUBLIC`/`PRIVATE`：统一 `consent` 页确认或拒绝；展示应用名称与描述；确认后自动开通全部 `SELF` 并发放 code（绑定 `applicationCode`，5 分钟 TTL）。
- 有 `applicationCode` 且类型为 `SUPPORT`/`SYSTEM`：忽略该参数，走无参发码路径；事务仍保留原始 `applicationCode` 供审计，状态机认冻结字段 `openPlatformConsent=false`。
- 有 `applicationCode` 但 Basis 查不到或失败：错误页，不发码、不写开通。

非目标：

- IAM 不拥有应用、组织、套餐、租户开通、API 能力或订单主数据。
- IAM 不替代 Gateway 或领域服务的最终 API 权限判定。
- 本方案不新增 `oauth_client`、`tenant_oauth_client_grant`、`client_credentials` 或 PKCE。
- 确认页不勾选控制域；不做回调白名单。

## 3. 领域协作与数据归属

| 事实 | 所属 | IAM 使用方式 |
| --- | --- | --- |
| 应用、能力域、租户开通状态 | Basis：`application`、`control_domain`、`application_authorization` | 发码、换票和刷新时经受信 API 查询并校验；consent 前用 `applicationType` 分流 |
| 客户端实例与持钥证明 | 现有调用方生成 `clientId` 和 DPoP 密钥对 | IAM 将 code 绑定到 `clientId`，Token 绑定 Client DPoP 公钥 |
| 会话、授权交互、短期 code、DPoP、Token | IAM | Redis 保存短期授权事务；JWT 由 IAM 签发 |
| API 路由、请求 DPoP、接口级最终授权与限流 | Gateway / 领域服务 | 消费 JWT 与受信主体上下文 |

### 3.1 自助开通控制域（SELF）

仅 `openPlatformConsent=true`（开放平台类型）在用户确认后执行。`ControlDomainType.SELF` 表示允许租户管理员在确认授权时由系统自动开通。确认时：

1. Basis 校验当前用户属于目标租户且为管理员；
2. 查询该应用全部 `SELF` 控制域；
3. 逐个调用既有 `ApplicationAuthorizationService.save`（幂等）；
4. 无 `SELF` 时成功返回且 `authorizationIds` 为空，IAM 仍发码。

机构创建时仍跳过 `SELF`；`fetchTokenContext` 对非默认主应用继续校验目标应用开通；主应用 `application_suite` 展开规则不变。

### 3.2 事务冻结字段 `openPlatformConsent`

| 值 | 含义 |
| --- | --- |
| `null` | 尚未解析（仅当原始 `applicationCode` 非空时需要解析） |
| `true` | `PUBLIC`/`PRIVATE` → consent + SELF |
| `false` | `SUPPORT`/`SYSTEM` → 后续按无 `applicationCode` 处理 |

发码与状态机只认 `openPlatformConsent`；原始 `applicationCode` 保留在事务中。

## 4. 目标授权码流程

```text
Client → GET /auth/authorize
IAM → 登录后分流
  无 applicationCode
    单用户 → 直接发码 → redirectUri?code=...（10 分钟，无绑定）
    多用户 → 选用户 → 选定后发码
  有 applicationCode → Basis /application/list 查 applicationType
    查失败 / 无结果 / 类型非法 → 错误页
    SUPPORT / SYSTEM → 忽略码，等同无参发码
    PUBLIC / PRIVATE → consent 预览 → 用户确认或拒绝
拒绝 → 错误回调，不写开通、不发 code
确认 + openPlatformConsent → Basis activate_self → 发码（5 分钟 TTL，绑定 applicationCode）
```

## 5. 协议要点

| 参数 | 规则 |
| --- | --- |
| `clientId` / `redirectUri` / `state` | 既有契约；`redirectUri` 接受任意地址 |
| `applicationCode` | 可选；有则查 Basis 类型；仅开放平台类型展示确认并开通 SELF，换票时 Client DPoP `acd` 须匹配码上绑定值 |

Basis 受信 API（仅开通，无独立 preview）：
`POST /application_authorization/activate_self`（自动开通全部 SELF）。
类型与 consent 预览由 IAM 调用既有 `/application/list`、`/organ/id_name_map` 本地拼装。

## 6. 安全控制

1. 回调地址本轮接受任意 `redirectUri`。
2. 换票须通过 Client DPoP 与 Application DPoP；开放平台路径发码绑定的 `applicationCode` 须等于 `acd`。
3. OAuth 上下文冻结在授权事务（`tid`）；确认时只从事务读取，拒绝跨会话篡改。
4. 用户拒绝不写 `application_authorization`、不发 code。
5. 管理员校验在确认开通（`activate_self`）时执行，预览阶段不强制。
6. `applicationCode` 无法解析为有效应用类型时失败关闭，不得静默降级为无参发码。
7. `confirmApplication` 仅允许 `openPlatformConsent=true` 的事务进入。

## 7. 验收清单

- 无 `applicationCode`：单用户自动发码；多用户选后发码，不强制确认/拒绝页；不调 Basis 查类型。
- `SUPPORT`/`SYSTEM` + `applicationCode`：等同无参；不进 consent、不调 `activate_self`、码不绑定应用。
- `PUBLIC`/`PRIVATE` + `applicationCode`：须点确认才发码；拒绝不发码、不写开通。
- 确认页不勾选控制域；开放平台路径仅展示名称与描述。
- 确认自动开通全部 `SELF`；无 `SELF` 仍可发码。
- Basis 查失败或未知 code：错误页，不发码。
- `acd` 不匹配失败；无参 / 页面应用路径与原 Main Shell 体验兼容。
