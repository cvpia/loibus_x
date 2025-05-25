# 使用OpenJDK 17作为基础镜像
FROM openjdk:17-jdk-slim

# 设置工作目录
WORKDIR /app
# 复制构建好的JAR文件到容器中
COPY target/demo-0.0.1-SNAPSHOT.jar /opt/app.jar
VOLUME /app/down
VOLUME /app/page
VOLUME /app/images
VOLUME /app/items
ENV TZ=Asia/Shanghai
ENV api.email=""
ENV api.password=""
# 暴露应用端口
EXPOSE 8080

# 设置容器启动时执行的命令
ENTRYPOINT ["java", "-jar", "/opt/app.jar"]