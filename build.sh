#!/usr/bin/env bash
set -e

# 切到脚本所在目录（确保在 g2rain-iam 根目录执行）
cd "$(dirname "$0")"

APP_IMAGE="g2rain/g2rain-iam"

# 第一个参数可选：指定 tag；不传时默认 latest
TAG="${1:-latest}"

echo "Building Docker image: ${APP_IMAGE}:${TAG}"

# 编译并执行 Jib 构建
mvn -DskipTests=true \
  clean compile jib:dockerBuild \
  -Djib.to.image=${APP_IMAGE}:${TAG}
