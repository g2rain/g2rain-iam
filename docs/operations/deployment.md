# 部署

构建使用 `mvn clean package` 或 `mvn compile jib:dockerBuild`。

Dockerfile 暴露 `8080`，应用默认端口为 `8082`；部署清单必须显式设置 `SERVER_PORT` 并正确映射探针和 Service。

发布前准备 Nacos/Secret、Redis、Basis、Gateway 和回调域名；部署后验证健康、登录、授权、Token、注销和真实 IdP。密钥轮换与普通镜像发布分开执行，并确保回滚版本能读取当前密钥和会话格式。
