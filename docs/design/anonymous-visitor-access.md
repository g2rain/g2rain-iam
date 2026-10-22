# 匿名访客接入方案（接入码 + 密钥派生访客身份）

状态：**已实施，待联调验收**  
日期：2026-10-22（实施后按实际代码同步）  
范围：`g2rain-common`、`g2rain-spring-boot-starter`、`g2rain-iam`、`g2rain-basis`、`g2rain-gateway-webflux` / `g2rain-gateway-webmvc`、`g2rain-mcp-registry`、`g2rain-agent-runtime`、`g2rain-web/g2rain-chat-shell`、`g2rain-web/g2rain-manager-app`  
替代：[Token 签发双模式对齐](./member-token-issuance-alignment.md) 中"匿名会话仍禁止 refresh"的约定（见 §8）；MEMBER 其余设计不变  
相关：[IAM 页面与授权流程解耦](./iam-page-flow-upgrade.md)

## 1. 背景

chat-shell 要支持未登录访客直接对话：没登录就是访客，左下角可以登录；登录后按正常的页面和按钮权限使用；退出后回到原来的访客身份。IAM / Basis 已有一版匿名发码链路，但只适合演示，不能上生产。

### 1.1 现状与问题

| # | 环节 | 现状 | 问题 |
| --- | --- | --- | --- |
| 1 | 进入匿名模式 | `state=anonymous\|csrf`，用业务 `state` 表达 IAM 模式（`AuthorizationState`） | 与"业务 state 不得承载 IAM 控制语义"的约定冲突 |
| 2 | 归属机构 | IAM 全局配置唯一一个 `g2rain.iam.anonymous.organ-id` + `role-ids` | 只能服务一个机构 |
| 3 | 访客身份 | 每次换票随机生成 `userId`、`passportId`（`fetchAnonymousTokenContext`） | token 过期重新授权后变成"另一个人"，历史会话找不回来 |
| 4 | 权限 | 走角色链；`roleIds` 为空时回退为机构 ADMIN 角色（`resolveAnonymousRoleIds`） | 访客可能拿到机构管理员权限 |
| 5 | 管理机构标志 | `adminCompany = organ.admin` | 管理机构的访客会被隔离拦截器整体放行 |
| 6 | 刷新 | IAM 拒绝 ANONYMOUS refresh，但载荷写了 `refreshExpireAt` | 前端以为能刷新，失败后 `redirectToSSO()` 把访客带到员工登录页 |
| 7 | 授权码绑定 | IAM 只把 `clientId` 记到授权事务和 code 上；授权请求里的 `publicKey` 收到后既不保存也不校验 | `clientId` 是公开的，截获 code 的人可以自己生成密钥对、填上同一个 `clientId` 换走 token（员工流程同样存在） |
| 8 | 网关、MCP、Starter 鉴权 | Gateway 把 ANONYMOUS 落到员工 `userPerm` 角色链；`McpHandlerFilter`、`LoginGuardInterceptor` 只要类型是 ANONYMOUS 就放行；`BuiltInApiPermission` 把 ANONYMOUS 落到员工 `userApiPerm` | 没有身份字段校验，权限口径错误 |
| 9 | 会话数据 | chat-shell 直接调用生成的通用 `/chat_conversation/list`、`/chat_message/list`、`DELETE /chat_conversation/{id}`，`selectSummaryList` 不强制 `userId = 当前主体` | 同一机构的员工或访客能列出、删除别人的会话 |

## 2. 目标与非目标

**目标**

- 每个租户可开放自己的匿名入口，并能随时停用；客户端无法冒用其他机构。
- 同一浏览器的访客身份稳定：刷新页面、token 过期、登录后再退出，都还是同一个访客。
- 访客身份不可被复制冒用：只知道访客 ID 不足以成为该访客。
- 匿名权限最小化：平台发布、租户开通、失败即关闭。
- 停用单个接入码、关闭机构匿名能力都能立刻生效。
- 不影响现有的员工登录、SSO、换票、刷新、切换机构，也不影响管理平台按机构查看会话。

**非目标（本期不做）**

- 跨浏览器、跨设备，或清空浏览器数据后保留访客身份（需要登录）；
- 访客登录后合并匿名会话；
- 密钥轮换；
- 私钥改为不可导出、存入 IndexedDB（以后在组件层统一调整）；
- 按接入码固定智能体（避免 basis 耦合 Agent 业务）；
- 来源域名校验；
- 限流、新访客上限、对话配额；
- 访客数据过期清理、访客统计。

