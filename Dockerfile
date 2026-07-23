# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
# 先单独下载依赖，利用Docker层缓存加速后续构建
RUN mvn -B dependency:go-offline || true
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- 运行阶段 ----
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /build/target/ops-platform.jar /app/ops-platform.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/ops-platform.jar"]
