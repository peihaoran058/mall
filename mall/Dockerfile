# ==========================================================
# 第19课时：Docker 打包运行
# 1) mvn clean package -DskipTests
# 2) docker build -t mall:1.0.0 .
# 3) docker run -d --name mall -p 8080:8080 \
#      -e MYSQL_HOST=host.docker.internal -e REDIS_HOST=host.docker.internal \
#      -e SPRING_PROFILES_ACTIVE=prod mall:1.0.0
# ==========================================================
FROM openjdk:8-jre-slim

LABEL maintainer="mall-course" version="1.0.0"

WORKDIR /app

ENV TZ=Asia/Shanghai \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-Xms256m -Xmx512m"

COPY target/mall.jar /app/mall.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/mall.jar"]
