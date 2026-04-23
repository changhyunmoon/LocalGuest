-- =============================================================================
-- LocalGuest MySQL 전역 시드 (JPA 엔티티 기준 테이블 빠짐없이)
-- =============================================================================
-- 스캔한 엔티티/테이블: member, guide_profiles, guide_schedules, guide_feeds,
--   guide_careers, guide_images, match_request, payment, refund, tour_extension,
--   review, scrapbooks, chat_rooms, chat_participants
--   (MongoDB chat_messages 등 비관계형 제외)
--
-- 비즈니스 조건
--   · 가이드 75: 1~25 서로 다른 지역 25곳 / 26~75 동일 25지역 순환 + 성향·키워드 3파 변주
--   · 게스트 50: 941001~941050, 성향 태그는 match_request.concept / concept_summary
--   · 여행 내역 match_request 210건 (기본 150 + 리뷰용 보강 60)
--   · 나머지 테이블은 위 FK에 맞춰 연관 데이터로 채움 (피드·경력·이미지·결제·환불·연장·리뷰·스크랩·채팅방)
--
-- 비밀번호 BCrypt = 평문 "LocalGuest1!" (로그인 통일) — node bcryptjs hashSync로 검증됨, Spring BCryptPasswordEncoder와 호환
-- 이미지 URL = https://picsum.photos/seed/.../800/800
-- MySQL 8.0+
-- ID 블록이 기존 데이터와 겹치면 DELETE 구간을 조정하세요.
--
-- -----------------------------------------------------------------------------
-- [이메일 주소 형식]
--   · 공통:  {역할}{두 자리 순번}@localGuest.com
--       - 역할 prefix: 가이드 = guide, 게스트 = guest (소문자).
--       - 순번: LPAD(숫자, 2, '0') → 01, 02, … 09, 10, … 75 / 50.
--       - 도메인: @ 뒤는 localGuest.com (첫 l 소문자, G 대문자 — DB·시드·프론트와 동일해야 로그인 매칭됨).
--   · 예: guide01@localGuest.com … guide75@localGuest.com,
--         guest01@localGuest.com … guest50@localGuest.com
--   · 채팅 시드: chat_rooms.owner_email = guideNN@…, chat_participants.user_email 동일 규칙.
-- -----------------------------------------------------------------------------
-- [DB에 쓰이는 시드 ID 번호 구간] — 아래 DELETE … BETWEEN … 과 동일 (충돌 시 구간 조정)
--   member(가이드)       940001 ~ 940075
--   guide_profiles       841001 ~ 841075
--   guide_schedules      851001 ~ 851075
--   member(게스트)       941001 ~ 941050
--   match_request          862001 ~ 862210 (150 기본 + 60 리뷰용 보강)
--   guide_feeds            871001 ~ 871075  (DELETE 는 ~871080 까지 여유)
--   guide_careers          872001 ~ 872075  (DELETE 는 ~872080)
--   guide_images           873001 ~ 873150  (가이드당 2행)
--   payment                880001 ~ 880550 (ACCOMPANY 880001~, CHAT 880301~)
--   refund                 881001 ~ 881005  (고정 VALUES)
--   tour_extension         882001 ~ (조건 만족 매칭 건수만큼, 상한 ~882040)
--   review                 INSERT 시 id 미지정 → AUTO_INCREMENT (DELETE 885001~885120 은 잔여 시드 정리용)
--   scrapbooks             id 자동 — match_request_id 로 유일 제약 충족
--   chat_rooms             883001 ~ 883025
--   chat_participants      884001 ~ 884050  (방당 가이드+게스트 2행)
-- -----------------------------------------------------------------------------
-- [이 SQL 파일에서의 “행(줄)” 번호 — 편집 시 밀리므로 참고용]
--   아래 숫자는 “이 주석 블록이 들어간 직후” 기준입니다. 정확한 위치는 에디터에서
--   `-- =========================` 로 섹션 제목을 검색하는 것이 가장 안전합니다.
--   62~84    : USE, 세션·FK 설정, DELETE (기존 시드 PK 구간 삭제), FK 복구
--   85~101   : 가이드 member INSERT
--   103~244  : 가이드 guide_profiles INSERT (VALUES 블록이 길어 행 수 많음)
--   246~261  : 가이드 guide_schedules
--   263~279  : 게스트 member
--   281~342  : match_request
--   344~358  : guide_feeds / 360~373 guide_careers / 375~402 guide_images(2개 INSERT)
--   404~470  : payment 두 블록 / 472~493 refund(+주석) / 495~523 tour_extension
--   525~540  : review / 542~556 scrapbooks / 558~605 chat_rooms·chat_participants(끝)
-- -----------------------------------------------------------------------------
-- =============================================================================

