import { useEffect, useState } from 'react'

import { MypageDevHint } from '../components/MypageDevHint.jsx'
import { useAuth } from '../context/useAuth.js'
import { loadGuestPrivacyForm, persistGuestPrivacyForm } from '../lib/guestMypagePrefs.js'

import './MypagePrivacyPage.css'

export function MypagePrivacyPage() {
  const { email } = useAuth()
  const [nickname, setNickname] = useState('')
  const [bookingNotify, setBookingNotify] = useState(true)
  const [guideMessageNotify, setGuideMessageNotify] = useState(true)
  const [tags, setTags] = useState([])
  const [prefsOpen, setPrefsOpen] = useState(false)
  const [newTag, setNewTag] = useState('')
  const [savedFlash, setSavedFlash] = useState(false)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    const f = loadGuestPrivacyForm(email)
    setNickname(f.nickname)
    setBookingNotify(f.bookingNotify)
    setGuideMessageNotify(f.guideMessageNotify)
    setTags(f.tags)
  }, [email])

  const handleSave = () => {
    setSaving(true)
    try {
      persistGuestPrivacyForm({
        nickname,
        bookingNotify,
        guideMessageNotify,
        tags,
      })
      setSavedFlash(true)
      window.setTimeout(() => setSavedFlash(false), 3200)
      window.dispatchEvent(new Event('localguest_mypage_prefs_updated'))
    } finally {
      setSaving(false)
    }
  }

  const addTag = () => {
    const t = newTag.replace(/^#+/, '').trim()
    if (!t || tags.includes(t)) {
      setNewTag('')
      return
    }
    setTags((prev) => [...prev, t])
    setNewTag('')
  }

  const removeTag = (t) => {
    setTags((prev) => prev.filter((x) => x !== t))
  }

  return (
    <div className="mp-privacy">
      <h1 className="mp-privacy-title">
        회원 정보 및 여행 설정 <span aria-hidden>⚙️</span>
      </h1>
      <p className="mp-privacy-hint">
        회원 닉네임·알림·여행 성향은 전용 서버 연동 전까지 이 브라우저에만 저장됩니다.
      </p>
      <MypageDevHint className="mp-privacy-hint">
        백엔드에 <code>PATCH /members/me</code> 등이 추가되면 연동 예정입니다.
      </MypageDevHint>
      {savedFlash && <p className="mp-privacy-banner">설정이 저장되었습니다.</p>}

      <section className="mp-privacy-section">
        <h2>기본 정보</h2>
        <div className="mp-privacy-field">
          <label className="field-label" htmlFor="mp-nick">
            닉네임
          </label>
          <input
            id="mp-nick"
            type="text"
            autoComplete="nickname"
            value={nickname}
            onChange={(e) => setNickname(e.target.value)}
            maxLength={40}
          />
        </div>
        <div className="mp-privacy-email-block">
          <span className="label">이메일 계정</span>
          <span className="value">{email ?? '—'}</span>
          <p className="note">연동된 이메일 계정은 변경할 수 없습니다.</p>
        </div>
      </section>

      <section className="mp-privacy-section">
        <h2>여행 일정 및 앱 알림</h2>
        <div className="mp-privacy-row">
          <div>
            <strong>예약 현황 및 투어 일정 알림</strong>
            <p>예약 접수, 가이드 승인, 일정 변경 등의 알림(푸시·이메일) 수신 여부입니다.</p>
          </div>
          <label className="mp-toggle">
            <input type="checkbox" checked={bookingNotify} onChange={(e) => setBookingNotify(e.target.checked)} />
            <span className="mp-toggle-ui" />
          </label>
        </div>
        <div className="mp-privacy-row">
          <div>
            <strong>가이드 메시지 알림</strong>
            <p>채팅에서 가이드가 메시지를 보낼 때 알림을 받습니다.</p>
          </div>
          <label className="mp-toggle">
            <input
              type="checkbox"
              checked={guideMessageNotify}
              onChange={(e) => setGuideMessageNotify(e.target.checked)}
            />
            <span className="mp-toggle-ui" />
          </label>
        </div>
      </section>

      <section className="mp-privacy-section">
        <h2>나의 여행 성향 관리</h2>
        <div className="mp-privacy-tags-wrap">
          <div className="mp-privacy-tags">
            {tags.map((t) => (
              <span key={t}>
                #{t}
              </span>
            ))}
          </div>
          <button type="button" className="mp-privacy-edit-prefs" onClick={() => setPrefsOpen((o) => !o)}>
            성향 수정하기 {prefsOpen ? '∧' : '>'}
          </button>
        </div>
        {prefsOpen && (
          <div className="mp-privacy-editor">
            <p>태그를 추가·삭제한 뒤 아래 &quot;설정 저장하기&quot;를 눌러 반영합니다.</p>
            <div className="mp-privacy-chips">
              {tags.map((t) => (
                <span key={t} className="mp-privacy-chip">
                  #{t}
                  <button type="button" onClick={() => removeTag(t)} aria-label={`${t} 삭제`}>
                    ×
                  </button>
                </span>
              ))}
            </div>
            <div className="mp-privacy-add-row">
              <input
                type="text"
                value={newTag}
                onChange={(e) => setNewTag(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault()
                    addTag()
                  }
                }}
                placeholder="새 태그 (예: 카페투어)"
                maxLength={24}
              />
              <button type="button" onClick={addTag} disabled={!newTag.trim()}>
                추가
              </button>
            </div>
          </div>
        )}
      </section>

      <div className="mp-privacy-save-row">
        <button type="button" className="mp-privacy-save" onClick={handleSave} disabled={saving}>
          설정 저장하기
        </button>
      </div>
    </div>
  )
}
