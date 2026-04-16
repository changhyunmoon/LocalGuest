import { useMemo, useState } from 'react'
import { useNavigate, useSearchParams, useLocation } from 'react-router-dom'

import { apiRequest } from '../api/client'

import './PaymentKakaoStubPage.css'

function formatWon(n) {
  const v = Number(n)
  if (Number.isNaN(v)) return '—'
  return `${v.toLocaleString('ko-KR')}원`
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

  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')

  const itemLabel = useMemo(() => {
    const t = String(paymentType).toUpperCase()
    if (t === 'CHAT') return '정보만 받기 (채팅)'
    if (t === 'ACCOMPANY') return '직접 만나기 (동행)'
    return 'LocalGuest 결제'
  }, [paymentType])

  const valid = pgOrderNo.trim() !== '' && amount !== '' && !Number.isNaN(Number(amount))

  const goExternal = () => {
    if (externalRedirect) {
      window.location.href = String(externalRedirect)
    }
  }

  const onStubPay = async () => {
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
      if (!res.ok) throw new Error(t || '결제 승인에 실패했습니다.')
      if (guideId) {
        const qs = new URLSearchParams()
        if (requestId) qs.set('requestId', requestId)
        if (paymentId) qs.set('paymentId', paymentId)
        navigate(`/guides/${guideId}/match/complete?${qs.toString()}`)
        return
      }
      navigate('/mypage/payments', { state: { hint: 'Stub 결제가 완료되었습니다.' } })
    } catch (e) {
      setErr(e instanceof Error ? e.message : '오류')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="pks">
      <div className="pks-card">
        <header className="pks-head">
          <p className="pks-brand">
            kakao<span>pay</span>
          </p>
          <p className="pks-sub">테스트 · Stub 결제 화면 (로컬 PG)</p>
        </header>
        <div className="pks-body">
          <p className="pks-merchant">LocalGuest</p>
          <p className="pks-item">{itemLabel}</p>
          <p className="pks-amount-label">결제금액</p>
          <p className="pks-amount">{formatWon(amount)}</p>
          {paymentId && (
            <p className="pks-meta">
              paymentId: {paymentId}
              <br />
              주문번호: {pgOrderNo}
            </p>
          )}
          {err && <p className="pks-err">{err}</p>}
          <div className="pks-actions">
            {externalRedirect ? (
              <button type="button" className="pks-btn pks-btn--pay" onClick={() => goExternal()}>
                카카오페이 결제창으로 이동
              </button>
            ) : (
              <button type="button" className="pks-btn pks-btn--pay" disabled={busy || !valid} onClick={() => void onStubPay()}>
                {busy ? '처리 중…' : '결제하기'}
              </button>
            )}
            <button
              type="button"
              className="pks-btn pks-btn--ghost"
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
          {!externalRedirect && (
            <p className="pks-note">
              실제 카카오페이가 아닙니다. 서버가 <code>matching.payment.provider=fake</code> 일 때 Stub 승인 API로
              결제를 완료합니다.
            </p>
          )}
          {externalRedirect && (
            <p className="pks-note">서버가 카카오 결제창 URL을 내려준 경우, 위 버튼으로 이동합니다.</p>
          )}
        </div>
      </div>
    </div>
  )
}
