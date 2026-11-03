# syntax=docker/dockerfile:1

# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 先只拷 pom.xml，利用 Docker 层缓存预下载依赖，加快后续构建
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

# 再拷源码并打包（跳过测试；接口自测用 e2e_test.py 单独跑）
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# 非 root 用户运行，更安全
RUN useradd -r -u 1001 appuser || true
COPY --from=build /app/target/aitecc-moments.jar app.jar
RUN mkdir -p /app/data /app/uploads && chown -R appuser:appuser /app
USER appuser

# Render 会注入 PORT（默认 10000），应用通过 ${PORT:8080} 读取并监听
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
