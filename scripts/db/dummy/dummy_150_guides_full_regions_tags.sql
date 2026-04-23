-- =============================================================================
-- 가이드 더미 150건 (한 파일) — 같은 지역 다수 + 성향/태그 + 전체 URL 프로필 이미지
-- =============================================================================
-- - member: id 920001 ~ 920150 (ROLE=GUIDE), 비밀번호 평문 = password (BCrypt 아래)
-- - guide_profiles: id 820001 ~ 820150, member_id 동일 범위 매칭
-- - 지역: 부산 75명 + 서울 75명 (문자열 그대로 반복 → 목록/필터에서 동일 지역 다건 확인용)
-- - profile_image / member.profile_image_url: https 전체 URL (picsum seed, 800x800 고유)
-- - guide_style / keywords / default_course / local_story 채움
-- MySQL 8.0+ (INSERT … WITH RECURSIVE). PK 충돌 시 하단 DELETE 후 재실행.
-- =============================================================================

USE local_guide_db;

SET SESSION cte_max_recursion_depth = 200000;

-- 재시드 예시 (FK 순서에 맞게 조정)
-- SET FOREIGN_KEY_CHECKS = 0;
-- DELETE FROM guide_profiles WHERE id BETWEEN 820001 AND 820150;
-- DELETE FROM member WHERE id BETWEEN 920001 AND 920150;
-- SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO member (id, email, password, name, nickname, role, status, profile_image_url, created_at, updated_at)
WITH RECURSIVE seq(n) AS (
  SELECT 1 AS n
  UNION ALL
  SELECT n + 1 FROM seq WHERE n < 150
)
SELECT
  920000 + n,
  CONCAT('seed150_guide_', LPAD(CAST(n AS CHAR), 3, '0'), '@localguest.test'),
  '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG',
  CONCAT('로컬가이드멤버', CAST(n AS CHAR)),
  CONCAT('gm_seed_', CAST(n AS CHAR)),
  'GUIDE',
  'ACTIVE',
  CONCAT('https://picsum.photos/seed/localguest-m', LPAD(CAST(n AS CHAR), 3, '0'), '/800/800'),
  NOW(6),
  NOW(6)
FROM seq;

INSERT INTO guide_profiles (
  id,
  member_id,
  nickname,
  region,
  is_approved,
  is_active,
  review_count,
  average_rating,
  profile_image,
  bio,
  language,
  price_per_hour,
  residence_years,
  local_story,
  keywords,
  default_course,
  guide_style,
  created_at,
  updated_at
)
WITH RECURSIVE seq(n) AS (
  SELECT 1 AS n
  UNION ALL
  SELECT n + 1 FROM seq WHERE n < 150
)
SELECT
  820000 + n,
  920000 + n,
  CONCAT(
    IF(n <= 75, '부산로컬', '서울로컬'),
    '_',
    CAST(n AS CHAR)
  ),
  IF(n <= 75, '부산', '서울'),
  1,
  1,
  (n MOD 45),
  ROUND(3.2 + ((n MOD 18) / 10), 2),
  CONCAT('https://picsum.photos/seed/localguest-g', LPAD(CAST(n AS CHAR), 3, '0'), '/800/800'),
  CONCAT(
    IF(n <= 75, '부산', '서울'),
    ' 토박이로 ',
    CAST(5 + (n MOD 15) AS CHAR),
    '년째 살고 있어요. 동네만 아는 코스로 모십니다.'
  ),
  IF(n MOD 3 = 0, '한국어,영어', '한국어'),
  CAST(28000 + (n * 400) AS DECIMAL(10, 2)),
  2 + (n MOD 18),
  CONCAT(
    '처음 와도 길 잃지 않게 ',
    IF(n <= 75, '해운대·영도·서면', '한강·성수·익선'),
    ' 라인으로 잡아드려요. 제 취향은 조용한 로컬 카페입니다.'
  ),
  CONCAT(
    '#',
    IF(n <= 75, '부산', '서울'),
    ',#',
    ELT((n MOD 6) + 1, '맛집', '야경', '역사', '카페', '숨은명소', '감성산책'),
    ',#',
    ELT((n MOD 5) + 1, '가족추천', '사진도움', '힐링', '미식', '걷기약'),
    ',#로컬메이트'
  ),
  CONCAT(
    '1) 만남 장소 안내 → 2) ',
    ELT((n MOD 4) + 1, '대표 뷰포인트', '골목 시장', '야시장 먹거리', '전통찻집'),
    ' → 3) ',
    IF(n <= 75, '광안리·청사포', '북촌·익선'),
    ' 산책 (약 3시간)'
  ),
  ELT((n MOD 7) + 1, '감성 투어', '미식 큐레이션', '역사 산책', '야경 스팟', '사진 동행', '힐링 로컬', '가족 친화'),
  NOW(6),
  NOW(6)
FROM seq;

-- =============================================================================
-- (선택) 정산 API 검증 — payment COMPLETED 합산 시 250000 기대 (가이드 id=99)
-- 필요할 때만 주석 해제해 앞부분과 별도 실행
-- =============================================================================
/*
INSERT IGNORE INTO member (id, email, password, name, nickname, role, status, created_at, updated_at) VALUES
(99, 'settlement_seed_guest@localguest.test', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '정산시드게스트', 'settle_guest', 'GUEST', 'ACTIVE', NOW(6), NOW(6)),
(100, 'settlement_seed_guide@localguest.test', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '정산시드가이드', 'settle_guide', 'GUIDE', 'ACTIVE', NOW(6), NOW(6));

INSERT IGNORE INTO guide_profiles (id, member_id, nickname, region, is_approved, is_active, review_count, average_rating, profile_image, bio, language, price_per_hour, residence_years, created_at, updated_at) VALUES
(99, 100, '정산시드가이드', '서울', 1, 1, 0, 0.00, 'https://picsum.photos/seed/localguest-settle/800/800', '정산 API 검증용', '한국어', 40000.00, 5, NOW(6), NOW(6));

INSERT IGNORE INTO guide_schedules (id, guide_id, available_date, start_time, end_time, status, is_paid, created_at, updated_at) VALUES
(1099, 99, '2026-05-01', '09:00:00', '18:00:00', 'AVAILABLE', 0, NOW(6), NOW(6));

INSERT IGNORE INTO match_request (id, guest_id, guide_id, guide_schedule_id, destination, status, desired_date, desired_budget, created_at, updated_at) VALUES
(9901, 99, 99, 1099, '서울 야경 코스', 'COMPLETED', '2026-04-22', 200000, NOW(6), NOW(6));

INSERT IGNORE INTO payment (id, match_request_id, payer_id, amount, payment_type, pg_order_no, pg_transaction_id, status, paid_at, refund_deadline, created_at, updated_at) VALUES
(99001, 9901, 99, 200000, 'ACCOMPANY', 'PG-SEED-99001-ACCOMPANY', 'PG-TXN-99001', 'COMPLETED', NOW(6), DATE_ADD(NOW(6), INTERVAL 2 HOUR), NOW(6), NOW(6)),
(99002, 9901, 99, 50000, 'CHAT', 'PG-SEED-99001-CHAT', 'PG-TXN-99002', 'COMPLETED', NOW(6), DATE_ADD(NOW(6), INTERVAL 2 HOUR), NOW(6), NOW(6));
*/
