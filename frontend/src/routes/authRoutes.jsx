import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

import { useAuth } from '../context/useAuth.js'
import { getNeedsTravelOnboardingFromToken, getRoleFromToken } from '../lib/jwt.js'
import { hasSavedTravelDna } from '../lib/travelDna.js'
import { FindIdPage } from '../pages/FindIdPage'
import { ForgotPasswordPage } from '../pages/ForgotPasswordPage'
import { LoginPage } from '../pages/LoginPage'
import { OnboardingPage } from '../pages/OnboardingPage'
import { SignupPage } from '../pages/SignupPage'
import { OAUTH_GUIDE_DISCLAIMER_OK_KEY } from '../lib/oauthGuideFlow.js'
import { setStoredOauthRejection } from '../lib/oauthRejection.js'
import '../pages/FormPage.css'

const LS_TOKEN_KEY = 'localguest_access_token'

/**
 * GUEST(여행자) 토큰으로는 가이드 대시보드·수락함·코스편집 URL 등(가이드 앱)으로 보내면 안 됨.
 * `oauth_intended_role`이 비었을 때(세션 이슈 등)에도 returnTo가 `/guide/mypage`로 남는 경우를 막는다.
 * `/guide/register`, `/guide/apply` 는 여행자도 이용 가능.
 * @param {string} [baseDestination]
 */
function isGuestIneligibleForGuideAppPath(baseDestination) {
  const p = String(baseDestination ?? '')
    .split('?')[0]
    .split('#')[0]
  if (p === '/guide/mypage' || p.startsWith('/guide/mypage/')) return true
  if (p === '/guide/inbox' || p.startsWith('/guide/inbox/')) return true
  if (p.startsWith('/guide/requests/') || p.startsWith('/guide/schedules/')) return true
  return false
}

/**
 * GUEST 콜백용 — 가이드 앱 URL이면 여행자 마이페이지로.
 * @param {string} [baseDestination]
 */
function toGuestSafeOauthReturn(baseDestination) {
  if (isGuestIneligibleForGuideAppPath(baseDestination)) {
    return '/mypage'
  }
  return String(baseDestination ?? '/mypage')
}

/**
 * GUEST(또는 role 파싱 실패) + 여행 DNA 없음 → 온보딩. `onboarding: true` JWT(최초 소셜 가입)도 동일.
 * GUIDE 플로는 returnTo 그대로. GUEST는 returnTo에 가이드 앱 딥링크가 있으면 `toGuestSafeOauthReturn`으로 치환.
 * @param {string} token
 * @param {string} baseDestination
 */
function resolveOauthPostLoginDestination(token, baseDestination) {
  const roleRaw = getRoleFromToken(token) ?? ''
  const role = roleRaw || 'GUEST'
  if (role === 'GUIDE') return baseDestination
  if (role === 'GUEST') {
    const claim = getNeedsTravelOnboardingFromToken(token)
    if (claim) {
      if (hasSavedTravelDna()) {
        if (baseDestination === '/onboarding') return '/mypage'
        return toGuestSafeOauthReturn(baseDestination)
      }
      return '/onboarding'
    }
    if (!hasSavedTravelDna()) return '/onboarding'
    if (baseDestination === '/onboarding') return '/mypage'
    return toGuestSafeOauthReturn(baseDestination)
  }
  return baseDestination
}

function oauthRoleLabel(r) {
  return r === 'GUIDE' ? '가이드 (GUIDE)' : '여행자 (GUEST)'
}

