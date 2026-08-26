# 运行流程

## 登录与授权码

进入 `/auth/authorize` → 校验客户端、回调和会话 → 登录或 IdP → 建立 HttpOnly 会话 → 用户确认 → 生成短期授权码 → 仅回调允许的 `redirect_uri`。

## Token

调用 `/auth/token` → DPoP Filter 校验证明 → 校验 grant、授权码/刷新凭据和客户端 → 选择活动密钥 → 签发访问 Token → 更新一次性或刷新状态。

## IdP

生成带 state 的入口 → 外部回调 → 校验 state、签名/时间窗和企业上下文 → Adapter 解析稳定主体 → Basis 查询或建立绑定 → 建立会话或签发授权码。

## 密钥轮换

发布新 key → 保留验证所需旧公钥 → 激活新签名 key → 同步 Gateway → 等待旧 Token 生命周期 → 撤销旧 key。轮换必须可回滚。
