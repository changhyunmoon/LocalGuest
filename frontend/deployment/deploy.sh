#!/bin/bash

# 1. 환경 설정
BASE_DIR="/home/ubuntu/frontend-deploy"
DOCKER_DIR="$BASE_DIR/docker"
NGINX_CONF_DIR="$BASE_DIR/nginx"

COMPOSE_FILE="$DOCKER_DIR/docker-compose.yml"
DOCKER_COMPOSE="docker compose -f $COMPOSE_FILE"

# 필수 환경변수 체크
if [ -z "$DOCKERHUB_USERNAME" ] || [ -z "$FRONTEND_IMAGE_NAME" ] || [ -z "$FRONTEND_IMAGE_TAG" ]; then
    echo "❌ 에러: 필수 환경변수(DOCKERHUB_USERNAME, IMAGE_NAME, TAG)가 누락되었습니다."
    exit 1
fi

echo "--- 🎨 프론트엔드 Blue-Green 배포 시작 ---"
cd "$DOCKER_DIR"

# 2. Blue/Green 결정
# 현재 실행 중인 컨테이너가 blue인지 확인
IS_BLUE=$($DOCKER_COMPOSE ps | grep "frontend-blue" | grep "running" || true)

if [ -z "$IS_BLUE" ]; then
    TARGET_COLOR="blue"; TARGET_PORT=3001; OLD_COLOR="green"; INC_FILE="fe_blue.inc"
else
    TARGET_COLOR="green"; TARGET_PORT=3002; OLD_COLOR="blue"; INC_FILE="fe_green.inc"
fi

echo "### 🚢 배포 타겟: $TARGET_COLOR (Port: $TARGET_PORT) ###"

# 3. 새 버전 이미지 Pull
echo "1. $TARGET_COLOR 이미지 Pull..."
$DOCKER_COMPOSE pull frontend-$TARGET_COLOR || exit 1

# 4. 새 컨테이너 실행 (Docker Compose 사용)
echo "2. $TARGET_COLOR 컨테이너 실행..."
$DOCKER_COMPOSE up -d frontend-$TARGET_COLOR || exit 1

# 5. 헬스체크
echo "3. 헬스체크 시작..."
for i in {1..10}; do
    sleep 5
    HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:$TARGET_PORT)
    if [ "$HTTP_STATUS" -eq 200 ]; then
        echo "✅ 프론트엔드 헬스체크 성공!"
        break
    fi
    if [ $i -eq 10 ]; then
        echo "❌ 헬스체크 실패! 배포를 중단합니다."
        $DOCKER_COMPOSE stop frontend-$TARGET_COLOR
        exit 1
    fi
    echo "...대기 중 ($i/10)"
done

# 6. Nginx 설정 전환
echo "4. Nginx 설정 교체 및 Reload..."
sudo cp "$NGINX_CONF_DIR/$INC_FILE" /etc/nginx/conf.d/frontend.inc

if sudo nginx -t; then
    sudo nginx -s reload
    echo "✅ Nginx 리로드 완료!"
else
    echo "❌ Nginx 설정 오류 발생!"
    exit 1
fi

# 7. 구 버전 정리 및 최적화
echo "5. 이전 컨테이너($OLD_COLOR) 및 불필요한 이미지 정리..."
$DOCKER_COMPOSE stop frontend-$OLD_COLOR || true
$DOCKER_COMPOSE rm -f frontend-$OLD_COLOR || true

# 디스크 공간 확보
docker image prune -f
docker builder prune -f

echo "🎊 프론트엔드 배포 완료!"
df -h | grep '/$'