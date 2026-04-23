# LocalGuest Backend

Spring Boot 멀티모듈 백엔드입니다.  
인증, 가이드/매칭/결제/리뷰 도메인, AI 추천 파이프라인, 실시간 채팅, 운영 배포를 담당합니다.

---

## Tech Stack

- Java 17 (Gradle toolchain)
- Spring Boot 3.4.x
- Spring Security + JWT + OAuth2 Client
- Spring Data JPA / Redis / MongoDB
- Springdoc OpenAPI (Swagger UI)
- Docker + Nginx (Blue-Green)

---

## 멀티모듈 구조

```text
backend
├─ api-server/              # 실행 애플리케이션(조립 모듈)
├─ module-domain/           # member/guide/matching/review/guest-mypage
├─ module-chat/             # STOMP/SockJS + chat repository
├─ module-ai/               # prompt parser, scoring, policy
├─ module-ai-integration/   # DB 후보/가용일 연결
├─ module-openai/           # OpenAI 연동 모듈
├─ module-gemini/           # Gemini 연동 모듈
├─ module-common/           # 공통 설정, Redis/S3, stress endpoint
└─ deployment/              # compose, nginx 전환, deploy script
```

모듈 선언 파일: `settings.gradle`

---

## 실행 방법

Linux/macOS:

```bash
./gradlew :api-server:bootRun
```

Windows:

```powershell
.\gradlew.bat :api-server:bootRun
```

실행 모듈 빌드:

```bash
./gradlew clean :api-server:build
```

---

## 핵심 도메인/API

- Auth: `/auth/*`
- Member: `/members/*`
- Guide: `/guides/*`
- Matching/Payment/Extension: `/matching/*`
- AI: `/ai/recommend`, `/ai/recommend/click`
- Chat: `/chat/rooms/*`, `/ws-stomp`, `/notifications/subscribe`
- Review: `/reviews/*`

Swagger:

- `/swagger-ui/index.html`
- `/api/swagger-ui/index.html`

---

## 데이터 계층

- MySQL: 핵심 도메인 데이터(JPA)
- MongoDB: 채팅 메시지 저장소
- Redis: 인증/신호/캐시성 데이터

`ApiServerApplication`에서 JPA/Mongo repository 패키지 스캔을 통합 설정합니다.

---

## AI 파이프라인

- `module-ai`: 프롬프트 파싱, 규칙 기반 스코어링/리랭크, 추천 응답 조립
- `module-ai-integration`: DB 기반 후보군 생성 및 가용일 결합
- `module-openai`, `module-gemini`: LLM 연동 확장 모듈

운영 전략: **DB 선필터링 + AI 후분석** 2단계 처리

---

## 보안/인증

- JWT 필터 기반 stateless 인증
- OAuth2 Google 로그인 지원
- 엔드포인트별 permitAll/인증필수 정책은 `api-server`의 `SecurityConfig` 기준

---

## 배포 / CI-CD

- 워크플로우: `.github/workflows/backend_cicd.yml`
- Docker 이미지 빌드/푸시 후 EC2 배포
- 배포 스크립트: `deployment/deploy.sh`
- Blue-Green 포트:
  - blue: `8081`
  - green: `8082`
- 인프라 compose: `deployment/docker/docker-compose.infra.yml`

---

## 성능/관측

- Actuator health: `/actuator/health`, `/api/actuator/health`
- 로그: `api-server/src/main/resources/logback-spring.xml`
- 개발용 스트레스 API: `/stress-test/cpu`, `/stress-test/memory`, `/stress-test/delay`
- k6 스크립트: `../stress-test/scripts/test1.js`

---

## 참고 문서

- 매칭 도메인 상세: `docs/BACKEND_MATCHING.md`

