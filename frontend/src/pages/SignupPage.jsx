import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'

import {
  apiRequest,
  beginGoogleOAuth,
  confirmSignupEmailVerification,
  fetchNicknameAvailable,
  sendSignupEmailVerification,
  toUserErrorMessage,
} from '../api/client'

import './SignupPage.css'

const STEPS = [
  { n: 1, label: '계정 정보' },
  { n: 2, label: '본인 인증' },
  { n: 3, label: '가입 완료' },
  { n: 4, label: '여행 성향' },
]

const DNA_MOMENT_OPTIONS = [
  { val: '인스타 감성 사진', icon: '📸', name: '인스타 감성', hint: '아무도 모르는 그 골목, 그 빛' },
  { val: '현지인 찐맛집', icon: '🍜', name: '현지인 찐맛집', hint: '관광객은 절대 못 찾는 그 집' },
  { val: '숨겨진 명소', icon: '🗺️', name: '숨겨진 명소', hint: '가이드북엔 없는 진짜 로컬 스팟' },
  { val: '액티비티 체험', icon: '🏄', name: '액티비티', hint: '몸으로 기억하는 여행' },
  { val: '역사 문화 탐방', icon: '🏛️', name: '역사 & 문화', hint: '그 도시의 진짜 이야기' },
  { val: '야경 감성 카페', icon: '☕', name: '야경 & 카페', hint: '느리게, 오래 기억되는 저녁' },
]
const DNA_PACE_OPTIONS = [
  { val: '빠르고 알차게', label: '⚡ 빠르고 알차게' },
  { val: '적당히 여유롭게', label: '🌿 적당히 여유롭게' },
  { val: '느긋하게 흘러가듯', label: '🌊 느긋하게 흘러가듯' },
]
const DNA_WITH_OPTIONS = ['혼자', '연인과', '친구들과', '가족과']
const DNA_EXPECT_OPTIONS = ['현지 언어 소통', '사진 잘 찍어주기', '디테일한 설명', '즉흥 일정 조율', '조용히 옆에서']

function buildDnaPrompt(moments, pace, travelWith, expect) {
  const parts = []
  if (moments.length) parts.push(moments.join(', ') + '을 원하는')
  if (pace) parts.push(pace + ' 여행을 즐기는')
  if (travelWith) parts.push(travelWith + ' 떠나는')
  if (expect) parts.push(expect + '을 기대하는')
  return parts.length ? `${parts.join(', ')} 여행자에게 맞는 가이드 추천해줘` : ''
}

