# 按 applicationCode 的登录方式与注册策略

状态：**已实施（P0）**  
范围：IAM 浏览器授权事务内的登录页展示、账号密码 / IdP 入口、Passport 注册开关；策略以 **IAM 配置**（按 `applicationCode`）维护。**主因是同一 IAM 上多个 Main Shell 需要不同登录集合**（文档示例：`g2rain-main-shell`、`g2rain-admin-shell`）；开放平台应用一般不写专条、命中 `default`。与现有 `tid` 事务、`openPlatformConsent` 分流协作。  
关联：[IAM 页面与授权流程解耦](iam-page-flow-upgrade.md)、[应用授权确认分流](open-platform-oauth-upgrade.md)。

本方案由 `docs/project.yaml` 的 `aiCoding.activeRequirement` 授权实施。

## 1. 背景与问题

**实施前（现状）** IAM 登录与注册由全局配置决定，与 `applicationCode` 无关：

| 能力 | 现状 |
| --- | --- |
| 账号密码 | 登录页始终展示；有效 `tid` 内 `POST /auth/login` 始终可用 |
| 钉钉 / 企微 | 由全局 `g2rain.iam.dingtalk|wecom.login-page-bind-mode` 是否非空决定入口显隐 |
| Passport 注册 | 无关闭开关；有 `tid` 时登录页始终提供「注册账号」 |
| `applicationCode` | 仅用于开放平台 consent / SELF / 发码绑定，**不**驱动登录方法 |

**问题**：平台已有多个 Main Shell，各自带自己的 `applicationCode` 顶层进入 `/auth/authorize`。IAM 只有一套全局登录页，无法让 `g2rain-main-shell` 与 `g2rain-admin-shell` 使用不同方式集合（例如一个允许企微、一个仅密码且禁止注册）。开放平台 `PUBLIC`/`PRIVATE` 应用数量多、变化快，一般**不必**为每个第三方应用写专条。

**本方案后**：密码、IdP、注册均**不再始终可用**。是否展示与是否接受提交，只取决于事务冻结的策略条目（专条或 default 二选一，见 §4.3）。

- **多个 Shell**：在 `applications` 上按 Shell 的 `applicationCode` 各写一条完整策略。
- **开放平台应用**：通常 **Map 未命中 → 整段 `default`**（出厂：仅密码 + 允许注册）。
- 子应用若也带自己的 code 进授权且未写专条，同样走 `default`；登录策略与 consent 是否忽略 `SUPPORT`/`SYSTEM` 无关。

## 2. 目标与非目标

### 2.1 目标

- 按配置**整段选用**策略：`applicationCode` **命中** `applications` Map → 只用该专条；**未命中**（无 code 或 Map 无此 key）→ 只用 `default`；
- **默认配置不追加、不合并到专条**：专条未列出的方式（含 `PASSWORD`、注册）不得从 `default` 补齐；
- **键只用 `applicationCode`，不用 `applicationId`**；
- **生效策略 = 平台能力 ∩ 所选整段许可**（所选 = 专条或 default，二者取一）；
- 建事务时解析并**冻结**；登录 / 注册 / IdP 只读冻结值；
- UI 隐藏与服务端拒绝双闸门；密码登录仅当冻结策略含 `PASSWORD` 时可用（含展示与 `POST /auth/login`）；
- 出厂 `default`：仅 `PASSWORD` + `allowRegister=true`；
- 各 Main Shell 必须在专条中**写全**该壳所需集合（需要 IdP、关闭密码或禁止注册时尤其如此）；开放平台应用默认不配专条、命中 `default`；
- 不改变 JWT / DPoP / 发码契约；**不改 Basis 应用表/API**。

### 2.2 非目标

