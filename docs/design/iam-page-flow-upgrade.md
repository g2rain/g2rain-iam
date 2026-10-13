# IAM 页面与授权流程解耦升级方案

状态：**已收敛（站内只认 tid）**  
范围：IAM 的登录、注册、IdP 回调、用户选择、应用授权确认、默认业务侧入口与退出流程。  
本方案由 `docs/project.yaml` 的 `aiCoding.activeRequirement` 授权实施。

## 1. 背景与问题

IAM 当前页面可区分为首页、登录、注册、用户选择/授权确认、退出和错误页，但 OAuth 上下文
`clientId`、`redirectUri`、`state`、`applicationCode` 被反复放在 URL 与隐藏表单中传递。

这导致以下问题：

- 登录、注册、IdP、选用户和确认页相互了解 OAuth 参数，注册回登录已有遗漏 `applicationCode` 的风险；
- 有 `applicationCode` 的授权上下文写入单个浏览器 Session，多标签页并发会互相覆盖；
- Session 保存了 `state`，确认时却未把表单 `state` 与已保存值比对；
- `ModelAndViewService` 同时处理页面选择、跳转 URL、应用开通和授权码签发，职责过重；
- 默认业务侧入口需要先让业务侧生成 Client DPoP 上下文，IAM 不应把用户带到无 `clientId` 的直接登录页；
- 退出只删除 IAM 浏览器 Session，已签发 JWT 和刷新能力仍按原生命周期有效。

## 2. 目标与非目标

### 2.1 目标

- 让每个 IAM 页面只承担明确的交互职责，不持有或拼接完整 OAuth 上下文；
- 以服务端短期授权事务承载浏览器流程：首次全参数进入，之后只认 `tid`；
- 用轻量流程 Cookie 防止复制 `tid` 到其他浏览器继续；同浏览器多标签靠不同 `tid` 隔离；
- 保持业务侧先生成 Client DPoP / `clientId`，再顶层进入 `/auth/authorize` 的现有方向；
- 明确“本浏览器退出”与“全局退出”的不同安全语义；
- 不改变 Basis、Gateway、Main Shell 的职责归属。

### 2.2 非目标

- 不在 IAM 内实现 Main Shell 菜单、Tab、子应用治理或业务页面；
- 不将 Gateway 的最终 API 鉴权迁入 IAM；
- 不改变 JWT claims、DPoP 算法或现有 IdP 供应商协议，除非后续独立需求批准；
- 不在本方案内引入 `client_credentials`、PKCE、PAR/交接凭据、独立“初始化事务”API 或新的 OAuth Client 主数据；
- 不在创建授权事务时校验 Client DPoP（与现状一致；持钥证明仍在 `/auth/token` 换票时校验）。

## 3. 目标页面职责

| 页面/入口 | 目标职责 | 不再承担的职责 |
| --- | --- | --- |
| IAM 首页 | 会话概览、退出、转入默认业务侧 | 直接拼接业务侧地址、直接进入无 `clientId` 的登录 |
| 默认业务侧入口 `/auth/platform` | 后端按 `platform-base-url` 302 至默认业务侧 | 生成 Client DPoP 或代替业务侧创建客户端 |
| `/auth/authorize` | 全参数首次建事务，或仅 `tid` 续跑并按状态分流 | 在后续页面间透传完整 OAuth 参数 |
| 登录页 | 账号密码和 IdP 身份认证 | 透传完整 OAuth 参数、判断业务侧回调地址 |
| 注册页 | Passport 注册与回到同一授权事务 | 保存 OAuth 参数、决定业务侧默认地址 |
| 用户选择页 | 从当前 Passport 可用用户中选择身份 | 应用开通、发授权码 |
| 授权确认页 | 展示目标应用并确认/拒绝 | 解析 URL 中的回调地址和 state |
| 退出页 | 展示退出结果 | 伪报服务端会话删除成功 |
| 错误页 | 展示安全的流程错误和重试操作 | 自动跳转任意外部地址 |

## 4. 目标流程

