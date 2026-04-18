import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

import { apiRequest } from '../api/client.js'
import { useAuth } from '../context/useAuth.js'
import { fetchGuideMatchRequests } from '../lib/matchingGuest.js'

import './GuideInboxPage.css'

async function readJsonError(res, text) {
  try {
    const j = JSON.parse(text)
    return (j.message ?? text) || '요청 실패'
  } catch {
    return text || '요청 실패'
  }
}

function statusLabel(status) {
  const map = {
    PENDING:     { label: '대기중',      bg: '#fef9c3', color: '#854d0e' },
    ACCEPTED:    { label: '제시안 전송됨', bg: '#dbeafe', color: '#1e40af' },
    REJECTED:    { label: '거절됨',      bg: '#fee2e2', color: '#991b1b' },
    PAID:        { label: '결제완료',    bg: '#ede9fe', color: '#6d28d9' },
    COMPLETED:   { label: '완료',        bg: '#d1fae5', color: '#065f46' },
    IN_PROGRESS: { label: '투어 진행중', bg: '#fef9c3', color: '#854d0e' },
    CANCELLED:   { label: '취소됨',      bg: '#f3f4f6', color: '#6b7280' },
  }
  const s = map[status] ?? { label: status, bg: '#f3f4f6', color: '#374151' }
  return (
    <span style={{ fontSize: '0.76rem', fontWeight: 700, padding: '0.2rem 0.55rem', borderRadius: 999, background: s.bg, color: s.color, whiteSpace: 'nowrap' }}>
      {s.label}
    </span>
  )
}