- 不在 Basis 持久化登录/注册策略，不经 manager-app 维护本策略（后续若要运营台，另立需求，仍建议以 `applicationCode` 为键同步到 IAM 配置源）；
- 不按 `applicationId`、`organId` / 租户覆盖（可后续扩展）；
- 不改变 `openPlatformConsent` 对 `SUPPORT`/`SYSTEM`「忽略应用做 consent」的语义（consent 仍查 Basis `applicationType`）；
- 不把 IdP AppSecret / Suite Secret 写入按应用策略段（凭证仍归既有全局 IdP 配置）；
- 不新增 OAuth Client 主数据、PKCE、PAR；
- 不把无 `tid` 的裸登录/注册重新开放为正式入口。

## 3. 领域协作与数据归属

| 事实 | 所属 | 说明 |
| --- | --- | --- |
| 按应用的登录/注册策略 | **IAM 配置** | YAML / Nacos 等；**键 = `applicationCode`** |
| 平台默认认证策略 | **IAM 配置** | 无 code、或 code 无专条时使用 |
| 平台 IdP 能力（凭证、全局 bindMode） | **IAM 配置** | 既有 `dingtalk`/`wecom` 段 |
| 应用主数据、`applicationType`、开通 | **Basis** | 仅服务 consent / SELF / 发码绑定；**不**提供登录策略 |
| 本次授权策略快照 | **IAM** Redis 授权事务 | 与 `applicationCode`、`openPlatformConsent` 一并冻结 |
| 登录 / 注册 / IdP 编排与拒绝 | **IAM** | 读事务冻结策略 |

本策略属于 IAM 认证入口编排配置，不是应用主数据的一部分；与 Basis 的协作边界保持清晰。

## 4. 策略模型

### 4.1 登录方式枚举

| code | 含义 |
| --- | --- |
| `PASSWORD` | 账号密码 |
| `DINGTALK` | 钉钉扫码登录 |
| `WECOM` | 企业微信扫码登录 |

| 字段 | 说明 |
| --- | --- |
| `loginMethods` | 该条目完整许可集合；**至少一种**；未列出的方式一律不允许 |
| `dingTalkBindMode` | 可选；仅当本条目 `loginMethods` 含 `DINGTALK` 时有意义：有值用该值，否则用全局 `dingtalk.login-page-bind-mode`（平台能力回退，**不是**从 `default` 策略合并） |
| `weComBindMode` | 可选；含 `WECOM` 时同上 |
| `allowRegister` | 本条目是否允许注册；专条省略时绑定缺省为 `false`，**不得**回填 `default.allowRegister` |

### 4.2 IAM 配置结构（推荐）

键空间以 **`applicationCode` 字符串** 为 Map key，禁止使用数字 `applicationId`。

示例以两个真实 Shell 的 `applicationCode` 为键（与各仓 `docs/project.yaml` 的 `runtime.applicationCode` 一致）。下列方法集合仅为说明「多壳不同策略」；上线以环境配置为准。

```yaml
g2rain:
  iam:
    auth-policy:
      # 未命中专条时整段使用。开放平台 PUBLIC/PRIVATE 应用一般走这里。
      default:
        login-methods:
          - PASSWORD
        allow-register: true
      applications:
        g2rain-main-shell:             # 综合管理平台入口（/main）
          login-methods:
            - PASSWORD
            - WECOM
          we-com-bind-mode: INTERNAL
          allow-register: false
        g2rain-admin-shell:            # 另一 Main Shell 入口（/admin）；与上条互斥、不合并 default
          login-methods:
            - WECOM
          we-com-bind-mode: INTERNAL
          allow-register: false
        # 开放平台应用（如某客户 PUBLIC app）通常不写在这里 → 命中 default
```

| key | 角色 | 本示例生效 |
| --- | --- | --- |
| `g2rain-main-shell` | 命中专条 | 密码 + 企微；不可注册 |
| `g2rain-admin-shell` | 命中专条 | **仅**企微；无密码、不可注册（**不**因 default 含密码而放行） |
| 某开放平台 `applicationCode`（未出现在 Map） | 未命中 | 整段 `default`：仅密码 + 可注册 |
| 无 `applicationCode` | 未命中 | 同上 `default` |