USE local_guide_db;

SET SESSION cte_max_recursion_depth = 400000;
SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM refund WHERE id BETWEEN 881001 AND 881020;
DELETE FROM payment WHERE id BETWEEN 880001 AND 880550;
DELETE FROM tour_extension WHERE id BETWEEN 882001 AND 882040;
DELETE FROM review WHERE id BETWEEN 885001 AND 885120;
DELETE FROM review WHERE match_request_id BETWEEN 862001 AND 862220;
DELETE FROM scrapbooks WHERE match_request_id BETWEEN 862001 AND 862220;
DELETE FROM chat_participants WHERE id BETWEEN 884001 AND 884080;
DELETE FROM chat_rooms WHERE id BETWEEN 883001 AND 883040;
DELETE FROM match_request WHERE id BETWEEN 862001 AND 862220;
DELETE FROM guide_feeds WHERE id BETWEEN 871001 AND 871080;
DELETE FROM guide_careers WHERE id BETWEEN 872001 AND 872080;
DELETE FROM guide_images WHERE id BETWEEN 873001 AND 873200;
DELETE FROM guide_schedules WHERE id BETWEEN 851001 AND 851075;
DELETE FROM guide_profiles WHERE id BETWEEN 841001 AND 841075;
DELETE FROM member WHERE id BETWEEN 941001 AND 941050;
DELETE FROM member WHERE id BETWEEN 940001 AND 940075;

SET FOREIGN_KEY_CHECKS = 1;

-- ========================= 가이드 회원 75 =========================
INSERT INTO member (id, email, password, name, nickname, role, status, profile_image_url, created_at, updated_at)
WITH RECURSIVE seq(n) AS (
  SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 75
)
SELECT
  940000 + n,
  CONCAT('guide', LPAD(CAST(n AS CHAR), 2, '0'), '@localGuest.com'),
  '$2a$10$dn9eoH411zNPuPvGZao1QONVd/vSQgkoHHu5l8JM4W.kmw4QelJrS',
  CONCAT('가이드시드', CAST(n AS CHAR)),
  CONCAT('gseed_', CAST(n AS CHAR)),
  'GUIDE',
  'ACTIVE',
  CONCAT('https://picsum.photos/seed/localguest-g75-', LPAD(CAST(n AS CHAR), 3, '0'), '/800/800'),
  NOW(6),
  NOW(6)
FROM seq;

