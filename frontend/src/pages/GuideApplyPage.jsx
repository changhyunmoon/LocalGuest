import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'

import { apiRequest } from '../api/client'
import { setStoredGuideId } from '../lib/guideId.js'
import { useAuth } from '../context/useAuth.js'

import './FormPage.css'

export function GuideApplyPage() {
  const { email, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const fromTerms = Boolean(location.state?.fromTerms)
  const [nickname, setNickname] = useState('')
  const [profileImage, setProfileImage] = useState('')
  const [bio, setBio] = useState('')
  const [region, setRegion] = useState('')
  const [language, setLanguage] = useState('')
  const [pricePerHour, setPricePerHour] = useState('')
  const [residenceYears, setResidenceYears] = useState('')
  const [localStory, setLocalStory] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const price = Number(pricePerHour)
      if (Number.isNaN(price) || price <= 0) {
        setError('시간당 가격은 0보다 큰 숫자여야 합니다.')
        setLoading(false)
        return
      }
      const body = {
        nickname: nickname.trim(),
        profileImage: profileImage.trim() || undefined,
        bio: bio.trim() || undefined,
        region: region.trim(),
        language: language.trim(),
        pricePerHour: price,
        residenceYears: residenceYears ? Number(residenceYears) : undefined,
        localStory: localStory.trim() || undefined,
      }
      const res = await apiRequest('/guides', { method: 'POST', json: body })
      const text = await res.text()
      if (!res.ok) {
        try {
          const j = JSON.parse(text)
          setError(j.message ?? '신청에 실패했습니다.')
        } catch {
          setError(text || '신청에 실패했습니다.')
        }
        return
      }
      const profile = text ? JSON.parse(text) : null
      if (profile?.guideId != null) {
        setStoredGuideId(profile.guideId)
      }
      const approved = profile?.isApproved === true
      sessionStorage.setItem(
        'login_flash',
        `가이드 프로필이 등록되었습니다. 관리자 승인: ${approved ? '완료' : '대기'} · JWT 역할 반영을 위해 다시 로그인해 주세요.`,
      )
      if (email) {
        sessionStorage.setItem('prefill_login_email', email)
      }
      await logout()
      navigate('/auth/login', { replace: true })
    } catch {
      setError('네트워크 오류가 발생했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="form-page" style={{ maxWidth: 480 }}>
      <h1>가이드 신청</h1>
      {fromTerms && (
        <p className="form-success" style={{ marginBottom: '1rem' }}>
          약관 동의가 완료되었습니다. 아래에 프로필 정보를 입력해 주세요.
          {location.state?.agreeMarketing ? ' (마케팅 수신 동의: 백엔드 필드 연동 예정)' : null}
        </p>
      )}
      <p className="form-hint">
        F01-03 · <code>POST /api/guides</code> — 성공 시 서버에서 회원 역할이 GUIDE로 변경되며, JWT는{' '}
        <strong>재로그인</strong> 후에야 갱신됩니다.
      </p>

      <form className="form-card" onSubmit={(e) => void handleSubmit(e)}>
        <label className="field">
          <span>가이드 닉네임 *</span>
          <input value={nickname} onChange={(e) => setNickname(e.target.value)} required />
        </label>
        <label className="field">
          <span>프로필 이미지 URL</span>
          <input value={profileImage} onChange={(e) => setProfileImage(e.target.value)} />
        </label>
        <label className="field">
          <span>소개</span>
          <textarea rows={3} value={bio} onChange={(e) => setBio(e.target.value)} />
        </label>
        <label className="field">
          <span>활동 지역 *</span>
          <input value={region} onChange={(e) => setRegion(e.target.value)} required />
        </label>
        <label className="field">
          <span>구사 언어 *</span>
          <input value={language} onChange={(e) => setLanguage(e.target.value)} required />
        </label>
        <label className="field">
          <span>시간당 가격 (원) *</span>
          <input
            type="number"
            min={1}
            step={1}
            value={pricePerHour}
            onChange={(e) => setPricePerHour(e.target.value)}
            required
          />
        </label>
        <label className="field">
          <span>거주 연수</span>
          <input type="number" min={0} value={residenceYears} onChange={(e) => setResidenceYears(e.target.value)} />
        </label>
        <label className="field">
          <span>로컬 스토리</span>
          <textarea rows={3} value={localStory} onChange={(e) => setLocalStory(e.target.value)} />
        </label>

        {error && <p className="form-error">{error}</p>}

        <button type="submit" className="submit" disabled={loading}>
          {loading ? '전송 중…' : '신청하기'}
        </button>
      </form>

      <p className="form-footer">
        <Link to="/mypage">← 마이페이지</Link>
      </p>
    </div>
  )
}
