# 运行流程

## 登录与授权码

进入 `/auth/authorize` → 校验客户端、回调和会话 → 登录或 IdP → 建立 HttpOnly 会话 → 按是否携带 `applicationCode` 分流：

- 未携带 `applicationCode`：单用户直接发码并回调调用方 `redirectUri`；多用户先选用户，选定后发码（10 分钟 TTL，不写开通）。
- 携带 `applicationCode`：进入 `consent` 前将 OAuth 参数绑定到会话；IAM 本地查询应用与机构信息展示预览；确认后校验会话绑定，再调用 Basis `activate_self` 开通全部 `SELF` 控制域并发码（5 分钟 TTL）。拒绝则 `access_denied`，不开通、不发码。

本轮不做回调白名单。

## Token

调用 `/auth/token` → DPoP Filter 校验证明 → 校验 grant、授权码/刷新凭据和客户端 → 选择活动密钥 → 签发访问 Token → 更新一次性或刷新状态。

## IdP

生成带 state 的入口 → 外部回调 → 校验 state、签名/时间窗和企业上下文 → Adapter 解析稳定主体 → Basis 查询或建立绑定 → 建立会话或签发授权码。

## 密钥轮换

发布新 key → 保留验证所需旧公钥 → 激活新签名 key → 同步 Gateway → 等待旧 Token 生命周期 → 撤销旧 key。轮换必须可回滚。
