import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import './OnboardingPage.css'

const STEPPER_STEPS = [
  { n: 1, label: '계정 정보' },
  { n: 2, label: '본인 인증' },
  { n: 3, label: '가입 완료' },
  { n: 4, label: '여행 성향' },
]

const MOMENT_OPTIONS = [
  { val: '인스타 감성 사진', icon: '📸', name: '인스타 감성', hint: '아무도 모르는 그 골목, 그 빛' },
  { val: '현지인 찐맛집', icon: '🍜', name: '현지인 찐맛집', hint: '관광객은 절대 못 찾는 그 집' },
  { val: '숨겨진 명소', icon: '🗺️', name: '숨겨진 명소', hint: '가이드북엔 없는 진짜 로컬 스팟' },
  { val: '액티비티 체험', icon: '🏄', name: '액티비티', hint: '몸으로 기억하는 여행' },
  { val: '역사 문화 탐방', icon: '🏛️', name: '역사 & 문화', hint: '그 도시의 진짜 이야기' },
  { val: '야경 감성 카페', icon: '☕', name: '야경 & 카페', hint: '느리게, 오래 기억되는 저녁' },
]

const PACE_OPTIONS = [
  { val: '빠르고 알차게', label: '⚡ 빠르고 알차게' },
  { val: '적당히 여유롭게', label: '🌿 적당히 여유롭게' },
  { val: '느긋하게 흘러가듯', label: '🌊 느긋하게 흘러가듯' },
]
const WITH_OPTIONS = ['혼자', '연인과', '친구들과', '가족과']
const EXPECT_OPTIONS = ['현지 언어 소통', '사진 잘 찍어주기', '디테일한 설명', '즉흥 일정 조율', '조용히 옆에서']

function buildPrompt(moments, pace, travelWith, expect) {
  const parts = []
  if (moments.length) parts.push(moments.join(', ') + '을 원하는')
  if (pace) parts.push(pace + ' 여행을 즐기는')
  if (travelWith) parts.push(travelWith + ' 떠나는')
  if (expect) parts.push(expect + '을 기대하는')
  return parts.length ? `${parts.join(', ')} 여행자에게 맞는 가이드 추천해줘` : ''
}

function buildPreviewText(moments, pace, travelWith, expect) {
  const parts = []
  if (moments.length) parts.push(moments.join(' · ') + '을 원하는')
  if (pace) parts.push(pace + ' 여행을 즐기는')
  if (travelWith) parts.push(travelWith + ' 떠나는')
  if (expect) parts.push(expect + '을 기대하는')
  return parts.length ? `"${parts.join(', ')} 여행자"` : ''
}

