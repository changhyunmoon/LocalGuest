import './PageStates.css'

/**
 * @param {{ label?: string, className?: string }} props
 */
export function PageLoading({ label = '불러오는 중…', className = '' }) {
  return (
    <div
      className={`page-state--loading ${className}`.trim()}
      role="status"
      aria-live="polite"
      aria-busy="true"
    >
      <span className="page-state-spinner" aria-hidden />
      <span className="page-state-label">{label}</span>
    </div>
  )
}

/**
 * @param {{ message: string, title?: string, onRetry?: () => void, retryLabel?: string, children?: import('react').ReactNode, className?: string }} props
 */
export function PageError({ message, title, onRetry, retryLabel = '다시 시도', children, className = '' }) {
  return (
    <div className={`page-state--error ${className}`.trim()} role="alert">
      {title ? <p className="page-state-title">{title}</p> : null}
      <p className="page-state-msg">{message}</p>
      {onRetry ? (
        <button type="button" className="page-state-retry" onClick={onRetry}>
          {retryLabel}
        </button>
      ) : null}
      {children ? <div className="page-state-footer">{children}</div> : null}
    </div>
  )
}

/**
 * @param {{ title?: string, children: import('react').ReactNode, className?: string }} props
 */
export function PageEmpty({ title, children, className = '' }) {
  return (
    <div className={`page-state--empty ${className}`.trim()}>
      {title ? <p className="page-state-empty-title">{title}</p> : null}
      <div className="page-state-empty-body">{children}</div>
    </div>
  )
}