## 3. 核心思路

| 问题 | 方案 |
| --- | --- |
| 访客属于哪个机构 | 客户端只携带租户创建的**接入码** `entryKey`，由 Basis 解析出机构和应用 |
| 访客是谁 | 不由客户端声明。IAM 对**验签通过的 DPoP 公钥**计算指纹（RFC 7638），Basis 按"机构 + 指纹"映射为稳定的 visitorId |
| 访客能做什么 | 新会话主体 `ANONYMOUS` 的控制单元，与 MEMBER 同构：平台发布 ∩ 租户开通；不走角色链 |
| 怎么停 | 网关按机构缓存"可用接口 + 启用中的接入码"，通过 `ANONYMOUS_PERM` 同步事件立即失效 |
| 访客能看到哪些数据 | 下游以 `userId = visitorId` 归属数据；chat-shell 改用按当前主体过滤的专用接口 |

身份成立的前提是**持有私钥**：换票、刷新、调用接口都要用私钥签名 DPoP Proof。网关已逐请求校验 Proof 的 `iat / jti / htm / htu`，并比对 Proof 公钥与 token 中 `clientPublicKey` 的指纹。别人拿到访客 ID 或 access token，没有私钥也无法冒充。

## 4. 接入码（Basis + manager-app）

### 4.1 作用

接入码是租户在自己机构下创建的一条记录，表示"允许匿名访客通过某个应用进入我的机构"。它的作用不是隐藏机构 ID，而是：

- **开关：** 租户主动创建才开放，停用即关闭；
- **轮换：** 码被滥用时新建一个，停用旧的；
- **绑定：** 一条接入码只对一个应用有效；
- **单独停用：** 网关能立刻停掉某一个接入码的访客（见 §7）。

接入码不是秘密，会出现在 URL 和前端配置里。防冒充靠的是 DPoP 密钥，不靠接入码保密。

### 4.2 数据模型：`anonymous_entry`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT | 主键，即 `entryId` |
| `organ_id` | BIGINT NOT NULL | 所属机构，必须是租户机构 |
| `application_id` | BIGINT NOT NULL | 适用的应用，如 chat-shell |
| `entry_name` | VARCHAR(64) NOT NULL | 名称，如"官网客服" |
| `entry_key` | VARCHAR(40) NOT NULL，`utf8mb4_bin` | 接入码，`ek_` + 22 位 base62 随机串（约 131 bit），区分大小写 |
| `status` | VARCHAR(32) NOT NULL | `ACTIVE`（有效，默认）/ `INACTIVE`（无效），枚举 `EntryStatus` |
| `delete_flag`、`create_time`、`update_time` | | 平台标准字段 |

- `entry_key` 唯一，采用 [ADR-0004](../../../../docs/architecture/decisions/0004-logical-delete-unique-indexes.md) 的函数索引写法；
- 一个机构、一个应用下的接入码条数不限；
- 创建时校验：机构为有效的租户机构、机构已开通该应用；非运营公司只能为本机构创建；
- 接入码由服务端生成，状态固定为 `ACTIVE`；更新只允许改名称；启停走 `POST /anonymous_entry/{id}/status`。

### 4.3 管理

- 不单独设菜单：在 manager-app「应用授权」列表的每一行提供"匿名接入码"按钮，弹窗内管理这组"机构 + 应用"下的接入码（列表、新增、改名、启停、复制、删除），机构与应用取自当前行；
- 访客数据只读、平时用不到，前端不提供访客页面；
- 接入码启停、删除时，Basis 发出 `ANONYMOUS_PERM` 事件（载荷为 organId）。

### 4.4 解析：`AnonymousEntryService.resolve` 与 `requireOpenOrgan`

- `resolve(entryKey, applicationId)`（换票）/ `resolve(entryId, applicationId)`（刷新）：接入码存在、为 `ACTIVE`、且属于该应用，否则统一返回"接入码不可用"（`basis.40081`），不区分具体原因；
- `requireOpenOrgan(organId, applicationId)`：机构为 `ACTIVE` 的租户机构（管理机构不允许开放匿名入口），且已开通该应用（`application_authorization` 为 `ACTIVATED`）。失败时返回已有的具体错误码（`ORGAN_UNAVAILABLE`、`APPLICATION_AUTHORIZATION_MISSING` 等），便于创建接入码时管理员定位原因；接入码本身是公开的，不会多泄露信息；
- 换票时不单独检查"机构是否已开通 ANONYMOUS 能力"：没开通的访客拿到 token 也调不了任何接口，最终由网关按权限快照拒绝。