配置绑定建议：`@ConfigurationProperties(prefix = "g2rain.iam.auth-policy")`：

- `default`：仅在未命中专条时整段使用；出厂为仅 `PASSWORD` + `allow-register=true`；面向开放平台与其它未登记入口；
- `applications`：`Map<String, AuthPolicyEntry>`，key = **Shell 或其它需要差异化的** `applicationCode`（本方案优先登记各 Main Shell）。

可通过 Nacos / 部署环境覆盖；策略段不得含 Secret。

### 4.3 解析规则（互斥选用，禁止合并）

```text
resolve(applicationCode):
  if blank(applicationCode):
    entry = config.default                    # 整段
    source = PLATFORM_DEFAULT
  else if config.applications contains key(applicationCode):
    entry = config.applications[applicationCode]   # 整段；不再读 default
    source = APPLICATION
  else:
    entry = config.default                    # 未命中才用默认；整段
    source = PLATFORM_DEFAULT
  # 禁止：entry.loginMethods ∪ default.loginMethods
  # 禁止：用 default.allowRegister 补齐专条
  effective = entry ∩ 平台 IdP 能力（仅裁剪本条目中声明但能力不具备的 IdP）
  if effective.loginMethods empty:
    fail closed
  freeze into transaction
```

| 命中情况 | 选用 |
| --- | --- |
| 无 `applicationCode` | **仅** `default` |
| 有 code 且 Map **命中** | **仅**该专条（完整替换，不追加 default） |
| 有 code 且 Map **未命中** | **仅** `default` |

反例（错误实现，必须禁止）：

- 专条为 `[WECOM]`，再把 `default` 的 `PASSWORD` / `allowRegister` 并进去 → 错误；
- 专条未含 `PASSWORD` 仍展示密码表单或接受 `POST /auth/login` → 错误；
- 「专条 + default 取并集」→ 错误。

说明：

- **登录策略不调用 Basis**；未命中专条只选用 `default`，不因此错误页。
- consent 仍按 open-platform 查 Basis，与登录策略解析分离。
- 全局 `login-page-bind-mode` 是平台能力 / IdP 模式回退，**不是**策略条目的合并源。

### 4.4 默认策略与回退

**出厂 / 属性类缺省（必须提供），且仅在未命中专条时整段生效：**

| 项 | 值 |
| --- | --- |
| `loginMethods` | 仅 `PASSWORD` |
| `allowRegister` | `true` |
| IdP | 不包含 |

| 场景 | 行为 |
| --- | --- |
| `g2rain-main-shell` / `g2rain-admin-shell` 等已登记 Shell | 整段使用对应专条；**不**读 `default` |
| 开放平台应用 code（一般未登记） | 整段使用 `default` |
| 无 `applicationCode` | 整段使用 `default` |
| 省略整个 `default` 段 | 代码缺省仍为「仅 PASSWORD + 允许注册」 |
| 专条只有 `WECOM`（如示例中的 admin-shell） | 仅企微；无密码、无注册 |
| 策略声明 IdP 但全局无能力 | 从**本条目**剔除该 IdP；剔除后为空 → 错误页 |

相对实施前：多个 Shell 不再共用同一套全局登录页；未命中（含绝大多数开放平台应用）走 `default`；命中专条则完全以专条为准。

### 4.5 与 `openPlatformConsent` 的关系（必须写死）

| 字段 | 用途 | 数据源 |
| --- | --- | --- |
| `openPlatformConsent` | 认证后是否 consent / SELF / 发码是否绑应用 | Basis `applicationType` |
| `authPolicy` | 登录页与注册闸门 | **仅 IAM 配置** |

对 `SUPPORT` / `SYSTEM`：consent 可忽略应用语义，但**登录/注册仍按事务内原始 `applicationCode` 查 IAM 配置 Map**（有专条用专条，否则 default）。

典型部署：