-- ========================= 가이드 프로필 75 =========================
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
  SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 75
)
SELECT
  841000 + n,
  940000 + n,
  CONCAT('로컬메이트_', CAST(n AS CHAR)),
  ELT(
    CASE WHEN n <= 25 THEN n ELSE MOD(n - 26, 25) + 1 END,
    '서울',
    '부산',
    '제주',
    '강릉',
    '여수',
    '경주',
    '전주',
    '안동',
    '통영',
    '속초',
    '춘천',
    '포항',
    '대구',
    '광주',
    '목포',
    '수원',
    '인천',
    '평창',
    '양양',
    '거제',
    '해남',
    '순천',
    '군산',
    '울산',
    '천안'
  ),
  1,
  1,
  MOD(n, 38),
  ROUND(3.3 + MOD(n, 17) / 10, 2),
  CONCAT('https://picsum.photos/seed/localguest-gp-', LPAD(CAST(n AS CHAR), 3, '0'), '/800/800'),
  CONCAT(
    ELT(
      CASE WHEN n <= 25 THEN n ELSE MOD(n - 26, 25) + 1 END,
      '서울',
      '부산',
      '제주',
      '강릉',
      '여수',
      '경주',
      '전주',
      '안동',
      '통영',
      '속초',
      '춘천',
      '포항',
      '대구',
      '광주',
      '목포',
      '수원',
      '인천',
      '평창',
      '양양',
      '거제',
      '해남',
      '순천',
      '군산',
      '울산',
      '천안'
    ),
    ' ',
    CASE
      WHEN n <= 25 THEN '웨이브A'
      WHEN n <= 50 THEN '웨이브B(동일지역 다른성향)'
      ELSE '웨이브C'
    END,
    ' 가이드'
  ),
  IF(MOD(n, 4) = 0, '한국어,영어', '한국어'),
  CAST(26000 + n * 500 AS DECIMAL(10, 2)),
  2 + MOD(n, 16),
  CONCAT('로컬 스토리 #', CAST(n AS CHAR), ' — ', ELT(MOD(n, 5) + 1, '시장', '오름', '한옥', '바다', '카페')),
  CASE
    WHEN n <= 25 THEN CONCAT(
      '#',
      ELT(n, '서울', '부산', '제주', '강릉', '여수', '경주', '전주', '안동', '통영', '속초', '춘천', '포항', '대구', '광주', '목포', '수원', '인천', '평창', '양양', '거제', '해남', '순천', '군산', '울산', '천안'),
      ',#',
      ELT(MOD(n, 6) + 1, '맛집', '야경', '역사', '카페', '사진', '가족'),
      ',#웨이브1'
    )
    WHEN n <= 50 THEN CONCAT(
      '#',
      ELT(MOD(n - 26, 25) + 1, '서울', '부산', '제주', '강릉', '여수', '경주', '전주', '안동', '통영', '속초', '춘천', '포항', '대구', '광주', '목포', '수원', '인천', '평창', '양양', '거제', '해남', '순천', '군산', '울산', '천안'),
      ',#',
      ELT(MOD(n, 7) + 1, '미식', '힐링', '건축', '해변', '시장', '야경', '건강'),
      ',#웨이브2'
    )
    ELSE CONCAT(
      '#',
      ELT(MOD(n - 51, 25) + 1, '서울', '부산', '제주', '강릉', '여수', '경주', '전주', '안동', '통영', '속초', '춘천', '포항', '대구', '광주', '목포', '수원', '인천', '평창', '양양', '거제', '해남', '순천', '군산', '울산', '천안'),
      ',#',
      ELT(MOD(n, 5) + 1, '숨은명소', '감성', '체험', '드라이브', '역사산책'),
      ',#웨이브3'
    )
  END,
  CONCAT(
    '① ',
    ELT(MOD(n, 4) + 1, '만남', '역결', '집결', '주차장'),
    ' → ② ',
    ELT(MOD(n + 2, 5) + 1, '시장', '뷰', '카페거리', '문화재', '산책'),
    ' → ③ ',
    ELT(MOD(n + 3, 3) + 1, '야경', '노을', '야시장')
  ),
  CASE
    WHEN n <= 25 THEN ELT(MOD(n - 1, 7) + 1, '감성 투어', '미식 큐레이션', '역사 산책', '야경 스팟', '사진 동행', '힐링 로컬', '가족 친화')
    WHEN n <= 50 THEN ELT(MOD(n + 1, 6) + 1, '스냅 투어', '시장 미식', '문화 해설', '드라이브', '카페 투어', '건축 산책')
    ELSE ELT(MOD(n + 2, 5) + 1, '체험 중심', '로컬 숨코스', '힐링 걷기', '야경·감성', '가족 맞춤')
  END,
  NOW(6),
  NOW(6)
FROM seq;