function OAuth2CallbackPage() {
  const { setToken } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [roleMismatch, setRoleMismatch] = useState(null)
  /** 가이드로 시도했는데 토큰이 GUEST(아직 guide_profiles 없음 등) — 온보딩/가이드 등록 안내 */
  const [guideAsGuest, setGuideAsGuest] = useState(null)

  useEffect(() => {
    const params = new URLSearchParams(location.search)
    if (params.get('authError') === '1') {
      setStoredOauthRejection(String(params.get('reason') ?? 'oauth_failed'))
      navigate({ pathname: '/auth/login' }, { replace: true })
      return
    }
    const token = String(params.get('token') ?? '').trim()
    if (!token) return
    setToken(token)

    let destination = '/mypage'
    let intended = ''
    try {
      const saved = sessionStorage.getItem('oauth_return_to')
      if (saved) {
        destination = saved
        sessionStorage.removeItem('oauth_return_to')
      }
      intended = String(sessionStorage.getItem('oauth_intended_role') ?? '').toUpperCase()
      sessionStorage.removeItem('oauth_intended_role')
    } catch {
      /* ignore */
    }

    const signed = getRoleFromToken(token) ?? 'GUEST'
    if (intended === 'GUIDE' && signed === 'GUEST') {
      let preAck = false
      try {
        preAck = sessionStorage.getItem(OAUTH_GUIDE_DISCLAIMER_OK_KEY) === '1'
        if (preAck) {
          sessionStorage.removeItem(OAUTH_GUIDE_DISCLAIMER_OK_KEY)
        }
      } catch {
        /* ignore */
      }
      if (preAck) {
        const next = !hasSavedTravelDna() ? '/onboarding' : '/guide/register'
        navigate(next, { replace: true })
        return
      }
      setGuideAsGuest({ destination, needsOnboarding: !hasSavedTravelDna() })
      return
    }
    if (signed === 'GUEST' && isGuestIneligibleForGuideAppPath(destination)) {
      setGuideAsGuest({ destination, needsOnboarding: !hasSavedTravelDna() })
      return
    }
    if (intended && signed && intended !== signed) {
      setRoleMismatch({ destination, requestedRole: intended, signedInRole: signed })
      return
    }
    const next = resolveOauthPostLoginDestination(token, destination)
    navigate(next, { replace: true })
  }, [location.search, navigate, setToken])

  const hasToken = new URLSearchParams(location.search).has('token')
  if (hasToken && guideAsGuest) {
    const g = guideAsGuest
    return (
      <div
        style={{
          position: 'fixed',
          inset: 0,
          background: 'rgba(15, 23, 42, 0.45)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 50,
          padding: '1rem',
        }}
        role="dialog"
        aria-modal="true"
        aria-labelledby="oauth-guide-guest-title"
      >
        <div
          className="form-card"
          style={{ maxWidth: 440, width: '100%', boxShadow: '0 25px 50px -12px rgba(0,0,0,0.35)' }}
        >
          <h2 id="oauth-guide-guest-title" style={{ marginTop: 0 }}>
            가이드로 로그인
          </h2>
          <p className="form-hint" style={{ marginBottom: '1rem' }}>
            {g.needsOnboarding ? (
              <>
                가이드로 활동하시려면 <strong>먼저 여행자(게스트) 계정</strong>으로 이용을 시작한 뒤,{' '}
                <strong>가이드 약관·신청(프로필 등록)</strong>을 완료해야 합니다. 아직 여행 성향을 입력하지 않으셨다면
                아래에서 설문을 먼저 완료해 주세요. 이후 마이페이지에서 가이드 등록을 이어갈 수 있어요.
              </>
            ) : (
              <>
                가이드로 활동하시려면 <strong>가이드 약관 동의</strong>와 <strong>가이드 등록(프로필)</strong>이 필요합니다.
                지금은 <strong>여행자(게스트) 계정</strong>으로 로그인되었습니다. 아래에서 가이드 등록을 진행하거나, 나중에
                마이페이지에서도 신청하실 수 있어요.
              </>
            )}
          </p>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            {g.needsOnboarding ? (
              <button
                type="button"
                className="submit"
                onClick={() => {
                  setGuideAsGuest(null)
                  navigate('/onboarding', { replace: true })
                }}
              >
                여행 성향 설문으로 이동
              </button>
            ) : (
              <button
                type="button"
                className="submit"
                onClick={() => {
                  setGuideAsGuest(null)
                  navigate('/guide/register', { replace: true })
                }}
              >
                가이드 등록(약관)으로 이동
              </button>
            )}
            <button
              type="button"
              className="submit ghost"
              onClick={() => {
                const t = String(localStorage.getItem(LS_TOKEN_KEY) ?? '').trim()
                setGuideAsGuest(null)
                const next = t ? resolveOauthPostLoginDestination(t, g.destination) : g.destination
                navigate(next, { replace: true })
              }}
            >
              {g.needsOnboarding ? '나중에 (마이페이지로)' : '나중에 (마이페이지로)'}
            </button>
          </div>
        </div>
      </div>
    )
  }
  if (hasToken && roleMismatch) {
    return (
      <div
        style={{
          position: 'fixed',
          inset: 0,
          background: 'rgba(15, 23, 42, 0.45)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 50,
          padding: '1rem',
        }}
        role="dialog"
        aria-modal="true"
        aria-labelledby="oauth-role-mismatch-title"
      >
        <div
          className="form-card"
          style={{ maxWidth: 420, width: '100%', boxShadow: '0 25px 50px -12px rgba(0,0,0,0.35)' }}
        >
          <h2 id="oauth-role-mismatch-title" style={{ marginTop: 0 }}>
            로그인 유형 안내
          </h2>
          <p className="form-hint" style={{ marginBottom: '1rem' }}>
            선택하신 로그인 유형은 {oauthRoleLabel(roleMismatch.requestedRole)}인데, 실제로는{' '}
            {oauthRoleLabel(roleMismatch.signedInRole)} 계정으로 로그인되었습니다. (소셜 계정·서버에 등록된 역할 기준)
          </p>
          <button
            type="button"
            className="submit"
            onClick={() => {
              const t = String(localStorage.getItem(LS_TOKEN_KEY) ?? '').trim()
              const dest = t ? resolveOauthPostLoginDestination(t, roleMismatch.destination) : roleMismatch.destination
              setRoleMismatch(null)
              navigate(dest, { replace: true })
            }}
          >
            확인 후 이동
          </button>
        </div>
      </div>
    )
  }
  if (hasToken) {
    return <p style={{ margin: '2rem auto', maxWidth: 420 }}>소셜 로그인 처리 중입니다…</p>
  }
  return (
    <div style={{ margin: '2rem auto', maxWidth: 520 }}>
      <h2>소셜 로그인 콜백</h2>
      <p className="form-hint">토큰 없이 이 페이지에 도달했어요. 로그인을 다시 시도하거나 `authError` 처리 직전일 수 있어요.</p>
    </div>
  )
}

export const authRoutes = [
  { path: 'auth/login', element: <LoginPage /> },
  { path: 'auth/find-id', element: <FindIdPage /> },
  { path: 'auth/forgot-password', element: <ForgotPasswordPage /> },
  { path: 'auth/signup', element: <SignupPage /> },
  { path: 'oauth2/callback', element: <OAuth2CallbackPage /> },
  { path: 'onboarding', element: <OnboardingPage /> },
]