## 5. 授权码绑定公钥与访客身份（IAM + Basis）

### 5.1 授权参数

- 授权接口新增参数 `mode`，直接绑定现有枚举 `AuthorizationMode { USER, ANONYMOUS }`，不传默认 `USER`；
- `mode=ANONYMOUS` 时 `entryKey` 必填；`USER` 时忽略；
- `state` 只用于回传 CSRF 值。**删除** `AuthorizationState` 中的匿名解析，不保留对 `state=anonymous` 的兼容；
- 授权事务的 `authorizationMode` 改为从 `mode` 取值。

### 5.2 授权码绑定公钥（员工与匿名同时启用）

1. 授权时，IAM 解析请求中的 `publicKey`，计算指纹 `jkt`，写入授权事务（Redis，`AUTHORIZATION_TRANSACTION:{tid}`）；
2. 发 code 时，把 `jkt` 写入 `AuthorizationCodeDto`（Redis）；
3. 换 token 时，如果 code 带有 `jkt`，就要求 Client DPoP Proof 公钥的指纹与它一致，不一致即拒绝。

兼容规则：

| 情况 | 处理 |
| --- | --- |
| `mode=ANONYMOUS` 未带 `publicKey` | 拒绝 |
| `mode=USER` 未带 `publicKey`（如开放平台第三方客户端） | 不记录 `jkt`，换票时不校验 |
| 带了 `publicKey` 但解析失败，或不是 EC P-256 | 授权时拒绝 |
| 发布前已生成、没有 `jkt` 的授权事务和 code | 换票时不校验，正在登录的用户不受影响 |
| 钉钉、企业微信扫码回调 | 沿用同一授权事务（`tid`），`jkt` 已在事务中，不受影响 |

授权事务的去重 key 从 `mode + applicationCode + clientId + state` 扩展为同时包含 `jkt`，匿名时再加上 `entryKey`，避免复用到别的密钥或别的租户入口的事务。

我们自己的 shell 和 app 授权时都已携带 `publicKey`，并且 `clientId` 与密钥对在 `generateClient()` 中一起生成、一起保存，所以这一项**不会让任何现有前端出现新的失败场景**，前端也无需改动。

### 5.3 指纹计算

指纹是对公钥的 `crv / kty / x / y` 做 SHA-256 后得到的 43 位 base64url 字符串，同一把公钥的指纹永远相同。

- 只接受 ES256（EC P-256）。这是现状：前端只生成 ES256 密钥，IAM、网关也只用 EC 验签；
- IAM 和网关明确要求 `crv = P-256`、`alg = ES256`。现在只检查了"是 EC 公钥"，没有检查曲线，借这次一并收紧；
- 统一使用 Nimbus 的 `toPublicJWK().computeThumbprint()`，与现有 `IamUtils.matches` 一致，不新增类；
- **指纹只由 IAM 根据验签通过的公钥计算。** 前端不计算指纹；客户端传来的任何"访客 ID"一律忽略。Basis 只接收 IAM 算好的指纹。

### 5.4 访客：`anonymous_visitor`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT | 雪花 ID，即 **visitorId** |
| `organ_id` | BIGINT NOT NULL | 所属机构 |
| `key_thumbprint` | CHAR(43) NOT NULL | 公钥指纹 |
| `entry_id` | BIGINT NOT NULL | 首次进入时使用的接入码 |
| `last_seen_time` | DATETIME NOT NULL | 最近一次换票或刷新的时间 |
| `delete_flag`、`create_time`、`update_time` | | 平台标准字段 |

- 唯一键 `(organ_id, key_thumbprint)`，使用函数索引；
- visitorId 只在某把密钥**第一次**进入某个机构时生成一次，之后按"机构 + 指纹"查回；
- 同一浏览器访问两个机构的入口，得到两个互不关联的 visitorId；同一机构换了接入码，visitorId 不变；
- `resolveVisitor(organId, thumbprint, entryId)`：先查，查不到就插入；插入遇到唯一键冲突时再查一次（并发换票）。`last_seen_time` 只在换票和刷新时更新；
- 访客数据本期不清理。