-- ========================= 가이드 일정 75 =========================
INSERT INTO guide_schedules (id, guide_id, available_date, start_time, end_time, status, is_paid, created_at, updated_at)
WITH RECURSIVE seq(n) AS (
  SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 75
)
SELECT
  851000 + n,
  841000 + n,
  DATE_ADD('2026-05-01', INTERVAL MOD(n, 28) DAY),
  '10:00:00',
  '18:00:00',
  'AVAILABLE',
  0,
  NOW(6),
  NOW(6)
FROM seq;

-- ========================= 게스트 회원 50 =========================
INSERT INTO member (id, email, password, name, nickname, role, status, profile_image_url, created_at, updated_at)
WITH RECURSIVE seq(n) AS (
  SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 50
)
SELECT
  941000 + n,
  CONCAT('guest', LPAD(CAST(n AS CHAR), 2, '0'), '@localGuest.com'),
  '$2a$10$dn9eoH411zNPuPvGZao1QONVd/vSQgkoHHu5l8JM4W.kmw4QelJrS',
  CONCAT('게스트시드', CAST(n AS CHAR)),
  CONCAT('uguest_', CAST(n AS CHAR)),
  'GUEST',
  'ACTIVE',
  CONCAT('https://picsum.photos/seed/localguest-u50-', LPAD(CAST(n AS CHAR), 3, '0'), '/800/800'),
  NOW(6),
  NOW(6)
FROM seq;

-- ========================= match_request 150 =========================
INSERT INTO match_request (
  id,
  guest_id,
  guide_id,
  guide_schedule_id,
  destination,
  concept,
  concept_summary,
  desired_date,
  desired_budget,
  budget_min_won,
  budget_max_won,
  status,
  created_at,
  updated_at
)
WITH RECURSIVE seq(n) AS (
  SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 150
)
SELECT
  862000 + n,
  941000 + MOD((n - 1) * 11, 50) + 1,
  841000 + MOD(n - 1, 75) + 1,
  851000 + MOD(n - 1, 75) + 1,
  CONCAT(
    ELT(MOD(n, 25) + 1, '서울', '부산', '제주', '강릉', '여수', '경주', '전주', '안동', '통영', '속초', '춘천', '포항', '대구', '광주', '목포', '수원', '인천', '평창', '양양', '거제', '해남', '순천', '군산', '울산', '천안'),
    ' ',
    ELT(MOD(n + 3, 6) + 1, '맛집투어', '야경산책', '시장체험', '카페투어', '역사코스', '힐링산책')
  ),
  CONCAT(
    '#미식,#야경,#로컬 ',
    CASE
      WHEN MOD(n, 10) < 4 THEN '#힐링중시 #사진좋아함'
      WHEN MOD(n, 10) < 7 THEN '#가족동반 #걷기편함'
      ELSE '#감성숙소 #카페투어'
    END,
    ' #extra',
    ELT(MOD(n, 8) + 1, '숨카페', '노을', '시장', '야시장', '바다', '산책', '미술', '전통')
  ),
  CONCAT(
    '게스트 성향: 공통(미식·야경·로컬) + ',
    ELT(MOD(n, 5) + 1, '힐링', '가족', '감성', '사진', '역사'),
    ' · trip#',
    CAST(n AS CHAR)
  ),
  DATE_ADD('2026-04-10', INTERVAL MOD(n, 60) DAY),
  120000 + MOD(n, 40) * 15000,
  80000 + MOD(n, 20) * 5000,
  180000 + MOD(n, 25) * 12000,
  ELT(
    MOD(n, 6) + 1,
    'PENDING',
    'ACCEPTED',
    'PAID',
    'IN_PROGRESS',
    'COMPLETED',
    'CANCELLED'
  ),
  NOW(6),
  NOW(6)
FROM seq;

