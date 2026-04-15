#!/bin/bash

# 1. 환경 설정
TARGET_PORT_BLUE=3001
TARGET_PORT_GREEN=3002

# 필수 환경변수 체크 (변수가 비어있으면 배포 중단)
if [ -z "$DOCKERHUB_USERNAME" ] || [ -z "$FRONTEND_IMAGE_NAME" ] || [ -z "$FRONTEND_IMAGE_TAG" ]; then
    echo "❌ 에러: 필수 환경변수(DOCKERHUB_USERNAME, IMAGE_NAME, TAG)가 누락되었습니다."
    exit 1
fi

# 2. 현재 실행 중인 컬러 확인
IS_BLUE=$(docker ps --filter "name=frontend-blue" --filter "status=running" -q)

if [ -z "$IS_BLUE" ]; then
    TARGET_COLOR="blue"; TARGET_PORT=$TARGET_PORT_BLUE; OLD_COLOR="green"; INC_FILE="fe_blue.inc"
else
    TARGET_COLOR="green"; TARGET_PORT=$TARGET_PORT_GREEN; OLD_COLOR="blue"; INC_FILE="fe_green.inc"
fi

echo "--- 🎨 프론트엔드 $TARGET_COLOR 배포 시작 (Port: $TARGET_PORT) ---"

# 3. 이미지 Pull 및 컨테이너 교체
docker pull $DOCKERHUB_USERNAME/$FRONTEND_IMAGE_NAME:$FRONTEND_IMAGE_TAG

# 기존에 죽어있을 수도 있는 타겟 컨테이너 정리
docker stop frontend-$TARGET_COLOR 2>/dev/null || true
docker rm frontend-$TARGET_COLOR 2>/dev/null || true

# 4. 새 컨테이너 실행
docker run -d \
  --name frontend-$TARGET_COLOR \
  -p $TARGET_PORT:80 \
  --restart always \
  $DOCKERHUB_USERNAME/$FRONTEND_IMAGE_NAME:$FRONTEND_IMAGE_TAG

# 5. 헬스체크 (최대 5번 시도)
echo "⏳ 헬스체크 시작..."
for i in {1..5}; do
    sleep 5
    HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:$TARGET_PORT)
    if [ "$HTTP_STATUS" -eq 200 ]; then
        echo "✅ 프론트엔드 헬스체크 성공!"
        break
    fi
    if [ $i -eq 5 ]; then
        echo "❌ 헬스체크 실패! 배포를 중단하고 롤백합니다."
        docker stop frontend-$TARGET_COLOR || true
        exit 1
    fi
    echo "...대기 중 ($i/5)"
done

# 6. Nginx 설정 전환
echo "🔄 Nginx 설정 교체 및 Reload..."
sudo cp /home/ubuntu/frontend-deploy/nginx/$INC_FILE /etc/nginx/conf.d/frontend.inc

if sudo nginx -t; then
    sudo nginx -s reload
    echo "✅ Nginx 리로드 완료!"
else
    echo "❌ Nginx 설정 오류 발생!"
    exit 1
fi

# 7. 구 버전 정리 및 최적화
echo "🧹 불필요한 이미지 및 캐시 정리..."

# 이전 버전 컨테이너 삭제
docker stop frontend-$OLD_COLOR 2>/dev/null || true
docker rm frontend-$OLD_COLOR 2>/dev/null || true

# 찌꺼기 이미지 및 빌드 캐시 정리
docker image prune -f
docker builder prune -f

echo "🎊 프론트엔드 배포 및 디스크 정리 완료!"
df -h | grep '/$'