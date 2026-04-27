# LocalGuest Frontend

React + Vite 기반 SPA입니다.  
홈, AI 추천, 가이드 탐색, 매칭 플로우, 메시지, 마이페이지 화면을 담당합니다.

---

## Tech Stack

- React 19
- React Router 7
- Vite 8
- STOMP + SockJS (실시간 메시지)
- Kakao Maps SDK (지도/지오코딩)
- ESLint 9

---

## 프로젝트 구조

```text
frontend
├─ src
│  ├─ api/                # API client, auth-aware request helper
│  ├─ components/         # 공통 UI 컴포넌트
│  ├─ context/            # AuthProvider 등 전역 상태
│  ├─ layouts/            # AppLayout, 대시보드 레이아웃
│  ├─ pages/              # 홈/가이드/AI/메시지/마이페이지 화면
│  ├─ routes/             # common/auth/guide/mypage 라우트 분리
│  └─ lib/                # 지도/유틸/도메인 헬퍼
├─ deployment/            # 프론트 blue-green 배포 스크립트
└─ Dockerfile
```

---

## 실행 방법

```bash
npm install
npm run dev
```

기본 실행 주소: `http://localhost:5173`

---

## 환경 변수

`.env.local` 예시:

```env
VITE_API_BASE_URL=/api
VITE_PROXY_API_TARGET=http://localhost:8080
VITE_KAKAO_MAP_APP_KEY=YOUR_KAKAO_JS_KEY
VITE_DEV_START_PATH=/ai-search
```

설명:

- `VITE_API_BASE_URL`: 프론트 API 호출 prefix (기본 `/api`)
- `VITE_PROXY_API_TARGET`: Vite dev proxy 대상 백엔드 주소
- `VITE_KAKAO_MAP_APP_KEY`: 카카오 지도 SDK 키
- `VITE_DEV_START_PATH`: 개발 시 첫 진입 경로 강제 이동(선택)

---

## 라우팅 개요

- 공통: 홈, 가이드 목록/상세, AI 검색, 메시지
- 인증: 로그인/회원가입/아이디찾기/비밀번호재설정/OAuth 콜백
- 가이드: 신청, 인박스, 코스 편집, 가이드 마이페이지
- 여행자: 예정 일정, 스크랩북, 여행/결제/리뷰 등 마이페이지

라우트 엔트리: `src/App.jsx`

---

## API 연동 규칙

- 공통 요청 유틸: `src/api/client.js`
- 기본 토큰 키: `localguest_access_token`
- 401 발생 시 전역 unauthorized handler를 통해 로그아웃 UX 연동
- API Base URL은 `VITE_API_BASE_URL` 기준으로 조립

---

## 배포

- GitHub Actions: `.github/workflows/frontend_cicd.yml`
- Docker 이미지 빌드/푸시 후 EC2 배포 스크립트 실행
- 배포 스크립트: `deployment/deploy.sh`
- Blue-Green 포트:
  - blue: `3001`
  - green: `3002`

