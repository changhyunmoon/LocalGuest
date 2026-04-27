import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { createPortal } from 'react-dom'

import { apiRequest } from '../api/client.js'
import { useAuth } from '../context/useAuth.js'
import { loadGuestPrivacyForm, persistGuestPrivacyForm } from '../lib/guestMypagePrefs.js'
import { buildTravelDnaPreview, loadTravelDna } from '../lib/travelDna.js'

import './MypageMemberPages.css'
import './MypagePrivacyPage.css'

function parseApiErrorMessage(text) {
  if (!text) return '요청 실패'
  try {
    const j = JSON.parse(text)
    return (j.message ?? text) || '요청 실패'
  } catch {
    return text || '요청 실패'
  }
}

function useToast() {
  const [toasts, setToasts] = useState([])
  const addToast = useCallback((message, type = 'success') => {
    const id = Date.now() + Math.random()
    setToasts((prev) => [...prev, { id, message, type }])
    setTimeout(() => setToasts((prev) => prev.filter((t) => t.id !== id)), 2000)
  }, [])
  return { toasts, addToast }
}

function Toast({ toasts }) {
  if (toasts.length === 0) return null
  if (typeof document === 'undefined') return null
  return createPortal(
    <div style={{ position: 'fixed', top: '1.1rem', left: '50%', transform: 'translateX(-50%)', zIndex: 9999, display: 'flex', flexDirection: 'column', gap: '0.5rem', alignItems: 'center', pointerEvents: 'none' }}>
      {toasts.map((t) => (
        <div
          key={t.id}
          style={{
            padding: '0.74rem 0.98rem',
            borderRadius: 12,
            border: t.type === 'success' ? '1px solid #e7a8c2' : '1px solid #f7b4c1',
            background: t.type === 'success' ? 'linear-gradient(180deg, #f7d9e8, #efbfd0)' : 'linear-gradient(180deg, #ffe8ee, #ffd8e1)',
            color: t.type === 'success' ? '#5a2f45' : '#9f274c',
            fontSize: '0.86rem',
            fontWeight: 700,
            boxShadow: '0 14px 28px rgba(15, 23, 42, 0.16)',
            textAlign: 'center',
            letterSpacing: '-0.01em',
            minWidth: 240,
            maxWidth: 420,
          }}
        >
          {t.message}
        </div>
      ))}
    </div>,
    document.body,
  )
}

