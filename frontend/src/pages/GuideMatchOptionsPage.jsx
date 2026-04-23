import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'

import { PageError, PageLoading } from '../components/PageStates.jsx'
import { ReviewCarousel } from '../components/ReviewCarousel.jsx'
import { apiRequest } from '../api/client'
import { rememberRecentGuide } from '../lib/recentGuides.js'
import { extractReviewListFromPage } from '../lib/reviewPage.js'
import { fetchGuestPayments, pickLatestCompletedPaymentIdForRequest } from '../lib/matchingGuest.js'
import { loadKakaoSdk } from '../lib/kakaoMapSdk.js'

import './GuideMatchOptionsPage.css'

/** 가이드 마이페이지 「종일 패키지」와 동일: 서버 `pricePerHour` × 8 (`GuideFeesPage`와 맞춤) */
const FULL_DAY_HOURS = 8
const KAKAO_APP_KEY = import.meta.env.VITE_KAKAO_MAP_APP_KEY

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
  return rows.map((line, idx) => {
    const parts = line.split('|').map((p) => p.trim()).filter(Boolean)
    // 새 포맷: "SPOT n | time | desc"
    if (parts.length >= 3 && /^SPOT\s*\d+/i.test(parts[0])) {
      return { idx, time: parts[1], desc: parts.slice(2).join(' | ') }
    }
    // 구 포맷(장소명만): desc로 처리
    return { idx, time: '', desc: line }
  })
}

function hasProposalContent(row) {
  if (!row) return false
  if (String(row.status ?? '').toUpperCase() !== 'ACCEPTED') return false
  return !!(String(row.proposedSchedule ?? '').trim() || String(row.proposeMessage ?? '').trim())
}