- 差异化专条只配 **各 Main Shell**（`g2rain-main-shell`、`g2rain-admin-shell`；以后新增壳再加一行）；
- **开放平台应用一般命中 `default`**，不必随客户应用增减去改 IAM 策略 Map；
- 微前端子应用通常不单独顶层弹登录；若带子应用 code 进入 `/authorize` 且未写专条，同样是 `default`。

## 5. 授权事务冻结

| 字段 | 说明 |
| --- | --- |
| `authPolicy.loginMethods` | 生效后的方法集合（已与平台能力求交） |
| `authPolicy.dingTalkBindMode` | 生效钉钉模式；无钉钉则为空 |
| `authPolicy.weComBindMode` | 生效企微模式；无企微则为空 |
| `authPolicy.allowRegister` | 是否允许注册 |
| `authPolicy.source` | `APPLICATION` \| `PLATFORM_DEFAULT` |
| `authPolicy.applicationCode` | 解析时使用的 code（可空）；便于审计，与事务顶层 `applicationCode` 一致 |

解析时机：**首次全参数建事务成功后立即**从本地配置解析并写入，再 PRG 进入仅 `tid` 续跑。  
本步不依赖 Basis；仅当生效方法集合为空时进入错误路径。

站内页面只带 `tid`；策略只从事务读取。

配置热更新（Nacos）：**已冻结事务不回写**；新事务用新配置。文档与运维约定如此，避免同一 `tid` 中途改入口。

## 6. 目标流程

```text
业务侧 → 顶层 /auth/authorize（全参数，含可选 applicationCode）
IAM → 创建 tid + flow Cookie
IAM → resolveAuthPolicy（读本地 auth-policy 配置，按 applicationCode）
       → ∩ 平台 IdP 能力 → 冻结
       → 生效方法为空 → 错误页
IAM → 303 仅 tid 续跑
  → 未登录：renderLogin（按冻结策略）
  → POST login | IdP | 注册：校验冻结策略
  → 认证成功后：既有选用户 / openPlatformConsent（仍可查 Basis）/ 发码
```

匿名模式不进入登录/注册；与本策略无关。

## 7. UI 与服务端双闸门

### 7.1 渲染

| 入口 | 行为 |
| --- | --- |
| `renderLogin` | **仅**渲染冻结策略中的方式；无 `PASSWORD` 则不渲染密码表单 |
| `renderRegister` | 仅当冻结 `allowRegister=true`；否则错误页或回登录 |
| 登录页注册链接 | 仅当冻结 `allowRegister=true` |

密码、IdP、注册链接均非「始终展示」。

### 7.2 服务端强制

| 入口 | 校验 |
| --- | --- |
| `POST /auth/login` | 冻结策略含 `PASSWORD`；否则拒绝（即使有效 `tid`） |
| 钉钉浏览器登录链路 | 含 `DINGTALK`，bindMode 与冻结值一致 |
| 企微扫码登录链路 | 含 `WECOM`，bindMode 一致 |
| `GET` 注册 / `POST /auth/passport_register` | 冻结 `allowRegister=true` |

非登录用途的企微能力（客服 decrypt、第三方安装回调等）**不受**本策略约束。

### 7.3 无 tid

注册/密码登录须挂在有效授权事务上；无 `tid` 不得绕过策略。

## 8. 配置与实现变化（本仓为主）

### 8.1 IAM

- 新增 `g2rain.iam.auth-policy` 配置属性类与校验（default 必填语义、methods 枚举、Map key 非空白）；
- 启动或刷新时可选：若某专条声明的 IdP 在全局永远不可用，打 warn（不阻断启动，求交在事务解析时执行）；
- `AuthorizationTransactionDto` / Redis 增加冻结字段；
- 建事务路径调用 `resolveAuthPolicy`；
- `renderLogin` / `renderRegister` 与 Login / Passport / IdP 登录闸门；
- 模板按模型显隐；
- 单测：`g2rain-main-shell` / `g2rain-admin-shell` 命中各专条、未登记的开放平台 code 用 default、无 code 用 default、**admin-shell 专条不合并 default**（仅 WECOM 时密码/注册均不可用）、交集为空、bindMode 平台回退。

