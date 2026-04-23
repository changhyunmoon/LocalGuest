# LocalGuest

**현지인이 채워드리는 진짜 로컬 여행 플랫폼**  
여행자의 취향을 AI로 해석해 로컬 가이드를 매칭하고, 제안서·결제·채팅·리뷰까지 한 흐름에서 완결하는 양면 플랫폼입니다.

`장성재: Tech Lead`  
`윤석규: AI 매칭 · UI/UX 개발`  
`박수빈: Guide 개발 · API Docs`  
`문창현: Platform · DevOps 개발`  
`표지민: Auth · Member · UI 개발`

---

## 1) Brand Story & Product Vision

- **Why LocalGuest**: 정보 과잉 속에서 “어디를 어떻게 가야 할지”를 줄이고, 현지인이 아는 진짜 경험으로 연결
- **Core Value**: 예약 중심이 아닌 **경험 중심** 여행 설계 (여행자) + 포트폴리오 기반 수익화 (가이드)
- **Service Slogan**: **AI 추천 + 로컬 동행 + 제안서 기반 커스텀 여행**
- **Product Positioning**: OTA 보조 기능이 아닌, 현지인 동행에 초점을 둔 AI 매칭 플랫폼

### MVP Narrative

- **Guest Flow**: 성향 입력 → AI 추천 → 가이드 선택 → 제안 수락 → 결제 → 채팅 → 리뷰/스크랩
- **Guide Flow**: 프로필/코스 등록 → 요청 수신 → 제안 작성 → 매칭 확정 → 투어 진행 → 리뷰 축적
- **AI Strategy**: **DB 선필터링 + AI 후분석** 2단계 구조로 추천 비용과 응답 속도 동시 개선

---

## 2) Project Overview

- **Goal**: 여행자와 로컬 가이드를 빠르게 매칭하고, 실제 매칭/결제/일정/채팅까지 하나의 흐름으로 연결
- **MVP Focus**: UX 검증과 핵심 도메인 흐름(회원/가이드/매칭/AI/채팅) 안정화
- **Core Experience**
  - AI 기반 가이드 추천
  - 가이드 탐색/상세/매칭 요청
  - 결제 및 여행 일정 관리
  - 실시간 메시징

---

## 3) Repository Structure

```text
LocalGuest
├─ backend/                         # Spring Boot 멀티모듈
│  ├─ api-server/                   # 실행 모듈 (Boot Application)
│  ├─ module-domain/                # member/guide/matching/review/guest-mypage
│  ├─ module-chat/                  # STOMP/SockJS, 채팅 저장/알림
│  ├─ module-ai/                    # 프롬프트 파싱 + 룰 기반 추천 엔진
│  ├─ module-ai-integration/        # AI 후보/가용일 DB 연동 어댑터
│  ├─ module-common/                # 공통 설정, Redis/S3, 스트레스 테스트 엔드포인트
│  └─ deployment/                   # Docker Compose + Blue/Green 배포 스크립트
├─ frontend/                        # React + Vite SPA
│  ├─ src/pages/                    # 홈/가이드/AI검색/메시지/마이페이지
│  ├─ src/routes/                   # 공통/인증/가이드/마이페이지 라우팅
│  └─ deployment/                   # 프론트 Blue/Green 배포 스크립트
├─ stress-test/                     # k6 부하 테스트 스크립트
└─ .github/workflows/               # frontend/backend/k6 CI/CD
```

---

## 4) Architecture (Code-Based)

### Backend (Multi-Module)

- **Entry Point**: `backend/api-server`
  - `module-*`를 조립하고 실행 가능한 JAR 생성
- **Domain**: `module-domain`
  - 회원(Member), 가이드(Guide), 매칭/결제(Matching), 리뷰(Review), 게스트 마이페이지
- **AI**: `module-ai` + `module-ai-integration`
  - 자연어 프롬프트 파싱
  - 룰 기반 스코어링/리랭크
  - 클릭/노출 신호를 반영한 추천 보정
- **Chat**: `module-chat`
  - STOMP endpoint(`/ws-stomp`), pub/sub(`/pub`, `/sub`)
  - 채팅 메시지 조회/읽음 처리/알림
- **Common**: `module-common`
  - Redis/S3 등 공통 인프라 설정
  - 스트레스 테스트용 API

### Frontend

- React Router 기반 SPA
- 인증 상태(`AuthProvider`)와 API 클라이언트 레이어(`src/api/client.js`) 분리
- 주요 화면: 홈, 가이드 목록/상세, AI 추천, 메시지, 여행자/가이드 마이페이지
- 지도/지오코딩: Kakao Maps SDK 기반

---

## 5) Main Features (MVP)

- **Auth & Account**
  - 회원가입, 로그인(JWT), OAuth2(Google), 아이디/비밀번호 찾기, 프로필 관리
- **Guide Discovery & Matching**
  - 가이드 목록/상세 조회, 매칭 요청 생성/수락/거절/취소, 제안 일정 협의
