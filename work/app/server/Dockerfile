# 构建阶段：Maven 编译打包
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 先单独拷 pom 并预热依赖，利用 Docker 层缓存：源码改动时不用重新下载依赖
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B clean package -DskipTests

# 运行阶段：仅 JRE，镜像体积从 ~800MB 降到 ~180MB
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# alpine 默认无时区数据，不装会导致 -Duser.timezone=Asia/Shanghai 失效，时间全是 UTC
RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

# 非 root 运行
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /build/target/*.jar app.jar
RUN chown -R app:app /app
USER app

EXPOSE 8000

# 免费实例只有 512MB 内存：SerialGC + 72% 堆上限，避免与元空间/线程栈抢内存被 OOM Kill
ENV JAVA_OPTS="-XX:MaxRAMPercentage=72 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -Duser.timezone=Asia/Shanghai -Dfile.encoding=UTF-8"

# Render 会注入 PORT 环境变量，必须让 Spring Boot 监听它
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar --server.port=${PORT:-8000}"]
