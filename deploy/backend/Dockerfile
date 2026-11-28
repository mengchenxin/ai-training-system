FROM eclipse-temurin:21-jre-alpine

LABEL org.opencontainers.image.title="ai-training-backend"

WORKDIR /app
ENV TZ=Asia/Shanghai
ENV LANG=C.UTF-8

COPY app.jar /app/app.jar

EXPOSE 8000

# 2C2G 机器：堆 512M + 元空间 192M，给 MySQL 与系统留足余量
ENTRYPOINT ["java", \
  "-Xms256m", "-Xmx512m", \
  "-XX:MaxMetaspaceSize=192m", \
  "-XX:+UseSerialGC", \
  "-Duser.timezone=Asia/Shanghai", \
  "-Dfile.encoding=UTF-8", \
  "-jar", "/app/app.jar", \
  "--server.port=8000"]