-- 보강: COMPLETED 매칭 60건 (가이드 841001~841010에 리뷰가 쌓이도록)
INSERT INTO match_request (
  id,
  guest_id,
  guide_id,
  guide_schedule_id,
  destination,
  concept,
  concept_summary,
  desired_date,
  desired_budget,
  budget_min_won,
  budget_max_won,
  status,
  created_at,
  updated_at
)
WITH RECURSIVE seq(n) AS (
  SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 60
)
SELECT
  862150 + n,
  941000 + MOD(n - 1, 50) + 1,
  841000 + MOD(n - 1, 10) + 1,
  851000 + MOD(n - 1, 75) + 1,
  CONCAT('서울 리뷰시드 #', n),
  '#미식 #리뷰 #로컬',
  CONCAT('리뷰용 더미 매칭 · ', n),
  DATE_ADD('2026-04-20', INTERVAL n DAY),
  130000 + MOD(n, 20) * 10000,
  90000 + MOD(n, 12) * 5000,
  190000 + MOD(n, 15) * 10000,
  'COMPLETED',
  NOW(6),
  NOW(6)
FROM seq;

-- ========================= guide_feeds 75 =========================
INSERT INTO guide_feeds (id, guide_id, content, image_url, is_deleted, created_at, updated_at)
WITH RECURSIVE seq(n) AS (
  SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 75
)
SELECT
  871000 + n,
  841000 + n,
  CONCAT('오늘의 로컬 피드 #', CAST(n AS CHAR), ' — ', ELT(MOD(n, 4) + 1, '맛집 탐방', '숨은 산책로', '야경 포인트', '시장 먹거리'),
         ' 다녀왔어요!'),
  CONCAT('https://picsum.photos/seed/localguest-feed-', LPAD(CAST(n AS CHAR), 3, '0'), '/800/800'),
  0,
  NOW(6),
  NOW(6)
FROM seq;

-- ========================= guide_careers 75 =========================
INSERT INTO guide_careers (id, guide_id, title, description, acquired_at, created_at, updated_at)
WITH RECURSIVE seq(n) AS (
  SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 75
)
SELECT
  872000 + n,
  841000 + n,
  ELT(MOD(n, 5) + 1, '지역해설사 자격', '문화관광 해설사', '바리스타 2급', '수상레저 안전교육', '1급 식품위생'),
  CONCAT('자격·경력 설명 #', CAST(n AS CHAR), ' (더미)'),
  DATE_ADD('2018-01-01', INTERVAL MOD(n, 2000) DAY),
  NOW(6),
  NOW(6)
FROM seq;

-- ========================= guide_images 150 (가이드당 2장) =========================
INSERT INTO guide_images (id, guide_id, image_url, sort_order, is_main, created_at, updated_at)
WITH RECURSIVE seq(n) AS (
  SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 75
)
SELECT
  873000 + n,
  841000 + n,
  CONCAT('https://picsum.photos/seed/localguest-gimg-', LPAD(CAST(n AS CHAR), 3, '0'), '-a/800/800'),
  0,
  1,
  NOW(6),
  NOW(6)
FROM seq;

INSERT INTO guide_images (id, guide_id, image_url, sort_order, is_main, created_at, updated_at)
WITH RECURSIVE seq(n) AS (
  SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 75
)
SELECT
  873075 + n,
  841000 + n,
  CONCAT('https://picsum.photos/seed/localguest-gimg-', LPAD(CAST(n AS CHAR), 3, '0'), '-b/800/800'),
  1,
  0,
  NOW(6),
  NOW(6)
FROM seq;

-- ========================= payment (정산·내역용, PAID/COMPLETED/IN_PROGRESS 매칭) =========================
INSERT INTO payment (
  id,
  match_request_id,
  payer_id,
  amount,
  payment_type,
  pg_order_no,
  pg_transaction_id,
  status,
  paid_at,
  refund_deadline,
  created_at,
  updated_at
)
SELECT
  880000 + ROW_NUMBER() OVER (ORDER BY id) AS rn,
  id,
  guest_id,
  CASE status
    WHEN 'COMPLETED' THEN 150000
    WHEN 'PAID' THEN 200000
    ELSE 170000
  END,
  'ACCOMPANY',
  CONCAT('PG-SEED-', id, '-ACCOMPANY'),
  CONCAT('PG-TXN-', id),
  'COMPLETED',
  NOW(6),
  DATE_ADD(NOW(6), INTERVAL 2 HOUR),
  NOW(6),
  NOW(6)
