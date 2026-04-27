import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams, useLocation } from 'react-router-dom'

import { apiRequest } from '../api/client'

import './PaymentKakaoStubPage.css'

function formatWon(n) {
  const v = Number(n)
  if (Number.isNaN(v)) return '—'
  return `${v.toLocaleString('ko-KR')}원`
}

function parseApiError(text) {
  if (!text) return '요청 실패'
  try {
    const j = JSON.parse(text)
    return (j.message ?? text) || '요청 실패'
  } catch {
    return text || '요청 실패'
  }
}

export function PaymentKakaoStubPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const location = useLocation()
  const externalRedirect = location.state?.externalRedirect

  const paymentId = searchParams.get('paymentId') ?? ''
  const amount = searchParams.get('amount') ?? ''
  const pgOrderNo = searchParams.get('pgOrderNo') ?? ''
  const pgToken = searchParams.get('pgToken') ?? ''
  const paymentType = searchParams.get('paymentType') ?? ''
  const requestId = searchParams.get('requestId') ?? ''
  const guideId = searchParams.get('guideId') ?? ''
  const result = searchParams.get('result') ?? ''
  const qpError = searchParams.get('error') ?? ''

  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')
  /** @type {'idle' | 'need_login'} */
  const [authGate, setAuthGate] = useState('idle')

  const autoConfirmStarted = useRef(false)

  const itemLabel = useMemo(() => {
    const t = String(paymentType).toUpperCase()
    if (t === 'CHAT') return '정보만 받기 (채팅)'
    if (t === 'ACCOMPANY') return '직접 만나기 (동행)'
    if (t === 'EXTENSION') return '투어 하루 연장'
    return 'LocalGuest 결제'
  }, [paymentType])

  const valid = pgOrderNo.trim() !== '' && amount !== '' && !Number.isNaN(Number(amount))

  const loginReturnState = useMemo(
    () => ({
      returnTo: `${location.pathname}${location.search || ''}`,
      hint: '결제를 마치려면 로그인이 필요합니다. 로그인하면 이 화면으로 다시 돌아옵니다.',
      preferredRole: 'GUEST',
    }),
    [location.pathname, location.search],
  )

  const navigateAfterPaid = useCallback(() => {
    if (guideId) {
      const qs = new URLSearchParams()
      if (requestId) qs.set('requestId', requestId)
      if (paymentId) qs.set('paymentId', paymentId)
      navigate(`/guides/${guideId}/match/complete?${qs.toString()}`, { replace: true })
      return
    }
    navigate('/mypage/payments', {
      replace: true,
      state: { hint: '결제가 완료되었습니다. 아래에서 영수증·상태를 확인할 수 있어요.' },
    })
  }, [guideId, requestId, paymentId, navigate])

  const runConfirm = useCallback(async () => {
    setErr('')
    if (!valid) {
      setErr('결제 정보가 없습니다. 매칭 화면에서 다시 결제를 시도해 주세요.')
      return
    }
    setBusy(true)
    try {
      const res = await apiRequest('/matching/payments/confirm', {
        method: 'POST',
        json: {
          pgOrderNo,
          amount: Number(amount),
          ...(pgToken ? { pgToken } : {}),
        },
      })
      const t = await res.text()
      if (res.status === 401) {
        setAuthGate('need_login')
        return
      }
      if (!res.ok) {
        const msg = parseApiError(t)
        if (msg.includes('이미 완료') || msg.includes('PAYMENT_ALREADY_COMPLETED')) {
          navigateAfterPaid()
          return
        }
        throw new Error(msg)
      }
      navigateAfterPaid()
    } catch (e) {
      setErr(e instanceof Error ? e.message : '오류')
    } finally {
      setBusy(false)
    }
  }, [valid, pgOrderNo, amount, pgToken, navigateAfterPaid])

  /** 카카오·백엔드 리다이렉트로 ?result=success 복귀 시 자동 승인 (전체 페이지 이동으로 state 소실 대비) */
  useEffect(() => {
    if (result !== 'success') return
    if (!valid) return
    if (autoConfirmStarted.current) return
    autoConfirmStarted.current = true
    void runConfirm()
  }, [result, valid, runConfirm])

  const goExternal = () => {
    if (externalRedirect) {
      window.location.href = String(externalRedirect)
    }
  }

  const onStubPay = async () => {
    await runConfirm()
  }

  const cameFromKakao = Boolean(pgToken) || result === 'success' || result === 'cancel' || result === 'fail'

  const showStubActions =
    authGate !== 'need_login' && result !== 'cancel' && result !== 'fail' && qpError !== 'missing_pgOrderNo'

  return (
    <div className="pks">
      <div className="pks-card">
        <header className="pks-head">
          <p className="pks-brand">
            kakao<span>pay</span>
          </p>
          <p className="pks-sub">
            {result === 'success' && busy
              ? '결제 승인 처리 중…'
              : externalRedirect
                ? '카카오페이로 이동해 결제를 완료해 주세요'
                : '테스트 · Stub 결제 화면 (로컬 PG)'}
          </p>
        </header>
        <div className="pks-body">
          {qpError === 'missing_pgOrderNo' && (
            <div className="pks-banner pks-banner--warn" role="status">
              <strong>주문 정보가 누락됐습니다.</strong>
              <p>카카오페이 복귀 URL에 주문번호가 없습니다. 매칭 화면에서 결제를 다시 시도하거나, 결제 내역을 확인해 주세요.</p>
              <div className="pks-inline-links">
                <Link to="/mypage/payments">결제 내역</Link>
                <Link to="/mypage/tour">연장·환불</Link>
              </div>
            </div>
          )}

          {result === 'cancel' && (
            <div className="pks-banner pks-banner--muted" role="status">
              <strong>결제를 취소했습니다.</strong>
              <p>카카오페이 창에서 취소한 경우입니다. 필요하면 매칭 화면에서 다시 시도할 수 있어요.</p>
              <div className="pks-inline-links">
                {guideId ? (
                  <Link to={`/guides/${guideId}/match`} state={requestId ? { requestId: Number(requestId) } : undefined}>
                    매칭·결제로 돌아가기
                  </Link>
                ) : null}
                <Link to="/mypage/payments">결제 내역</Link>
              </div>
            </div>
          )}

          {result === 'fail' && (
            <div className="pks-banner pks-banner--warn" role="status">
              <strong>결제에 실패했습니다.</strong>
              <p>카드 한도·앱 설정 등을 확인한 뒤 다시 시도해 주세요.</p>
              <div className="pks-inline-links">
                {guideId ? (
                  <Link to={`/guides/${guideId}/match`} state={requestId ? { requestId: Number(requestId) } : undefined}>
                    다시 결제하기
                  </Link>
                ) : null}
                <Link to="/mypage/payments">결제 내역</Link>
              </div>
            </div>
          )}

          {authGate === 'need_login' && (
            <div className="pks-banner pks-banner--warn" role="alert">
              <strong>로그인이 필요합니다.</strong>
              <p>외부 결제 창에서 돌아온 뒤에는 브라우저가 새로고침되어 로그인 상태가 끊길 수 있어요. 다시 로그인하면 이 결제 화면으로 돌아옵니다.</p>
              <Link className="pks-btn pks-btn--pay pks-btn--block" to="/auth/login" state={loginReturnState}>
                로그인하고 계속
              </Link>
            </div>
          )}

          {showStubActions && (
            <>
              <p className="pks-merchant">LocalGuest</p>
              <p className="pks-item">{itemLabel}</p>
              <p className="pks-amount-label">결제금액</p>
              <p className="pks-amount">{formatWon(amount)}</p>
              {paymentId && (
                <p className="pks-meta">
                  paymentId: {paymentId}
                  <br />
                  주문번호: {pgOrderNo}
                  {pgToken ? (
                    <>
                      <br />
                      pg_token: 수신됨
                    </>
                  ) : null}
                </p>
              )}
              {result === 'success' && busy && (
                <p className="pks-banner pks-banner--info">PG에서 돌아온 뒤 서버에 결제를 확정하고 있습니다. 잠시만 기다려 주세요.</p>
              )}
              {err && <p className="pks-err">{err}</p>}
              <div className="pks-actions">
                {externalRedirect ? (
                  <button type="button" className="pks-btn pks-btn--pay" onClick={() => goExternal()}>
                    카카오페이 결제창으로 이동
                  </button>
                ) : (
                  <button
                    type="button"
                    className="pks-btn pks-btn--pay"
                    disabled={busy || !valid || result === 'success'}
                    onClick={() => void onStubPay()}
                  >
                    {busy ? '처리 중…' : '결제 확정하기'}
                  </button>
                )}
                <button
                  type="button"
                  className="pks-btn pks-btn--ghost"
                  disabled={busy}
                  onClick={() => {
                    if (guideId) {
                      navigate(`/guides/${guideId}/match`, { replace: true, state: { requestId: requestId || undefined } })
                      return
                    }
                    navigate('/mypage/payments', { replace: true })
                  }}
                >
                  취소하고 나가기
                </button>
              </div>
              {cameFromKakao && (
                <p className="pks-note">
                  카카오페이에서 돌아온 요청입니다. <code>result=success</code> 인 경우 서버 승인(<code>POST /matching/payments/confirm</code>)까지 자동으로 진행합니다.
                </p>
              )}
              {!cameFromKakao && externalRedirect && (
                <p className="pks-note">아래 버튼으로 카카오 결제창을 연 뒤 결제를 완료하면 이 화면으로 돌아옵니다.</p>
              )}
              {!cameFromKakao && !externalRedirect && (
                <p className="pks-note">
                  로컬 fake PG: 서버가 <code>matching.payment.provider=fake</code> 일 때 Stub 승인 API로 결제를 완료합니다.
                </p>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  )
}
