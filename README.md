# LocalGuest

**현지인이 채워드리는 진짜 로컬 여행 플랫폼**  
여행자의 취향을 AI로 해석해 로컬 가이드를 매칭하고, 제안서·결제·채팅·리뷰까지 하나의 흐름으로 연결합니다.

`장성재: Tech Lead`  
`윤석규: AI 매칭 · UI/UX 개발`  
`박수빈: Guide 개발 · API Docs`  
`문창현: Platform · DevOps 개발`  
`표지민: Auth · Member · UI 개발`

---

## 프로젝트 개요

- **Product**: AI 기반 로컬 가이드 매칭 양면 플랫폼 (Guest ↔ Guide)
- **Core Flow**: 성향 입력 → AI 추천 → 매칭 요청/제안 → 결제 → 채팅 → 리뷰
- **Key Strategy**: DB 선필터링 + AI 후분석 2단계 추천 구조

---

## 문서 구조

이 루트 README는 프로젝트 허브 문서입니다.

- 프론트엔드 상세: [`frontend/README.md`](./frontend/README.md)
- 백엔드 상세: [`backend/README.md`](./backend/README.md)
- 매칭 도메인 문서: [`backend/docs/BACKEND_MATCHING.md`](./backend/docs/BACKEND_MATCHING.md)

---

## Repository Structure

```text
LocalGuest
├─ backend/                         # Spring Boot 멀티모듈
│  ├─ api-server/                   # 실행 모듈
│  ├─ module-domain/                # member/guide/matching/review/mypage
│  ├─ module-chat/                  # STOMP/SockJS, 채팅
│  ├─ module-ai/                    # 추천 엔진/정책
│  ├─ module-ai-integration/        # DB 후보 조립
│  ├─ module-openai/                # OpenAI 연동 모듈
│  ├─ module-gemini/                # Gemini 연동 모듈
│  ├─ module-common/                # 공통 설정/유틸
│  └─ deployment/                   # 배포 스크립트/compose
├─ frontend/                        # React + Vite
├─ stress-test/                     # k6 스크립트
└─ .github/workflows/               # CI/CD
```

---

## Quick Start

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

기본 프록시 기준:

- Frontend dev server: `http://localhost:5173`
- Backend API: `http://localhost:8080`

---

## 핵심 API 그룹

- Auth: `/auth/*`
- Member: `/members/*`
- Guide: `/guides/*`
- Matching/Payment: `/matching/*`
- AI: `/ai/recommend`, `/ai/recommend/click`
- Chat: `/chat/rooms/*`, `/ws-stomp`, `/notifications/subscribe`
- Review: `/reviews/*`

Swagger UI:

- `/swagger-ui/index.html`
- `/api/swagger-ui/index.html`

---

## Infra / CI-CD / 운영

- CI/CD: GitHub Actions (`frontend_cicd.yml`, `backend_cicd.yml`, `k6-deploy.yml.yml`)
- 배포 방식: Blue-Green + Nginx upstream 전환
- 부하 테스트: `stress-test/scripts/test1.js`
- 개발용 스트레스 엔드포인트: `/stress-test/cpu`, `/stress-test/memory`, `/stress-test/delay`
- 로깅/헬스체크: logback 파일 롤링 + `/actuator/health`

