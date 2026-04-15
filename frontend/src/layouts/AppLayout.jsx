import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'

import { SiteFooter } from '../components/SiteFooter.jsx'
import { useAuth } from '../context/useAuth.js'

/** @param {...string | false | undefined} extra */
function pillClass(...extra) {
  return ({ isActive }) => {
    const parts = ['shell-pill-link', ...extra.filter(Boolean)]
    if (isActive) {
      parts.push('is-on')
    }
    return parts.join(' ')
  }
}

export function AppLayout() {
  const { pathname } = useLocation()
  const { isAuthenticated, email, isGuide, logout } = useAuth()
  const guideMypageActive = isGuide && pathname.startsWith('/guide/mypage')

  return (
    <div className="shell">
      <header className="shell-header">
        <Link to="/" className="shell-logo">
          LocalGuest
        </Link>

        <nav className="shell-nav-pill" aria-label="주요 메뉴">
          <NavLink to="/" end className={pillClass()}>
            홈
          </NavLink>
          <NavLink to="/guides" className={pillClass()}>
            가이드
          </NavLink>
          <NavLink to="/ai-search" className={pillClass('shell-pill-link--spark')}>
            AI 검색
          </NavLink>
          <NavLink to="/messages" className={pillClass()}>
            메시지
          </NavLink>
          <NavLink
            to={isGuide ? '/guide/mypage/fees' : '/mypage'}
            className={({ isActive }) => {
              const on = isGuide ? guideMypageActive : isActive
              const parts = ['shell-pill-link', 'shell-pill-link--cta']
              if (on) {
                parts.push('is-on')
              }
              return parts.join(' ')
            }}
          >
            {isGuide ? '가이드 마이페이지' : '마이페이지'}
          </NavLink>
          {isAuthenticated && !isGuide && (
            <NavLink to="/guide/register" className={pillClass()}>
              가이드 등록
            </NavLink>
          )}
          {isGuide && (
            <NavLink to="/guide/inbox" className={pillClass('shell-pill-link--guide')}>
              가이드 예약함
            </NavLink>
          )}
        </nav>

        <div className="shell-actions">
          {isAuthenticated ? (
            <>
              <span className="shell-mode" title="역할 표시">
                {isGuide ? '가이드 모드' : '여행자 모드'}
              </span>
              <span className="shell-email" title={email ?? ''}>
                {email}
              </span>
              <button type="button" className="shell-btn shell-btn--ghost" onClick={() => void logout()}>
                로그아웃
              </button>
            </>
          ) : (
            <>
              <span className="shell-mode shell-mode--muted">여행자 모드</span>
              <Link to="/auth/login" className="shell-btn shell-btn--ghost">
                로그인
              </Link>
              <Link to="/auth/signup" className="shell-btn shell-btn--dark">
                회원가입
              </Link>
            </>
          )}
        </div>
      </header>

      <main className="shell-main">
        <Outlet />
      </main>

      <SiteFooter />
    </div>
  )
}
