/**
 * Vite 개발 서버에서만 API·구현 힌트를 표시한다. 프로덕션 빌드에서는 렌더하지 않음.
 * @param {{ children: import('react').ReactNode, className?: string }} props
 */
export function MypageDevHint({ children, className = '' }) {
  if (!import.meta.env.DEV) return null
  return <p className={['mp-dev-hint', className].filter(Boolean).join(' ')}>{children}</p>
}
