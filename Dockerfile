# ---- 构建阶段 ----
FROM maven:3.9-amazoncorretto-21 AS build
WORKDIR /app

# 先复制 pom.xml 并下载依赖（利用 Docker 层缓存，代码不变时跳过）
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源码并打包
COPY src ./src
RUN mvn clean package -DskipTests -B

# ---- 运行阶段 ----
FROM amazoncorretto:21-alpine
WORKDIR /app

COPY --from=build /app/target/yu-ai-agent-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8123

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
