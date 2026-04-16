import { useEffect, useState } from 'react'
import { Link, useParams, useLocation, useNavigate } from 'react-router-dom'

import { apiRequest } from '../api/client'
import { useAuth } from '../context/useAuth.js'

/**
 * @param {unknown} raw
 * @returns {string}
 */
function formatScheduleDate(raw) {
  if (raw == null) return ''
  const s = String(raw)
  return s.length >= 10 ? s.slice(0, 10) : s
}

export function GuideDetailPage() {
  const { guideId } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const { isAuthenticated, isGuide } = useAuth()

  const [detail, setDetail] = useState(null)
  const [schedules, setSchedules] = useState([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  const [selectedScheduleId, setSelectedScheduleId] = useState(null)
  const [destination, setDestination] = useState('')
  const [concept, setConcept] = useState('')
  const [submitErr, setSubmitErr] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setLoading(true)
      setError('')
      try {
        const res = await apiRequest(`/guides/${guideId}/detail`, { method: 'GET', skipAuth: true })
        const text = await res.text()
        if (!res.ok) {
          throw new Error(text || '조회 실패')
        }
        const data = text ? JSON.parse(text) : null
        if (!cancelled) {
          setDetail(data)
          const p = data?.profile
          if (p?.region) setDestination(String(p.region).trim())
        }

        const schRes = await apiRequest(`/guides/${guideId}/schedules`, { method: 'GET', skipAuth: true })
        const schText = await schRes.text()
        if (schRes.ok) {
          const list = schText ? JSON.parse(schText) : []
          const avail = Array.isArray(list)
            ? list.filter((s) => String(s?.status ?? '') === 'AVAILABLE')
            : []
          if (!cancelled) {
            setSchedules(avail)
            if (avail.length > 0 && avail[0]?.scheduleId != null) {
              setSelectedScheduleId(Number(avail[0].scheduleId))
            }
          }
        } else if (!cancelled) {
          setSchedules([])
        }
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : '오류')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [guideId])

  useEffect(() => {
    if (!loading && detail?.profile && location.hash === '#match-request') {
      requestAnimationFrame(() => {
        document.getElementById('match-request')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
      })
    }
  }, [loading, detail, location.hash])

  const onSubmitMatch = async (e) => {
    e.preventDefault()
    setSubmitErr('')
    if (!isAuthenticated) {
      setSubmitErr('로그인이 필요합니다.')
      return
    }
    if (isGuide) {
      setSubmitErr('여행자(GUEST) 계정으로 로그인한 뒤 요청해 주세요.')
      return
    }
    if (selectedScheduleId == null || Number.isNaN(Number(selectedScheduleId))) {
      setSubmitErr('예약 가능한 일정을 선택해 주세요. (가이드가 일정을 등록해야 합니다.)')
      return
    }
    const dest = destination.trim()
    if (!dest) {
      setSubmitErr('여행 목적지(지역)를 입력해 주세요.')
      return
    }

    const sch = schedules.find((s) => Number(s.scheduleId) === Number(selectedScheduleId))
    const desiredDate = sch?.availableDate != null ? formatScheduleDate(sch.availableDate) : undefined

    setSubmitting(true)
    try {
      const res = await apiRequest('/matching/requests', {
        method: 'POST',
        json: {
          guideId: Number(guideId),
          scheduleId: Number(selectedScheduleId),
          destination: dest,
          concept: concept.trim() || undefined,
          desiredDate: desiredDate || undefined,
        },
      })
      const t = await res.text()
      if (res.status === 401 || res.status === 403) {
        setSubmitErr('권한이 없거나 로그인이 만료되었습니다. 다시 로그인해 주세요.')
        return
      }
      if (!res.ok) {
        let msg = t || '요청에 실패했습니다.'
        try {
          const j = JSON.parse(t)
          if (j?.message) msg = j.message
        } catch {
          /* ignore */
        }
        setSubmitErr(msg)
        return
      }
      const data = t ? JSON.parse(t) : {}
      const rid = data?.requestId
      navigate(`/guides/${guideId}/match`, {
        replace: false,
        state: { requestId: rid != null ? Number(rid) : undefined },
      })
    } catch {
      setSubmitErr('네트워크 오류가 발생했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) return <p>불러오는 중…</p>
  if (error) return <p style={{ color: '#b71c1c' }}>{error}</p>
  if (!detail?.profile) return <p>데이터가 없습니다.</p>

  const p = detail.profile

  return (
    <div>
      <p>
        <Link to="/guides">← 목록</Link>
      </p>
      <h1 style={{ marginTop: 0 }}>{p.nickname}</h1>
      <p style={{ color: '#555' }}>
        {p.region} · {p.language} · {p.pricePerHour != null ? `${p.pricePerHour} / 시간` : ''}
      </p>
      {p.bio && <p style={{ lineHeight: 1.6 }}>{p.bio}</p>}
      <p style={{ fontSize: '0.85rem', color: '#777' }}>
        승인: {String(p.isApproved)} · 활성: {String(p.isActive)}
      </p>

      <section
        id="match-request"
        style={{
          marginTop: '2rem',
          padding: '1.25rem',
          border: '1px solid #e5e7eb',
          borderRadius: 12,
          background: '#fafafa',
        }}
      >
        <h2 style={{ marginTop: 0, fontSize: '1.15rem' }}>매칭 요청하기</h2>
        <p style={{ color: '#4b5563', fontSize: '0.95rem', lineHeight: 1.5 }}>
          예약 가능한 일정을 고르고 목적지를 적으면 가이드에게 매칭 요청이 전달됩니다. (가이드가 일정을 아직 등록하지 않았다면
          요청할 수 없어요.)
        </p>

        {!isAuthenticated && (
          <p>
            <Link
              to="/auth/login"
              state={{
                returnTo: `/guides/${guideId}#match-request`,
                hint: '로그인 후 이 가이드에게 매칭 요청을 보낼 수 있어요.',
              }}
            >
              로그인하고 요청하기
            </Link>
          </p>
        )}

        {isAuthenticated && isGuide && (
          <p style={{ color: '#b45309' }}>가이드 계정으로는 게스트 매칭 요청을 보낼 수 없습니다. 여행자로 로그인해 주세요.</p>
        )}

        {isAuthenticated && !isGuide && (
          <form onSubmit={(e) => void onSubmitMatch(e)} style={{ display: 'grid', gap: '0.85rem', maxWidth: 480 }}>
            <label style={{ display: 'grid', gap: 6 }}>
              <span>예약 가능 일정</span>
              {schedules.length === 0 ? (
                <span style={{ color: '#6b7280' }}>등록된 예약 가능 일정이 없습니다. 가이드에게 일정 등록을 요청해 주세요.</span>
              ) : (
                <select
                  value={selectedScheduleId ?? ''}
                  onChange={(ev) => setSelectedScheduleId(ev.target.value ? Number(ev.target.value) : null)}
                  required
                >
                  {schedules.map((s) => (
                    <option key={s.scheduleId} value={s.scheduleId}>
                      {formatScheduleDate(s.availableDate)} {s.startTime ?? ''} ~ {s.endTime ?? ''}
                    </option>
                  ))}
                </select>
              )}
            </label>
            <label style={{ display: 'grid', gap: 6 }}>
              <span>여행 목적지 · 지역</span>
              <input
                type="text"
                value={destination}
                onChange={(ev) => setDestination(ev.target.value)}
                placeholder="예: 구미, 경북 구미시"
                required
              />
            </label>
            <label style={{ display: 'grid', gap: 6 }}>
              <span>하고 싶은 일 (선택)</span>
              <textarea
                rows={3}
                value={concept}
                onChange={(ev) => setConcept(ev.target.value)}
                placeholder="예: 동네 맛집과 산책 코스를 함께하고 싶어요."
              />
            </label>
            {submitErr && <p style={{ color: '#b91c1c', margin: 0 }}>{submitErr}</p>}
            <div>
              <button
                type="submit"
                disabled={submitting || schedules.length === 0}
                style={{
                  padding: '0.6rem 1.2rem',
                  borderRadius: 8,
                  border: 'none',
                  background: '#111',
                  color: '#fff',
                  cursor: schedules.length === 0 ? 'not-allowed' : 'pointer',
                }}
              >
                {submitting ? '전송 중…' : '매칭 요청 보내기'}
              </button>
            </div>
          </form>
        )}
      </section>
    </div>
  )
}
