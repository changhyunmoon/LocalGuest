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

/** `/guides/:id/match/complete` — 일정·매칭 완료 흐름이므로 상단은 마이페이지 탭을 켠다(AI 검색 탭은 끔). */
function isGuidesMatchCompletePath(pathname) {
  return /^\/guides\/[^/]+\/match\/complete(?:\/|$)/.test(pathname)
}

/** `/guides/:id/match*` — 일정 확인/결제 전 플로우. 상단은 마이페이지(예정된 로컬 만남) 컨텍스트로 본다. */
function isGuidesMatchFlowPath(pathname) {
  return /^\/guides\/[^/]+\/match(?:\/|$)/.test(pathname)
}

/**
 * `/guides/...?nav=itinerary` — 예정된 로컬 만남에서 연 가이드 상세·피드 등.
 * 상단 탭은 "예정된 로컬 만남"을 유지하고 AI 검색은 끈다.
 */
function isItineraryNavShell(pathname, search) {
  if (new URLSearchParams(search).get('nav') !== 'itinerary') return false
  if (pathname === '/guides') return false
  if (pathname.startsWith('/guides/') && !pathname.includes('/match')) return true
  return false
}

/** 가이드 목록·상세 등 `/guides*` 는 메뉴에서 빠졌지만, 상단에서는 AI 검색 흐름으로 간주해 같은 탭을 켠다. */
function isAiSearchShellActive(pathname, search, matchComplete, matchFlow) {
  if (matchComplete) return false
  if (matchFlow) return false
  if (isItineraryNavShell(pathname, search)) return false
  if (pathname === '/ai-search' || pathname.startsWith('/ai-search/')) return true
  if (pathname === '/guides' || pathname.startsWith('/guides/')) return true
  return false
}

function AppLayoutInner() {
  const location = useLocation()
  const { pathname, search } = location
  const { isAuthenticated, email, isGuide, logout } = useAuth()
  const { pendingCount } = useGuidePendingRequests()
  const guideMypageActive = isGuide && pathname.startsWith('/guide/mypage')
  const matchComplete = isGuidesMatchCompletePath(pathname)
  const matchFlow = isGuidesMatchFlowPath(pathname)
  const shellItinerary = matchComplete || matchFlow || isItineraryNavShell(pathname, search)
  const focusMode = pathname.startsWith('/auth/signup') || pathname.startsWith('/onboarding')
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
  const routeTransitionKey = `${pathname}${search}`

  return (
    <div className={`shell${focusMode ? ' shell--focus' : ''}`}>
      {!focusMode && (
      <header className="shell-header">
        <Link to="/" className="shell-logo">
          <span className="shell-logo__wordmark">LocalGuest</span>
          <span className="shell-logo__tag">your local travel muse</span>
        </Link>

        <nav className="shell-nav-pill" aria-label="주요 메뉴">
          <NavLink to="/" end className={pillClass()}>
            홈
          </NavLink>
          <NavLink
            to="/ai-search"
            className={({ isActive }) => {
              const on = (isActive || isAiSearchShellActive(pathname, search, matchComplete, matchFlow)) && !shellItinerary
              const parts = ['shell-pill-link', 'shell-pill-link--spark']
              if (on) parts.push('is-on')
              return parts.join(' ')
            }}
          >
            AI 검색
          </NavLink>
          <NavLink to="/messages" className={pillClass()}>
            메시지
          </NavLink>
          {!isGuide && (
            <NavLink to="/mypage/scrapbook" className={pillClass()}>
              스크랩북
            </NavLink>
          )}
          {!isGuide && (
            <NavLink
              to="/mypage/itinerary"
              className={({ isActive }) => {
                const on = isActive || shellItinerary
                const parts = ['shell-pill-link', 'shell-pill-link--cta']
                if (on) parts.push('is-on')
                return parts.join(' ')
              }}
            >
              예정된 로컬 만남
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
              <NavLink
                to={isGuide ? '/guide/mypage/profile' : '/mypage'}
                className={({ isActive }) => {
                  const on = isGuide ? guideMypageActive || matchComplete : isActive || shellItinerary
                  const parts = ['shell-btn', 'shell-btn--ghost']
                  if (on) parts.push('shell-btn--dark')
                  return parts.join(' ')
                }}
              >
                {isGuide ? '가이드 마이페이지' : '여행자 마이페이지'}
              </NavLink>
              <span className="shell-email" title={email ?? ''}>
                {email}
              </span>
              <button type="button" className="shell-btn shell-btn--ghost" onClick={() => void logout()}>
                로그아웃
              </button>
            </>
          ) : (
            <>
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
      )}

      <main className={`shell-main${focusMode ? ' shell-main--focus' : ''}`}>
        <div key={routeTransitionKey} className="shell-route-transition">
          <Outlet />
        </div>
      </main>

      {!focusMode && <SiteFooter />}
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