FROM match_request
WHERE id BETWEEN 862001 AND 862210
  AND status IN ('PAID', 'COMPLETED', 'IN_PROGRESS');

-- CHAT 결제 (동일 매칭에 두 번째 타입 — uq: match_id + payment_type)
INSERT INTO payment (
  id,
  match_request_id,
  payer_id,
  amount,
  payment_type,
  pg_order_no,
  pg_transaction_id,
  status,
  paid_at,
  refund_deadline,
  created_at,
  updated_at
)
SELECT
  880300 + ROW_NUMBER() OVER (ORDER BY id) AS rn,
  id,
  guest_id,
  30000,
  'CHAT',
  CONCAT('PG-SEED-', id, '-CHAT'),
  CONCAT('PG-TXN-CHAT-', id),
  'COMPLETED',
  NOW(6),
  DATE_ADD(NOW(6), INTERVAL 2 HOUR),
  NOW(6),
  NOW(6)
FROM match_request
WHERE id BETWEEN 862001 AND 862210
  AND status IN ('PAID', 'COMPLETED', 'IN_PROGRESS');

-- ========================= refund (일부 결제에 환불 요청) =========================
INSERT INTO refund (
  id,
  payment_id,
  requester_id,
  refund_type,
  reason,
  evidence_url,
  ai_processed,
  status,
  processed_at,
  created_at,
  updated_at
)
VALUES
(881001, 880001, 941012, 'MANUAL', '일정 변경으로 환불 요청', 'https://picsum.photos/seed/refund-ev-1/800/800', 0, 'PENDING', NULL, NOW(6), NOW(6)),
(881002, 880005, 941003, 'CANCEL_GUEST', '게스트 취소', NULL, 0, 'APPROVED', NOW(6), NOW(6), NOW(6)),
(881003, 880010, 941020, 'AUTO', 'PG 자동 환불', NULL, 1, 'REJECTED', NOW(6), NOW(6), NOW(6)),
(881004, 880015, 941008, 'MANUAL', '증빙 제출', 'https://picsum.photos/seed/refund-ev-4/800/800', 0, 'PENDING', NULL, NOW(6), NOW(6)),
(881005, 880020, 941001, 'CANCEL_GUIDE', '가이드 일정 취소', NULL, 0, 'APPROVED', NOW(6), NOW(6), NOW(6));

-- refund.payment_id 는 위 payment 첫 블록 기준 880001~880005 를 가정합니다. payment 가 비면 이 블록은 삭제하세요.

-- ========================= tour_extension =========================
INSERT INTO tour_extension (
  id,
  match_request_id,
  guest_id,
  extended_date,
  extended_price,
  status,
  requested_at,
  guide_approved_at,
  deadline_at,
  created_at,
  updated_at
)
SELECT
  882000 + ROW_NUMBER() OVER (ORDER BY id) AS rn,
  id,
  guest_id,
  DATE_ADD('2026-06-15', INTERVAL MOD(id, 10) DAY),
  45000 + MOD(id, 8) * 5000,
  ELT(MOD(id, 4) + 1, 'REQUESTED', 'GUIDE_APPROVED', 'PAID', 'REJECTED'),
  NOW(6),
  IF(MOD(id, 4) = 1, NULL, NOW(6)),
  DATE_ADD(NOW(6), INTERVAL 14 DAY),
  NOW(6),
  NOW(6)
FROM match_request
WHERE id BETWEEN 862001 AND 862020
  AND status IN ('PAID', 'COMPLETED', 'IN_PROGRESS');

