import { useCallback, useEffect, useRef, useState } from 'react'

import { formatReviewDate } from '../lib/reviewFormat.js'
import { reviewStableKey } from '../lib/reviewPage.js'

import './ReviewCarousel.css'

/**
 * @param {Object} p
 * @param {Array<Record<string, unknown>>} p.reviews
 * @param {'default' | 'dialog'} [p.variant]
 * @param {string} [p.className]
 */
export function ReviewCarousel({ reviews, variant = 'default', className = '' }) {
  const n = Array.isArray(reviews) ? reviews.length : 0
  const [i, setI] = useState(0)
  const touch = { x0: 0, y0: 0, active: false }

  useEffect(() => {
    setI(0)
  }, [n])

  const go = useCallback(
    (dir) => {
      if (n <= 1) return
      setI((prev) => {
        const next = prev + dir
        if (next < 0) return n - 1
        if (next >= n) return 0
        return next
      })
    },
    [n],
  )

  const onKeyDown = useCallback(
    (e) => {
      if (e.key === 'ArrowLeft') {
        e.preventDefault()
        go(-1)
      } else if (e.key === 'ArrowRight') {
        e.preventDefault()
        go(1)
      }
    },
    [go],
  )

  if (!n) return null

  const vmod = `rev-carousel--${variant === 'dialog' ? 'dialog' : 'default'}`
  const showNav = n > 1

  return (
    <div
      className={`rev-carousel ${vmod} ${className}`.trim()}
      onKeyDown={onKeyDown}
      tabIndex={0}
      role="region"
      aria-label="가이드 후기"
    >
      {showNav && (
        <div className="rev-carousel-chrome" aria-hidden={false}>
          <button type="button" className="rev-carousel-btn rev-carousel-btn--prev" onClick={() => go(-1)} aria-label="이전 후기">
            ‹
          </button>
          <span className="rev-carousel-counter" aria-live="polite">
            {i + 1} / {n}
          </span>
          <button type="button" className="rev-carousel-btn rev-carousel-btn--next" onClick={() => go(1)} aria-label="다음 후기">
            ›
          </button>
        </div>
      )}

      <div
        className="rev-carousel-viewport"
        onTouchStart={(e) => {
          if (!showNav || e.touches.length !== 1) return
          touchRef.current.x0 = e.touches[0].clientX
          touchRef.current.y0 = e.touches[0].clientY
          touchRef.current.active = true
        }}
        onTouchEnd={(e) => {
          if (!showNav || !touchRef.current.active) return
          touchRef.current.active = false
          const t = e.changedTouches[0]
          const dx = t.clientX - touchRef.current.x0
          const dy = t.clientY - touchRef.current.y0
          if (Math.abs(dx) < 48 || Math.abs(dx) < Math.abs(dy) * 1.2) return
          if (dx < 0) go(1)
          else go(-1)
        }}
      >
        <ul
          className="rev-carousel-track"
          style={{
            width: `${n * 100}%`,
            transform: `translateX(-${(i * 100) / n}%)`,
          }}
        >
          {reviews.map((r, idx) => (
            <li
              key={reviewStableKey(/** @type {Record<string, unknown>} */ (r), idx)}
              className="rev-carousel-slide"
              style={{ width: `${100 / n}%` }}
              aria-hidden={!showNav ? false : idx !== i}
            >
              <ReviewSlide r={/** @type {Record<string, unknown>} */ (r)} />
            </li>
          ))}
        </ul>
      </div>

      {showNav && (
        <div className="rev-carousel-dots" role="tablist" aria-label="후기 번호">
          {reviews.map((r, idx) => (
            <button
              key={reviewStableKey(/** @type {Record<string, unknown>} */ (r), idx)}
              type="button"
              role="tab"
              className="rev-carousel-dot"
              data-on={idx === i ? '1' : '0'}
              aria-selected={idx === i}
              aria-label={`${idx + 1}번 후기로 이동`}
              onClick={() => setI(idx)}
            />
          ))}
        </div>
      )}
    </div>
  )
}

/**
 * @param {Object} p
 * @param {Record<string, unknown>} p.r
 */
function ReviewSlide({ r }) {
  const nick = r.writeNickname != null && String(r.writeNickname).trim() !== '' ? String(r.writeNickname).trim() : '여행자'
  const ratingN = Math.min(5, Math.max(0, Math.round(Number(r.rating) || 0)))
  const body = r.content != null && String(r.content).trim() !== '' ? String(r.content).trim() : '내용 없음'
  const d = formatReviewDate(/** @type {string|undefined} */ (r.createdAt))
  return (
    <div className="rev-slide">
      <div className="rev-slide-head">
        <span className="rev-slide-name">{nick}</span>
        <span className="rev-slide-stars" aria-label={`${ratingN}점`}>
          {ratingN > 0 ? '★'.repeat(ratingN) : '·'}
        </span>
        {d ? <span className="rev-slide-when">{d}</span> : null}
      </div>
      <p className="rev-slide-text">{body}</p>
    </div>
  )
}
