import './PaymentDetailPanel.css'

const PAYMENT_TYPE_LABEL = {
  CHAT: '채팅',
  ACCOMPANY: '동행',
  EXTENSION: '연장',
}

const KNOWN_KEYS = new Set([
  'paymentId',
  'requestId',
  'amount',
  'paymentType',
  'status',
  'pgOrderNo',
  'pgTransactionId',
  'redirectUrl',
  'paidAt',
  'refundDeadline',
])

function paymentStatusLabel(s) {
  const m = {
    PENDING: '결제 대기',
    COMPLETED: '결제완료',
    REFUNDED: '환불완료',
    FAILED: '실패',
    CANCELLED: '취소',
  }
  return m[s] ?? String(s ?? '—')
}

function formatWhen(iso) {
  if (!iso) return '—'
  try {
    const d = new Date(iso)
    if (Number.isNaN(d.getTime())) return String(iso)
    return d.toLocaleString('ko-KR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return String(iso)
  }
}

/**
 * @param {{ detail: Record<string, unknown> }} props
 */
export function PaymentDetailPanel({ detail }) {
  if (!detail || typeof detail !== 'object') {
    return <p className="mp-pay-detail-empty">표시할 데이터가 없습니다.</p>
  }

  const d = detail
  const paymentId = d.paymentId
  const requestId = d.requestId
  const amount = d.amount
  const paymentType = d.paymentType
  const status = d.status
  const pgOrderNo = d.pgOrderNo
  const pgTransactionId = d.pgTransactionId
  const redirectUrl = d.redirectUrl
  const paidAt = d.paidAt
  const refundDeadline = d.refundDeadline

  const typeKey = typeof paymentType === 'string' ? paymentType : ''
  const typeLabel = PAYMENT_TYPE_LABEL[typeKey] ? `${PAYMENT_TYPE_LABEL[typeKey]} (${typeKey})` : typeKey || '—'

  const extra = Object.entries(d).filter(([k]) => !KNOWN_KEYS.has(k))

  return (
    <div className="mp-pay-detail">
      <dl className="mp-pay-detail-dl">
        <div>
          <dt>결제 ID</dt>
          <dd>{paymentId != null ? String(paymentId) : '—'}</dd>
        </div>
        <div>
          <dt>매칭 요청 ID</dt>
          <dd>{requestId != null ? String(requestId) : '—'}</dd>
        </div>
        <div>
          <dt>금액</dt>
          <dd>{amount != null && !Number.isNaN(Number(amount)) ? `₩ ${Number(amount).toLocaleString('ko-KR')}` : '—'}</dd>
        </div>
        <div>
          <dt>결제 유형</dt>
          <dd>{typeLabel}</dd>
        </div>
        <div>
          <dt>상태</dt>
          <dd>{paymentStatusLabel(status)}</dd>
        </div>
        <div>
          <dt>PG 주문번호</dt>
          <dd className="mp-pay-detail-mono">{pgOrderNo != null && String(pgOrderNo).trim() ? String(pgOrderNo) : '—'}</dd>
        </div>
        <div>
          <dt>PG 거래 ID</dt>
          <dd className="mp-pay-detail-mono">{pgTransactionId != null && String(pgTransactionId).trim() ? String(pgTransactionId) : '—'}</dd>
        </div>
        <div>
          <dt>결제 완료 일시</dt>
          <dd>{formatWhen(paidAt)}</dd>
        </div>
        <div>
          <dt>환불 마감</dt>
          <dd>{formatWhen(refundDeadline)}</dd>
        </div>
        {redirectUrl != null && String(redirectUrl).trim() ? (
          <div className="mp-pay-detail-span">
            <dt>결제창 URL (준비)</dt>
            <dd className="mp-pay-detail-mono mp-pay-detail-break">{String(redirectUrl)}</dd>
          </div>
        ) : null}
      </dl>

      {extra.length > 0 ? (
        <details className="mp-pay-detail-extra">
          <summary>추가 필드 ({extra.length})</summary>
          <table className="mp-pay-detail-table">
            <tbody>
              {extra.map(([k, v]) => (
                <tr key={k}>
                  <th scope="row">{k}</th>
                  <td className="mp-pay-detail-mono">{v === null || v === undefined ? '—' : typeof v === 'object' ? JSON.stringify(v) : String(v)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </details>
      ) : null}
    </div>
  )
}
