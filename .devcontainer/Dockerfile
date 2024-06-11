FROM mcr.microsoft.com/vscode/devcontainers/base:ubuntu-20.04

# 安装 OpenJDK 17
RUN apt-get update && apt-get install -y openjdk-17-jdk

# 设置默认的 Java 版本
RUN update-alternatives --set java /usr/lib/jvm/java-17-openjdk-amd64/bin/java
