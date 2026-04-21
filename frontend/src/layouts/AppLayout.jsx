import { useEffect, useRef } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'

import { SiteFooter } from '../components/SiteFooter.jsx'
import { GuidePendingRequestsProvider, useGuidePendingRequests } from '../context/GuidePendingRequestsProvider.jsx'
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

/** `/guides/:id/match/complete` — 일정·매칭 완료 흐름이므로 상단은 마이페이지 탭을 켠다(가이드 탭은 끔). */
function isGuidesMatchCompletePath(pathname) {
  return /^\/guides\/[^/]+\/match\/complete(?:\/|$)/.test(pathname)
}

function AppLayoutInner() {
  const { pathname } = useLocation()
  const { isAuthenticated, email, isGuide, logout } = useAuth()
  const { pendingCount } = useGuidePendingRequests()
  const guideMypageActive = isGuide && pathname.startsWith('/guide/mypage')
  const guestItineraryActive = !isGuide && (pathname.startsWith('/mypage/itinerary') || pathname.startsWith('/upcoming-trips'))
  const matchComplete = isGuidesMatchCompletePath(pathname)
  const baseTitleRef = useRef(typeof document !== 'undefined' ? document.title : 'LocalGuest')

  useEffect(() => {
    const base = baseTitleRef.current
    const onInbox = pathname === '/guide/inbox' || pathname.startsWith('/guide/inbox/')
    if (pendingCount > 0 && !onInbox) {
      document.title = pendingCount > 99 ? `(99+) ${base}` : `(${pendingCount}) ${base}`
    } else {
      document.title = base
    }
  }, [pendingCount, pathname])

  const inboxAria =
    pendingCount > 0 ? `매칭 요청, 처리 필요 ${pendingCount}건` : '매칭 요청'

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
          <NavLink
            to="/guides"
            className={({ isActive }) => {
              const on = isActive && !matchComplete
              const parts = ['shell-pill-link']
              if (on) parts.push('is-on')
              return parts.join(' ')
            }}
          >
            가이드
          </NavLink>
          <NavLink to="/ai-search" className={pillClass('shell-pill-link--spark')}>
            AI 검색
          </NavLink>
          <NavLink to="/messages" className={pillClass()}>
            메시지
          </NavLink>
          <NavLink
            to={isGuide ? '/guide/mypage/profile' : '/mypage'}
            className={({ isActive }) => {
              const on = isGuide
                ? guideMypageActive || matchComplete
                : (isActive || matchComplete) && !guestItineraryActive
              const parts = ['shell-pill-link', 'shell-pill-link--cta']
              if (on) {
                parts.push('is-on')
              }
              return parts.join(' ')
            }}
          >
            {isGuide ? '가이드 마이페이지' : '마이페이지'}
          </NavLink>
          {!isGuide && (
            <NavLink to="/upcoming-trips" className={pillClass('shell-pill-link--cta')}>
              앞으로의 여행 일정
            </NavLink>
          )}
          {isGuide && (
            <NavLink
              to="/guide/inbox"
              className={pillClass('shell-pill-link--guide', 'shell-pill-link--inbox')}
              aria-label={inboxAria}
              title={pendingCount > 0 ? `처리할 매칭 요청 ${pendingCount}건` : undefined}
            >
              <span className="shell-inbox-label">매칭 요청</span>
              {pendingCount > 0 && (
                <span className="shell-inbox-badge" aria-hidden="true">
                  {pendingCount > 99 ? '99+' : pendingCount}
                </span>
              )}
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

export function AppLayout() {
  return (
    <GuidePendingRequestsProvider>
      <AppLayoutInner />
    </GuidePendingRequestsProvider>
  )
}
