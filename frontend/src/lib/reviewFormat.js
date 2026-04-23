/**
 * @param {string | undefined} iso
 * @returns {string}
 */
export function formatReviewDate(iso) {
  if (iso == null || iso === '') return ''
  try {
    return new Date(iso).toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })
  } catch {
    return ''
  }
}
