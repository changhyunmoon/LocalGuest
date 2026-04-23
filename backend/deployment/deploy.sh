#!/bin/bash

# 1. 환경 설정
BASE_DIR="/home/ubuntu/backend-deploy"
DOCKER_DIR="$BASE_DIR/docker"
NGINX_CONF_DIR="$BASE_DIR/nginx"

COMPOSE_APP="$DOCKER_DIR/docker-compose.yml"
COMPOSE_INFRA="$DOCKER_DIR/docker-compose.infra.yml"
COMPOSE_MONITORING="$DOCKER_DIR/docker-compose.monitoring.yml"

DOCKER_COMPOSE_APP="docker compose -f $COMPOSE_APP"
DOCKER_COMPOSE_INFRA="docker compose -f $COMPOSE_INFRA"
DOCKER_COMPOSE_MONITORING="docker compose -f $COMPOSE_MONITORING"

echo "--- 🚀 멀티모듈(api-server) 배포 프로세스 시작 ---"
cd "$DOCKER_DIR" || exit 1

# 2. 인프라(Redis, MongoDB) 및 네트워크 점검
echo "--- 📦 1. 인프라 환경 및 네트워크 점검 ---"

if [ -f "$COMPOSE_INFRA" ]; then
    $DOCKER_COMPOSE_INFRA up -d || { echo "❌ 인프라 실행 실패"; exit 1; }

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

# 2-1. 모니터링(Prometheus, Grafana) 환경 점검
echo "--- 📊 2. 모니터링 환경 점검 ---"

if [ -f "$COMPOSE_MONITORING" ]; then
    $DOCKER_COMPOSE_MONITORING up -d || { echo "❌ 모니터링 실행 실패"; exit 1; }

    RUNNING_PROMETHEUS=$(docker ps --filter "name=prometheus" --filter "status=running" -q)
    RUNNING_GRAFANA=$(docker ps --filter "name=grafana" --filter "status=running" -q)

    if [ -n "$RUNNING_PROMETHEUS" ] && [ -n "$RUNNING_GRAFANA" ]; then
        echo "✅ 모니터링 컨테이너 가동 확인 완료."
    else
        echo "⏳ 모니터링 안정화 대기 (10s)..."
        sleep 10
    fi

    # Prometheus 헬스체크
    for i in {1..10}; do
        echo "🔎 Prometheus 헬스체크 중... ($i/10)"
        sleep 3
        PROM_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:9090/-/healthy || true)

        if [ "$PROM_STATUS" -eq 200 ]; then
            echo "✅ Prometheus 헬스체크 성공!"
            break
        fi

        if [ $i -eq 10 ]; then
            echo "⚠️ Prometheus 헬스체크 실패. 로그를 확인하세요."
            docker logs --tail 50 prometheus || true
        fi
    done
else
    echo "⚠️ $COMPOSE_MONITORING 파일이 없어 모니터링 단계는 건너뜁니다."
fi

# 3. 사전 환경 검사
if [ -z "$DOCKER_IMAGE_TAG" ] || [ -z "$DOCKERHUB_USERNAME" ] || [ -z "$MONGO_ROOT_USER" ]; then
    echo "❌ 환경변수 누락 (TAG, USERNAME, 혹은 MONGO_ROOT_USER)"
    exit 1
fi

echo "✅ 사전 환경 검사 완료!"

# 4. Blue/Green 결정
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

# 9. 이전 컨테이너 정리
echo "5. 이전 컨테이너($OLD_COLOR) 및 불필요한 이미지 정리..."
$DOCKER_COMPOSE_APP stop backend-$OLD_COLOR || true
$DOCKER_COMPOSE_APP rm -f backend-$OLD_COLOR || true

# 10. 디스크 공간 확보
docker image prune -af
docker builder prune -f

echo "🎊 배포 완료 및 디스크 정리 완료!"
echo "--- 현재 디스크 사용량 ---"
df -h | grep '/$'

echo "--- 현재 실행 중 컨테이너 ---"
docker ps