### 5.5 visitorId 放在 `userId` 中

下游会话数据以 `user_id BIGINT NOT NULL` 归属（如 `chat_conversation`）。visitorId 是雪花 ID，与员工 userId 不冲突，复用 `userId` 可以让下游不改表。约束：`sessionType = ANONYMOUS` 时，下游不得把 userId 当员工去 Basis 查用户。

## 6. 权限模型（Basis）

`control_unit.session_type` 已包含 `ANONYMOUS`，口径与 MEMBER 一致：

- 平台发布 `session_type = ANONYMOUS` 的控制单元；访客能用哪些接口，完全由控制单元里放了什么决定；
- 租户在应用授权中开通或关闭这些能力；
- 生效接口 = 应用授权 ∩ 机构已开通 ∩ 已发布的 ANONYMOUS 控制单元，与 `ResourceApiDao` 中 MEMBER 的口径一致；
- `getSessionApiPermissions(ANONYMOUS, organId)` 按机构返回，并附带该机构启用中的接入码 ID（见 §7）；
- 通用的会话 list / delete 接口**不得**放入 ANONYMOUS 控制单元；
- chat-shell 访客至少需要：`/harness/v1/chat/completions`、`/harness/chat/conversation/list`、`/harness/chat/message/list`、`DELETE /harness/chat/conversation/{id}`；如需访客切换语言，再加入语言列表与国际化文案接口（不加时前端只打警告，不影响对话）；
- 删除 `resolveAnonymousRoleIds` 及 ADMIN 回退；删除 IAM 配置 `g2rain.iam.anonymous.*`；
- 以下变化都发出 `ANONYMOUS_PERM`（载荷为 organId）：接入码启停或删除、ANONYMOUS 能力开关、ANONYMOUS 控制单元及其接口变化。

## 7. Token 与鉴权

### 7.1 ANONYMOUS 载荷契约

| 字段 | 取值 |
| --- | --- |
| `sessionType` | `ANONYMOUS` |
| `organId` / `organType` / `organName` | 来自接入码，必须是租户机构 |
| `userId` | visitorId，大于 0 |
| `passportId` / `memberId` | `null` |
| `roleIds` | 空 |
| `adminUser` / `adminCompany` | 固定为 `false` |
| `name` | `访客` |
| `applicationScopes` | 只包含接入码绑定的这一个应用 |
| `clientId` / `clientPublicKey` | 换票时的 DPoP 公钥 |
| `entryId` | 接入码 ID（**新增字段**，需要改 common 的 `TokenJWTPayload`） |

### 7.2 Gateway（webflux / webmvc）

- `GatewayTokenAuthFilter` 对 ANONYMOUS 做失败关闭校验，写法与 `validateMemberSessionClaims` 相同：上表任一项不符即拒绝；
- 新增 `AnonymousPerm`，按 organId 懒加载并缓存，结构照搬 `MemberPerm`：
  ```java
  record AnonymousOrganPermission(Set<Long> apiIds, Set<Long> entryIds)
  ```
- `ApiPermissionFilter` 新增 ANONYMOUS 分支：接口在 `apiIds` 中，**并且** token 的 `entryId` 在 `entryIds` 中，才放行；
- 收到 `ANONYMOUS_PERM` 事件后清掉该机构的快照；快照加载失败返回 503，失败即关闭；
- DPoP 逐请求校验不变，只增加 §5.3 的曲线与算法限制。

### 7.3 mcp-registry

- `McpHandlerFilter.isAuthenticated`：ANONYMOUS 改为要求 `organId > 0`、`userId > 0`、`passportId` 与 `memberId` 为空；
- `BuiltInApiPermission.check`：新增 ANONYMOUS 分支，使用新增的 `AnonymousApiPerm`（照搬 `MemberApiPerm`，按机构校验接口；`entryId` 只由网关校验），不再落到员工 `userApiPerm`；监听 `ANONYMOUS_PERM`；
- 顺带修复：`McpHandlerFilter` 缺少 MEMBER 分支，会员会话调用 MCP 端点会被判为未认证。

### 7.4 Starter

`LoginGuardInterceptor` 的 ANONYMOUS 分支补充 `userId > 0`，与其他会话类型"类型正确且主体 ID 存在"的规则一致。完整的字段校验由网关负责，这里只做兜底。

### 7.5 生效时间

