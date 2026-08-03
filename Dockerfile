FROM ubuntu:22.04 as tools
RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    coreutils && \
    rm -rf /var/lib/apt/lists/*

# JRE만 있는 가벼운 이미지 사용
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
# entrypoint 가 기동 시 wallet 을 복원할 때 사용한다 (런타임 의존성)
COPY --from=tools /usr/bin/base64 /usr/bin/base64
COPY --from=tools /bin/tar /bin/tar

COPY app/*.jar ./app.jar

COPY --chmod=0755 docker-entrypoint.sh /app/docker-entrypoint.sh

# Heap dump 저장 디렉토리 생성
RUN mkdir -p /app/logs

ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["/app/docker-entrypoint.sh"]
CMD ["java", \
"-Xms700m", \
"-Xmx700m", \
"-XX:MaxMetaspaceSize=160m", \
"-XX:+UseStringDeduplication", \
"-XX:MaxDirectMemorySize=64m", \
"-XX:ReservedCodeCacheSize=128m", \
"-Xlog:gc*:file=/app/logs/gc-%t.log:time,level,tags", \
"-XX:+HeapDumpOnOutOfMemoryError", \
"-XX:HeapDumpPath=/app/logs/heapdump-%t-%p.hprof", \
"-XX:ErrorFile=/app/logs/hs_err_pid%p.log", \
"-XX:+ExitOnOutOfMemoryError", \
"-jar", \
"app.jar"]