```text
浏览器 → IAM /auth/platform
IAM → 默认业务侧 /main/home
业务侧 → 生成 Client DPoP 密钥对与 clientId（本地，不经 IAM）
业务侧 → 顶层进入 IAM GET|POST /auth/authorize
         （全参数：clientId、redirectUri、state、applicationCode? …）
IAM → 创建 authorization transaction，冻结参数，建立或复用 flow Cookie，绑定到 tid
IAM → 303 到下一页面，URL 仅携带 tid
  → 登录 / 注册 / IdP（页面与表单只带 tid）
  → 用户选择（如需要）
  → 应用授权确认（仅当 openPlatformConsent=true，即 PUBLIC/PRIVATE）
  → 原子完成事务，签发 code（或拒绝）
  → redirect 冻结的 redirectUri?code=...&state=...（或 error=access_denied）
  → 清除本事务数据（终态保留至 TTL 仅用于幂等读，或立即删除后不可再推进）
业务侧 → /auth/token（Client DPoP + Application DPoP；与现状一致）
```

### 4.1 `/auth/authorize` 入口约定

| 请求形态 | 含义 | 行为 |
| --- | --- | --- |
| **全参数、无 `tid`** | 首次进入 | 校验必要参数 → 创建事务 → 设/复用 flow Cookie → PRG，后续只带 `tid` |
| **仅 `tid`（可带无关噪声参数则忽略或拒绝）** | 已在流转 | 校验 `tid` + flow Cookie → 按事务状态渲染或跳转 |
| **既无合格全参数、也无有效 `tid`** | 非法 | 错误页 |
| **同时带全参数与 `tid`** | 歧义 | 以 `tid` 为准续跑；忽略全参数，防止用 URL 改写已冻结上下文 |

IAM 过程中除本次「首次全参数建事务」外，**所有页面只接收 `tid`**；缺少、无效、或 Cookie 不匹配一律错误页，须由业务侧重新以全参数进入 `/auth/authorize`。

`/auth/platform` 仍只是到默认业务侧的后端跳转。业务侧负责生成 Client DPoP 上下文后再带 `clientId` 等进入 authorize；IAM 不接受无 `clientId` 的直接登录作为授权入口。

创建事务时**不**校验 Client DPoP Header（顶层跳转也无法携带）。持钥与 `clientId`（DPoP `kid`）一致性仍在换票时校验，与现网一致。

## 5. 授权事务设计

新增短期 `AuthorizationTransaction`，存储于 Redis，建议 TTL 为 10 分钟：

| 字段 | 说明 |
| --- | --- |
| `tid` / `transactionId` | 高熵随机、不含业务含义；IAM 页面间唯一传递的授权引用 |
| `applicationCode` | 可选的目标应用编码；原始请求值冻结保存（审计） |
| `openPlatformConsent` | 可空；有 `applicationCode` 时查 Basis `applicationType` 后冻结：`true`=PUBLIC/PRIVATE 须确认，`false`=SUPPORT/SYSTEM 忽略 |
| `clientId` | 业务侧动态生成的 Client DPoP 客户端标识；发码时绑定，换票时与 DPoP `kid` 比对 |
| `redirectUri` | 首次请求提供的回调地址；本阶段不做强校验，后续不可改写 |
| `state` | 原样保存，仅从事务回传，不再信任表单值 |
| `flowCookieHash` | 浏览器流程 Cookie 的哈希，禁止跨浏览器续跑 |
| `authorizationMode` | `USER` 或 `ANONYMOUS`；可由首次全参数中的既有 `state=anonymous` 约定解析 |
| `sessionId` | 登录完成后绑定，用于防止跨会话确认 |
| `selectedUserId` | 用户选择完成后绑定并校验归属 |
| `activationOperationId` / `activationLeaseUntil` | SELF 开通的幂等操作标识和短租约 |
| `status` | `CREATED`、`IDP_PENDING`、`AUTHENTICATED`、`USER_SELECTED`、`CONSENT_REQUIRED`、`ACTIVATING`、`DENIED`、`COMPLETED`、`CANCELLED`、`FAILED` |
| `createdAt` / `expiresAt` | 审计与过期控制 |

