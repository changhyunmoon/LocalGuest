import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'

import { apiRequest } from '../api/client'

import './GuideProposalPage.css'

function parseCourseSteps(text) {
  if (!text || !String(text).trim()) return []
  const t = String(text).trim()
  if (t.includes('->')) {
    return t
      .split('->')
      .map((s) => s.trim())
      .filter(Boolean)
  }
  return t.split(/\n/).map((s) => s.trim()).filter(Boolean)
}

function splitProposeMessage(msg) {
  if (!msg || !String(msg).trim()) return { courseLine: '', note: '' }
  const t = String(msg).trim()
  const blocks = t.split(/\n\n+/)
  const first = blocks[0] ?? ''
  if (first.includes('->')) {
    return { courseLine: first, note: blocks.slice(1).join('\n\n').trim() }
  }
  return { courseLine: '', note: t }
}

function findLatestProposal(list, guideId) {
  const gid = Number(guideId)
  if (!Array.isArray(list)) return null
  const rows = list.filter(
    (r) =>
      Number(r.guideId) === gid &&
      r.status === 'ACCEPTED' &&
      (String(r.proposedSchedule ?? '').trim() || String(r.proposeMessage ?? '').trim()),
  )
  rows.sort((a, b) => {
    const ta = a.createdAt ? new Date(a.createdAt).getTime() : 0
    const tb = b.createdAt ? new Date(b.createdAt).getTime() : 0
    return tb - ta
  })
  return rows[0] ?? null
}

function formatWon(n) {
  if (n == null || Number.isNaN(Number(n))) return '—'
  return `${Number(n).toLocaleString('ko-KR')}원`
}

