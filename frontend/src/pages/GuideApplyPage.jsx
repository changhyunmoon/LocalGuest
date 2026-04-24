import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'

import { apiRequest, fetchNicknameAvailable } from '../api/client'
import { setStoredGuideId } from '../lib/guideId.js'

import './FormPage.css'

export function GuideApplyPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const fromTerms = Boolean(location.state?.fromTerms)
  const [nickname, setNickname] = useState('')
  const [nicknameChecked, setNicknameChecked] = useState(false)
  const [nicknameCheckBusy, setNicknameCheckBusy] = useState(false)
  const [nicknameCheckMsg, setNicknameCheckMsg] = useState('')
  const [bio, setBio] = useState('')
  const [region, setRegion] = useState('')
  const [language, setLanguage] = useState('')
  const [pricePerHour, setPricePerHour] = useState('')
  const [residenceYears, setResidenceYears] = useState('')
  const [localStory, setLocalStory] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const handleNicknameCheck = async () => {
    setError('')
    const trimmed = nickname.trim()
    if (!trimmed) {
      setNicknameChecked(false)
      setNicknameCheckMsg('가이드 닉네임을 먼저 입력해 주세요.')
      return
    }
    setNicknameCheckBusy(true)
    try {
      const available = await fetchNicknameAvailable(trimmed)
      if (available) {
        setNicknameChecked(true)
        setNicknameCheckMsg('사용 가능한 가이드명입니다.')
      } else {
        setNicknameChecked(false)
        setNicknameCheckMsg('이미 사용 중인 가이드명입니다.')
      }
    } catch (err) {
      setNicknameChecked(false)
      setNicknameCheckMsg('')
      setError(err instanceof Error ? err.message : '가이드명 중복 확인에 실패했습니다.')
    } finally {
      setNicknameCheckBusy(false)
    }
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    if (!nicknameChecked) {
      setError('가이드 닉네임 중복 확인을 완료해 주세요.')
      return
    }
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
      navigate('/guide/mypage', { replace: true })
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
        </p>
      )}
      <p className="form-hint">신청이 완료되면 가이드 전용 화면에서 활동을 이어갈 수 있어요. 이미 가입한 계정이면 로그인 시 &quot;가이드&quot; 유형을 선택해 주세요.</p>

      <form className="form-card" onSubmit={(e) => void handleSubmit(e)}>
        <label className="field">
          <span>가이드 닉네임 *</span>
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <input
              style={{ flex: 1 }}
              value={nickname}
              onChange={(e) => {
                setNickname(e.target.value)
                setNicknameChecked(false)
                setNicknameCheckMsg('')
              }}
              required
            />
            <button type="button" className="submit ghost" onClick={() => void handleNicknameCheck()} disabled={nicknameCheckBusy}>
              {nicknameCheckBusy ? '확인 중…' : '중복 확인'}
            </button>
          </div>
          {nicknameCheckMsg && (
            <p
              style={{
                margin: 0,
                fontSize: '0.82rem',
                color: nicknameChecked ? '#047857' : '#b71c1c',
              }}
            >
              {nicknameCheckMsg}
            </p>
          )}
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
