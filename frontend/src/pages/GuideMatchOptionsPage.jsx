import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'

import { PageError, PageLoading } from '../components/PageStates.jsx'
import { apiRequest } from '../api/client'
import { fetchGuestPayments, pickLatestCompletedPaymentIdForRequest } from '../lib/matchingGuest.js'

import './GuideMatchOptionsPage.css'

const PRICE_CHAT = 10000
const PRICE_ACCOMPANY = 50000
const KAKAO_APP_KEY = import.meta.env.VITE_KAKAO_MAP_APP_KEY

function loadKakaoSdk(appKey) {
  if (!appKey) return Promise.reject(new Error('카카오맵 앱 키가 없습니다.'))
  if (window.kakao?.maps?.services) return Promise.resolve(window.kakao)
  return new Promise((resolve, reject) => {
    const existing = document.getElementById('kakao-map-sdk')
    if (existing) {
      existing.addEventListener('load', () => window.kakao.maps.load(() => resolve(window.kakao)))
      existing.addEventListener('error', () => reject(new Error('카카오맵 SDK 로드 실패')))
      return
    }
    const script = document.createElement('script')
    script.id = 'kakao-map-sdk'
    script.async = true
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${appKey}&autoload=false&libraries=services`
    script.onload = () => window.kakao.maps.load(() => resolve(window.kakao))
    script.onerror = () => reject(new Error('카카오맵 SDK 로드 실패'))
    document.head.appendChild(script)
  })
}

function geocodeAddress(kakao, query) {
  return new Promise((resolve) => {
    const geocoder = new kakao.maps.services.Geocoder()
    geocoder.addressSearch(query, (result, status) => {
      if (status !== kakao.maps.services.Status.OK || !result?.length) {
        resolve(null)
        return
      }
      resolve({ lat: Number(result[0].y), lng: Number(result[0].x) })
    })
  })
}

function findLatestRequestForGuide(list, guideId) {
  const gid = Number(guideId)
  if (!Array.isArray(list)) return null
  const rows = list.filter((r) => Number(r.guideId) === gid)
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

function parsePreviewSpots(proposedSchedule) {
  const raw = String(proposedSchedule ?? '').trim()
  if (!raw) return []
  const normalized = raw.replace(/\s*->\s*/g, '\n')
  const rows = normalized
    .split(/\r?\n|,/)
    .map((v) => v.trim())
    .filter(Boolean)
  return rows
}

function hasProposalContent(row) {
  if (!row) return false
  if (String(row.status ?? '').toUpperCase() !== 'ACCEPTED') return false
  return !!(String(row.proposedSchedule ?? '').trim() || String(row.proposeMessage ?? '').trim())
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
  const [activeRequest, setActiveRequest] = useState(null)
  const [paymentType, setPaymentType] = useState(/** @type {'CHAT' | 'ACCOMPANY'} */ ('CHAT'))
  const [reviews, setReviews] = useState([])
  const [paying, setPaying] = useState(false)
  const [payErr, setPayErr] = useState('')
  const [payFocus, setPayFocus] = useState(false)
  const [proposalArrivedNotice, setProposalArrivedNotice] = useState(false)
  const [checkingProposal, setCheckingProposal] = useState(false)
  const matchingOptionsRef = useRef(null)
  const previewMapRef = useRef(null)
  const hasProposalRef = useRef(false)
  const initializedProposalRef = useRef(false)

  const amount = paymentType === 'CHAT' ? PRICE_CHAT : PRICE_ACCOMPANY

  const load = useCallback(async (options = {}) => {
    const silent = Boolean(options?.silent)
    if (!silent) {
      setLoading(true)
      setError('')
    }
    try {
      const res = await apiRequest(`/guides/${guideId}/detail`, { method: 'GET', skipAuth: true })
      const text = await res.text()
      if (!res.ok) throw new Error(text || '가이드를 불러오지 못했습니다.')
      const detail = text ? JSON.parse(text) : null
      setProfile(detail?.profile ?? null)

      const listRes = await apiRequest('/matching/requests/guest/list', { method: 'GET' })
      const list = listRes.ok ? (await listRes.text().then((t) => (t ? JSON.parse(t) : []))) : []
      const safeList = Array.isArray(list) ? list : []

      let rid = stateRequestId != null ? Number(stateRequestId) : null
      if (rid == null || Number.isNaN(rid)) {
        const latest = findLatestRequestForGuide(safeList, guideId)
        rid = latest?.requestId != null ? Number(latest.requestId) : null
      }

      const activeRid = rid != null && !Number.isNaN(rid) ? rid : null
      const row = activeRid != null ? safeList.find((r) => Number(r.requestId) === Number(activeRid)) : null
      const hasProposalNow = hasProposalContent(row)
      if (initializedProposalRef.current) {
        if (!hasProposalRef.current && hasProposalNow) {
          setProposalArrivedNotice(true)
        }
      } else {
        initializedProposalRef.current = true
      }
      hasProposalRef.current = hasProposalNow
      setActiveRequest(row ?? null)
      if (row && (row.status === 'PAID' || row.status === 'IN_PROGRESS')) {
        let payments = []
        try {
          payments = await fetchGuestPayments(apiRequest)
        } catch {
          payments = []
        }
        const paymentId = pickLatestCompletedPaymentIdForRequest(payments, activeRid)
        const qs = new URLSearchParams()
        qs.set('requestId', String(activeRid))
        if (paymentId != null) qs.set('paymentId', String(paymentId))
        navigate(`/guides/${guideId}/match/complete?${qs.toString()}`, { replace: true })
        return
      }

      setRequestId(activeRid)

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
      if (!silent) {
        setError(e instanceof Error ? e.message : '오류')
      }
    } finally {
      if (!silent) {
        setLoading(false)
      }
    }
  }, [guideId, stateRequestId, navigate])

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

  const previewSpots = useMemo(() => {
    return parsePreviewSpots(activeRequest?.proposedSchedule)
  }, [activeRequest])

  const previewHint = useMemo(() => {
    return String(activeRequest?.proposeMessage ?? '').trim()
  }, [activeRequest])
  const courseReadyForPayment = useMemo(() => {
    return String(activeRequest?.proposedSchedule ?? '').trim().length > 0
  }, [activeRequest])
  const previewFirstSpot = previewSpots[0] ?? ''
  const previewLockedSpots = previewSpots.slice(1)
  const hasLockedPreview = previewLockedSpots.length > 0 || !!previewHint

  useEffect(() => {
    if (!payFocus) return
    const timer = setTimeout(() => setPayFocus(false), 1400)
    return () => clearTimeout(timer)
  }, [payFocus])

  useEffect(() => {
    if (!proposalArrivedNotice) return
    const timer = setTimeout(() => setProposalArrivedNotice(false), 5000)
    return () => clearTimeout(timer)
  }, [proposalArrivedNotice])

  useEffect(() => {
    if (requestId == null) return
    if (courseReadyForPayment) return
    const timer = setInterval(() => {
      if (typeof document !== 'undefined' && document.visibilityState !== 'visible') return
      void load({ silent: true })
    }, 45000)
    return () => clearInterval(timer)
  }, [requestId, courseReadyForPayment, load])

  useEffect(() => {
    if (requestId == null || courseReadyForPayment) return
    const onFocus = () => {
      void load({ silent: true })
    }
    const onVisibility = () => {
      if (document.visibilityState === 'visible') {
        void load({ silent: true })
      }
    }
    window.addEventListener('focus', onFocus)
    document.addEventListener('visibilitychange', onVisibility)
    return () => {
      window.removeEventListener('focus', onFocus)
      document.removeEventListener('visibilitychange', onVisibility)
    }
  }, [requestId, courseReadyForPayment, load])

  const onCheckProposalNow = async () => {
    if (checkingProposal) return
    setCheckingProposal(true)
    try {
      await load({ silent: true })
    } finally {
      setCheckingProposal(false)
    }
  }

  const moveToPaymentOptions = () => {
    matchingOptionsRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    setPayFocus(true)
  }

  useEffect(() => {
    if (!previewMapRef.current) return
    let cancelled = false
    ;(async () => {
      try {
        const kakao = await loadKakaoSdk(KAKAO_APP_KEY)
        if (cancelled || !previewMapRef.current) return
        const fallback = { lat: 33.4996, lng: 126.5312 }
        const map = new kakao.maps.Map(previewMapRef.current, {
          center: new kakao.maps.LatLng(fallback.lat, fallback.lng),
          level: 7,
        })
        const query = profile?.region?.trim()
        if (!query) return
        const point = await geocodeAddress(kakao, query)
        if (cancelled || !point) return
        const center = new kakao.maps.LatLng(point.lat, point.lng)
        map.setCenter(center)
      } catch {
        /* preview map fallback: keep grey background */
      }
    })()
    return () => {
      cancelled = true
    }
  }, [profile?.region])

  const onPay = async () => {
    setPayErr('')
    if (requestId == null) {
      setPayErr('매칭 요청이 없으면 결제할 수 없습니다. 가이드 제안을 받은 뒤 다시 시도해 주세요.')
      return
    }
    if (!courseReadyForPayment) {
      setPayErr('가이드가 코스를 작성한 뒤 결제를 진행할 수 있어요.')
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

  if (loading) {
    return (
      <div className="gmo">
        <PageLoading />
      </div>
    )
  }
  if (error || !profile) {
    return (
      <div className="gmo">
        <PageError message={error || '가이드를 찾을 수 없습니다.'}>
          <Link to="/guides">가이드 목록</Link>
        </PageError>
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
      {requestId != null && !courseReadyForPayment && (
        <div className="gmo-watch-banner">
          <p>가이드 제시안을 기다리는 중이에요. 이 탭으로 돌아오면 자동으로 최신 상태를 확인합니다.</p>
          <button type="button" className="gmo-watch-btn" onClick={() => void onCheckProposalNow()} disabled={checkingProposal}>
            {checkingProposal ? '확인 중…' : '지금 확인'}
          </button>
        </div>
      )}
      {proposalArrivedNotice && (
        <p className="gmo-arrived-banner">제시안이 도착했어요! 아래에서 코스 일부를 확인하고 결제를 진행해 주세요.</p>
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
            <div ref={previewMapRef} className="gmo-map-canvas" />
            <div className="gmo-map-overlay">
              <div className="gmo-lock" aria-hidden>
                🔒
              </div>
              <p className="gmo-map-title">매칭 후 상세 코스가 공개됩니다</p>
              <p className="gmo-map-sub">결제 및 매칭을 완료하고 나만의 비밀 지도를 확인하세요.</p>
            </div>
          </div>
        </section>

        <aside
          ref={matchingOptionsRef}
          className={`gmo-side${payFocus ? ' gmo-side--focus' : ''}`}
          aria-labelledby="gmo-side-title"
        >
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

          <button
            type="button"
            className="gmo-pay"
            disabled={paying || requestId == null || !courseReadyForPayment}
            onClick={() => void onPay()}
          >
            {paying ? '처리 중…' : !courseReadyForPayment ? '가이드 코스 작성 대기중' : '결제하고 코스 열기'}
          </button>
          {!courseReadyForPayment && (
            <p className="gmo-course-gate">가이드가 코스를 작성하면 결제 버튼이 활성화됩니다.</p>
          )}
          {payErr && <p className="gmo-err">{payErr}</p>}
        </aside>
      </div>

      {previewSpots.length > 0 && (
        <section className="gmo-half-preview" aria-label="코스 텍스트 미리보기">
          <p className="gmo-half-title">가이드가 작성한 코스 일부</p>
          <ul className="gmo-spot-preview-list">
            <li className="gmo-spot-preview-item">
              <span className="gmo-spot-preview-num">1</span>
              <span className="gmo-spot-preview-name">{previewFirstSpot}</span>
            </li>
          </ul>

          {hasLockedPreview && (
            <div className="gmo-locked-zone">
              <div className="gmo-locked-blur" aria-hidden>
                <ul className="gmo-spot-preview-list">
                  {previewLockedSpots.map((spot, idx) => (
                    <li key={`${idx + 1}-${spot.slice(0, 12)}`} className="gmo-spot-preview-item">
                      <span className="gmo-spot-preview-num">{idx + 2}</span>
                      <span className="gmo-spot-preview-name">{spot}</span>
                    </li>
                  ))}
                </ul>
                {previewHint && <p className="gmo-half-visible">가이드 메모: {previewHint}</p>}
                <p className="gmo-half-foot">나머지 상세 코스는 결제 후 공개됩니다.</p>
              </div>
              <div className="gmo-locked-overlay">
                <div className="gmo-pay-hint-card">
                  <p className="gmo-pay-hint-title">아직 공개되지 않은 코스가 있어요</p>
                  <p className="gmo-pay-hint-sub">결제하기를 누르면 위 매칭 옵션에서 바로 진행할 수 있어요.</p>
                  <button type="button" className="gmo-pay-hint-btn" onClick={moveToPaymentOptions}>
                    결제하기로 이동
                  </button>
                </div>
              </div>
            </div>
          )}
          {!hasLockedPreview && (
            <div className="gmo-pay-hint-inline">
              <p className="gmo-half-foot">나머지 상세 코스는 결제 후 공개됩니다.</p>
            </div>
          )}
          {!hasLockedPreview && (
            <div className="gmo-pay-hint-inline">
              <button type="button" className="gmo-pay-hint-btn" onClick={moveToPaymentOptions}>
                결제하기로 이동
              </button>
            </div>
          )}
        </section>
      )}
      {previewSpots.length === 0 && (
        <section className="gmo-course-pending" aria-live="polite">
          <span className="gmo-course-pending-dot" aria-hidden />
          <p className="gmo-course-pending-title">가이드가 코스를 작성 중이에요</p>
          <p className="gmo-course-pending-sub">조금만 기다려 주세요. 작성이 완료되면 일부 코스가 여기에 먼저 공개됩니다.</p>
        </section>
      )}

      <section className="gmo-reviews" aria-labelledby="gmo-rev-title">
        <h2 id="gmo-rev-title">여행자들의 후기 ({rc})</h2>
        <p className="gmo-reviews-readonly">후기는 투어가 완료된 뒤 작성할 수 있어요. 지금은 기존 후기만 확인할 수 있습니다.</p>
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
