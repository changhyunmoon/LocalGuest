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

# 2. 인프라(Redis, MongoDB) 및 네트워크 점검
echo "--- 📦 1. 인프라 환경 및 네트워크 점검 ---"

if [ -f "$COMPOSE_INFRA" ]; then
    # 인프라 기동 (이미 실행 중이면 유지, 설정 변경 시 업데이트)
    # [중요] 변수 미지정 경고를 방지하려면 실행 시점에 변수가 있어야 함
    $DOCKER_COMPOSE_INFRA up -d || { echo "❌ 인프라 실행 실패"; exit 1; }

    # 컨테이너 상태 확인
    RUNNING_REDIS=$(docker ps --filter "name=redis" --filter "status=running" -q)
    RUNNING_MONGO=$(docker ps --filter "name=mongodb" --filter "status=running" -q)

    if [ -n "$RUNNING_REDIS" ] && [ -n "$RUNNING_MONGO" ]; then
        echo "✅ 인프라 컨테이너 가동 확인 완료."
    else
        echo "⏳ 인프라 안정화 대기 (15s)..."
        sleep 15

    fi
else
    echo "❌ 에러: $COMPOSE_INFRA 파일을 찾을 수 없습니다."
    exit 1
fi

# 3. 사전 환경 검사 (누락된 변수 체크 추가)
if [ -z "$DOCKER_IMAGE_TAG" ] || [ -z "$DOCKERHUB_USERNAME" ] || [ -z "$MONGO_ROOT_USER" ]; then
    echo "❌ 환경변수 누락 (TAG, USERNAME, 혹은 MONGO_ROOT_USER)"
    exit 1
fi

echo "✅ 사전 환경 검사 완료!"

# 4. Blue/Green 결정 (이하 로직 동일)
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

# 9. 구 버전 정리 및 최적화
echo "5. 이전 컨테이너($OLD_COLOR) 및 불필요한 이미지 정리..."
$DOCKER_COMPOSE_APP stop backend-$OLD_COLOR || true
$DOCKER_COMPOSE_APP rm -f backend-$OLD_COLOR || true

# 디스크 공간 확보
docker image prune -af
docker builder prune -f # 추가: 빌드 캐시 정리

echo "🎊 배포 완료 및 디스크 정리 완료!"
echo "--- 현재 디스크 사용량 ---"
df -h | grep '/$'