-- ========================= review (COMPLETED 매칭, match_request_id UNIQUE) =========================
-- 테이블명이 reviews 이면 아래를 reviews 로 바꾸세요.
INSERT INTO review (match_request_id, member_id, guide_id, rating, content, deleted, created_at, updated_at)
SELECT
  mr.id,
  mr.guest_id,
  mr.guide_id,
  4 + MOD(mr.id, 2),
  CONCAT('로컬 투어 리뷰 더미 — 매칭#', CAST(mr.id AS CHAR)),
  0,
  NOW(6),
  NOW(6)
FROM match_request mr
WHERE mr.status = 'COMPLETED'
  AND mr.id BETWEEN 862001 AND 862210;

-- guide_profiles 집계를 실제 review와 맞춤 (review_count / average_rating; 리뷰 없으면 0)
UPDATE guide_profiles gp
LEFT JOIN (
  SELECT
    guide_id,
    COUNT(*) AS cnt,
    ROUND(AVG(rating), 2) AS avg_r
  FROM review
  WHERE deleted = 0
  GROUP BY guide_id
) r ON r.guide_id = gp.id
SET
  gp.review_count = IFNULL(r.cnt, 0),
  gp.average_rating = IFNULL(r.avg_r, 0);

-- ========================= scrapbooks (match_request_id UNIQUE) =========================
INSERT INTO scrapbooks (guest_id, match_request_id, title, content, main_image_url, tags, created_at, updated_at)
SELECT
  mr.guest_id,
  mr.id,
  CONCAT('스크랩북: ', mr.destination),
  CONCAT('여행 기록 더미. ', IFNULL(mr.concept_summary, '')),
  CONCAT('https://picsum.photos/seed/scrap-', CAST(mr.id AS CHAR), '/800/800'),
  CONCAT('#스크랩,#', REPLACE(SUBSTRING(mr.destination, 1, 4), ' ', ''), ',#로컬'),
  NOW(6),
  NOW(6)
FROM match_request mr
WHERE mr.status IN ('COMPLETED', 'PAID')
  AND mr.id BETWEEN 862030 AND 862150
LIMIT 45;

-- ========================= chat_rooms + chat_participants =========================
INSERT INTO chat_rooms (id, room_id, title, last_message, last_message_at, participant_count, owner_email, created_at, updated_at)
WITH RECURSIVE seq(n) AS (
  SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 25
)
SELECT
  883000 + n,
  CONCAT('lg-seed-room-', LPAD(CAST(n AS CHAR), 3, '0'), '-', UUID()),
  CONCAT('매칭 채팅 #', CAST(n AS CHAR)),
  '안녕하세요! 일정 문의드려요.',
  NOW(6),
  2,
  CONCAT('guide', LPAD(CAST(n AS CHAR), 2, '0'), '@localGuest.com'),
  NOW(6),
  NOW(6)
FROM seq;

INSERT INTO chat_participants (id, chat_room_id, user_id, user_email, user_nickname, last_read_at, is_alarm_on, created_at, updated_at)
WITH RECURSIVE seq(n) AS (
  SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 25
)
SELECT
  884000 + (n - 1) * 2 + 1,
  883000 + n,
  940000 + n,
  CONCAT('guide', LPAD(CAST(n AS CHAR), 2, '0'), '@localGuest.com'),
  CONCAT('gseed_', CAST(n AS CHAR)),
  NOW(6),
  1,
  NOW(6),
  NOW(6)
FROM seq;

INSERT INTO chat_participants (id, chat_room_id, user_id, user_email, user_nickname, last_read_at, is_alarm_on, created_at, updated_at)
WITH RECURSIVE seq(n) AS (
  SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 25
)
SELECT
  884000 + (n - 1) * 2 + 2,
  883000 + n,
  941000 + MOD(n + 7, 50) + 1,
  CONCAT('guest', LPAD(CAST(MOD(n + 7, 50) + 1 AS CHAR), 2, '0'), '@localGuest.com'),
  CONCAT('uguest_', CAST(MOD(n + 7, 50) + 1 AS CHAR)),
  NOW(6),
  1,
  NOW(6),
  NOW(6)
FROM seq;