export function OnboardingPage() {
  const navigate = useNavigate()
  const [moments, setMoments] = useState([])
  const [pace, setPace] = useState('')
  const [travelWith, setTravelWith] = useState('')
  const [expect, setExpect] = useState('')

  const toggleMoment = (val) => {
    setMoments((prev) => (prev.includes(val) ? prev.filter((v) => v !== val) : [...prev, val]))
  }

  const totalSelected = moments.length + (pace ? 1 : 0) + (travelWith ? 1 : 0) + (expect ? 1 : 0)
  const previewText = buildPreviewText(moments, pace, travelWith, expect)

  const handleNext = () => {
    const dna = { moments, pace, travelWith, expect }
    localStorage.setItem('localguest_travel_dna', JSON.stringify(dna))
    navigate('/ai-search', { state: { initialPrompt: buildPrompt(moments, pace, travelWith, expect) } })
  }

  return (
    <div className="onb-wrap">
      <div className="onb-stepper" role="list">
        {STEPPER_STEPS.map((item) => (
          <div key={item.n} role="listitem" className={`onb-step ${item.n === 4 ? 'is-current' : ''}`}>
            <div className={`onb-step-circle ${item.n < 4 ? 'done' : 'current'}`}>
              {item.n < 4 ? '✓' : item.n}
            </div>
            <div className="onb-step-label">
              <strong>{item.label}</strong>
            </div>
          </div>
        ))}
      </div>

      <div className="onb-card">
        <div className="onb-banner">
          <span className="onb-banner-icon">🎉</span>
          <div>
            <div className="onb-banner-title">가입이 완료됐어요!</div>
            <div className="onb-banner-sub">마지막으로 여행 스타일만 알려주시면 AI가 바로 가이드를 찾아드려요.</div>
          </div>
        </div>

        <p className="onb-kicker">STEP 4 · TRAVEL DNA</p>
        <h1 className="onb-title">
          당신만의 여행 DNA를
          <br />
          알려주세요
        </h1>
        <p className="onb-lead">
          선택하신 스타일로 AI가 딱 맞는 로컬 가이드를 연결해 드려요.
          <br />
          정답은 없어요, 솔직하게 골라주세요.
        </p>

        <div className="onb-q-block">
          <div className="onb-q-title">어떤 순간을 원하나요?</div>
          <div className="onb-q-desc">여러 개 선택할수록 추천이 정확해져요</div>
          <div className="onb-card-grid">
            {MOMENT_OPTIONS.map((opt) => (
              <div
                key={opt.val}
                className={`onb-vibe-card ${moments.includes(opt.val) ? 'on' : ''}`}
                onClick={() => toggleMoment(opt.val)}
              >
                <div className="onb-check">
                  <svg viewBox="0 0 10 8" width="9" height="9" stroke="#fff" fill="none" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                    <polyline points="1,4 3.5,7 9,1" />
                  </svg>
                </div>
                <div className="onb-card-icon">{opt.icon}</div>
                <div className="onb-card-name">{opt.name}</div>
                <div className="onb-card-hint">{opt.hint}</div>
              </div>
            ))}
          </div>
        </div>

        <div className="onb-divider" />

        <div className="onb-q-block">
          <div className="onb-q-title">여행 페이스는요?</div>
          <div className="onb-pill-row">
            {PACE_OPTIONS.map((opt) => (
              <div
                key={opt.val}
                className={`onb-pill ${pace === opt.val ? 'on' : ''}`}
                onClick={() => setPace(opt.val)}
              >
                {opt.label}
              </div>
            ))}
          </div>
        </div>

        <div className="onb-q-block">
          <div className="onb-q-title">함께하는 사람은요?</div>
          <div className="onb-pill-row">
            {WITH_OPTIONS.map((opt) => (
              <div
                key={opt}
                className={`onb-pill ${travelWith === opt ? 'on' : ''}`}
                onClick={() => setTravelWith(opt)}
              >
                {opt}
              </div>
            ))}
          </div>
        </div>

        <div className="onb-q-block">
          <div className="onb-q-title">가이드에게 기대하는 건요?</div>
          <div className="onb-pill-row">
            {EXPECT_OPTIONS.map((opt) => (
              <div
                key={opt}
                className={`onb-pill ${expect === opt ? 'on' : ''}`}
                onClick={() => setExpect(opt)}
              >
                {opt}
              </div>
            ))}
          </div>
        </div>

        <div className="onb-preview-box">
          <div className="onb-preview-label">내 여행 스타일 미리보기</div>
          <div className={`onb-preview-text ${previewText ? 'filled' : ''}`}>
            {previewText || '선택할수록 나만의 여행 프로필이 완성돼요.'}
          </div>
        </div>

        <div className="onb-counter">
          {totalSelected > 0 ? `총 ${totalSelected}가지 선택됨` : '아직 선택 전이에요'}
        </div>

        <div className="onb-btn-row">
          <button className="onb-btn-skip" onClick={() => navigate('/')}>
            나중에 할게요
          </button>
          <button className="onb-btn-next" onClick={handleNext}>
            내 가이드 찾으러 가기 →
          </button>
        </div>
      </div>

      <p className="onb-footer-note">
        <Link to="/auth/login">로그인 페이지로 이동</Link>
      </p>
    </div>
  )
}
