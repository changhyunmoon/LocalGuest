export function loadTravelDna() {
  try {
    const raw = localStorage.getItem('localguest_travel_dna')
    if (!raw) return null
    const parsed = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object') return null
    return {
      moments: Array.isArray(parsed.moments) ? parsed.moments.filter(Boolean).map(String) : [],
      pace: parsed.pace ? String(parsed.pace) : '',
      travelWith: parsed.travelWith ? String(parsed.travelWith) : '',
      expect: parsed.expect ? String(parsed.expect) : '',
    }
  } catch {
    return null
  }
}

export function buildTravelDnaPreview(dna) {
  if (!dna) return ''
  const parts = []
  if (dna.moments?.length) parts.push(`${dna.moments.join(' · ')}을 원하는`)
  if (dna.pace) parts.push(`${dna.pace} 여행을 즐기는`)
  if (dna.travelWith) parts.push(`${dna.travelWith} 떠나는`)
  if (dna.expect) parts.push(`${dna.expect}을 기대하는`)
  return parts.length ? `${parts.join(', ')} 여행자` : ''
}

export function extractTravelDnaTags(dna) {
  if (!dna) return []
  const raw = [
    ...(Array.isArray(dna.moments) ? dna.moments : []),
    dna.pace,
    dna.travelWith,
    dna.expect,
  ]
  const uniq = new Set()
  for (const item of raw) {
    const t = String(item ?? '').trim()
    if (!t) continue
    uniq.add(t)
  }
  return Array.from(uniq)
}
