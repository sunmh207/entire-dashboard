# syntax=docker/dockerfile:1.7

# =========================================================
# Stage 1 — Frontend build (Nuxt static)
# =========================================================
FROM node:24-alpine AS frontend-builder

WORKDIR /frontend

# 使用 corepack 提供 pnpm（更稳定）
RUN corepack enable

# 先复制依赖文件（最大化缓存）
COPY web/package.json web/pnpm-lock.yaml ./

# pnpm cache
RUN --mount=type=cache,target=/root/.pnpm-store \
    pnpm install --frozen-lockfile

# 再复制源码
COPY web/ .

# 生成静态站点
RUN --mount=type=cache,target=/root/.pnpm-store \
    pnpm run generate



# =========================================================
# Stage 2 — Backend build (Spring Boot)
# =========================================================
FROM gradle:jdk25-alpine AS backend-builder

WORKDIR /backend

# 先复制 Gradle 配置
COPY server/build.gradle.kts .
COPY server/settings.gradle.kts .
COPY server/gradle ./gradle

# 下载依赖（缓存）
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle dependencies --no-daemon || true

# 再复制源码
COPY server/src ./src

# 构建 jar
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle bootJar --no-daemon



# =========================================================
# Stage 3 — Runtime image
# =========================================================
FROM eclipse-temurin:25-jdk-noble AS runtime

ENV TZ=Asia/Shanghai

# 安装运行依赖
RUN apt-get update \
 && apt-get install -y nginx supervisor wget tzdata \
 && ln -snf /usr/share/zoneinfo/$TZ /etc/localtime \
 && echo $TZ > /etc/timezone \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 复制 Spring Boot jar
COPY --from=backend-builder /backend/build/libs/*.jar /app/app.jar

# 复制 Nuxt 静态资源
COPY --from=frontend-builder /frontend/.output/public /usr/share/nginx/html

# 创建目录
RUN mkdir -p /app/data /app/logs \
 && rm -f /etc/nginx/sites-enabled/default \
 && rm -f /etc/nginx/conf.d/default.conf

# 复制配置
COPY docker/nginx.conf /etc/nginx/conf.d/app.conf
COPY docker/supervisord.conf /etc/supervisord.conf

EXPOSE 80 8080

# 默认配置（生产建议由 compose 覆盖）
ENV DB_HOST=localhost \
    DB_PORT=3306 \
    DB_NAME=entire-dashboard \
    DB_USERNAME=root \
    DB_PASSWORD=root \
    JWT_SECRET=ChangeThisSecretKeyInProduction \
    APP_USERNAME=admin \
    APP_PASSWORD=admin \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseZGC"

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -q -O - http://localhost:80/ || exit 1

CMD ["/usr/bin/supervisord", "-c", "/etc/supervisord.conf"]