function buildDnaPreview(moments, pace, travelWith, expect) {
  const parts = []
  if (moments.length) parts.push(moments.join(' · ') + '을 원하는')
  if (pace) parts.push(pace + ' 여행을 즐기는')
  if (travelWith) parts.push(travelWith + ' 떠나는')
  if (expect) parts.push(expect + '을 기대하는')
  return parts.length ? `"${parts.join(', ')} 여행자"` : ''
}

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
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
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
  const [idCheckMessage, setIdCheckMessage] = useState('')
  const [loading, setLoading] = useState(false)
  const [doneId, setDoneId] = useState(null)
  const [dupBusy, setDupBusy] = useState(false)
  const [emailSendBusy, setEmailSendBusy] = useState(false)
  const [emailConfirmBusy, setEmailConfirmBusy] = useState(false)

  const [dnaModalOpen, setDnaModalOpen] = useState(false)
  const [dnaMoments, setDnaMoments] = useState([])
  const [dnaPace, setDnaPace] = useState('')
  const [dnaWith, setDnaWith] = useState('')
  const [dnaExpect, setDnaExpect] = useState('')

  const nickname = userId.trim()
  const isPopNavRef = useRef(false)

  // Step 3 진입 시 DNA 모달 자동 오픈
  useEffect(() => {
    if (step === 3) setDnaModalOpen(true)
  }, [step])

  // 스텝 진입 시 history entry 추가 (step 1은 초기 상태이므로 skip)
  useEffect(() => {
    if (step === 1) return
    if (isPopNavRef.current) { isPopNavRef.current = false; return }
    window.history.pushState({ signupStep: step }, '')
  }, [step])

  // 브라우저 뒤로가기 핸들링
  useEffect(() => {
    const onPop = (e) => {
      const target = typeof e.state?.signupStep === 'number' ? e.state.signupStep : 1
      isPopNavRef.current = true
      setStep(target >= 1 && target <= 3 ? target : 1)
    }
    window.addEventListener('popstate', onPop)
    return () => window.removeEventListener('popstate', onPop)
  }, [])

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
    setIdCheckMessage('')
    if (!idPatternOk(nickname)) {
      setIdCheckMessage('아이디는 영문·숫자 2~16자여야 합니다.')
      setIdFormatChecked(false)
      return
    }
    setDupBusy(true)
    setIdFormatChecked(false)
    try {
      const available = await fetchNicknameAvailable(nickname)
      if (!available) {
        setIdCheckMessage('이미 사용 중인 아이디(닉네임)입니다.')
        setIdFormatChecked(false)
        return
      }
      setIdFormatChecked(true)
      setIdCheckMessage('사용 가능한 아이디입니다. (최종 가입 시 서버에서 한 번 더 확인합니다.)')
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
      setStep(3)
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

  if (step === 3) {
    const dnaPreview = buildDnaPreview(dnaMoments, dnaPace, dnaWith, dnaExpect)
    const dnaTotalSelected = dnaMoments.length + (dnaPace ? 1 : 0) + (dnaWith ? 1 : 0) + (dnaExpect ? 1 : 0)

    const handleDnaNext = () => {
      localStorage.setItem('localguest_travel_dna', JSON.stringify({ moments: dnaMoments, pace: dnaPace, travelWith: dnaWith, expect: dnaExpect }))
      navigate('/ai-search', { state: { initialPrompt: buildDnaPrompt(dnaMoments, dnaPace, dnaWith, dnaExpect) } })
    }

    const toggleDnaMoment = (val) =>
      setDnaMoments((prev) => (prev.includes(val) ? prev.filter((v) => v !== val) : [...prev, val]))

    /* ── inline style tokens ── */
    const S = {
      overlay: { position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.52)', zIndex: 300, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '1rem', overflowY: 'auto' },
      modal: { background: '#fff', borderRadius: 16, padding: '1.75rem 1.5rem 1.5rem', maxWidth: 560, width: '100%', maxHeight: '88vh', overflowY: 'auto', boxShadow: '0 24px 64px rgba(0,0,0,0.22)', colorScheme: 'light' },
      banner: { display: 'flex', alignItems: 'flex-start', gap: 10, padding: '12px 14px', borderRadius: 10, background: '#ecfdf5', border: '1px solid #a7f3d0', marginBottom: '1.25rem' },
      bannerTitle: { fontSize: '0.82rem', fontWeight: 600, color: '#065f46' },
      bannerSub: { fontSize: '0.75rem', color: '#059669', marginTop: 2, lineHeight: 1.45 },
      kicker: { fontSize: '0.7rem', fontWeight: 600, letterSpacing: '0.12em', color: '#9aa0a6', margin: '0 0 0.3rem' },
      title: { fontSize: '1.25rem', fontWeight: 800, letterSpacing: '-0.03em', color: '#1f2328', margin: '0 0 0.4rem', lineHeight: 1.3 },
      lead: { fontSize: '0.85rem', color: '#6b7280', lineHeight: 1.55, margin: '0 0 1.25rem' },
      qBlock: { marginBottom: '1.25rem' },
      qTitle: { fontSize: '0.85rem', fontWeight: 700, color: '#1f2328', marginBottom: '0.18rem' },
      qDesc: { fontSize: '0.72rem', color: '#9aa0a6', marginBottom: '0.6rem' },
      cardGrid: { display: 'grid', gridTemplateColumns: 'repeat(auto-fill,minmax(140px,1fr))', gap: 8 },
      card: (on) => ({ background: on ? '#fff' : '#f4f5f7', border: on ? '1.5px solid #1f2328' : '1px solid #e8eaed', borderRadius: 12, padding: '11px 11px 9px', cursor: 'pointer', position: 'relative', userSelect: 'none', transition: 'border-color .15s, background .15s' }),
      cardIcon: { fontSize: '1.25rem', marginBottom: 6, lineHeight: 1 },
      cardName: { fontSize: '0.8rem', fontWeight: 600, color: '#1f2328', marginBottom: 2 },
      cardHint: { fontSize: '0.68rem', color: '#9aa0a6', lineHeight: 1.4 },
      check: (on) => ({ position: 'absolute', top: 8, right: 8, width: 18, height: 18, borderRadius: '50%', background: '#1f2328', display: 'flex', alignItems: 'center', justifyContent: 'center', opacity: on ? 1 : 0, transform: on ? 'scale(1)' : 'scale(0.6)', transition: 'opacity .18s, transform .18s' }),
      divider: { height: 1, background: '#e8eaed', margin: '0 0 1.25rem' },
      pillRow: { display: 'flex', flexWrap: 'wrap', gap: 7 },
      pill: (on) => ({ padding: '7px 13px', borderRadius: 999, border: on ? '1px solid #1f2328' : '1px solid #e8eaed', background: on ? '#1f2328' : '#f4f5f7', fontSize: '0.82rem', color: on ? '#fff' : '#4b5563', cursor: 'pointer', transition: 'all .15s', userSelect: 'none' }),
      preview: { background: '#f4f5f7', borderRadius: 12, padding: '11px 13px', marginTop: '1.1rem', minHeight: 50, border: '1px solid #e8eaed' },
      previewLabel: { fontSize: '0.65rem', fontWeight: 600, letterSpacing: '0.06em', color: '#9aa0a6', textTransform: 'uppercase', marginBottom: 4 },
      previewText: (filled) => ({ fontSize: '0.82rem', color: filled ? '#1f2328' : '#9aa0a6', fontStyle: filled ? 'normal' : 'italic', lineHeight: 1.55 }),
      counter: { fontSize: '0.7rem', color: '#9aa0a6', marginTop: '0.4rem', textAlign: 'right' },
      btnRow: { display: 'flex', gap: 8, marginTop: '1.4rem' },
      btnSkip: { flex: 1, padding: '0.72rem', borderRadius: 12, border: '1px solid #e8eaed', background: 'transparent', fontSize: '0.82rem', color: '#9aa0a6', cursor: 'pointer', fontFamily: 'inherit' },
      btnNext: { flex: 2.5, padding: '0.72rem', borderRadius: 12, border: 'none', background: '#1f2328', color: '#fff', fontSize: '0.88rem', fontWeight: 700, cursor: 'pointer', fontFamily: 'inherit' },
    }

    return (
      <>
        <div className="signup-wrap">
          <Stepper step={3} />

          <div className="signup-complete">
            <span className="signup-complete-accent" aria-hidden />
            <div className="signup-complete-deco" aria-hidden>
              <span className="dot d1" />
              <span className="dot d2" />
              <span className="dot d3" />
              <span className="dot d4" />
              <span className="dot d5" />
            </div>
            <div className="signup-complete-icon" aria-hidden>🎉</div>
            <h1 className="signup-complete-title">환영합니다!</h1>
            <p className="signup-complete-line">
              <strong>LocalGuest</strong> 가입이 완료되었습니다.
            </p>
            <p className="signup-complete-sub">여행 스타일을 알려주시면 AI가 딱 맞는 가이드를 추천해드릴게요.</p>
            {doneId != null && <p className="signup-complete-id">회원 ID: {doneId}</p>}

            <div className="signup-complete-actions">
              <button type="button" className="signup-complete-btn signup-complete-btn--ghost" onClick={() => setDnaModalOpen(true)}>
                내 여행 스타일 설정하기
              </button>
              <Link
                to="/auth/login"
                className="signup-complete-btn signup-complete-btn--dark"
                state={{ fromSignup: true, hint: '로그인 후 상단「AI 검색」또는 /api/ai/recommend 를 이용할 수 있습니다. (현재 해당 API는 인증 필요)' }}
              >
                ✨ AI 가이드 추천받기
              </Link>
            </div>
          </div>

          <p className="signup-footer-note">
            <Link to="/auth/login">로그인</Link>
          </p>
        </div>

        {dnaModalOpen && (
          <div style={S.overlay} onClick={(e) => { if (e.target === e.currentTarget) setDnaModalOpen(false) }}>
            <div style={S.modal}>
              <div style={S.banner}>
                <span style={{ fontSize: '1.1rem', lineHeight: 1.4, flexShrink: 0 }}>🎉</span>
                <div>
                  <div style={S.bannerTitle}>가입이 완료됐어요!</div>
                  <div style={S.bannerSub}>마지막으로 여행 스타일만 알려주시면 AI가 바로 가이드를 찾아드려요.</div>
                </div>
              </div>

              <p style={S.kicker}>STEP 4 · TRAVEL DNA</p>
              <h2 style={S.title}>당신만의 여행 DNA를<br />알려주세요</h2>
              <p style={S.lead}>선택하신 스타일로 AI가 딱 맞는 로컬 가이드를 연결해 드려요.<br />정답은 없어요, 솔직하게 골라주세요.</p>

              <div style={S.qBlock}>
                <div style={S.qTitle}>어떤 순간을 원하나요?</div>
                <div style={S.qDesc}>여러 개 선택할수록 추천이 정확해져요</div>
                <div style={S.cardGrid}>
                  {DNA_MOMENT_OPTIONS.map((opt) => {
                    const on = dnaMoments.includes(opt.val)
                    return (
                      <div key={opt.val} style={S.card(on)} onClick={() => toggleDnaMoment(opt.val)}>
                        <div style={S.check(on)}>
                          <svg viewBox="0 0 10 8" width="9" height="9" stroke="#fff" fill="none" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                            <polyline points="1,4 3.5,7 9,1" />
                          </svg>
                        </div>
                        <div style={S.cardIcon}>{opt.icon}</div>
                        <div style={S.cardName}>{opt.name}</div>
                        <div style={S.cardHint}>{opt.hint}</div>
                      </div>
                    )
                  })}
                </div>
              </div>

              <div style={S.divider} />

              <div style={S.qBlock}>
                <div style={S.qTitle}>여행 페이스는요?</div>
                <div style={S.pillRow}>
                  {DNA_PACE_OPTIONS.map((opt) => (
                    <div key={opt.val} style={S.pill(dnaPace === opt.val)} onClick={() => setDnaPace(opt.val)}>{opt.label}</div>
                  ))}
                </div>
              </div>

              <div style={S.qBlock}>
                <div style={S.qTitle}>함께하는 사람은요?</div>
                <div style={S.pillRow}>
                  {DNA_WITH_OPTIONS.map((opt) => (
                    <div key={opt} style={S.pill(dnaWith === opt)} onClick={() => setDnaWith(opt)}>{opt}</div>
                  ))}
                </div>
              </div>

              <div style={S.qBlock}>
                <div style={S.qTitle}>가이드에게 기대하는 건요?</div>
                <div style={S.pillRow}>
                  {DNA_EXPECT_OPTIONS.map((opt) => (
                    <div key={opt} style={S.pill(dnaExpect === opt)} onClick={() => setDnaExpect(opt)}>{opt}</div>
                  ))}
                </div>
              </div>

              <div style={S.preview}>
                <div style={S.previewLabel}>내 여행 스타일 미리보기</div>
                <div style={S.previewText(!!dnaPreview)}>{dnaPreview || '선택할수록 나만의 여행 프로필이 완성돼요.'}</div>
              </div>
              <div style={S.counter}>{dnaTotalSelected > 0 ? `총 ${dnaTotalSelected}가지 선택됨` : '아직 선택 전이에요'}</div>

              <div style={S.btnRow}>
                <button style={S.btnSkip} onClick={() => setDnaModalOpen(false)}>나중에 할게요</button>
                <button style={S.btnNext} onClick={handleDnaNext}>내 가이드 찾으러 가기 →</button>
              </div>
            </div>
          </div>
        )}
      </>
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
                {emailSendBusy ? '발송 중…' : codeSent ? '발송완료' : '인증번호 발송'}
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
            disabled={!emailVerified || loading}
            onClick={() => {
              setError('')
              void completeJoin()
            }}
          >
            {loading ? '처리 중…' : '✓ 인증 완료 → 가입 완료'}
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

        <button type="button" className="signup-google" onClick={() => beginGoogleOAuth('GUEST', '/mypage')}>
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
                setIdCheckMessage('')
              }}
            />
            <button type="button" className="signup-dup" onClick={() => void handleDupCheck()} disabled={dupBusy}>
              {dupBusy ? '확인 중…' : '중복 확인'}
            </button>
          </div>
          {idCheckMessage && (
            <p className="signup-hint" style={{ color: idFormatChecked ? '#047857' : '#b71c1c' }}>
              {idCheckMessage}
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