export function GuideProposalPage() {
  const { guideId } = useParams()
  const navigate = useNavigate()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [profile, setProfile] = useState(null)
  const [proposal, setProposal] = useState(null)
  /** 'unauthorized' | 'list_error' | 'no_match' | 'has_match' */
  const [matchState, setMatchState] = useState('no_match')
  const [firstFeed, setFirstFeed] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const res = await apiRequest(`/guides/${guideId}/detail`, { method: 'GET', skipAuth: true })
      const text = await res.text()
      if (!res.ok) throw new Error(text || '가이드를 불러오지 못했습니다.')
      const detail = text ? JSON.parse(text) : null
      setProfile(detail?.profile ?? null)
      const feeds = Array.isArray(detail?.feeds) ? detail.feeds : []
      setFirstFeed(feeds[0] ?? null)

      const listRes = await apiRequest('/matching/requests/guest/list', { method: 'GET' })
      if (listRes.status === 401 || listRes.status === 403) {
        setProposal(null)
        setMatchState('unauthorized')
      } else {
        const lt = await listRes.text()
        if (!listRes.ok) {
          setProposal(null)
          setMatchState('list_error')
        } else {
          const list = lt ? JSON.parse(lt) : []
          const match = findLatestProposal(list, guideId)
          setProposal(match)
          setMatchState(match ? 'has_match' : 'no_match')
        }
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : '오류')
    } finally {
      setLoading(false)
    }
  }, [guideId])

  useEffect(() => {
    void load()
  }, [load])

  const showDemo = matchState !== 'has_match'

  const scheduleText = useMemo(() => {
    if (proposal?.proposedSchedule?.trim()) return String(proposal.proposedSchedule).trim()
    if (showDemo && firstFeed?.content) {
      const line = String(firstFeed.content).split(/\n/)[0]?.trim()
      return line ? `예시 일정 · ${line.slice(0, 48)}${line.length > 48 ? '…' : ''}` : '가이드와 일정을 조율해 주세요.'
    }
    return '가이드가 일정을 제안하면 이곳에 표시됩니다.'
  }, [proposal, showDemo, firstFeed])

  const { courseLine, note } = useMemo(() => {
    if (proposal?.proposeMessage) return splitProposeMessage(proposal.proposeMessage)
    if (showDemo && firstFeed?.content) {
      const body = String(firstFeed.content).split(/\n/).slice(1).join('\n').trim()
      const title = String(firstFeed.content).split(/\n/)[0]?.trim() ?? ''
      return splitProposeMessage(body || title)
    }
    return { courseLine: '', note: '' }
  }, [proposal, showDemo, firstFeed])

  const courseSteps = useMemo(() => {
    if (courseLine) return parseCourseSteps(courseLine)
    if (proposal?.conceptSummary) return [String(proposal.conceptSummary).slice(0, 120)]
    if (showDemo && profile?.bio) return [profile.bio.slice(0, 100) + (profile.bio.length > 100 ? '…' : '')]
    return ['코스는 매칭 요청 후 가이드가 제안해 드려요.']
  }, [courseLine, proposal, showDemo, profile])

  const guideMessage = useMemo(() => {
    if (note) return note
    if (proposal?.proposeMessage && !courseLine) return String(proposal.proposeMessage).trim()
    if (showDemo) {
      return (
        profile?.bio ||
        '현지 가이드가 조용한 명소와 동선을 제안해 드릴 수 있어요. 실제 제안문은 예약·매칭이 진행된 뒤에 표시됩니다.'
      )
    }
    return '가이드 메시지가 없습니다.'
  }, [note, proposal, showDemo, profile, courseLine])

  const priceAmount = useMemo(() => {
    if (proposal?.desiredBudget != null && proposal.desiredBudget !== '') return Number(proposal.desiredBudget)
    const ph = profile?.pricePerHour != null ? Number(profile.pricePerHour) : null
    if (ph != null && !Number.isNaN(ph)) return Math.round(ph * 4)
    return 120000
  }, [proposal, profile])

  const rating =
    profile?.averageRating != null && profile?.averageRating !== ''
      ? Number(profile.averageRating).toFixed(1)
      : '—'
  const rc = profile?.reviewCount != null ? profile.reviewCount : 0

  if (loading) return <p className="gp" style={{ color: '#6b7280' }}>불러오는 중…</p>
  if (error || !profile) {
    return (
      <div className="gp">
        <p className="gp-err">{error || '프로필을 찾을 수 없습니다.'}</p>
        <Link to="/guides">가이드 목록</Link>
      </div>
    )
  }

  return (
    <div className="gp">
      <button type="button" className="gp-back" onClick={() => navigate(-1)}>
        ← 뒤로 가기
      </button>

      {showDemo && (
        <p className="gp-banner">
          {matchState === 'unauthorized' && (
            <>
              로그인하면 이 가이드와의 <strong>매칭 제안</strong>을 불러옵니다. 아래는 피드·프로필 기반{' '}
              <strong>미리보기</strong>입니다.
            </>
          )}
          {matchState === 'list_error' && (
            <>매칭 목록을 불러오지 못했습니다. 아래는 피드·프로필 기반 <strong>미리보기</strong>입니다.</>
          )}
          {matchState === 'no_match' && (
            <>
              이 가이드에 대한 <strong>도착한 제안</strong>이 아직 없습니다. 매칭 요청 후 가이드가 제안을 내면 이 화면에
              반영됩니다. 아래는 <strong>미리보기</strong>입니다.
            </>
          )}
        </p>
      )}

      <article className="gp-card">
        <h1 className="gp-title">가이드의 제안이 도착했어요! ✨</h1>
        <p className="gp-sub">AI가 추천한 가이드가 여행자님의 요청에 맞춤 일정을 보내왔습니다.</p>

        <div className="gp-profile">
          <div
            className="gp-avatar"
            style={
              profile.profileImage ? { backgroundImage: `url(${profile.profileImage})` } : undefined
            }
          />
          <div>
            <p className="gp-name">{profile.nickname ?? '가이드'}</p>
            <p className="gp-meta">
              ⭐ {rating} ({rc}) · {profile.region ?? ''}
            </p>
          </div>
        </div>

        <div className="gp-box">
          <section>
            <h3>제안 일정 📅</h3>
            <p>{scheduleText}</p>
          </section>
          <section>
            <h3>추천 코스 🗺️</h3>
            <ol className="gp-steps">
              {courseSteps.map((step, i) => (
                <li key={`${i}-${step.slice(0, 12)}`}>{step}</li>
              ))}
            </ol>
          </section>
          <section>
            <h3>가이드 메시지 💌</h3>
            <p>{guideMessage}</p>
          </section>
          <hr className="gp-divider" />
          <div className="gp-price-row">
            <span className="gp-price-label">총 결제 금액</span>
            <p className="gp-price">{formatWon(priceAmount)}</p>
          </div>
        </div>
      </article>

      <div className="gp-actions">
        <Link to="/ai-search" className="gp-btn gp-btn--ghost">
          다른 가이드 제안 보기
        </Link>
        {matchState === 'unauthorized' && (
          <Link
            to="/auth/login"
            className="gp-btn gp-btn--primary"
            state={{
              returnTo: `/guides/${guideId}/proposal`,
              hint: '로그인 후 내 매칭 요청에서 가이드 제안을 확인할 수 있어요.',
            }}
          >
            로그인하고 제안 확인
          </Link>
        )}
        {matchState === 'has_match' && proposal?.requestId != null && (
          <Link
            to={`/guides/${guideId}/match`}
            state={{ requestId: proposal.requestId }}
            className="gp-btn gp-btn--primary"
          >
            코스 확인 및 매칭하기
          </Link>
        )}
        {(matchState === 'no_match' || matchState === 'list_error') && (
          <Link to={`/guides/${guideId}`} className="gp-btn gp-btn--primary">
            가이드 프로필에서 요청하기
          </Link>
        )}
      </div>
    </div>
  )
}
