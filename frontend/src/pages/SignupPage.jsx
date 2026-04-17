import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'

import {
  apiRequest,
  confirmSignupEmailVerification,
  fetchNicknameAvailable,
  sendSignupEmailVerification,
  toUserErrorMessage,
} from '../api/client'

import './SignupPage.css'

const STEPS = [
  { n: 1, label: '계정 정보' },
  { n: 2, label: '본인 인증' },
  { n: 3, label: '여행 성향' },
  { n: 4, label: '가입 완료' },
]

function idPatternOk(v) {
  return /^[a-zA-Z0-9]{2,16}$/.test(v)
}

function passwordPolicyOk(v) {
  if (v.length < 8 || v.length > 16) return false
  const hasLetter = /[A-Za-z]/.test(v)
  const hasDigit = /\d/.test(v)
  const hasSpecial = /[^A-Za-z0-9]/.test(v)
  return hasLetter && hasDigit && hasSpecial
}

function formatMmSs(totalSec) {
  const m = Math.floor(totalSec / 60)
  const s = totalSec % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

function Stepper({ step }) {
  const stepper = useMemo(
    () =>
      STEPS.map((item) => ({
        ...item,
        done: step > item.n,
        current: step === item.n,
      })),
    [step],
  )

  return (
    <div className="signup-stepper" role="list">
      {stepper.map((item) => (
        <div key={item.n} role="listitem" className={`signup-step ${item.current ? 'is-current' : ''}`}>
          <div className={`signup-step-circle ${item.done ? 'done' : ''} ${item.current ? 'current' : ''}`}>
            {item.done ? '✓' : item.n}
          </div>
          <div className="signup-step-label">
            <strong>{item.label}</strong>
          </div>
        </div>
      ))}
    </div>
  )
}

export function SignupPage() {
  const [step, setStep] = useState(1)

  const [email, setEmail] = useState('')
  const [name, setName] = useState('')
  const [userId, setUserId] = useState('')
  const [password, setPassword] = useState('')
  const [password2, setPassword2] = useState('')
  const [showPw, setShowPw] = useState(false)
  const [agreeAll, setAgreeAll] = useState(false)
  const [agreeTos, setAgreeTos] = useState(false)
  const [agreePrivacy, setAgreePrivacy] = useState(false)
  const [idFormatChecked, setIdFormatChecked] = useState(false)

  const [verifyCode, setVerifyCode] = useState('')
  const [codeSent, setCodeSent] = useState(false)
  const [secondsLeft, setSecondsLeft] = useState(null)
  const [emailVerified, setEmailVerified] = useState(false)

  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [doneId, setDoneId] = useState(null)
  const [dupBusy, setDupBusy] = useState(false)
  const [emailSendBusy, setEmailSendBusy] = useState(false)
  const [emailConfirmBusy, setEmailConfirmBusy] = useState(false)

  const nickname = userId.trim()

  useEffect(() => {
    if (secondsLeft == null || secondsLeft <= 0) {
      return undefined
    }
    const t = window.setTimeout(() => {
      setSecondsLeft((s) => (s == null || s <= 1 ? 0 : s - 1))
    }, 1000)
    return () => window.clearTimeout(t)
  }, [secondsLeft])

  const toggleAgreeAll = (checked) => {
    setAgreeAll(checked)
    setAgreeTos(checked)
    setAgreePrivacy(checked)
  }

  const handleAgreeTos = (v) => {
    setAgreeTos(v)
    setAgreeAll(v && agreePrivacy)
  }

  const handleAgreePrivacy = (v) => {
    setAgreePrivacy(v)
    setAgreeAll(agreeTos && v)
  }

  const handleDupCheck = async () => {
    setError('')
    if (!idPatternOk(nickname)) {
      setError('아이디는 영문·숫자 2~16자여야 합니다.')
      setIdFormatChecked(false)
      return
    }
    setDupBusy(true)
    setIdFormatChecked(false)
    try {
      const available = await fetchNicknameAvailable(nickname)
      if (!available) {
        setError('이미 사용 중인 아이디(닉네임)입니다.')
        setIdFormatChecked(false)
        return
      }
      setIdFormatChecked(true)
    } catch (err) {
      setError(err instanceof Error ? err.message : '중복 확인에 실패했습니다.')
      setIdFormatChecked(false)
    } finally {
      setDupBusy(false)
    }
  }

  const resetVerification = () => {
    setVerifyCode('')
    setCodeSent(false)
    setSecondsLeft(null)
    setEmailVerified(false)
  }

  const goStep2 = (e) => {
    e.preventDefault()
    setError('')
    if (!email.trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      setError('올바른 이메일을 입력해 주세요.')
      return
    }
    if (!name.trim()) {
      setError('이름을 입력해 주세요. (백엔드 필수 필드)')
      return
    }
    if (!idPatternOk(nickname)) {
      setError('아이디는 영문·숫자 2~16자여야 합니다.')
      return
    }
    if (!idFormatChecked) {
      setError('아이디 중복 확인(형식 검사)을 눌러 주세요.')
      return
    }
    if (!passwordPolicyOk(password)) {
      setError('비밀번호는 영문·숫자·특수문자를 포함해 8~16자여야 합니다.')
      return
    }
    if (password !== password2) {
      setError('비밀번호가 일치하지 않습니다.')
      return
    }
    if (!agreeTos || !agreePrivacy) {
      setError('필수 약관에 동의해 주세요.')
      return
    }
    resetVerification()
    setStep(2)
  }

  const handleSendCode = async () => {
    setError('')
    setEmailVerified(false)
    setVerifyCode('')
    setEmailSendBusy(true)
    try {
      const { expiresInSeconds } = await sendSignupEmailVerification(email.trim())
      setCodeSent(true)
      setSecondsLeft(expiresInSeconds)
    } catch (err) {
      setCodeSent(false)
      setSecondsLeft(null)
      setError(err instanceof Error ? err.message : '인증번호 발송에 실패했습니다.')
    } finally {
      setEmailSendBusy(false)
    }
  }

  const handleConfirmCode = async () => {
    setError('')
    if (!/^\d{6}$/.test(verifyCode)) {
      setEmailVerified(false)
      setError('인증번호 6자리를 입력해 주세요.')
      return
    }
    setEmailConfirmBusy(true)
    try {
      await confirmSignupEmailVerification(email.trim(), verifyCode)
      setEmailVerified(true)
    } catch (err) {
      setEmailVerified(false)
      setError(err instanceof Error ? err.message : '인증에 실패했습니다.')
    } finally {
      setEmailConfirmBusy(false)
    }
  }

  const completeJoin = async () => {
    setError('')
    setLoading(true)
    setDoneId(null)
    try {
      const body = {
        email: email.trim(),
        password,
        name: name.trim(),
        nickname: nickname || undefined,
      }
      const res = await apiRequest('/members/join', {
        method: 'POST',
        json: body,
        skipAuth: true,
      })
      const text = await res.text()
      if (!res.ok) {
        let payload = text
        if (text?.trim().startsWith('{') || text?.trim().startsWith('[')) {
          try {
            payload = JSON.parse(text)
          } catch {
            payload = text
          }
        }
        setError(toUserErrorMessage(res.status, payload))
        return
      }
      let memberId = null
      try {
        memberId = text ? JSON.parse(text) : null
      } catch {
        memberId = null
      }
      setDoneId(typeof memberId === 'number' ? memberId : null)
      sessionStorage.setItem('prefill_login_email', email.trim())
      setStep(4)
    } catch {
      setError('네트워크 오류가 발생했습니다.')
    } finally {
      setLoading(false)
    }
  }

  const timerActive = secondsLeft != null && secondsLeft > 0
  const timerLabel =
    timerActive && secondsLeft != null
      ? formatMmSs(secondsLeft)
      : codeSent
        ? '00:00'
        : '03:00'

  if (step === 4) {
    return (
      <div className="signup-wrap">
        <Stepper step={4} />

        <div className="signup-complete">
          <span className="signup-complete-accent" aria-hidden />
          <div className="signup-complete-deco" aria-hidden>
            <span className="dot d1" />
            <span className="dot d2" />
            <span className="dot d3" />
            <span className="dot d4" />
            <span className="dot d5" />
          </div>
          <div className="signup-complete-icon" aria-hidden>
            🎉
          </div>
          <h1 className="signup-complete-title">환영합니다!</h1>
          <p className="signup-complete-line">
            <strong>LocalGuest</strong> 가입이 완료되었습니다.
          </p>
          <p className="signup-complete-sub">입력해주신 성향을 바탕으로 딱 맞는 가이드를 추천해드릴게요.</p>
          {doneId != null && <p className="signup-complete-id">회원 ID: {doneId}</p>}

          <div className="signup-complete-actions">
            <Link to="/" className="signup-complete-btn signup-complete-btn--ghost">
              홈으로 가기
            </Link>
            <Link
              to="/auth/login"
              className="signup-complete-btn signup-complete-btn--dark"
              state={{
                fromSignup: true,
                hint:
                  '로그인 후 상단「AI 검색」또는 /api/ai/recommend 를 이용할 수 있습니다. (현재 해당 API는 인증 필요)',
              }}
            >
              ✨ AI 가이드 추천받기
            </Link>
          </div>
        </div>

        <p className="signup-footer-note">
          <Link to="/auth/login">로그인</Link>
        </p>
      </div>
    )
  }

  if (step === 3) {
    return (
      <div className="signup-wrap">
        <Stepper step={3} />
        <div className="signup-card">
          <span className="signup-card-accent" aria-hidden />
          <p className="signup-kicker">STEP 3. TRAVEL STYLE</p>
          <h1 className="signup-title">여행 성향</h1>
          <p className="signup-lead">
            이 단계 UI는 다음 디자인 이미지를 주시면 목업에 맞춰 채웁니다. 지금은{' '}
            <strong>가입 API만</strong> 호출할 수 있게 버튼을 두었습니다.
          </p>
          {error && <p className="signup-error">{error}</p>}
          <div className="signup-step2-actions">
            <button type="button" className="signup-btn-secondary" onClick={() => setStep(2)}>
              이전 단계
            </button>
            <button type="button" className="signup-next" onClick={() => void completeJoin()} disabled={loading}>
              {loading ? '처리 중…' : '✨ 가입 완료 & 가이드 찾기'}
            </button>
          </div>
        </div>
        <p className="signup-footer-note">
          이미 계정이 있나요? <Link to="/auth/login">로그인</Link>
        </p>
      </div>
    )
  }

  if (step === 2) {
    return (
      <div className="signup-wrap">
        <Stepper step={2} />

        <div className="signup-card">
          <span className="signup-card-accent signup-card-accent--lime" aria-hidden />
          <p className="signup-kicker">STEP 2. VERIFICATION</p>
          <h1 className="signup-title">이메일 인증을 완료해주세요 🔒</h1>
          <p className="signup-lead">
            안전한 서비스 이용을 위해 가입하신 이메일로 본인 인증이 필요합니다.
          </p>

          <div className="signup-info-box">
            💡 <strong>왜 인증이 필요한가요?</strong> 가이드와 여행자 간의 신뢰할 수 있는 매칭을 위해 필수적인 절차입니다.
            <span className="signup-info-sub">서버에서 인증번호를 발송·검증합니다. (백엔드 API가 준비되어 있어야 합니다.)</span>
          </div>

          <div className="signup-field">
            <div className="signup-label-row">
              <label className="signup-label" htmlFor="vf-email">
                가입 이메일<span className="req">*</span>
              </label>
            </div>
            <div className="signup-input-row">
              <input id="vf-email" className="signup-input" type="email" value={email} readOnly aria-readonly />
              <button
                type="button"
                className="signup-send-btn"
                onClick={() => void handleSendCode()}
                disabled={timerActive || emailSendBusy}
              >
                {emailSendBusy ? '발송 중…' : '인증번호 발송'}
              </button>
            </div>
            <p className="signup-hint">입력하신 이메일로 6자리 인증번호가 발송됩니다.</p>
          </div>

          <div className="signup-field">
            <div className="signup-label-row">
              <label className="signup-label" htmlFor="vf-code">
                인증번호<span className="req">*</span>
              </label>
              <span className="signup-timer" aria-live="polite">
                {timerLabel}
              </span>
            </div>
            <div className="signup-input-row">
              <input
                id="vf-code"
                className="signup-input"
                inputMode="numeric"
                autoComplete="one-time-code"
                placeholder="인증번호 6자리 입력"
                maxLength={6}
                value={verifyCode}
                onChange={(ev) => setVerifyCode(ev.target.value.replace(/\D/g, '').slice(0, 6))}
              />
              <button
                type="button"
                className="signup-verify-btn"
                onClick={() => void handleConfirmCode()}
                disabled={emailConfirmBusy}
              >
                {emailConfirmBusy ? '확인 중…' : '확인'}
              </button>
            </div>
            {emailVerified && (
              <p className="signup-hint" style={{ color: '#047857' }}>
                인증이 완료되었습니다.
              </p>
            )}
          </div>

          {error && <p className="signup-error">{error}</p>}

          <button
            type="button"
            className="signup-next"
            disabled={!emailVerified}
            onClick={() => {
              setError('')
              setStep(3)
            }}
          >
            ✓ 인증 완료 → 여행 성향 설정
          </button>

          <p className="signup-demo-note">
            <button type="button" className="signup-linkish" onClick={() => setStep(1)}>
              ← 이전 단계 (계정 정보)
            </button>
          </p>
        </div>

        <p className="signup-footer-note">
          이미 계정이 있나요? <Link to="/auth/login">로그인</Link>
        </p>
      </div>
    )
  }

  return (
    <div className="signup-wrap">
      <Stepper step={1} />

      <form className="signup-card" onSubmit={(e) => void goStep2(e)}>
        <span className="signup-card-accent" aria-hidden />
        <p className="signup-kicker">STEP 1. ACCOUNT</p>
        <h1 className="signup-title">계정 정보를 입력해주세요</h1>
        <p className="signup-lead">나만의 로컬 여행을 시작하기 위한 첫 번째 단계예요.</p>

        <button type="button" className="signup-google" disabled title="백엔드 미구현">
          <span className="signup-google-icon" aria-hidden>
            G
          </span>
          Google 계정으로 빠르게 시작하기
        </button>

        <div className="signup-or">또는 이메일로 가입</div>

        <div className="signup-field">
          <div className="signup-label-row">
            <label className="signup-label" htmlFor="su-email">
              이메일<span className="req">*</span>
            </label>
          </div>
          <input
            id="su-email"
            className="signup-input"
            type="email"
            autoComplete="email"
            placeholder="hello@example.com"
            value={email}
            onChange={(ev) => {
              setEmail(ev.target.value)
              setIdFormatChecked(false)
              resetVerification()
            }}
          />
        </div>

        <div className="signup-field">
          <div className="signup-label-row">
            <label className="signup-label" htmlFor="su-name">
              이름<span className="req">*</span>
            </label>
          </div>
          <p className="signup-hint">백엔드 회원가입 API 필수 항목입니다.</p>
          <input
            id="su-name"
            className="signup-input"
            type="text"
            autoComplete="name"
            placeholder="홍길동"
            value={name}
            onChange={(ev) => setName(ev.target.value)}
          />
        </div>

        <div className="signup-field">
          <div className="signup-label-row">
            <label className="signup-label" htmlFor="su-id">
              아이디<span className="req">*</span>
            </label>
          </div>
          <p className="signup-hint">영문, 숫자 2~16자 · 서비스 닉네임으로 저장됩니다.</p>
          <div className="signup-input-row">
            <input
              id="su-id"
              className="signup-input"
              type="text"
              autoComplete="username"
              placeholder="아이디를 입력해주세요"
              value={userId}
              onChange={(ev) => {
                setUserId(ev.target.value)
                setIdFormatChecked(false)
              }}
            />
            <button type="button" className="signup-dup" onClick={() => void handleDupCheck()} disabled={dupBusy}>
              {dupBusy ? '확인 중…' : '중복 확인'}
            </button>
          </div>
          {idFormatChecked && (
            <p className="signup-hint" style={{ color: '#047857' }}>
              사용 가능한 아이디입니다. (최종 가입 시 서버에서 한 번 더 확인합니다.)
            </p>
          )}
        </div>

        <div className="signup-field">
          <div className="signup-label-row">
            <label className="signup-label" htmlFor="su-pw">
              비밀번호<span className="req">*</span>
            </label>
          </div>
          <p className="signup-hint">영문+숫자+특수문자 8자 이상, 최대 16자</p>
          <div className="signup-pass-wrap">
            <input
              id="su-pw"
              className="signup-input"
              type={showPw ? 'text' : 'password'}
              autoComplete="new-password"
              placeholder="비밀번호를 입력해주세요"
              value={password}
              onChange={(ev) => setPassword(ev.target.value)}
            />
            <button type="button" className="signup-eye" onClick={() => setShowPw((v) => !v)} aria-label="비밀번호 표시 전환">
              {showPw ? '🙈' : '👁'}
            </button>
          </div>
        </div>

        <div className="signup-field">
          <div className="signup-label-row">
            <label className="signup-label" htmlFor="su-pw2">
              비밀번호 확인<span className="req">*</span>
            </label>
          </div>
          <input
            id="su-pw2"
            className="signup-input"
            type={showPw ? 'text' : 'password'}
            autoComplete="new-password"
            placeholder="비밀번호를 한 번 더 입력해주세요"
            value={password2}
            onChange={(ev) => setPassword2(ev.target.value)}
          />
        </div>

        <div className="signup-terms">
          <label className="signup-check">
            <input type="checkbox" checked={agreeAll} onChange={(ev) => toggleAgreeAll(ev.target.checked)} />
            <span>전체 동의</span>
          </label>
          <label className="signup-check nested">
            <input type="checkbox" checked={agreeTos} onChange={(ev) => handleAgreeTos(ev.target.checked)} />
            <span>[필수] 이용약관 동의</span>
            <a href="#" onClick={(ev) => ev.preventDefault()}>
              보기
            </a>
          </label>
          <label className="signup-check nested">
            <input type="checkbox" checked={agreePrivacy} onChange={(ev) => handleAgreePrivacy(ev.target.checked)} />
            <span>[필수] 개인정보처리방침 동의</span>
            <a href="#" onClick={(ev) => ev.preventDefault()}>
              보기
            </a>
          </label>
        </div>

        {error && <p className="signup-error">{error}</p>}

        <button type="submit" className="signup-next">
          다음 단계 → 본인 인증
        </button>
      </form>

      <p className="signup-footer-note">
        이미 계정이 있나요? <Link to="/auth/login">로그인</Link>
      </p>
    </div>
  )
}