export function MypagePrivacyPage() {
  const { email, logout } = useAuth()
  const navigate = useNavigate()
  const { toasts, addToast } = useToast()
  const photoInputRef = useRef(null)
  const [nickname, setNickname] = useState('')
  const [profileImageUrl, setProfileImageUrl] = useState(null)
  const [bookingNotify, setBookingNotify] = useState(true)
  const [guideMessageNotify, setGuideMessageNotify] = useState(true)
  const [tags, setTags] = useState([])
  const [prefsOpen, setPrefsOpen] = useState(false)
  const [newTag, setNewTag] = useState('')
  const [saving, setSaving] = useState(false)
  const [imageBusy, setImageBusy] = useState(false)
  const [imageError, setImageError] = useState('')
  const [withdrawBusy, setWithdrawBusy] = useState(false)
  const [withdrawErr, setWithdrawErr] = useState('')
  const [withdrawModalOpen, setWithdrawModalOpen] = useState(false)

  useEffect(() => {
    const f = loadGuestPrivacyForm(email)
    setNickname(f.nickname)
    setBookingNotify(f.bookingNotify)
    setGuideMessageNotify(f.guideMessageNotify)
    setTags(f.tags)
  }, [email])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const res = await apiRequest('/members/me/profile', { method: 'GET' })
        const text = await res.text()
        if (!res.ok) throw new Error(parseApiErrorMessage(text))
        const json = text ? JSON.parse(text) : {}
        if (!cancelled) setProfileImageUrl(json?.profileImageUrl ? String(json.profileImageUrl) : null)
      } catch (e) {
        if (!cancelled) {
          setProfileImageUrl(null)
          setImageError(e instanceof Error ? e.message : '프로필 이미지를 불러오지 못했습니다.')
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  const dnaSummary = useMemo(() => buildTravelDnaPreview(loadTravelDna()), [])

  const saveGuestProfileImage = async (nextImageUrl) => {
    const saveRes = await apiRequest('/members/me/profile', {
      method: 'PUT',
      json: { profileImageUrl: nextImageUrl },
    })
    const saveText = await saveRes.text()
    if (!saveRes.ok) throw new Error(parseApiErrorMessage(saveText))
    const saveJson = saveText ? JSON.parse(saveText) : {}
    return saveJson?.profileImageUrl ? String(saveJson.profileImageUrl) : null
  }

  const uploadGuestProfileImage = async (file) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('folder', 'guest-profile')
    const uploadRes = await apiRequest('/files/upload', { method: 'POST', body: formData })
    const uploadText = await uploadRes.text()
    if (!uploadRes.ok) throw new Error(parseApiErrorMessage(uploadText))
    const uploadJson = uploadText ? JSON.parse(uploadText) : {}
    const uploadedUrl = String(uploadJson?.url ?? '').trim()
    if (!uploadedUrl) throw new Error('이미지 업로드 URL을 받지 못했습니다.')
    return saveGuestProfileImage(uploadedUrl)
  }

  const handleSave = () => {
    setSaving(true)
    try {
      persistGuestPrivacyForm({
        nickname,
        bookingNotify,
        guideMessageNotify,
        tags,
      })
      window.dispatchEvent(new Event('localguest_mypage_prefs_updated'))
      addToast('설정이 반영됐어요.')
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

  const onWithdraw = async () => {
    setWithdrawBusy(true)
    setWithdrawErr('')
    try {
      const res = await apiRequest('/members/me?role=GUEST', { method: 'DELETE' })
      const text = await res.text()
      if (!res.ok) throw new Error(parseApiErrorMessage(text))
      await logout()
      navigate('/', { replace: true, state: { hint: '회원 탈퇴가 완료되었습니다.' } })
    } catch (e) {
      setWithdrawErr(e instanceof Error ? e.message : '탈퇴 처리 실패')
    } finally {
      setWithdrawBusy(false)
    }
  }

  return (
    <div className="mp-privacy">
      <h1 className="mp-privacy-title">
        회원 정보 및 여행 설정 <span aria-hidden>⚙️</span>
      </h1>
      <p className="mp-privacy-hint">여행 성향 태그를 관리하고 알림 설정을 조정할 수 있어요.</p>

      <section className="mp-privacy-section">
        <h2>기본 정보</h2>
        <div className="mp-privacy-field">
          <label className="field-label">프로필 이미지</label>
          <div className="mp-privacy-photo-row">
            <div
              role="img"
              aria-label="프로필 이미지 미리보기"
              className={`mp-privacy-photo-preview${profileImageUrl ? '' : ' is-empty'}`}
              style={{
                ...(profileImageUrl ? { backgroundImage: `url(${profileImageUrl})` } : {}),
              }}
            />
            <input
              ref={photoInputRef}
              type="file"
              accept="image/*"
              style={{ display: 'none' }}
              onChange={(e) => {
                const file = e.target.files?.[0]
                e.target.value = ''
                if (!file) return
                if (!file.type.startsWith('image/')) {
                  setImageError('이미지 파일만 업로드할 수 있습니다.')
                  return
                }
                void (async () => {
                  setImageBusy(true)
                  setImageError('')
                  try {
                    const savedUrl = await uploadGuestProfileImage(file)
                    setProfileImageUrl(savedUrl)
                    addToast('프로필 이미지가 저장됐어요.')
                  } catch (err) {
                    setImageError(err instanceof Error ? err.message : '프로필 이미지 업로드에 실패했습니다.')
                  } finally {
                    setImageBusy(false)
                  }
                })()
              }}
            />
            <button
              type="button"
              className="mp-privacy-photo-btn"
              disabled={imageBusy}
              onClick={() => photoInputRef.current?.click()}
            >
              {imageBusy ? '업로드 중…' : '이미지 변경'}
            </button>
            <button
              type="button"
              className="mp-privacy-photo-btn"
              disabled={imageBusy || !profileImageUrl}
              onClick={() => {
                void (async () => {
                  setImageBusy(true)
                  setImageError('')
                  try {
                    await saveGuestProfileImage('')
                    setProfileImageUrl(null)
                    addToast('프로필 이미지가 삭제됐어요.')
                  } catch (err) {
                    setImageError(err instanceof Error ? err.message : '프로필 이미지 삭제에 실패했습니다.')
                  } finally {
                    setImageBusy(false)
                  }
                })()
              }}
            >
              이미지 삭제
            </button>
          </div>
          {imageError && <p className="err mp-privacy-image-err">{imageError}</p>}
        </div>
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
        <p className="mp-privacy-dna-summary">
          {dnaSummary || '선택한 태그를 기반으로 나의 여행 성향이 요약됩니다.'}
        </p>
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

      <section className="mp-privacy-section mp-privacy-danger">
        <h2>계정 탈퇴</h2>
        <p className="mp-privacy-danger-hint">
          탈퇴 시 서비스 이용이 종료되며 복구가 어려울 수 있습니다. 로컬에만 있는 닉네임·알림 설정도 더 이상 쓰이지 않습니다.
        </p>
        {withdrawErr && <p className="err">{withdrawErr}</p>}
        <button
          type="button"
          className="mp-btn mp-btn--danger"
          disabled={withdrawBusy}
          onClick={() => setWithdrawModalOpen(true)}
        >
          {withdrawBusy ? '처리 중…' : '회원 탈퇴'}
        </button>
      </section>
      {withdrawModalOpen && (
        <div className="mp-withdraw-modal-overlay" role="dialog" aria-modal="true" aria-labelledby="mp-withdraw-title">
          <div className="mp-withdraw-modal">
            <p className="mp-withdraw-emoji" aria-hidden>
              😢
            </p>
            <h3 id="mp-withdraw-title">로컬 게스트를 떠나신다니 아쉬워요.</h3>
            <p>정말 탈퇴하시겠어요? 탈퇴 후에는 복구가 어려울 수 있어요.</p>
            <div className="mp-withdraw-actions">
              <button type="button" className="mp-btn mp-btn--line" onClick={() => setWithdrawModalOpen(false)} disabled={withdrawBusy}>
                머무를게요
              </button>
              <button
                type="button"
                className="mp-btn mp-btn--danger"
                disabled={withdrawBusy}
                onClick={() => {
                  setWithdrawModalOpen(false)
                  void onWithdraw()
                }}
              >
                {withdrawBusy ? '처리 중…' : '탈퇴할게요'}
              </button>
            </div>
          </div>
        </div>
      )}
      <Toast toasts={toasts} />
    </div>
  )
}