`ANONYMOUS`：首次建事务后可直接发码并 redirect，不进入 Session / 选用户 / 确认。  
`USER`：有 `applicationCode` 时先查 Basis `applicationType` 写入 `openPlatformConsent`——`false` 或无码则沿用直接发码或选用户后发码；`true`（PUBLIC/PRIVATE）则确认页 + SELF 开通后发码；查失败则错误页。详见 [open-platform-oauth-upgrade.md](open-platform-oauth-upgrade.md)。

离开 IAM（成功发码或拒绝回调）时：使用事务内冻结的 `redirectUri` 与 `state` 做 redirect，并将本事务标为终态、从流程索引移除；**出站后页面不得再依赖该 `tid` 推进业务**。终态记录可保留至 `expiresAt`，仅用于重复提交返回既有结果、禁止再次发码。

### 5.1 浏览器流程 Cookie（轻量）

目的：复制仅含 `tid` 的链接到**另一浏览器**时不得继续。

- Cookie 名：`G2RAIN_AUTH_FLOW`
- 属性：`HttpOnly; Secure; SameSite=Lax; Path=/auth`
- Redis 只存哈希到事务的 `flowCookieHash`
- 同浏览器多标签：各首次全参数各自生成 `tid`；若请求已带合法 flow Cookie 则**复用**其值写入新事务，禁止无条件换发覆盖导致其它标签失效
- 维护 `flowCookieHash → 未完成 tid` 短期索引；单个事务完成/拒绝/过期只移除该项；无未完成事务时清 Cookie；本浏览器退出则取消索引下全部未完成事务并清 Cookie

首次全参数进入 `/auth/authorize`（业务域顶层跳到 IAM，通常为 GET）时即可 `Set-Cookie`；后续 IAM 同源页面请求与 IdP 顶层 GET 回调在 Lax 下可携带该 Cookie。

流程内写操作校验 `tid + flowCookie`，不额外引入页面级 CSRF Token。不引入独立初始化 API、一次性交接凭据或 `SameSite=None`。

### 5.2 状态迁移、重入与浏览器后退

事务 TTL 固定 10 分钟，自创建起算、不因跳转续期。TTL 后查无或 `expiresAt` 已过即视为过期，展示须由业务侧重新全参数进入；不要求把已删记录再写为 `EXPIRED`。状态迁移用 Redis Lua 比较更新；终态不可回退。GET 只按服务端状态渲染或跳转；改状态用 POST 后重定向（PRG）。

| 当前状态 | 事件 / 条件 | 新状态 | 下一步 |
| --- | --- | --- | --- |
| `CREATED` | `ANONYMOUS` | `COMPLETED` | 原子签发匿名授权码并 redirect |
| `CREATED` | 已有有效 IAM Session | `AUTHENTICATED` | 进入用户判定 |
| `CREATED` | 账号密码登录成功 | `AUTHENTICATED` | 进入用户判定 |
| `CREATED` | 账号密码登录失败 | `CREATED` | 登录页显示安全错误 |
| `CREATED` | 无登录态，发起 IdP | `IDP_PENDING` | 登录页或 IdP |
| `CREATED` | 本地注册失败 | `CREATED` | 注册页显示安全错误 |
| `CREATED` | 本地注册成功 | `CREATED` | 回登录页，同一 `tid` |
| `IDP_PENDING` | IdP 回调校验成功 | `AUTHENTICATED` | 进入用户判定 |
| `IDP_PENDING` | 可重试的 IdP 失败 | `CREATED` | 回登录页，重新生成 `idp_state` |
| `AUTHENTICATED` | 无可用用户 | `FAILED` | 错误页 |
| `AUTHENTICATED` | 恰有一个可用用户 | `USER_SELECTED` | 继续授权分流 |
| `AUTHENTICATED` | 有多个可用用户 | `AUTHENTICATED` | 用户选择页 |
| `AUTHENTICATED` | 用户提交可用用户 | `USER_SELECTED` | 继续授权分流 |
| `USER_SELECTED` | 无码或 `openPlatformConsent=false` | `COMPLETED` | 原子发码并 redirect（不绑定应用） |
| `USER_SELECTED` | `openPlatformConsent=true` | `CONSENT_REQUIRED` | 授权确认页 |
| `CONSENT_REQUIRED` | 用户拒绝 | `DENIED` | redirect `error=access_denied&state=...` 并清推进能力 |
| `CONSENT_REQUIRED` | 用户确认 | `ACTIVATING` | 调用 Basis SELF 开通 |
| `ACTIVATING` | 开通成功并与发码同原子提交 | `COMPLETED` | redirect 带 code |
| `ACTIVATING` | Basis 失败、租约超时，或开通已成功但未与 `COMPLETED`/发码同原子提交 | `CONSENT_REQUIRED` | 可重试，复用同一 `activationOperationId` |
| 任一非终态 | TTL 到期 | （无持久迁移） | 过期页 |
| 任一非终态 | 本浏览器退出 | `CANCELLED` | 退出页 |