| 操作 | 生效时间 |
| --- | --- |
| 停用某一个接入码 | 立刻，只影响这个入口的访客 |
| 关闭机构的 ANONYMOUS 能力 | 立刻，影响该机构所有访客 |
| 修改 ANONYMOUS 控制单元里的接口 | 立刻 |

## 8. 刷新

- **允许 ANONYMOUS refresh**，替代原来"禁止刷新"的约定。匿名重新授权需要整页跳转，会打断正在进行的 SSE 对话；刷新同样要求私钥签名和公钥指纹匹配，安全性不降低；
- 新增 `refreshAnonymousToken`，结构参考 `refreshMemberToken`：basis `/login_token/anonymous_refresh_context` 按 `entryId` 复核接入码仍为 `ACTIVE`、机构可用且已开通应用，更新 `last_seen_time`，重新组装载荷；刷新不得换应用；
- 每次刷新都会复核，所以 `refreshExpireAt` 可以照常顺延；
- token 有效期使用应用默认配置；
- 刷新失败时前端重新走匿名授权，绝不跳员工登录页（见 §10.3）。

## 9. 会话数据归属（agent-runtime）

| | 管理平台（agent-app） | chat-shell |
| --- | --- | --- |
| 接口 | 保持现有通用接口，不改 | `GET /chat/conversation/list`、`GET /chat/message/list?conversationId=`、`DELETE /chat/conversation/{id}` |
| 隔离粒度 | 机构级（租户隔离） | 用户级，强制 `userId = 当前登录人或访客` |

- 会话列表：服务端强制 `organId`、`userId` 等于当前主体，忽略客户端传入的 userId；
- 消息列表：先确认 `conversationId` 属于当前主体，再查询；
- 删除会话：先校验归属；
- 会话不存在与不属于当前主体统一返回 `CONVERSATION_FORBIDDEN`；
- 三个接口放在 `ConversationController`（与 `/v1/chat/completions` 同一个对话入口），逻辑在 `ConversationService`；发消息时 `TurnSessionHydrate` 已校验会话归属，无需改动；
- 已知遗留：删除会话只删 `chat_conversation` 行，消息与 Redis 会话状态未清理（通用接口原本如此），另行处理。

chat-shell 改用专用接口后，员工在 chat-shell 中也只能看到自己的会话，现有问题一并修复。

## 10. chat-shell

### 10.1 交互

- 未登录即为访客，可以直接对话；
- 左下角提供"登录"，登录后按正常的页面和按钮权限使用；
- 退出后自动回到原来的访客身份，历史访客会话仍在。

### 10.2 接入码来源

- 优先级：URL `?entry=` > 部署配置 `VITE_ANONYMOUS_ENTRY_KEY`；
- 默认接入码由运营公司在 manager-app「应用授权 → 匿名接入码」中给自己的机构创建，再通过容器环境变量 `ANONYMOUS_ENTRY_KEY` 注入 chat-shell 的 `VITE_ANONYMOUS_ENTRY_KEY`（与 `SSO_BASE_URL` 同样在启动时写入 `env-config.js`）。直接访问 `/chat` 时使用它，访客属于运营公司；未配置时不开放匿名访问，未登录走员工登录；
- 租户的访客通过 `/chat?entry=ek_...` 进入；
- 首次读取后随 token store 持久化，之后不再依赖 URL。

### 10.3 授权、刷新与退出

- 未登录时 `start()` 走匿名授权：`mode=ANONYMOUS&entryKey=...`，不走员工 SSO；
- 刷新失败（被拒绝、已过期、接入码停用）时：清除 token，**保留密钥和接入码**，重新匿名授权；
- 接入码不可用时回调页提示"该入口暂不可用"，提供"重新进入"和"登录"，不循环跳转（store 记录最近一次授权模式 `authMode` 用于区分）；在途的 SSE 请求失败后提示重试，不跳页；
- 所有自动重新认证（启动、刷新失败、路由守卫、HTTP 鉴权失败）统一走 `sso.reauthenticate()`：开放匿名访问时回到访客，否则跳员工登录；
- 访客不请求员工 authority 用户信息，不加载智能体与模型下拉（列表为空时下拉不显示），走默认路由；
- `logout()`：只清 token，**保留密钥和接入码**，然后自动重新匿名授权。这样"匿名 → 登录 → 退出 → 再登录 → 再退出"每次退出后都是同一个访客。如果不保留接入码，从 `?entry=` 进来的访客退出后会落到默认接入码，变成运营公司的访客。

