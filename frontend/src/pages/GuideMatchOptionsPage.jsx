import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'

import { apiRequest } from '../api/client'

import './GuideMatchOptionsPage.css'

const PRICE_CHAT = 10000
const PRICE_ACCOMPANY = 50000

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

function formatKrw(n) {
  return `₩ ${Number(n).toLocaleString('ko-KR')}`
}

export function GuideMatchOptionsPage() {
  const { guideId } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const stateRequestId = location.state?.requestId

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [profile, setProfile] = useState(null)
  const [requestId, setRequestId] = useState(stateRequestId != null ? Number(stateRequestId) : null)
  const [paymentType, setPaymentType] = useState(/** @type {'CHAT' | 'ACCOMPANY'} */ ('CHAT'))
  const [reviews, setReviews] = useState([])
  const [reviewText, setReviewText] = useState('')
  const [paying, setPaying] = useState(false)
  const [reviewSubmitting, setReviewSubmitting] = useState(false)
  const [payErr, setPayErr] = useState('')
  const [reviewErr, setReviewErr] = useState('')

  const amount = paymentType === 'CHAT' ? PRICE_CHAT : PRICE_ACCOMPANY

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const res = await apiRequest(`/guides/${guideId}/detail`, { method: 'GET', skipAuth: true })
      const text = await res.text()
      if (!res.ok) throw new Error(text || '가이드를 불러오지 못했습니다.')
      const detail = text ? JSON.parse(text) : null
      setProfile(detail?.profile ?? null)

      let rid = stateRequestId != null ? Number(stateRequestId) : null
      if (rid == null || Number.isNaN(rid)) {
        const listRes = await apiRequest('/matching/requests/guest/list', { method: 'GET' })
        if (listRes.ok) {
          const lt = await listRes.text()
          const list = lt ? JSON.parse(lt) : []
          const m = findLatestProposal(list, guideId)
          rid = m?.requestId != null ? Number(m.requestId) : null
        }
      }
      setRequestId(rid != null && !Number.isNaN(rid) ? rid : null)

      const revRes = await apiRequest(`/reviews/guide/${guideId}?size=12&sort=createdAt,desc`, { method: 'GET', skipAuth: true })
      const revText = await revRes.text()
      if (revRes.ok) {
        const page = revText ? JSON.parse(revText) : {}
        const raw = page?.content
        setReviews(Array.isArray(raw) ? raw : [])
      } else {
        setReviews([])
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : '오류')
    } finally {
      setLoading(false)
    }
  }, [guideId, stateRequestId])

  useEffect(() => {
    void load()
  }, [load])

  const rating =
    profile?.averageRating != null && profile?.averageRating !== ''
      ? Number(profile.averageRating).toFixed(1)
      : '—'
  const rc = profile?.reviewCount != null ? profile.reviewCount : 0
  const likes = useMemo(() => Math.max(Math.round(rc * 2.8) + 40, 12), [rc])

  const quote = useMemo(() => {
    const b = profile?.bio?.trim()
    if (b) return `“${b.slice(0, 120)}${b.length > 120 ? '…' : ''}”`
    return '“관광객은 모르는, 사진 찍기 좋은 조용한 루트를 안내합니다.”'
  }, [profile])

  const onPay = async () => {
    setPayErr('')
    if (requestId == null) {
      setPayErr('매칭 요청이 없으면 결제할 수 없습니다. 가이드 제안을 받은 뒤 다시 시도해 주세요.')
      return
    }
    setPaying(true)
    try {
      const res = await apiRequest('/matching/payments', {
        method: 'POST',
        json: {
          matchRequestId: requestId,
          amount,
          paymentType,
        },
      })
      const t = await res.text()
      if (!res.ok) throw new Error(t || '결제 요청에 실패했습니다.')
      const data = t ? JSON.parse(t) : {}
      const qs = new URLSearchParams()
      if (data.paymentId != null) qs.set('paymentId', String(data.paymentId))
      if (data.amount != null) qs.set('amount', String(data.amount))
      if (data.pgOrderNo) qs.set('pgOrderNo', String(data.pgOrderNo))
      if (data.paymentType) qs.set('paymentType', String(data.paymentType))
      if (requestId != null) qs.set('requestId', String(requestId))
      if (guideId != null) qs.set('guideId', String(guideId))
      const redirect = data?.redirectUrl
      if (redirect && String(redirect).trim() !== '') {
        navigate(`/pay/kakao-stub?${qs.toString()}`, { state: { externalRedirect: String(redirect) } })
        return
      }
      navigate(`/pay/kakao-stub?${qs.toString()}`)
    } catch (e) {
      setPayErr(e instanceof Error ? e.message : '오류')
    } finally {
      setPaying(false)
    }
  }

  const onReviewSubmit = async (e) => {
    e.preventDefault()
    setReviewErr('')
    const content = reviewText.trim()
    if (content.length < 10) {
      setReviewErr('후기는 10자 이상 입력해 주세요.')
      return
    }
    if (requestId == null) {
      setReviewErr('리뷰는 매칭이 완료된 뒤 작성할 수 있어요.')
      return
    }
    setReviewSubmitting(true)
    try {
      const res = await apiRequest('/reviews', {
        method: 'POST',
        json: {
          guideId: Number(guideId),
          matchRequestId: requestId,
          rating: 5,
          content,
        },
      })
      const t = await res.text()
      if (!res.ok) throw new Error(t || '등록에 실패했습니다.')
      setReviewText('')
      void load()
    } catch (e) {
      setReviewErr(e instanceof Error ? e.message : '오류')
    } finally {
      setReviewSubmitting(false)
    }
  }

  if (loading) {
    return <p className="gmo" style={{ color: '#6b7280' }}>불러오는 중…</p>
  }
  if (error || !profile) {
    return (
      <div className="gmo">
        <p style={{ color: '#b91c1c' }}>{error || '가이드를 찾을 수 없습니다.'}</p>
        <Link to="/guides">가이드 목록</Link>
      </div>
    )
  }

  return (
    <div className="gmo">
      <Link to="/ai-search" className="gmo-back">
        ← 검색으로 돌아가기
      </Link>

      {requestId == null && (
        <p className="gmo-banner">
          아직 이 가이드와 연결된 <strong>매칭 요청</strong>이 없습니다. 코스는 미리보기만 제공되며, 결제는 제안을 받은 뒤에 가능합니다.
        </p>
      )}

      <header className="gmo-hero">
        <div
          className="gmo-hero-ph"
          style={profile.profileImage ? { backgroundImage: `url(${profile.profileImage})` } : undefined}
        />
        <div className="gmo-hero-body">
          <h1 className="gmo-hero-name">{profile.nickname ?? '가이드'}</h1>
          <p className="gmo-hero-meta">
            📍 {profile.region ?? ''} · ⭐ {rating} ({rc} 리뷰)
          </p>
          <p className="gmo-hero-quote">{quote}</p>
        </div>
        <div className="gmo-likes" aria-label="좋아요">
          <span aria-hidden>♡</span> {likes}
        </div>
      </header>

      <div className="gmo-grid">
        <section className="gmo-preview" aria-labelledby="gmo-preview-title">
          <h2 id="gmo-preview-title">추천 코스 미리보기</h2>
          <div className="gmo-map">
            <div className="gmo-lock" aria-hidden>
              🔒
            </div>
            <p className="gmo-map-title">매칭 후 상세 코스가 공개됩니다</p>
            <p className="gmo-map-sub">결제 및 매칭을 완료하고 나만의 비밀 지도를 확인하세요.</p>
          </div>
        </section>

        <aside className="gmo-side" aria-labelledby="gmo-side-title">
          <h2 id="gmo-side-title">가이드 매칭 옵션</h2>

          <label className="gmo-opt">
            <input
              type="radio"
              name="payType"
              checked={paymentType === 'CHAT'}
              onChange={() => setPaymentType('CHAT')}
            />
            <span className="gmo-opt-inner">
              <span className="gmo-opt-text">
                <span className="gmo-opt-title">정보만 받기 (채팅)</span>
                <span className="gmo-opt-desc">채팅으로 코스·팁을 받아요</span>
              </span>
              <span className="gmo-opt-price">{formatKrw(PRICE_CHAT)}</span>
            </span>
          </label>

          <label className="gmo-opt">
            <input
              type="radio"
              name="payType"
              checked={paymentType === 'ACCOMPANY'}
              onChange={() => setPaymentType('ACCOMPANY')}
            />
            <span className="gmo-opt-inner">
              <span className="gmo-opt-text">
                <span className="gmo-opt-title">직접 만나기 (동행)</span>
                <span className="gmo-opt-desc">현지에서 함께 동행해요</span>
              </span>
              <span className="gmo-opt-price">{formatKrw(PRICE_ACCOMPANY)}</span>
            </span>
          </label>

          <div className="gmo-total">
            <span>Total</span>
            <strong>{formatKrw(amount)}</strong>
          </div>

          <button type="button" className="gmo-pay" disabled={paying || requestId == null} onClick={() => void onPay()}>
            {paying ? '처리 중…' : '결제하고 코스 열기'}
          </button>
          {payErr && <p className="gmo-err">{payErr}</p>}
        </aside>
      </div>

      <section className="gmo-reviews" aria-labelledby="gmo-rev-title">
        <h2 id="gmo-rev-title">여행자들의 후기 ({rc})</h2>
        {reviewErr && <p className="gmo-err">{reviewErr}</p>}
        <form className="gmo-review-form" onSubmit={(e) => void onReviewSubmit(e)}>
          <textarea
            className="gmo-review-input"
            rows={2}
            placeholder="가이드에게 궁금한 점이나 후기를 남겨주세요…"
            value={reviewText}
            onChange={(e) => setReviewText(e.target.value)}
            disabled={requestId == null}
          />
          <button type="submit" className="gmo-review-submit" disabled={reviewSubmitting || requestId == null}>
            등록
          </button>
        </form>
        <ul className="gmo-review-list">
          {reviews.length === 0 ? (
            <li className="gmo-review-item">
              <div className="gmo-review-av" />
              <div>
                <p className="gmo-review-name">LocalGuest</p>
                <p className="gmo-review-text">아직 등록된 후기가 없어요. 매칭 후 첫 후기를 남겨 보세요!</p>
              </div>
            </li>
          ) : (
            reviews.slice(0, 6).map((r) => (
              <li key={r.id} className="gmo-review-item">
                <div className="gmo-review-av" />
                <div>
                  <p className="gmo-review-name">{r.writeNickname ?? '여행자'}</p>
                  <p className="gmo-review-text">{r.content}</p>
                </div>
              </li>
            ))
          )}
        </ul>
      </section>
    </div>
  )
}
