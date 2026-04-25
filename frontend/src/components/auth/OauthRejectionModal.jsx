import { useNavigate } from 'react-router-dom'

import { getOauthRejectionMessage, getOauthRejectionTitle } from '../../lib/oauthRejection.js'

/**
 * @param {{ reason: string; onClose: () => void; zIndex?: number }} props
 */
export function OauthRejectionModal({ reason, onClose, zIndex = 50 }) {
  const navigate = useNavigate()

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(15, 23, 42, 0.45)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex,
        padding: '1rem',
      }}
      role="dialog"
      aria-modal="true"
      aria-labelledby="oauth-reject-title"
    >
      <div
        className="form-card"
        style={{ maxWidth: 440, width: '100%', boxShadow: '0 25px 50px -12px rgba(0,0,0,0.35)' }}
      >
        <h2 id="oauth-reject-title" style={{ marginTop: 0 }}>
          {getOauthRejectionTitle(reason)}
        </h2>
        <p className="form-hint" style={{ marginBottom: '1rem' }}>
          {getOauthRejectionMessage(reason)}
        </p>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
          {(reason === 'guide_oauth_not_member' || reason === 'guest_oauth_not_registered') && (
            <button
              type="button"
              className="submit"
              onClick={() => {
                onClose()
                navigate('/auth/signup', { replace: true })
              }}
            >
              회원가입
            </button>
          )}
          {reason === 'guide_oauth_not_registered' && (
            <button
              type="button"
              className="submit"
              onClick={() => {
                onClose()
                navigate('/guide/register', { replace: true })
              }}
            >
              가이드 등록
            </button>
          )}
          <button
            type="button"
            className="submit ghost"
            onClick={() => {
              onClose()
            }}
          >
            닫기
          </button>
        </div>
      </div>
    </div>
  )
}