function parseProfileTags(keywords) {
  if (keywords == null || keywords === '') return []
  return String(keywords)
    .split(/[,#\s]+/)
    .map((s) => s.trim())
    .filter(Boolean)
    .slice(0, 10)
    .map((t) => (t.startsWith('#') ? t : `#${t}`))
}

function feedCardTitle(content) {
  if (!content || !String(content).trim()) return '피드'
  const line = String(content).split(/\r?\n/)[0]?.trim()
  if (!line) return '피드'
  return line.length > 72 ? `${line.slice(0, 72)}…` : line
}

function feedSnippet(content) {
  if (!content) return ''
  const lines = String(content).split(/\r?\n/)
  const rest = lines.slice(1).join(' ').trim()
  const chunk = rest || lines[0]?.trim() || ''
  return chunk.length > 140 ? `${chunk.slice(0, 140)}…` : chunk
}

function feedPrimaryImage(feed) {
  const urls = Array.isArray(feed?.imageUrls) ? feed.imageUrls : []
  const u0 = urls.find((u) => u && String(u).trim())
  if (u0) return String(u0).trim()
  if (feed?.imageUrl) {
    const first = String(feed.imageUrl).split(',')[0]?.trim()
    if (first) return first
  }
  return ''
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
  const [paying, setPaying] = useState(false)
  const [payErr, setPayErr] = useState('')
  const [proposalArrivedNotice, setProposalArrivedNotice] = useState(false)
  const [checkingProposal, setCheckingProposal] = useState(false)
  const previewMapRef = useRef(null)
  const hasProposalRef = useRef(false)
  const initializedProposalRef = useRef(false)

  /** `/guides/:id/detail` 전체 (모달에서 피드·소개 확장용) */
  const [guideDetail, setGuideDetail] = useState(null)
  const [profileModalOpen, setProfileModalOpen] = useState(false)
  const [modalReviews, setModalReviews] = useState([])
  const [modalReviewsLoading, setModalReviewsLoading] = useState(false)
  const [modalReviewsErr, setModalReviewsErr] = useState('')

  const accompanyPackageWon = useMemo(() => {
    const hourly = profile?.pricePerHour != null ? Number(profile.pricePerHour) : 0
    if (!Number.isFinite(hourly) || hourly <= 0) return 0
    return Math.round(hourly * FULL_DAY_HOURS)
  }, [profile])

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
      setGuideDetail(detail)
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
  const hasPreview = previewSpots.length > 0

  const modalTags = useMemo(() => parseProfileTags(profile?.keywords), [profile])

  const modalFeeds = useMemo(() => {
    const feeds = guideDetail?.feeds
    return Array.isArray(feeds) ? feeds : []
  }, [guideDetail])

  const modalCareers = useMemo(() => {
    const rows = guideDetail?.careers
    return Array.isArray(rows) ? rows : []
  }, [guideDetail])

  const modalStoryBlocks = useMemo(() => {
    const p = profile
    if (!p) return []
    return [
      p.residenceYears != null && Number(p.residenceYears) > 0
        ? { label: '거주', text: `이 지역에 약 ${p.residenceYears}년 살았어요.` }
        : null,
      p.localStory && String(p.localStory).trim()
        ? { label: '지역 이야기', text: String(p.localStory).trim() }
        : null,
      p.guideStyle && String(p.guideStyle).trim()
        ? { label: '가이드 스타일', text: String(p.guideStyle).trim() }
        : null,
      p.defaultCourse && String(p.defaultCourse).trim()
        ? { label: '추천 코스', text: String(p.defaultCourse).trim() }
        : null,
    ].filter(Boolean)
  }, [profile])

  useEffect(() => {
    if (!profileModalOpen) return
    const onKey = (e) => {
      if (e.key === 'Escape') {
        e.preventDefault()
        setProfileModalOpen(false)
      }
    }
    window.addEventListener('keydown', onKey)
    const prevOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      window.removeEventListener('keydown', onKey)
      document.body.style.overflow = prevOverflow
    }
  }, [profileModalOpen])

  useEffect(() => {
    if (!profileModalOpen || !guideId) return
    let cancelled = false
    setModalReviewsLoading(true)
    setModalReviewsErr('')
    void apiRequest(`/reviews/guide/${guideId}?size=15&sort=id,desc`, { method: 'GET', skipAuth: true })
      .then(async (revRes) => {
        const revText = await revRes.text()
        if (cancelled) return
        if (!revRes.ok) {
          setModalReviewsErr(revText || '후기를 불러오지 못했습니다.')
          setModalReviews([])
          return
        }
        const page = revText ? JSON.parse(revText) : {}
        setModalReviews(extractReviewListFromPage(page))
      })
      .catch(() => {
        if (!cancelled) {
          setModalReviewsErr('후기를 불러오지 못했습니다.')
          setModalReviews([])
        }
      })
      .finally(() => {
        if (!cancelled) setModalReviewsLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [profileModalOpen, guideId])

  useEffect(() => {
    if (!guideId) return
    rememberRecentGuide(Number(guideId))
  }, [guideId])

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
    if (accompanyPackageWon <= 0) {
      setPayErr('가이드가 종일 패키지 요금을 설정해야 결제할 수 있어요. 가이드 마이페이지에서 비용을 저장한 뒤 다시 시도해 주세요.')
      return
    }
    setPaying(true)
    try {
      const res = await apiRequest('/matching/payments', {
        method: 'POST',
        json: {
          matchRequestId: requestId,
          amount: accompanyPackageWon,
          paymentType: 'ACCOMPANY',
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
      <div className="gmo-topbar">
        <Link to={`/guides/${guideId}`} className="gmo-back gmo-back--pill">
          ← 가이드 프로필
        </Link>
        <Link to="/mypage/itinerary" className="gmo-back gmo-back--pill gmo-back--line">
          예정된 로컬 만남
        </Link>
      </div>
      <p className="gmo-kicker" role="text">
        로컬 동행 · 코스 응답
      </p>

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

      <header
        className="gmo-hero gmo-hero--clickable"
        role="button"
        tabIndex={0}
        aria-haspopup="dialog"
        aria-expanded={profileModalOpen}
        aria-label={`${profile.nickname ?? '가이드'} 상세 정보 열기`}
        onClick={() => setProfileModalOpen(true)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault()
            setProfileModalOpen(true)
          }
        }}
      >
        <div
          className="gmo-hero-ph"
          style={profile.profileImage ? { backgroundImage: `url(${profile.profileImage})` } : undefined}
        />
        <div className="gmo-hero-body">
          <h1 className="gmo-hero-name">{profile.nickname ?? '가이드'}</h1>
          <p className="gmo-hero-meta">
            {profile.region ?? ''} · 평점 {rating} ({rc} 리뷰)
          </p>
          <p className="gmo-hero-quote">{quote}</p>
          <p className="gmo-hero-hint">클릭하면 소개·피드·후기를 볼 수 있어요</p>
        </div>
      </header>

      <div className="gmo-main-flow">
        <section className="gmo-price-panel" aria-labelledby="gmo-price-title">
          <h2 id="gmo-price-title">직접 만나기 (동행)</h2>
          <p className="gmo-price-lead">
            가이드가 마이페이지에서 설정한 <strong>종일 패키지</strong> 요금입니다. (시간당 요금 × {FULL_DAY_HOURS}시간)
          </p>
          <div className="gmo-price-highlight">
            <span className="gmo-price-label">동행 패키지</span>
            <strong className="gmo-price-value">
              {accompanyPackageWon > 0 ? formatKrw(accompanyPackageWon) : '요금 미설정'}
            </strong>
          </div>
          <div className="gmo-total gmo-total--single">
            <span>결제 금액</span>
            <strong>{accompanyPackageWon > 0 ? formatKrw(accompanyPackageWon) : '—'}</strong>
          </div>

          <button
            type="button"
            className="gmo-pay"
            disabled={paying || requestId == null || !courseReadyForPayment || accompanyPackageWon <= 0}
            onClick={() => void onPay()}
          >
            {paying ? '처리 중…' : !courseReadyForPayment ? '가이드 코스 작성 대기중' : accompanyPackageWon <= 0 ? '요금 설정 대기' : '결제하고 코스 열기'}
          </button>
          {!courseReadyForPayment && (
            <p className="gmo-course-gate">가이드가 코스를 작성하면 결제 버튼이 활성화됩니다.</p>
          )}
          {courseReadyForPayment && accompanyPackageWon <= 0 && (
            <p className="gmo-course-gate">가이드가 가이드 비용(종일 패키지)을 저장해야 결제할 수 있어요.</p>
          )}
          {payErr && <p className="gmo-err">{payErr}</p>}
        </section>

        <section className="gmo-preview" aria-labelledby="gmo-preview-title">
          <h2 id="gmo-preview-title">추천 코스 미리보기</h2>
          <div className="gmo-map">
            <div ref={previewMapRef} className="gmo-map-canvas" />
            <div className="gmo-map-overlay">
              <div className="gmo-lock" aria-hidden>
                <span className="gmo-lock-glyph" />
              </div>
              <p className="gmo-map-title">매칭 후 상세 코스가 공개됩니다</p>
              <p className="gmo-map-sub">결제 및 매칭을 완료하고 나만의 비밀 지도를 확인하세요.</p>
            </div>
          </div>
        </section>
      </div>

      {hasPreview && (
        <section className="gmo-half-preview" aria-label="코스 텍스트 미리보기">
          <p className="gmo-half-title">가이드가 작성한 코스 (시간·설명)</p>
          <ul className="gmo-spot-preview-list">
            {previewSpots.map((spot, idx) => (
              <li key={`${idx + 1}-${spot.time}-${spot.desc.slice(0, 12)}`} className="gmo-spot-preview-item">
                <span className="gmo-spot-preview-num">{idx + 1}</span>
                <span className="gmo-spot-preview-name">로컬 스팟</span>
                {spot.time && <span className="gmo-spot-preview-time">{spot.time}</span>}
                <span className="gmo-spot-preview-desc">{spot.desc}</span>
              </li>
            ))}
          </ul>

          {previewHint && <p className="gmo-half-visible">가이드 메모: {previewHint}</p>}
          <div className="gmo-pay-hint-inline">
            <p className="gmo-half-foot">장소명은 투어 완료 후 공개됩니다.</p>
          </div>
          <div className="gmo-pay-hint-inline">
            <button type="button" className="gmo-pay-hint-btn" onClick={() => void onPay()}>
              결제 진행하기
            </button>
          </div>
        </section>
      )}
      {previewSpots.length === 0 && (
        <section className="gmo-course-pending" aria-live="polite">
          <span className="gmo-course-pending-dot" aria-hidden />
          <p className="gmo-course-pending-title">가이드가 코스를 작성 중이에요</p>
          <p className="gmo-course-pending-sub">조금만 기다려 주세요. 작성이 완료되면 일부 코스가 여기에 먼저 공개됩니다.</p>
        </section>
      )}

      {profileModalOpen && (
        <div
          className="gmo-profile-modal"
          role="presentation"
          onMouseDown={(e) => {
            if (e.target === e.currentTarget) setProfileModalOpen(false)
          }}
        >
          <div
            className="gmo-profile-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="gmo-profile-dialog-title"
            onMouseDown={(e) => e.stopPropagation()}
          >
            <div className="gmo-profile-dialog-head">
              <h2 id="gmo-profile-dialog-title" className="gmo-profile-dialog-title">
                {profile.nickname ?? '가이드'}
              </h2>
              <button type="button" className="gmo-profile-dialog-close" onClick={() => setProfileModalOpen(false)}>
                닫기
              </button>
            </div>
            <p className="gmo-profile-dialog-meta">
              {profile.region ?? ''} · 평점 {rating} ({rc} 리뷰)
            </p>
            {modalTags.length > 0 && (
              <div className="gmo-profile-dialog-tags">
                {modalTags.map((t) => (
                  <span key={t} className="gmo-profile-dialog-tag">
                    {t}
                  </span>
                ))}
              </div>
            )}

            <section className="gmo-profile-dialog-section" aria-labelledby="gmo-modal-intro">
              <h3 id="gmo-modal-intro" className="gmo-profile-dialog-h3">
                소개
              </h3>
              <div className="gmo-profile-dialog-intro-board">
                <span className="gmo-profile-dialog-clip" aria-hidden>
                  <svg viewBox="0 0 24 24" width="20" height="20" focusable="false" aria-hidden>
                    <path
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="1.7"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      d="M8.2 5.2v12.6a3.8 3.8 0 1 0 7.6 0V6.4a2.2 2.2 0 1 0-4.4 0v11.2"
                    />
                  </svg>
                </span>
                {profile.bio && String(profile.bio).trim() ? (
                  <p className="gmo-profile-dialog-prose gmo-profile-dialog-bio-inner">{String(profile.bio).trim()}</p>
                ) : (
                  <p className="gmo-profile-dialog-muted gmo-profile-dialog-bio-inner">등록된 한 줄 소개가 없어요.</p>
                )}
                {modalStoryBlocks.length > 0 && (
                  <dl className="gmo-profile-dialog-dl" style={{ marginTop: '0.85rem' }}>
                    {modalStoryBlocks.map((row) => (
                      <div key={row.label}>
                        <dt>{row.label}</dt>
                        <dd>{row.text}</dd>
                      </div>
                    ))}
                  </dl>
                )}
              </div>
            </section>

            {modalCareers.length > 0 ? (
              <section className="gmo-profile-dialog-section" aria-labelledby="gmo-modal-career">
                <h3 id="gmo-modal-career" className="gmo-profile-dialog-h3">
                  경력 · 자격
                </h3>
                <ul className="gmo-profile-dialog-careers">
                  {modalCareers.map((c) => {
                    const title = c?.title != null && String(c.title).trim() !== '' ? String(c.title).trim() : '경력'
                    const acq = c?.acquiredAt
                    const acqText =
                      acq != null && acq !== ''
                        ? String(acq).length >= 10
                          ? String(acq).slice(0, 10)
                          : String(acq)
                        : ''
                    const desc = c?.description && String(c.description).trim() ? String(c.description).trim() : ''
                    return (
                      <li key={c.careerId ?? title} className="gmo-profile-dialog-career">
                        <div className="gmo-profile-dialog-career-title">{title}</div>
                        {acqText ? <div className="gmo-profile-dialog-career-date">{acqText}</div> : null}
                        {desc ? <p className="gmo-profile-dialog-career-desc">{desc}</p> : null}
                      </li>
                    )
                  })}
                </ul>
              </section>
            ) : null}

            <section className="gmo-profile-dialog-section" aria-labelledby="gmo-modal-feeds">
              <h3 id="gmo-modal-feeds" className="gmo-profile-dialog-h3">
                피드
              </h3>
              {modalFeeds.length === 0 ? (
                <p className="gmo-profile-dialog-muted">등록된 피드가 없어요.</p>
              ) : (
                <div className="gmo-profile-dialog-feeds">
                  {modalFeeds.map((f) => {
                    const href = `/guides/${guideId}/feeds/${f.feedId}`
                    const bg = feedPrimaryImage(f)
                    return (
                      <Link key={f.feedId} to={href} className="gmo-profile-dialog-feed" onClick={() => setProfileModalOpen(false)}>
                        <div
                          className="gmo-profile-dialog-feed-img"
                          style={bg ? { backgroundImage: `url(${bg})` } : undefined}
                        />
                        <div className="gmo-profile-dialog-feed-body">
                          <span className="gmo-profile-dialog-feed-title">{feedCardTitle(f.content)}</span>
                          <span className="gmo-profile-dialog-feed-snippet">{feedSnippet(f.content)}</span>
                        </div>
                      </Link>
                    )
                  })}
                </div>
              )}
            </section>

            <section className="gmo-profile-dialog-section" aria-labelledby="gmo-modal-reviews">
              <h3 id="gmo-modal-reviews" className="gmo-profile-dialog-h3">
                후기
              </h3>
              {modalReviewsLoading ? (
                <p className="gmo-profile-dialog-muted">불러오는 중…</p>
              ) : modalReviewsErr ? (
                <p className="gmo-profile-dialog-muted">{modalReviewsErr}</p>
              ) : modalReviews.length === 0 ? (
                <p className="gmo-profile-dialog-muted">아직 등록된 후기가 없어요.</p>
              ) : (
                <ReviewCarousel reviews={modalReviews} variant="dialog" className="gmo-profile-review-carousel" />
              )}
            </section>

            <p className="gmo-profile-dialog-footer">
              <Link to={`/guides/${guideId}`} onClick={() => setProfileModalOpen(false)}>
                전체 프로필 페이지로 이동 →
              </Link>
            </p>
          </div>
        </div>
      )}
    </div>
  )
}