`COMPLETED`、`DENIED`、`CANCELLED`、`FAILED` 为终态。重复提交只返回既有结果或安全错误，绝不可再次发码。

浏览器后退/刷新/`tid` 重开：不信任历史页，按当前服务端状态前进；已完成不重新发码。

### 5.3 IdP 回调与退出并发

1. 终态优先不可改写；过期靠 `expiresAt` / 键不存在判断；
2. 本浏览器退出：原子取消该 `flowCookieHash` 下全部非终态事务及对应 `idp_state`，再删 Session 与 Cookie；
3. IdP 回调经 `idp_state` → `tid` 后须校验 flow Cookie、未终态、未过期；否则拒绝且不建立登录态；
4. 认证成功时先建候选 Session，再原子校验事务与流程绑定、写入 `sessionId` 并迁到 `AUTHENTICATED`；失败删候选 Session；
5. 发码与 `COMPLETED` 同一原子操作。退出晚于完成时不撤销已发码的既有语义；现网换票仍要求 Session 存在，删 Session 后该码不能继续换票。不在本方案引入全局 Token 撤销。

### 5.4 `applicationCode` 与 `clientId`

| 标识 | 用途 |
| --- | --- |
| `applicationCode` | 目标应用上下文；开放平台路径用于确认、Application DPoP、Token；页面应用路径忽略 |
| `openPlatformConsent` | 是否需要开放平台确认（由 Basis `applicationType` 解析） |
| `clientId` | 本次浏览器 Client DPoP 客户端；发码绑定，换票与 DPoP `kid` 一致 |

Basis 不保存 `tid`，不为动态 `clientId` 登记回调。本阶段不以 `applicationCode + redirectUri` 强校验创建。回调白名单等属后续协议升级。

### 5.5 `tid` 与业务 `state`

```text
业务侧 state  ←→  IAM tid
```

| 字段 | 创建方 | 用途 | 最终业务回调 |
| --- | --- | --- | --- |
| `tid` | IAM | 页面跳转与状态机 | 否 |
| `state` | 业务侧 | loginAttempt / CSRF | 是，原样回传 |

页面表单不得提交 `redirectUri` / `state` / `applicationCode` 参与回调。IdP 另用 `idp_state`，映射到 `tid`，不得覆盖业务 `state`。

双轨期可继续兼容空 `state` 与 `state=anonymous|...`；创建时解析为 `authorizationMode` 与实际业务 state。新页面链路禁止再让业务 `state` 表达模式以外的 IAM 控制语义。

同一有效期内，同一 `authorizationMode + applicationCode + clientId + state` 建议仅一个未完成事务；重复全参数进入可返回已有 `tid`（须 flow Cookie 匹配）或明确拒绝——实施时选定一种并固定。

规则摘要：

1. 状态变更与发码用 Redis Lua 原子比较更新；
2. 同一 `tid` 只能成功完成一次；
3. 除首次全参数建事务外，表单只提交 `tid` 与该页必要输入；
4. 每次推进校验 `tid + flowCookie`；
5. Session 只承载登录身份，不再存 `oauth*` 待确认参数；
6. `USER` 授权码绑定 `clientId`、Session；仅 `openPlatformConsent=true` 时额外绑定 `applicationCode`（TTL：开放平台 5 分钟，否则 10 分钟）；`ANONYMOUS` 沿用无 Session 语义。

## 6. 认证、鉴权与 DPoP 边界