### 10.4 密钥生命周期

访客身份等于"这个浏览器里这把密钥"。

| 场景 | 密钥 | 访客 | 处理 |
| --- | --- | --- | --- |
| 刷新页面、关闭再打开 | 不变 | 不变 | — |
| token 过期，刷新或重新授权 | 不变 | 不变 | — |
| 登录、退出、反复登录退出 | 不变 | 不变 | `logout()` 保留密钥 |
| 多个标签页 | 共用 localStorage | 不变 | 见"首次并发生成" |
| 清空浏览器数据、换浏览器、换设备 | 新密钥 | 新访客 | 符合预期 |
| 无痕模式 | 关闭窗口即丢失 | 每次都是新访客 | 符合预期 |
| localStorage 不可写 | 每次加载都是新密钥 | 每次都是新访客 | `writePersisted` 现在静默吞掉错误，改为检测写入失败并提示"当前浏览器无法保存访客记录" |
| 首次访问时两个标签页并发生成密钥 | 后写入的覆盖先写入的 | 分裂成两个访客 | 生成前用 `navigator.locks.request` 加锁，拿到锁后重读存储，已有就复用 |
| `applicationCode` 变更 | 存储键变化，等于新密钥 | 新访客 | `applicationCode` 上线后不得修改 |

## 11. 对现有功能的影响

| 现有功能 | 影响 |
| --- | --- |
| 员工登录、SSO | 不带 `mode` 时按 `USER` 处理，流程不变；新增授权码绑定公钥，对自有前端无新增失败场景（§5.2） |
| 授权码换 token | 匿名 code 进入新分支；员工 code 只多一次指纹比对 |
| 刷新、切换机构（`exchange_token`） | 匿名刷新是新增分支，员工、会员的刷新代码不动；`exchange_token` 补上 Proof 公钥与旧 Token 绑定公钥的比对（原先缺失，可被盗用 Token 的人用自己的密钥换票），与 refresh 一致 |
| Basis `fetchTokenContext` | 不动 |
| MEMBER | 不变。会员的机构和身份由外部身份源在服务端解析，本身就稳定、不可冒充，无需照搬本方案 |
| 管理平台会话查询 | 不变，通用接口保持机构级隔离 |
| admin-shell、main-shell、appkit | 无需改动 |

## 12. 发布顺序

1. **common：** `TokenJWTPayload` 增加 `entryId`；
2. **starter、gateway（webflux / webmvc）、mcp-registry：** 先认识新字段和 ANONYMOUS 校验；
3. **basis：** 两张表、解析、权限、`ANONYMOUS_PERM` 事件；
4. **agent-runtime：** chat-shell 专用会话接口；
5. **IAM：** `mode`、`entryKey`、授权码绑定公钥、匿名换票与刷新；删除全局匿名配置；
6. **manager-app：** 应用授权页的匿名接入码弹窗；运营公司创建默认接入码；
7. **chat-shell：** 匿名授权、专用会话接口、刷新与退出逻辑、配置默认接入码。

网关必须先于 IAM 上线，避免旧网关解析不了带 `entryId` 的 token。

## 13. 各项目改动

| 项目 | 改动 |
| --- | --- |
| common | `TokenJWTPayload.entryId` |
| starter | `LoginGuardInterceptor` ANONYMOUS 分支补 `userId > 0` |
| iam | `mode` / `entryKey` 参数；删除 `AuthorizationState` 匿名解析与 `g2rain.iam.anonymous.*`；授权事务和 code 记录 `jkt`，换票比对；去重 key 扩展；曲线与算法限制；匿名换票改调新接口；`refreshAnonymousToken`；`exchange_token` 补公钥比对 |
| basis | `anonymous_entry`、`anonymous_visitor` 两张表；`EntryStatus`；接入码新增/改名/启停/删除规则与 `/{id}/status`；`AnonymousEntryService.resolve` / `requireOpenOrgan`、`AnonymousVisitorService.resolve`；`fetchAnonymousTokenContext(entryKey, applicationCode, thumbprint)` 与新增 `fetchAnonymousRefreshContext(entryId, …)`，删除 ADMIN 回退；ANONYMOUS 权限快照（附 `entryIds`）；`MemberPermSyncService` 推广为 `SessionPermSyncService`（MEMBER + ANONYMOUS）；`ANONYMOUS_PERM` 事件；错误码 `ANONYMOUS_ENTRY_UNAVAILABLE` |
| gateway | ANONYMOUS 载荷校验；`AnonymousPerm`；`ApiPermissionFilter` 分支；曲线与算法限制 |
| mcp-registry | `McpHandlerFilter` 按会话类型校验主体 ID（补 MEMBER 分支）；`AnonymousApiPerm`（按机构，不校验 `entryId`）；`BuiltInApiPermission` 分支 |
| agent-runtime | `ConversationController` / `ConversationService` 新增三个按主体归属的会话接口 |
| manager-app | 应用授权页"匿名接入码"按钮与弹窗（`anonymous_entry` 嵌入组件）；不设独立菜单与访客页 |
| chat-shell | 访客默认态与左下角登录；`authorizeAnonymous` / `reauthenticate`；刷新失败、退出时保留密钥和接入码并重新匿名授权；密钥生成加跨标签页锁；存储写入失败提示；改用专用会话接口；访客隐藏智能体与模型下拉；`VITE_ANONYMOUS_ENTRY_KEY`（运行时注入） |

