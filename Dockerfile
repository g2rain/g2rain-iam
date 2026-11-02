FROM eclipse-temurin:25.0.4.1_1-jre
# 构建参数
ARG JAR_FILE
ARG BUILD_VERSION
LABEL org.opencontainers.image.source="https://github.com/g2rain/g2rain-iam" \
      org.opencontainers.image.licenses="Apache-2.0" \
      org.opencontainers.image.title="g2rain-iam" \
      org.opencontainers.image.version="${BUILD_VERSION}"
# 环境变量
ENV APP_VERSION=${BUILD_VERSION}
# 复制应用
COPY ${JAR_FILE} app.jar
# 暴露端口
EXPOSE 8082
# 启动命令
ENTRYPOINT ["java", "-jar", "/app.jar"]
