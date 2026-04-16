/**
 * PUT /guides/{guideId} 요청 본문 (UpdateGuideProfileRequest 계약).
 * @param {Record<string, unknown>} profile — GET GuideProfileResponse 형태
 */
export function buildGuidePutBody(profile) {
  if (!profile) {
    throw new Error('프로필이 없습니다.')
  }
  const hourly = profile.pricePerHour != null ? Number(profile.pricePerHour) : 0
  return {
    nickname: String(profile.nickname ?? '').trim(),
    profileImage: profile.profileImage ? String(profile.profileImage) : undefined,
    bio: profile.bio != null && profile.bio !== '' ? String(profile.bio) : undefined,
    region: String(profile.region ?? '').trim(),
    language: String(profile.language ?? '').trim(),
    pricePerHour: Number.isFinite(hourly) && hourly > 0 ? hourly : 0,
    isActive: profile.isActive !== false,
    residenceYears:
      profile.residenceYears != null && profile.residenceYears !== ''
        ? Number(profile.residenceYears)
        : undefined,
    localStory: profile.localStory != null && profile.localStory !== '' ? String(profile.localStory) : undefined,
    keywords: profile.keywords != null && profile.keywords !== '' ? String(profile.keywords) : undefined,
    defaultCourse:
      profile.defaultCourse != null && profile.defaultCourse !== '' ? String(profile.defaultCourse) : undefined,
    guideStyle: profile.guideStyle != null && profile.guideStyle !== '' ? String(profile.guideStyle) : undefined,
  }
}
