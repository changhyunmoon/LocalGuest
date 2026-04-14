#!/bin/bash

# 1. 환경 설정
BASE_DIR="/home/ubuntu/backend-deploy"
DOCKER_DIR="$BASE_DIR/docker"
NGINX_CONF_DIR="$BASE_DIR/nginx"

COMPOSE_APP="$DOCKER_DIR/docker-compose.yml"
COMPOSE_INFRA="$DOCKER_DIR/docker-compose.infra.yml"

DOCKER_COMPOSE_APP="docker compose -f $COMPOSE_APP"
DOCKER_COMPOSE_INFRA="docker compose -f $COMPOSE_INFRA"

echo "--- 🚀 멀티모듈(api-server) 배포 프로세스 시작 ---"
cd "$DOCKER_DIR"

# 2. 인프라(Redis, MongoDB) 선행 실행 보장
echo "--- 📦 1. 인프라 환경 점검 및 실행 ---"

# 네트워크 존재 확인 및 생성
docker network inspect team6-backend >/dev/null 2>&1 || docker network create team6-backend

if [ -f "$COMPOSE_INFRA" ]; then
    # running 상태인 인프라 컨테이너 확인
    RUNNING_REDIS=$(docker ps --filter "name=redis" --filter "status=running" -q)
    RUNNING_MONGO=$(docker ps --filter "name=mongodb" --filter "status=running" -q)

    if [ -n "$RUNNING_REDIS" ] && [ -n "$RUNNING_MONGO" ]; then
        echo "✅ 인프라가 이미 실행 중입니다. 배포를 진행합니다."
    else
        echo "⚠️  인프라가 실행 중이지 않습니다. 인프라를 먼저 기동합니다..."
        $DOCKER_COMPOSE_INFRA up -d || { echo "❌ 인프라 실행 실패"; exit 1; }
        echo "⏳ 인프라 안정화 대기 (15s)..."
        sleep 15
    fi
else
    echo "❌ 에러: $COMPOSE_INFRA 파일을 찾을 수 없습니다."
    exit 1
fi

# 3. 사전 환경 검사
[ -z "$DOCKER_IMAGE_TAG" ] || [ -z "$DOCKERHUB_USERNAME" ] && { echo "❌ 환경변수 누락"; exit 1; }
[ ! -f "$COMPOSE_APP" ] && { echo "❌ $COMPOSE_APP 없음"; exit 1; }

echo "✅ 사전 환경 검사 완료!"

# 4. Blue/Green 결정
IS_BLUE=$($DOCKER_COMPOSE_APP ps | grep "backend-blue" | grep "running" || true)

if [ -z "$IS_BLUE" ]; then
    TARGET_COLOR="blue"; TARGET_PORT=8081; OLD_COLOR="green"; INC_FILE="be_blue.inc"
else
    TARGET_COLOR="green"; TARGET_PORT=8082; OLD_COLOR="blue"; INC_FILE="be_green.inc"
fi

echo "### 🚢 배포 타겟: $TARGET_COLOR (Port: $TARGET_PORT) ###"

# 5. 새 버전 이미지 Pull
echo "1. $TARGET_COLOR 이미지 Pull..."
$DOCKER_COMPOSE_APP pull backend-$TARGET_COLOR || exit 1

# 6. 새 컨테이너 실행
echo "2. $TARGET_COLOR 컨테이너 실행..."
$DOCKER_COMPOSE_APP up -d backend-$TARGET_COLOR || exit 1

# 7. 헬스체크 (Spring Actuator)
for i in {1..30}; do
    echo "3. $TARGET_COLOR 헬스체크 중... ($i/30)"
    sleep 10
    HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:$TARGET_PORT/api/actuator/health)

    if [ "$HTTP_STATUS" -eq 200 ]; then
        echo "✅ 헬스체크 성공!"
        break
    fi

    if [ $i -eq 30 ]; then
        echo "❌ 헬스체크 실패. 로그 확인 후 롤백합니다."
        docker logs --tail 50 backend-$TARGET_COLOR
        $DOCKER_COMPOSE_APP stop backend-$TARGET_COLOR
        exit 1
    fi
done

# 8. Nginx 설정 전환
echo "4. Nginx 설정 교체 및 Reload..."
sudo cp "$NGINX_CONF_DIR/$INC_FILE" /etc/nginx/conf.d/backend.inc
sudo nginx -t && sudo nginx -s reload || { echo "❌ Nginx Reload 실패"; exit 1; }

# 9. 구 버전 정리 및 디스크 용량 최적화 (핵심 추가)
echo "5. 이전 컨테이너($OLD_COLOR) 및 불필요한 이미지 정리..."
$DOCKER_COMPOSE_APP stop backend-$OLD_COLOR || true
$DOCKER_COMPOSE_APP rm -f backend-$OLD_COLOR || true

# [개선] 현재 사용 중이지 않은 모든 이미지 삭제 (디스크 공간 확보)
# -f 옵션으로 확인 절차 없이 삭제합니다.
docker image prune -af

echo "🎊 배포 완료 및 디스크 정리 완료!"
echo "--- 현재 디스크 사용량 ---"
df -h | grep '/$'