## 14. 测试与验收

| # | 场景 | 期望 |
| --- | --- | --- |
| 1 | 同一浏览器两次匿名授权 | `userId` 相同，历史会话可见 |
| 2 | 匿名 → 登录 → 退出 → 登录 → 退出 | 每次退出后 `userId` 都与最初的访客相同 |
| 3 | 从 `?entry=` 进入后登录再退出 | 仍属于该接入码的机构，不落到默认接入码 |
| 4 | 清空浏览器数据后访问 | 新 `userId`，看不到旧会话 |
| 5 | 同一浏览器访问 A、B 两个机构的入口 | 两个不同 `userId` |
| 6 | `entryKey` 不存在、已停用或属于其他应用 | 统一返回"接入码不可用"（`basis.40081`） |
| 7 | 管理机构创建接入码 | 创建时拒绝；换票时也拒绝 |
| 8 | 用另一把密钥的 Proof 兑换 code（员工与匿名各测一次） | 指纹不匹配，拒绝 |
| 9 | 员工授权请求不带 `publicKey` | 正常登录，换票不校验指纹 |
| 10 | 上线时正在进行的员工登录 | 不受影响 |
| 11 | P-384 等非 P-256 公钥 | 拒绝 |
| 12 | 复制 access token 到另一个浏览器调用接口 | 网关 DPoP 校验失败 |
| 13 | 访客调用未开通的 ANONYMOUS 接口、员工接口、通用会话接口 | 403 |
| 14 | 访客查询或删除他人会话（伪造 userId 或 conversationId） | 查不到或拒绝 |
| 15 | 员工在 chat-shell 中查看会话 | 只能看到自己的；管理平台仍能看整个机构的 |
| 16 | 停用某一个接入码 | 该入口的在线访客下一次请求立即 403，其他入口不受影响；前端提示"入口不可用"，不跳登录页 |
| 17 | 关闭机构 ANONYMOUS 能力 | 该机构所有访客下一次请求立即 403 |
| 18 | access token 过期 | 自动刷新，SSE 对话不中断 |
| 19 | 首次访问时两个标签页并发打开 | 只生成一把密钥，两个标签页 `userId` 相同 |
| 20 | localStorage 不可写 | 页面提示无法保存记录，仍可对话 |
| 21 | `AnonymousPerm` 快照加载失败 | 503，失败即关闭 |
| 22 | MEMBER 会话调用 MCP 端点 | 认证通过（顺带修复） |

## 15. 后续可选项

以下事项本期不做，有需求时再单独设计：

- 私钥不可导出、存入 IndexedDB，子应用只拿签名函数（组件层统一调整）；
- IAM 换票端点 `ClientDPoPAuthFilter` 已校验 Proof 的 `iat` 时间窗与 `htm / htu`，但 `jti` 只校验了存在，缺少防重放；补齐后与网关一致；
- 限流、新访客上限、对话频率与模型配额；
- 访客数据过期清理、访客统计；
- 访客登录后合并匿名会话；密钥轮换。

## 16. 评审结论栏

| 日期 | 评审人 | 结论 |
| --- | --- | --- |
| 2026-10-21 | | 方案定稿 |
| 2026-10-22 | | 各项目实施完成，文档按实际代码同步，待联调验收 |
