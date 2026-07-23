#!/bin/bash
export JAVA_HOME=/usr/local/jdk17
export PATH=/usr/local/jdk17/bin:/usr/bin:/bin:$PATH

exec $JAVA_HOME/bin/java -jar /opt/ops-platform/target/ops-platform.jar \
  --spring.datasource.url="jdbc:mysql://127.0.0.1:3306/ops_platform?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true" \
  --spring.datasource.username=ops_user \
  --spring.datasource.password=Clbr@Mysql2024 \
  --ops.security.aes-secret=OpsPlatformAes16K