- **Payment Flow**
  - 매칭 결제 생성/승인/조회, 환불 요청, KakaoPay redirect 처리(stub 포함)
- **AI Recommendation**
  - 프롬프트 기반 가이드 추천(`/ai/recommend`)
  - 추천 카드 클릭 로그 수집(`/ai/recommend/click`)
- **Realtime Chat**
  - STOMP/SockJS 기반 실시간 메시징
  - 채팅방/메시지 조회 및 읽음 동기화
- **MyPage**
  - 여행 일정/결제/스크랩북/리뷰 등 사용자 여정 관리

---

## 6) Tech Stack

### Frontend

- React 19, React Router 7, Vite 8
- STOMP + SockJS (실시간 메시지)
- Vanilla CSS 기반 화면 구성
- Kakao Maps SDK (지도/지오코딩)

### Backend

- Java 17 Toolchain, Spring Boot 3.4.x
- Spring Web / Validation / Security / OAuth2 Client
- Spring Data JPA, Redis, MongoDB
- JWT (`jjwt`) 인증
- Springdoc OpenAPI (Swagger UI)

### Data & Infra

- MySQL (주 도메인 데이터)
- Redis (토큰/추천 신호/캐시성 데이터)
- MongoDB (채팅 저장소)
- Docker, Nginx, Blue/Green 배포
- GitHub Actions 기반 CI/CD

---

## 7) API Summary (Short)

주요 API 그룹만 간단히 정리합니다.

- **Auth**: `/auth/*` (login, logout, reissue)
- **Member**: `/members/*` (join, verification, recovery, profile)
- **Guide**: `/guides/*` (프로필/피드/일정/경력)
- **Matching**: `/matching/requests/*`, `/matching/payments/*`, `/matching/extensions/*`
- **AI**: `/ai/recommend`, `/ai/recommend/click`
- **Chat**: `/chat/rooms/*`, `/notifications/subscribe`, `/ws-stomp`
- **Review**: `/reviews/*`

상세는 Swagger UI를 사용합니다.

- `/swagger-ui/index.html`
- `/api/swagger-ui/index.html` (환경별 context-path 대응)

---

## 8) Local Run

### 1) Backend

```bash
cd backend
./gradlew :api-server:bootRun
```

Windows:

```powershell
cd backend
.\gradlew.bat :api-server:bootRun
```

### 2) Frontend

```bash
cd frontend
npm install
npm run dev
```

### 3) Frontend Environment (예시)

`frontend/.env.local`

```env
VITE_API_BASE_URL=/api
VITE_PROXY_API_TARGET=http://localhost:8080
VITE_KAKAO_MAP_APP_KEY=YOUR_KAKAO_JS_KEY
# 선택
VITE_DEV_START_PATH=/ai-search
```

---

## 9) Deployment & CI/CD

GitHub Actions 워크플로우:

- `frontend_cicd.yml`
  - 프론트 빌드/도커 이미지 푸시
  - EC2 전송 후 `frontend/deployment/deploy.sh` 실행
- `backend_cicd.yml`
  - 멀티모듈 빌드(`:api-server:build`)
  - 백엔드 이미지 푸시
  - EC2 전송 후 `backend/deployment/deploy.sh` 실행
- `k6-deploy.yml.yml`
  - `stress-test/scripts/*`를 k6 서버로 배포

배포 방식:

- **Blue/Green 전략**
  - Backend: `8081(blue)` / `8082(green)`
  - Frontend: `3001(blue)` / `3002(green)`
- 신버전 헬스체크 성공 시 Nginx upstream 전환
- 실패 시 대상 컨테이너 중단(롤백성 동작)

---

## 10) Load Test / Performance Test

### k6 부하 테스트

- 위치: `stress-test/scripts/test1.js`
- 예시: `/api/stress-test/cpu` 대상 단계적 부하(`stages`) 실행

### 서버 스트레스 엔드포인트

- `/stress-test/cpu`
- `/stress-test/memory`
- `/stress-test/delay`

> 운영 API 성능 검증 전, 인프라 한계/병목 유형(CPU, 메모리, 지연)을 빠르게 재현하기 위한 보조 엔드포인트입니다.

---

## 11) Logging & Observability

- `logback-spring.xml` 기반 콘솔 + 파일 로그 동시 기록
- 파일 로그 롤링(일 단위, maxHistory 30)
- 로그 패턴에 traceId MDC 슬롯 포함 (`[%X{traceId}]`)
- Spring Actuator 헬스체크 사용:
  - `/actuator/health`
  - `/api/actuator/health`

---

## 12) Notes

- 백엔드 매칭 도메인 상세 문서: `backend/docs/BACKEND_MATCHING.md`
- 본 README는 현재 레포 코드 기준으로 작성되며, 모듈/배포 스크립트 변경 시 함께 업데이트하는 것을 권장합니다.

