#!/bin/bash

# 1. 환경 설정 (경로 확인 필수)
BASE_DIR="/home/ubuntu/backend-deploy"
DOCKER_DIR="$BASE_DIR/docker"
NGINX_CONF_DIR="$BASE_DIR/nginx"

# 설정 파일 경로 정의
COMPOSE_APP="$DOCKER_DIR/docker-compose.yml"
COMPOSE_INFRA="$DOCKER_DIR/docker-compose.infra.yml" # 인프라 파일 추가

# 도커 컴포즈 명령어 정의
DOCKER_COMPOSE_APP="docker compose -f $COMPOSE_APP"
DOCKER_COMPOSE_INFRA="docker compose -f $COMPOSE_INFRA"

echo "--- 🚀 멀티모듈(api-server) 배포 프로세스 시작 ---"
cd "$DOCKER_DIR"

# [추가] 2. 인프라(Redis, MongoDB) 및 네트워크 체크
echo "--- 📦 1. 인프라 환경 점검 (Redis, MongoDB) ---"

# 공통 네트워크가 없다면 생성
docker network inspect team6-backend >/dev/null 2>&1 || \
    docker network create team6-backend

if [ -f "$COMPOSE_INFRA" ]; then
    echo "✅ 인프라 컨테이너 상태 확인 및 실행..."
    $DOCKER_COMPOSE_INFRA up -d

    # [추가] 인프라가 준비될 때까지 잠시 대기 (이름 해석 에러 방지용)
    echo "⏳ 인프라 서비스 안정화 대기 중 (10s)..."
    sleep 10
else
    echo "❌ 에러: $COMPOSE_INFRA 파일을 찾을 수 없습니다."
    exit 1
fi

# 3. 필수 환경 변수 주입 확인
if [ -z "$DOCKER_IMAGE_TAG" ] || [ -z "$DOCKERHUB_USERNAME" ]; then
    echo "❌ 에러: 필수 환경 변수(DOCKER_IMAGE_TAG 등)가 설정되지 않았습니다."
    exit 1
fi

if [ ! -f "$COMPOSE_APP" ]; then
    echo "❌ 에러: $COMPOSE_APP 파일을 찾을 수 없습니다."
    exit 1
fi

# 4. Nginx 조각 파일(.inc) 존재 확인
if [ ! -f "$NGINX_CONF_DIR/be_blue.inc" ] || [ ! -f "$NGINX_CONF_DIR/be_green.inc" ]; then
    echo "❌ 에러: Nginx 설정 파일(.inc)이 $NGINX_CONF_DIR 에 없습니다."
    exit 1
fi

echo "✅ 사전 환경 검사 통과!"

# 5. 현재 실행 중인 컨테이너 확인 (Blue/Green 결정)
IS_BLUE=$($DOCKER_COMPOSE_APP ps | grep "backend-blue" | grep "running" || true)

if [ -z "$IS_BLUE" ]; then
    TARGET_COLOR="blue"
    TARGET_PORT=8081
    OLD_COLOR="green"
    INC_FILE="be_blue.inc"
else
    TARGET_COLOR="green"
    TARGET_PORT=8082
    OLD_COLOR="blue"
    INC_FILE="be_green.inc"
fi

echo "### 배포 타겟: $TARGET_COLOR (Port: $TARGET_PORT) ###"

# 6. 새 버전 이미지 가져오기
echo "1. $TARGET_COLOR 이미지 Pull (Tag: $DOCKER_IMAGE_TAG)..."
$DOCKER_COMPOSE_APP pull backend-$TARGET_COLOR || exit 1

# 7. 새 컨테이너 실행
echo "2. $TARGET_COLOR 컨테이너 실행..."
$DOCKER_COMPOSE_APP up -d backend-$TARGET_COLOR || exit 1

# 8. 헬스체크 (Spring Actuator 활용)
for i in {1..30}; do
    echo "3. $TARGET_COLOR 헬스체크 중... ($i/30)"
    sleep 10

    HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:$TARGET_PORT/api/actuator/health)

    if [ "$HTTP_STATUS" -eq 200 ]; then
        echo "✅ 헬스체크 성공! (HTTP Status: $HTTP_STATUS)"
        break
    fi

    if [ $i -eq 30 ]; then
        echo "❌ 헬스체크 최종 실패 (마지막 응답 코드: $HTTP_STATUS)"
        echo "--- 최신 컨테이너 로그(마지막 50줄) ---"
        docker logs --tail 50 backend-$TARGET_COLOR
        echo "❌ $TARGET_COLOR 배포를 중단하고 롤백합니다."
        $DOCKER_COMPOSE_APP stop backend-$TARGET_COLOR
        exit 1
    fi
done

# 9. Nginx 설정 전환
echo "4. Nginx 설정 교체 및 Reload..."
if [ -f "$NGINX_CONF_DIR/$INC_FILE" ]; then
    sudo cp "$NGINX_CONF_DIR/$INC_FILE" /etc/nginx/conf.d/backend.inc

    if sudo nginx -t; then
        sudo nginx -s reload
        echo "✅ Nginx 설정 로드 완료 ($TARGET_COLOR)"
    else
        echo "❌ Nginx 설정 오류! 배포를 중단합니다."
        exit 1
    fi
else
    echo "❌ 에러: $INC_FILE 파일을 찾을 수 없습니다."
    exit 1
fi

# 10. 구 버전 컨테이너 정리
echo "5. 이전 컨테이너($OLD_COLOR) 정리..."
$DOCKER_COMPOSE_APP stop backend-$OLD_COLOR || true
$DOCKER_COMPOSE_APP rm -f backend-$OLD_COLOR || true

echo "🎊 LocalMate(api-server) $TARGET_COLOR 배포 완료!"