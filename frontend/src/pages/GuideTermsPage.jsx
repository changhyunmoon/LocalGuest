import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'

import { useAuth } from '../context/useAuth.js'

import './GuideTermsPage.css'

const TERMS_BODY = `제1조 (목적)
이 약관은 LocalGuest 플랫폼에서 로컬 가이드로 활동하기 위한 자격 요건 및 책임을 명확히 하여, 여행자와 가이드 간 신뢰할 수 있는 매칭 환경을 조성하는 것을 목적으로 합니다.

제2조 (가이드 자격)
① 신청자는 해당 지역에서 10년 이상 거주한 「진짜 로컬」에 해당함을 확인합니다.
② 운영진의 요청이 있을 경우 주민등록등본 등 거주 증빙 서류를 제출해야 합니다.
③ 허위 사실이 확인될 경우 자격은 즉시 박탈되며, 서비스 이용이 제한될 수 있습니다.`

export function GuideTermsPage() {
  const { isGuide } = useAuth()
  const navigate = useNavigate()
  const [agreeResidency, setAgreeResidency] = useState(false)
  const [agreePenalty, setAgreePenalty] = useState(false)
  const [agreeMarketing, setAgreeMarketing] = useState(false)
  const [error, setError] = useState('')

  const handleContinue = (e) => {
    e.preventDefault()
    setError('')
    if (!agreeResidency || !agreePenalty) {
      setError('필수 항목에 모두 동의해 주세요.')
      return
    }
    navigate('/guide/apply', { state: { fromTerms: true, agreeMarketing } })
  }

  if (isGuide) {
    return <Navigate to="/guide/inbox" replace />
  }

  return (
    <div className="guide-terms">
      <header className="guide-terms-hero">
        <span className="guide-terms-hero-accent" aria-hidden />
        <h1>로컬 가이드 등록</h1>
        <p>진짜 로컬만 알 수 있는 여행을 함께 만들어주세요.</p>
      </header>

      <section className="guide-terms-card">
        <span className="guide-terms-card-accent" aria-hidden />
        <h2>약관 동의 및 가이드 자격 📝</h2>

        <div className="guide-terms-scroll" tabIndex={0} role="region" aria-label="가이드 약관 본문">
          <pre className="guide-terms-pre">{TERMS_BODY}</pre>
        </div>

        <form className="guide-terms-form" onSubmit={(e) => void handleContinue(e)}>
          <label className="guide-terms-check">
            <input type="checkbox" checked={agreeResidency} onChange={(ev) => setAgreeResidency(ev.target.checked)} />
            <span>
              <span className="tag req">[필수]</span> 해당 지역에 10년 이상 거주하였음을 확인합니다.
            </span>
          </label>
          <label className="guide-terms-check">
            <input type="checkbox" checked={agreePenalty} onChange={(ev) => setAgreePenalty(ev.target.checked)} />
            <span>
              <span className="tag req">[필수]</span> 허위 사실 기재 시 자격 박탈에 동의합니다.
            </span>
          </label>
          <label className="guide-terms-check">
            <input type="checkbox" checked={agreeMarketing} onChange={(ev) => setAgreeMarketing(ev.target.checked)} />
            <span>
              <span className="tag opt">[선택]</span> 마케팅 및 프로모션 정보 수신에 동의합니다.
            </span>
          </label>

          {error && <p className="guide-terms-error">{error}</p>}

          <button type="submit" className="guide-terms-submit">
            동의하고 가이드 등록하기
          </button>
        </form>
      </section>

      <p className="guide-terms-back">
        <Link to="/mypage">← 마이페이지</Link>
      </p>
    </div>
  )
}
