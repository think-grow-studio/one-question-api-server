FROM ubuntu:22.04 as tools
RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    coreutils && \
    rm -rf /var/lib/apt/lists/*

# JRE만 있는 가벼운 이미지 사용
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=tools /usr/bin/base64 /usr/bin/base64
COPY --from=tools /bin/tar /bin/tar

COPY app/*.jar ./app.jar

# =========================================================================
# 1. 폴더 Secret 처리 (Base64 -> tar.gz -> 폴더 복원)
# =========================================================================
# 'wallet_base64' id로 secret을 마운트합니다.
RUN --mount=type=secret,id=wallet_base64 \
    # Secret 파일의 내용을 읽어 base64 디코딩 후, tar로 압축을 해제합니다.
    # 결과물은 /app/src/main/resources/main-wallet 경로에 생성됩니다.
    mkdir -p /app/src/main/resources && \
    cat /run/secrets/wallet_base64 | base64 -d | tar -xz -C /app/src/main/resources

# Heap dump 저장 디렉토리 생성
RUN mkdir -p /app/logs

ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", \
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