- IAM 认证：账号密码和 IdP 建立 HTTP-only Session；
- IAM 授权：事务校验选用户、确认意图、SELF 开通，再发码；
- Token：继续要求 Client DPoP + Application DPoP；`kid` 与码上 `clientId` 一致（有 `applicationCode` 时 `acd` 匹配）——**不**要求在 `/authorize` 创建时验 DPoP；
- Gateway / 领域服务：继续做最终 API 鉴权；
- `redirectUri`：本阶段沿用宽松接收；成功/拒绝回调只能用事务内地址。该决定相对“精确匹配”基线为阶段性偏差，上线须记录；
- IAM-001 / IAM-002（jti 重放、htu 规范化）独立推进，不作为本页面事务化的隐含前提。

## 7. 退出方案

### 7.1 本浏览器退出

- 仅保留 `POST /auth/logout`；GET 仅确认页或 405；
- 校验同源 `Origin/Referer` 后，原子取消当前 flow Cookie 下未完成事务及 IdP state，删除相关 Session，清除 Cookie；
- 删除失败不得展示“退出成功”；
- 退出页只回 IAM 首页或 `/auth/platform`，不自动跳事务 `redirectUri`。

### 7.2 全局退出（后续阶段）

需跨仓库：JWT `sid`/会话版本、IAM 撤销状态、Gateway 校验、刷新与 API 一致拒绝、失效窗口与回滚。本方案不实施。

## 8. 分阶段迁移

1. **准备**：事务模型、Redis Key、Lua、错误码、可观测性；保留 redirectUri / applicationCode 接收契约；
2. **双轨（已完成）**：`/auth/authorize` 支持全参数建 `tid` + 仅 `tid` 续跑；
3. **切换（已完成）**：登录/注册/选用户/确认/IdP 落地只带 `tid`；IdP state 绑定事务；
4. **收敛（已完成）**：移除 Session `oauth*`、隐藏域全参数、旧跳转分支与灰度回退开关；
5. **退出加固**：POST + Origin/Referer；全局退出另立需求。

回滚：未完成事务自然过期；已发码与 Token 语义不变。不再提供“关闭 tid、恢复页面间全参数透传”的运行时开关。

## 9. 威胁模型与验收

- 建事务后篡改 redirectUri、applicationCode、state、用户 ID 必须失败（只从事务读）；
- 复制仅含 `tid` 到另一浏览器必须失败（无/错 flow Cookie）；
- 两标签全参数进入不同应用：两个 `tid`，互不覆盖（Session 不再承载 oauth*）；
- 同浏览器复用 flow Cookie 时，不得因第二次建事务换发 Cookie 打穿第一标签；
- 缺 `tid`、无效 `tid`、Cookie 不匹配的 IAM 内页必须错误页；
- transaction、IdP state、授权码重复使用必须失败；
- 账号密码、注册、钉钉/企微回调均能回到同一 `tid`；匿名不建 Session 可完成；
- 无 `clientId` 的授权入口必须拒绝；`applicationCode` 可选；默认入口先去业务侧；
- 出 IAM 后 redirect 正确且该 `tid` 不可再推进；本浏览器退出后未完成事务与迟到 IdP 回调不得恢复登录态；
- IAM、Basis、Gateway、Main Shell 与至少一个真实业务客户端端到端联调。

## 10. 配置、兼容与发布

- 新增：transaction TTL、流程索引 TTL；**不**新增交接凭据 TTL、初始化 CORS Origin 白名单、双轨灰度开关（本方案无该入口且已收敛）；
- 流程 Cookie：`G2RAIN_AUTH_FLOW`，`HttpOnly; Secure; SameSite=Lax; Path=/auth`；
- Secret、私钥、IdP 凭据和 Cookie 原值不进日志、Git、错误页；
- Token claims、Cookie 名、DPoP、授权码、IdP 回调属跨仓库契约；业务侧发起方式保持顶层进入 `/auth/authorize` 全参数，站内只认 `tid`；
- 发布顺序：IAM tid 链路 → Main Shell 去掉多余透传（若有）→ Basis SELF 联调 → 全局退出另议；
- 回滚不撤销已签发有效 Token。
