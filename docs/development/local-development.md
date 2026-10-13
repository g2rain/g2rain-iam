# 本地开发

需要 JDK 25、Maven 3.9、Redis、Nacos、可访问的 Basis 服务；IdP 联调使用独立测试租户和回调域名。

```text
mvn test
mvn clean package
mvn spring-boot:run
```

默认端口 `8082`。本地 Secret 使用环境变量或未跟踪的安全配置，禁止写入 `application.yml`、IDE 配置和测试资源。
