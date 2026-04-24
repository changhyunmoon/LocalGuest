/**
 * 백엔드 OAuth2 실패 `reason` / 로그인 모달 문구.
 * Strict Mode 대비: `reason`을 sessionStorage에 잠시 보관해 모달이 유지되게 함.
 */
export const OAUTH_REJECTION_SESSION_KEY = 'localguest_oauth_rejection_reason'

export const OAUTH_REJECTION_MSG = {
  guide_oauth_not_member:
    '이 이메일로 등록된 회원(여행자)이 없어요. 먼저 동일 이메일로 회원가입을 완료한 뒤 가이드로 로그인해 주세요.',
  guide_oauth_not_registered:
    '가이드로 등록·승인이 완료되지 않은 계정이에요. 가이드 등록(프로필)을 끝낸 뒤 다시 시도해 주세요.',
  guest_oauth_not_registered:
    '이 이메일로 서비스에 (여행자) 회원가입이 되어 있지 않아요. 먼저 회원가입을 완료한 뒤 구글로 로그인해 주세요.',
  oauth_role_missing:
    '로그인에 필요한 정보(여행자/가이드 선택)를 서버에 전달하지 못했어요. Redis(배포)와 동일한 도메인으로 OAuth를 시작하는지, 그다음 다시 시도해 주세요.',
  oauth_member_inactive: '이 계정은 탈퇴·비활성 상태로 로그인할 수 없어요. 고객센터에 문의해 주세요.',
  invalid_user: '이메일 정보를 Google에서 가져올 수 없어 로그인을 완료할 수 없어요. Google 계정에 이메일이 공개되어 있는지 확인해 주세요.',
  oauth_failed: '구글 인증이 완료되지 않았거나 조건이 맞지 않아요. 잠시 후 다시 시도해 주세요.',
}

/**
 * @param {string} [code]
 * @returns {string}
 */
export function getOauthRejectionMessage(code) {
  if (!code) return OAUTH_REJECTION_MSG.oauth_failed
  return OAUTH_REJECTION_MSG[code] ?? OAUTH_REJECTION_MSG.oauth_failed
}

/**
 * @param {string} [code]
 * @returns {string}
 */
export function getOauthRejectionTitle(code) {
  if (code === 'guest_oauth_not_registered' || code === 'invalid_user') {
    return '구글 로그인을 진행할 수 없어요'
  }
  if (code === 'oauth_member_inactive') {
    return '이 계정으로는 로그인할 수 없어요'
  }
  if (code === 'oauth_role_missing') {
    return '로그인 정보가 만료되었어요'
  }
  if (code === 'guide_oauth_not_member' || code === 'guide_oauth_not_registered') {
    return '가이드로 로그인할 수 없어요'
  }
  return '구글 로그인에 문제가 있어요'
}

/** @returns {string | null} */
export function getStoredOauthRejection() {
  try {
    return sessionStorage.getItem(OAUTH_REJECTION_SESSION_KEY)
  } catch {
    return null
  }
}

/** @param {string} reason */
export function setStoredOauthRejection(reason) {
  try {
    sessionStorage.setItem(OAUTH_REJECTION_SESSION_KEY, reason)
  } catch {
    /* ignore */
  }
}

export function clearStoredOauthRejection() {
  try {
    sessionStorage.removeItem(OAUTH_REJECTION_SESSION_KEY)
  } catch {
    /* ignore */
  }
}