### 8.2 Basis / manager-app

- **本方案不改**。consent 继续只用既有应用查询。

### 8.3 运维文档

- 实施时在 `docs/operations/configuration.md` 登记 `auth-policy` 项与示例；部署仓库 / Nacos 按环境维护 `applications` Map。

### 8.4 不变契约

- `/auth/authorize` 参数、`tid`、flow Cookie、发码与 `/auth/token` 不变；
- Token claims、Cookie 名、IdP 外部回调 URL 默认不变。

## 9. 安全控制

1. 策略冻结后不可被 URL / 表单改写；续跑只认 `tid`。
2. 服务端强制校验冻结策略；隐藏 UI 不算安全。
3. 生效方法为空失败关闭；Map 未命中选用 `default`，**不得**与专条做并集。
4. 配置与日志不得含 IdP Secret、Cookie 原值、密码、验证码。
5. 注册保留 IP 限流与验证码。
6. flow Cookie 防跨浏览器续跑等既有不变量保持。
7. 配置 key 必须是 `applicationCode`；禁止把 `default` 字段合并进已命中专条。

## 10. 分阶段实施

| 阶段 | 内容 | 仓库 |
| --- | --- | --- |
| P0 | `auth-policy` 配置 + 解析冻结 + 渲染/闸门 + 单测 + configuration 文档 | **仅 iam**（及部署侧 Nacos/YAML） |
| P1（可选） | 按环境拆分示例配置、启动校验收紧 | iam / deploy |
| P2（可选） | 运营台编辑后下发 Nacos；按 organ 覆盖等 | 另立需求 |

回滚：回退 IAM 版本或清空 `applications` Map（全部走 default）。已冻结事务随 TTL 过期。若需短暂兼容开关，仅允许「未实现冻结时回退现网全局行为」，并设拆除期限。

## 11. 验收清单

- `applicationCode=g2rain-main-shell`（示例专条）：密码 + 企微；无注册；`passport_register` 拒绝。
- `applicationCode=g2rain-admin-shell`（示例专条仅企微）：登录页无密码/注册；`POST /auth/login` 失败（**不**因 default 含密码而放行）。
- 两个 Shell 的专条互不影响；不得把其中一个的方式并到另一个。
- 开放平台应用 code（未出现在 Map）：整段 `default` → 仅密码 + 可注册；consent 仍按 `PUBLIC`/`PRIVATE` 走确认，与登录策略无关。
- 无 `applicationCode`：整段 `default`。
- 命中专条时 default 的 methods/register **零贡献**。
- 出厂省略 `default` 段：缺省仍为仅 `PASSWORD` + 允许注册。
- key 仅为 `applicationCode` 字符串（示例键即 `g2rain-main-shell`、`g2rain-admin-shell`）。
- `SUPPORT`/`SYSTEM` consent 可忽略应用；登录策略仍按该 code 互斥选用专条或 default。
- 专条声明 IdP 但全局未启用：从本条目剔除；若无其它方式则错误页。
- 既有 tid / flow Cookie / IdP state / 终态不变量保持。

## 12. 决策摘要

1. **本方案主要为同一 IAM 上的多个 Main Shell 配不同登录集合**；示例键为 `g2rain-main-shell`、`g2rain-admin-shell`。
2. **开放平台应用一般不写专条，命中 `default`。**
3. **登录/注册策略是 IAM 配置，不是 Basis 应用主数据；键只用 `applicationCode`。**
4. **命中专条与 default 互斥整段选用；default 不追加到专条。**
5. **出厂 default = 仅 PASSWORD + 允许注册；密码/IdP/注册均非始终可用。**
6. **登录策略不调用 Basis；consent 仍可查 Basis。**
7. **事务冻结；UI 与服务端双闸门。**