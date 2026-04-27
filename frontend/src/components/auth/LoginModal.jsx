import { useEffect } from 'react'
import { createPortal } from 'react-dom'

import { LoginFormPanel } from './LoginFormPanel.jsx'

import './LoginModal.css'

/**
 * @param {{
 *   open: boolean
 *   onClose: () => void
 *   returnTo: string
 *   hint?: string
 *   preferredRole?: string
 * }} props
 */
export function LoginModal({ open, onClose, returnTo, hint = '', preferredRole = '' }) {
  useEffect(() => {
    if (!open) return
    const onKey = (e) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])

  useEffect(() => {
    if (!open) return
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = prev
    }
  }, [open])

  if (!open || typeof document === 'undefined') return null

  return createPortal(
    <div className="login-modal" role="presentation">
      <button type="button" className="login-modal__backdrop" aria-label="닫기" onClick={onClose} />
      <div
        className="login-modal__panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="login-modal-heading"
      >
        <div className="login-modal__head">
          <h2 id="login-modal-heading" className="login-modal__title">
            로그인
          </h2>
          <button type="button" className="login-modal__close" onClick={onClose} aria-label="닫기">
            ×
          </button>
        </div>
        <div className="login-modal__body">
          <LoginFormPanel
            returnTo={returnTo}
            hint={hint}
            preferredRole={preferredRole}
            showTitle={false}
            onEmailLoginSuccess={onClose}
            roleMismatchZIndex={10050}
          />
        </div>
      </div>
    </div>,
    document.body,
  )
}
