# Frontend Collaboration Structure

프론트 동시 작업 충돌을 줄이기 위한 최소 규칙입니다.

## Branch Prefix

- `feature/layout-*` : 공통 레이아웃/쉘
- `feature/router-*` : 라우트 추가/변경 (`src/routes/*`, `src/App.jsx`)
- `feature/guide-*` : 가이드 기능/페이지
- `feature/mypage-*` : 여행자 마이페이지 기능
- `feature/auth-*` : 로그인/회원가입/인증 UX

## File Ownership (권장)

- **layout owner**
  - `src/layouts/AppLayout.jsx`
  - `src/layouts/AppLayout.css`
  - `src/layouts/GuideDashboardLayout.jsx`
  - `src/layouts/GuideDashboardLayout.css`
  - `src/layouts/MypageShell.jsx`
- **router owner**
  - `src/App.jsx`
  - `src/routes/*`
- **auth owner**
  - `src/context/AuthProvider.jsx`
  - `src/pages/LoginPage.jsx`
  - `src/pages/SignupPage.jsx`
  - `src/routes/authRoutes.jsx`의 **route path 변경 권한**

다른 담당자가 공통 파일 수정이 필요하면:
1) 이슈 등록
2) owner 브랜치로 PR 요청

## Parallel Work Boundary (A/B 충돌 방지)

- **A 트랙 (`feature/router-*`)에서 수정 가능**
  - `src/App.jsx`
  - `src/routes/commonRoutes.jsx`
  - `src/routes/guideRoutes.jsx`
  - `src/routes/mypageRoutes.jsx`
  - `src/routes/authRoutes.jsx`는 import/조합 확인만, **경로 정책 변경 금지**
- **B 트랙 (`feature/auth-*`) 전용**
  - `src/context/AuthProvider.jsx`
  - `src/pages/LoginPage.jsx`, `src/pages/SignupPage.jsx`
  - `src/api/client.js`
  - 권한/에러 문구(401/403/500) 및 토큰 반영 플로우

## Routing Split

라우트 충돌 완화를 위해 도메인별 route 파일을 사용합니다.

- `src/routes/commonRoutes.jsx`
- `src/routes/authRoutes.jsx`
- `src/routes/mypageRoutes.jsx`
- `src/routes/guideRoutes.jsx`

새 페이지 추가 시:
1) 해당 도메인 route 파일만 수정
2) `App.jsx`는 가능하면 건드리지 않음
3) auth 경로(`auth/*`) 변경은 `feature/auth-*` PR에서만 반영

## Route QA Checklist (A 트랙)

- [ ] path 누락/중복 없음 (`guides/*`, `guide/*`, `mypage/*`, `auth/*`)
- [ ] wildcard(`*`) fallback 정상 동작
- [ ] `VITE_DEV_START_PATH` 사용 시 index route만 리다이렉트
- [ ] `VITE_DEV_START_PATH=/`일 때 홈 루프 없음

## CSS Rule

- 페이지/도메인 단위 CSS 우선
- 공통 CSS 수정 최소화
- 공통 스타일이 꼭 필요하면 `feature/layout-*`에서만 반영

## Daily Workflow

```bash
git switch main
git pull origin main
git switch -c feature/<domain>-<topic>
```

작업 중간:

```bash
git add -A
git commit -m "wip: <topic>"
git fetch origin
git rebase origin/main
```

