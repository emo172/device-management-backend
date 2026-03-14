# syntax=docker/dockerfile:1

# 第一阶段：使用 Maven + JDK 21 进行编译与测试。
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace

# 先复制构建描述文件，便于依赖层缓存复用。
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

# 再复制源码并打包正式 Jar。
COPY src ./src
RUN mvn -q clean package -DskipTests

# 第二阶段：仅保留运行时所需 JRE 与应用产物，缩小镜像体积。
FROM eclipse-temurin:21-jre
WORKDIR /app

ENV SPRING_PROFILES_ACTIVE=prod
ENV TZ=Asia/Shanghai

COPY --from=build /workspace/target/backend-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

# 统一从 Jar 入口启动，便于 Docker Compose 与后续部署平台复用同一命令。
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
