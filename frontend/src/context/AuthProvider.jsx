import { useCallback, useMemo, useState } from 'react'

import { apiLogin, apiRequest } from '../api/client'
import { clearStoredGuideId } from '../lib/guideId.js'
import { getEmailFromToken, getRoleFromToken, parseJwtPayload } from '../lib/jwt'

import { AuthContext } from './auth-context.js'

const TOKEN_KEY = 'localguest_access_token'

export function AuthProvider({ children }) {
  const [token, setTokenState] = useState(() => {
    const t = localStorage.getItem(TOKEN_KEY)
    // 과거 잘못 저장된 JSON 문자열 토큰을 정리한다.
    if (!t || t.startsWith('{')) {
      localStorage.removeItem(TOKEN_KEY)
      return null
    }
    return t
  })

  const setToken = useCallback((next) => {
    if (next) {
      localStorage.setItem(TOKEN_KEY, next)
    } else {
      localStorage.removeItem(TOKEN_KEY)
    }
    setTokenState(next)
  }, [])

  const claims = useMemo(() => parseJwtPayload(token ?? ''), [token])
  const email = token ? getEmailFromToken(token) : null
  const role = token ? getRoleFromToken(token) : null

  const login = useCallback(
    async (emailInput, password, roleInput = 'GUEST') => {
      const requestedRole = String(roleInput || 'GUEST').toUpperCase()
      const oppositeRole = requestedRole === 'GUIDE' ? 'GUEST' : 'GUIDE'

      const tryLogin = async (role) => {
        const { res, text } = await apiLogin('/auth/login', {
          email: emailInput,
          password,
          role,
        })
        return { res, text, role }
      }

      let attempt = await tryLogin(requestedRole)
      if (!attempt.res.ok && attempt.res.status === 401) {
        // 사용자가 role을 반대로 선택한 경우를 자동 보정한다.
        attempt = await tryLogin(oppositeRole)
      }

      if (!attempt.res.ok) {
        let message = '로그인에 실패했습니다.'
        try {
          const body = JSON.parse(attempt.text)
          if (body?.message) {
            message = body.message
          }
        } catch {
          if (attempt.text) {
            message = attempt.text
          }
        }
        throw new Error(message)
      }

      // 최신 백엔드: TokenResponse(JSON), 레거시: plain JWT 문자열
      let accessToken = ''
      if (attempt.text.startsWith('{')) {
        try {
          const body = JSON.parse(attempt.text)
          accessToken = String(body?.accessToken ?? '').trim()
        } catch {
          accessToken = ''
        }
      } else {
        accessToken = attempt.text
      }

      if (!accessToken) {
        throw new Error('서버 응답 형식이 예상과 다릅니다. accessToken을 확인해 주세요.')
      }
      setToken(accessToken)
    },
    [setToken],
  )

  const logout = useCallback(async () => {
    const current = localStorage.getItem(TOKEN_KEY)
    if (current) {
      try {
        await apiRequest('/auth/logout', { method: 'POST' })
      } catch {
        /* 네트워크 오류 시에도 로컬 세션은 정리 */
      }
    }
    clearStoredGuideId()
    setToken(null)
  }, [setToken])

  const value = useMemo(
    () => ({
      token,
      email,
      role,
      claims,
      isAuthenticated: Boolean(token),
      isGuide: role === 'GUIDE',
      login,
      logout,
      setToken,
    }),
    [token, email, role, claims, login, logout, setToken],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
