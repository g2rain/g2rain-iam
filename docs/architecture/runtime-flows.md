# 运行流程

## 登录与授权码

进入 `/auth/authorize` → 校验客户端、回调和会话 → 登录或 IdP → 建立 HttpOnly 会话 → 用户确认 → 生成短期授权码 → 回调调用方传入的 `redirectUri`（本轮不做白名单）。

携带 `applicationCode` 时，进入统一 `consent` 前将 OAuth 参数绑定到会话；IAM 本地查询应用与机构信息展示预览；确认后校验会话绑定，再调用 Basis `activate_self` 开通全部 `SELF` 控制域并发码（5 分钟 TTL）。未携带 `applicationCode` 时同样进入 `consent` 确认/拒绝（禁止单用户自动发码），确认时走原回调发码、不写开通。

## Token

调用 `/auth/token` → DPoP Filter 校验证明 → 校验 grant、授权码/刷新凭据和客户端 → 选择活动密钥 → 签发访问 Token → 更新一次性或刷新状态。

## IdP

生成带 state 的入口 → 外部回调 → 校验 state、签名/时间窗和企业上下文 → Adapter 解析稳定主体 → Basis 查询或建立绑定 → 建立会话或签发授权码。

## 密钥轮换

发布新 key → 保留验证所需旧公钥 → 激活新签名 key → 同步 Gateway → 等待旧 Token 生命周期 → 撤销旧 key。轮换必须可回滚。