export function GuideInboxPage() {
  const { isGuide } = useAuth()
  const [rows, setRows] = useState([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [busyId, setBusyId] = useState(null)
  const [toast, setToast] = useState('')
  const [proposeFor, setProposeFor] = useState(null)
  const [proposedSchedule, setProposedSchedule] = useState('')
  const [proposeMessage, setProposeMessage] = useState('')

  const fetchList = useCallback(async () => {
    setError('')
    setToast('')
    setRows([
      { requestId: 1, guestId: 42, status: 'PENDING', destination: '제주 올레길', desiredDate: '2026-05-10', desiredBudget: 150000, conceptSummary: '자연 속 힐링 여행', createdAt: '2026-04-18T10:00:00' },
      { requestId: 2, guestId: 55, status: 'ACCEPTED', destination: '부산 감천문화마을', desiredDate: '2026-05-15', desiredBudget: 200000, conceptSummary: '감성 사진 투어', createdAt: '2026-04-17T14:30:00' },
      { requestId: 3, guestId: 78, status: 'REJECTED', destination: '경주 역사탐방', desiredDate: '2026-04-20', desiredBudget: 100000, conceptSummary: null, createdAt: '2026-04-15T09:00:00' },
    ])
    return // TODO: 목업 확인 후 이 블록 삭제
    const data = await fetchGuideMatchRequests(apiRequest)
    setRows(Array.isArray(data) ? data : [])
  }, [])

  useEffect(() => {
    if (!isGuide) {
      setLoading(false)
      setRows([])
      return
    }
    let cancelled = false
    ;(async () => {
      setLoading(true)
      setError('')
      try {
        await fetchList()
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : '오류')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [isGuide, fetchList])

  const reject = async (requestId) => {
    if (!window.confirm('이 예약 요청을 거절할까요?')) return
    setBusyId(requestId)
    setToast('')
    try {
      const res = await apiRequest(`/matching/requests/${requestId}/reject`, { method: 'PATCH' })
      const text = await res.text()
      if (!res.ok) {
        setToast(await readJsonError(res, text))
        return
      }
      await fetchList()
    } catch {
      setToast('거절 요청 실패')
    } finally {
      setBusyId(null)
    }
  }

  const openPropose = (r) => {
    setProposeFor(r.requestId)
    setProposedSchedule(r.proposedSchedule ?? '')
    setProposeMessage(r.proposeMessage ?? '')
  }

  const closePropose = () => {
    setProposeFor(null)
    setProposedSchedule('')
    setProposeMessage('')
  }

  const submitPropose = async () => {
    if (proposeFor == null) return
    const body = {
      proposedSchedule: proposedSchedule.trim(),
      proposeMessage: proposeMessage.trim() || undefined,
    }
    if (!body.proposedSchedule) {
      setToast('제시 일정(proposedSchedule)은 필수입니다.')
      return
    }
    setBusyId(proposeFor)
    setToast('')
    try {
      const res = await apiRequest(`/matching/requests/${proposeFor}/propose`, { method: 'PATCH', json: body })
      const text = await res.text()
      if (!res.ok) {
        setToast(await readJsonError(res, text))
        return
      }
      closePropose()
      await fetchList()
    } catch {
      setToast('제시안 전송 실패')
    } finally {
      setBusyId(null)
    }
  }

  if (!isGuide) {
    return (
      <div className="inbox">
        <h1>가이드 예약함</h1>
        <p className="inbox-warn">
          이 화면은 JWT 역할이 <strong>GUIDE</strong>일 때만 API가 허용합니다. 가이드 신청 직후라면{' '}
          <strong>다시 로그인</strong>한 뒤 이용해 주세요.
        </p>
        <p>
          <Link to="/auth/login">로그인</Link> · <Link to="/guide/register">가이드 신청</Link>
        </p>
      </div>
    )
  }

  return (
    <div className="inbox">
      <h1>🤝 매칭 수락/거절</h1>

      {loading && <p>불러오는 중…</p>}
      {error && <p className="inbox-error">{error}</p>}
      {toast && <p className="inbox-error">{toast}</p>}

      {!loading && !error && rows.length === 0 && (
        <div className="inbox-empty">
          <span className="inbox-empty-icon">🤝</span>
          <p className="inbox-empty-title">받은 예약 요청이 없습니다</p>
          <p className="inbox-empty-desc">게스트가 매칭을 요청하면 여기에 표시됩니다</p>
        </div>
      )}

      {!loading && !error && rows.length > 0 && (
        <>
          <div className="inbox-table-wrap">
            <table className="inbox-table">
              <thead>
                <tr>
                  <th>상태</th>
                  <th>게스트</th>
                  <th>목적지</th>
                  <th>희망일</th>
                  <th>예산</th>
                  <th>동작</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.requestId}>
                    <td>{statusLabel(r.status)}</td>
                    <td>{`게스트 #${r.guestId}`}</td>
                    <td>{r.destination}</td>
                    <td>{r.desiredDate ?? '—'}</td>
                    <td>{r.desiredBudget ? '₩' + Number(r.desiredBudget).toLocaleString('ko-KR') : '—'}</td>
                    <td className="inbox-actions">
                      {r.status === 'PENDING' && (
                        <>
                          <button type="button" className="inbox-btn" onClick={() => openPropose(r)} disabled={busyId != null}>
                            제시안 보내기
                          </button>
                          <button
                            type="button"
                            className="inbox-btn inbox-btn--danger"
                            onClick={() => void reject(r.requestId)}
                            disabled={busyId != null}
                          >
                            거절
                          </button>
                        </>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="inbox-cards" aria-label="예약 요청 카드 목록">
            {rows.map((r) => (
              <article
                key={`card-${r.requestId}`}
                className="inbox-card"
                style={['REJECTED', 'CANCELLED', 'COMPLETED'].includes(r.status) ? { opacity: 0.55 } : undefined}
              >
                <header className="inbox-card-head">
                  <span className="inbox-card-id">#{r.requestId}</span>
                  {statusLabel(r.status)}
                </header>
                <p className="inbox-card-line">👤 <strong>게스트</strong> {`게스트 #${r.guestId}`}</p>
                <p className="inbox-card-line">📍 <strong>목적지</strong> {r.destination ?? '—'}</p>
                <p className="inbox-card-line">📅 <strong>희망일</strong> {r.desiredDate ?? '—'}</p>
                <p className="inbox-card-line">💰 <strong>예산</strong> {r.desiredBudget ? '₩' + Number(r.desiredBudget).toLocaleString('ko-KR') : '—'}</p>
                {r.conceptSummary && (
                  <p className="inbox-card-line">🗺️ <strong>여행 컨셉</strong> {r.conceptSummary}</p>
                )}
                {r.createdAt && (
                  <p className="inbox-card-line">🕐 <strong>요청일</strong> {new Date(r.createdAt).toLocaleDateString('ko-KR')}</p>
                )}
                {r.status === 'ACCEPTED' && (
                  <p className="inbox-proposed-hint">✉️ 제시안을 전송했습니다. 게스트가 수락하면 결제로 진행됩니다.</p>
                )}
                {r.status === 'PENDING' && (
                  <div className="inbox-card-actions">
                    <button type="button" className="inbox-btn" onClick={() => openPropose(r)} disabled={busyId != null}>
                      제시안 보내기
                    </button>
                    <button
                      type="button"
                      className="inbox-btn inbox-btn--danger"
                      onClick={() => void reject(r.requestId)}
                      disabled={busyId != null}
                    >
                      거절
                    </button>
                  </div>
                )}
              </article>
            ))}
          </div>
        </>
      )}

      {proposeFor != null && (
        <div className="inbox-modal-overlay" role="dialog" aria-modal="true" aria-labelledby="inbox-propose-title">
          <div className="inbox-modal">
            <h2 id="inbox-propose-title">제시안 보내기</h2>
            <p className="inbox-hint">요청 #{proposeFor}</p>
            <label className="inbox-modal-label" htmlFor="psched">
              제시 일정 (필수)
            </label>
            <textarea
              id="psched"
              className="inbox-modal-text"
              value={proposedSchedule}
              onChange={(e) => setProposedSchedule(e.target.value)}
              rows={4}
              placeholder="예: 10:00 명동 집합 → 남산 코스 → 18:00 해산"
            />
            <label className="inbox-modal-label" htmlFor="pmsg">
              메시지 (선택)
            </label>
            <textarea
              id="pmsg"
              className="inbox-modal-text"
              value={proposeMessage}
              onChange={(e) => setProposeMessage(e.target.value)}
              rows={3}
            />
            <div className="inbox-modal-actions">
              <button type="button" className="inbox-btn inbox-btn--ghost" onClick={closePropose} disabled={busyId != null}>
                취소
              </button>
              <button type="button" className="inbox-btn" onClick={() => void submitPropose()} disabled={busyId != null}>
                {busyId === proposeFor ? '전송 중…' : '전송'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
