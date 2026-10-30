#!/bin/sh
set -e

# Oracle wallet 은 이미지에 굽지 않고 컨테이너 기동 시점에 주입한다.
# ORACLE_WALLET_BASE64 = wallet 폴더를 tar.gz 로 묶어 base64 인코딩한 값 (GitHub Actions 시크릿).
#
# 압축 파일 안에 폴더명(main-wallet-kr / dev-wallet-kr)이 들어있어서 prod/dev 가 같은 스크립트를 쓴다.
# 푸는 위치는 ORACLE_DB_URL 의 TNS_ADMIN=./src/main/resources/<wallet> 과 맞물려 있으므로
# (WORKDIR 이 /app 이라 상대경로가 여기로 해석된다) 시크릿을 함께 고치지 않는 한 바꾸지 말 것.
WALLET_DIR=/app/src/main/resources

if [ -z "$ORACLE_WALLET_BASE64" ]; then
  echo "FATAL: ORACLE_WALLET_BASE64 가 비어 있습니다. DB 접속이 불가능하므로 기동을 중단합니다." >&2
  exit 1
fi

mkdir -p "$WALLET_DIR"
printf '%s' "$ORACLE_WALLET_BASE64" | base64 -d | tar -xz -C "$WALLET_DIR"
chmod -R go-rwx "$WALLET_DIR"

# exec 로 java 를 PID 1 로 유지해야 docker stop 의 SIGTERM 이 JVM 에 그대로 전달된다.
exec "$@"
