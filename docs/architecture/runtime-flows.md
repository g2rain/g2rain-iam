# 运行流程

## 登录与授权码

业务侧生成本地 Client DPoP 上下文后，顶层进入 `/auth/authorize`：

- **全参数、无 `tid`**：创建 Redis 授权事务，写入/复用流程 Cookie `G2RAIN_AUTH_FLOW`（`SameSite=Lax`），`303` 到仅含 `tid` 的续跑 URL。
- **仅 `tid`**：校验 `tid` + 流程 Cookie，按事务状态继续（登录 / 选用户 / 应用授权确认 / 发码）。

未登录则进入登录或 IdP；IAM 站内页面只携带 `tid`。按是否冻结了 `applicationCode` 分流：

- 未携带 `applicationCode`：单用户直接发码并回调；多用户先选用户再发码（授权码默认 10 分钟 TTL）。
- 携带 `applicationCode`：进入 consent；确认后调用 Basis `activate_self` 开通全部 `SELF` 并发码（5 分钟 TTL）。拒绝则 `access_denied`。

出站时使用事务内冻结的 `redirectUri`/`state`，事务终态化。本轮不做回调白名单。创建事务时不校验 Client DPoP（与现网一致）；持钥在 `/auth/token` 换票时校验。

站内页面、登录、注册与 IdP 只认 `tid` + 流程 Cookie；已移除 Session `oauth*` 与页面间全参数透传。

## Token

调用 `/auth/token` → DPoP Filter 校验证明 → 校验 grant、授权码/刷新凭据和客户端 → 选择活动密钥 → 签发访问 Token → 更新一次性或刷新状态。

## IdP

登录页仅带 `tid` 发起钉钉/企微；IAM 将 `idp_state` 映射到事务。外部回调后校验流程 Cookie，绑定 Session，再按事务状态继续授权。

## 密钥轮换

发布新 key → 保留验证所需旧公钥 → 激活新签名 key → 同步 Gateway → 等待旧 Token 生命周期 → 撤销旧 key。轮换必须可回滚。
