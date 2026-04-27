-- =============================================================================
-- 로컬 MySQL: 관계형 테이블 전부 비우고 → 풍부 더미 1회 재시드 (단일 실행)
-- =============================================================================
-- · DB명: application-local.yml 기준 local_guide_db (다르면 USE만 수정)
-- · 로그인 (모든 계정 동일 비밀번호 — BCrypt = 평문 LocalGuest1!)
--     가이드  guide01@localGuest.com … guide20@localGuest.com
--     게스트 guest01@localGuest.com … guest30@localGuest.com
-- · 스크랩북/티켓: COMPLETED 매칭은
--     - match_request.proposed_schedule 에 `→` 구간 (목록 지도용)
--     - guide_schedules.course_detail 은 프론트 parseCourseDetail 규격
--       `1. 장소 | 시간 | 설명` 줄바꿈 반복 (티켓 상세 타임라인·지도)
--     - COMPLETED + 스케줄 BOOKED + is_paid=1 → /form 에서 코스 공개
-- · 카카오페이 QR: ACCEPTED 매칭 + ACCOMPANY 결제 status=PENDING 행
--   (앱에서 결제 요청 시 기존 PENDING 재사용 → ready/redirect 플로우)
-- · ID 블록(다른 시드와 겹치지 않게): member 가이드 960001~, 게스트 961001~,
--   guide_profiles 950001~, guide_schedules 955001~, match_request 956001~,
--   payment 970001~, chat 983001~, 기타 보조 ID 아래 참고
-- · MongoDB(chat_message 등)는 이 스크립트로 지우지 않음.
-- · MySQL 8.0+
-- · 한글 깨짐 방지: 클라이언트가 UTF-8로 보내야 함. 아래 중 하나 필수.
--     mysql --default-character-set=utf8mb4 -h... -uroot -p local_guide_db < 이파일
--   (또는 mysql 프롬프트에서 먼저 SET NAMES utf8mb4; 후 SOURCE)
-- =============================================================================

USE local_guide_db;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE chat_participants;
TRUNCATE TABLE chat_rooms;
TRUNCATE TABLE scrapbooks;
TRUNCATE TABLE review;
TRUNCATE TABLE refund;
TRUNCATE TABLE payment;
TRUNCATE TABLE tour_extension;
TRUNCATE TABLE match_request;
TRUNCATE TABLE guide_images;
TRUNCATE TABLE guide_careers;
TRUNCATE TABLE guide_feeds;
TRUNCATE TABLE guide_schedules;
TRUNCATE TABLE guide_profiles;
TRUNCATE TABLE member;

SET FOREIGN_KEY_CHECKS = 1;

SET @pwd := '$2a$10$dn9eoH411zNPuPvGZao1QONVd/vSQgkoHHu5l8JM4W.kmw4QelJrS';
SET @now := NOW(6);

-- ========================= 가이드 회원 20 =========================
INSERT INTO member (id, email, password, name, nickname, role, status, profile_image_url, created_at, updated_at)
WITH RECURSIVE seq(n) AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 20)
SELECT
  960000 + n,
  CONCAT('guide', LPAD(CAST(n AS CHAR), 2, '0'), '@localGuest.com'),
  @pwd,
  CONCAT('가이드시드_', CAST(n AS CHAR)),
  CONCAT('gseed_', CAST(n AS CHAR)),
  'GUIDE',
  'ACTIVE',
  CONCAT('https://picsum.photos/seed/lg-rich-g-', LPAD(CAST(n AS CHAR), 2, '0'), '/800/800'),
  @now,
  @now
FROM seq;

-- ========================= 게스트 회원 30 =========================
INSERT INTO member (id, email, password, name, nickname, role, status, profile_image_url, created_at, updated_at)
WITH RECURSIVE seq(n) AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 30)
SELECT
  961000 + n,
  CONCAT('guest', LPAD(CAST(n AS CHAR), 2, '0'), '@localGuest.com'),
  @pwd,
  CONCAT('게스트시드_', CAST(n AS CHAR)),
  CONCAT('uguest_', CAST(n AS CHAR)),
  'GUEST',
  'ACTIVE',
  CONCAT('https://picsum.photos/seed/lg-rich-u-', LPAD(CAST(n AS CHAR), 2, '0'), '/800/800'),
  @now,
  @now
FROM seq;

-- ========================= 가이드 프로필 20 =========================
INSERT INTO guide_profiles (
  id, member_id, nickname, profile_image, bio, region, language, price_per_hour,
  average_rating, review_count, is_approved, is_active, residence_years, local_story,
  keywords, default_course, guide_style, created_at, updated_at
)
WITH RECURSIVE seq(n) AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 20)
SELECT
  950000 + n,
  960000 + n,
  CONCAT('로컬메이트_', CAST(n AS CHAR)),
  CONCAT('https://picsum.photos/seed/lg-rich-gp-', LPAD(CAST(n AS CHAR), 2, '0'), '/800/800'),
  CONCAT('풍부 시드 가이드 #', CAST(n AS CHAR), ' 입니다.'),
  ELT(
    MOD(n - 1, 10) + 1,
    '서울', '부산', '제주', '강릉', '여수', '경주', '전주', '대구', '광주', '인천'
  ),
  '한국어,English',
  50000.00 + MOD(n, 8) * 5000,
  4.20 + MOD(n, 8) * 0.05,
  0,
  1,
  1,
  5 + MOD(n, 10),
  '동네 산책과 시장 이야기를 좋아합니다.',
  CONCAT('#로컬,#미식,#', ELT(MOD(n, 5) + 1, '힐링', '야경', '역사', '카페', '가족')),
  '시장 → 명소 → 카페거리',
  '친근한 페이스',
  @now,
  @now
FROM seq;

-- ========================= 가이드 피드·경력·이미지 =========================
INSERT INTO guide_feeds (id, guide_id, content, image_url, is_deleted, created_at, updated_at)
WITH RECURSIVE seq(n) AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 20)
SELECT
  952000 + n,
  950000 + n,
  CONCAT('로컬 피드 #', CAST(n AS CHAR), ' — 맛집·산책 문의 환영!'),
  CONCAT('https://picsum.photos/seed/lg-rich-feed-', LPAD(CAST(n AS CHAR), 2, '0'), '/800/800'),
  0,
  @now,
  @now
FROM seq;

INSERT INTO guide_careers (id, guide_id, title, description, acquired_at, created_at, updated_at)
WITH RECURSIVE seq(n) AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 20)
SELECT
  952100 + n,
  950000 + n,
  ELT(MOD(n, 3) + 1, '문화관광 해설사', '지역해설사', '바리스타 2급'),
  CONCAT('경력 설명 #', CAST(n AS CHAR)),
  DATE_ADD('2018-01-01', INTERVAL MOD(n, 400) DAY),
  @now,
  @now
FROM seq;

INSERT INTO guide_images (id, guide_id, image_url, sort_order, is_main, created_at, updated_at)
WITH RECURSIVE seq(n) AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 20)
SELECT
  952200 + n,
  950000 + n,
  CONCAT('https://picsum.photos/seed/lg-rich-gimg-', LPAD(CAST(n AS CHAR), 2, '0'), '-a/800/800'),
  0,
  1,
  @now,
  @now
FROM seq;

INSERT INTO guide_images (id, guide_id, image_url, sort_order, is_main, created_at, updated_at)
WITH RECURSIVE seq(n) AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 20)
SELECT
  952220 + n,
  950000 + n,
  CONCAT('https://picsum.photos/seed/lg-rich-gimg-', LPAD(CAST(n AS CHAR), 2, '0'), '-b/800/800'),
  1,
  0,
  @now,
  @now
FROM seq;

-- ========================= 스케줄: 매칭용 80 + 예약 가능 25 =========================
-- 먼저 match_request_id NULL 로 삽입 후, match_request 삽입 뒤 UPDATE 로 연결
INSERT INTO guide_schedules (
  id, guide_id, available_date, start_time, end_time, status, is_paid,
  match_request_id, meeting_point, guide_message, course_detail,
  created_at, updated_at
)
WITH RECURSIVE seq(n) AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 105)
SELECT
  955000 + n,
  950000 + MOD(n - 1, 20) + 1,
  DATE_ADD('2026-02-01', INTERVAL n DAY),
  '10:00:00',
  '18:00:00',
  'AVAILABLE',
  0,
  NULL,
  NULL,
  NULL,
  NULL,
  @now,
  @now
FROM seq;

-- ========================= match_request 80건 =========================
-- n=1..35 COMPLETED (스크랩북·티켓·리뷰용 데이터 풍부)
-- n=36..50 ACCEPTED + 결제 PENDING (카카오 QR 플로우)
-- n=51..60 PAID (동행 결제 완료, 미래 일정)
-- n=61..75 PENDING
-- n=76..80 CANCELLED
INSERT INTO match_request (
  id, guest_id, guide_id, guide_schedule_id,
  destination, concept, concept_summary,
  desired_date, desired_budget, budget_min_won, budget_max_won,
  proposed_schedule, propose_message, status,
  created_at, updated_at
)
WITH RECURSIVE seq(n) AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 80)
SELECT
  956000 + n,
  961000 + MOD(n - 1, 30) + 1,
  950000 + MOD(n - 1, 20) + 1,
  955000 + n,
  CONCAT(
    -- 게스트별로 도시가 고르게 섞이도록:
    -- 동일 guest는 n이 30 간격으로 반복되므로, 순환 차수(floor((n-1)/30))를 섞어 도시가 바뀌게 한다.
    ELT(MOD(n + FLOOR((n - 1) / 30), 10) + 1, '서울', '부산', '제주', '강릉', '여수', '경주', '전주', '대구', '광주', '인천'),
    ' ',
    ELT(MOD(n + 2, 5) + 1, '맛집투어', '야경산책', '시장체험', '카페투어', '역사코스')
  ),
  CONCAT('#미식,#로컬,#풍부시드 ', CAST(n AS CHAR)),
  CONCAT('성향 요약 · trip#', CAST(n AS CHAR)),
  CASE
    WHEN n <= 35 THEN DATE_ADD('2025-10-01', INTERVAL n DAY)
    WHEN n <= 50 THEN DATE_ADD('2026-08-01', INTERVAL (n - 35) DAY)
    WHEN n <= 60 THEN DATE_ADD('2026-09-01', INTERVAL (n - 50) DAY)
    WHEN n <= 75 THEN DATE_ADD('2026-10-01', INTERVAL (n - 60) DAY)
    ELSE DATE_ADD('2026-11-01', INTERVAL (n - 75) DAY)
  END,
  150000 + MOD(n, 20) * 10000,
  100000 + MOD(n, 15) * 5000,
  220000 + MOD(n, 18) * 8000,
  CASE
    WHEN n <= 35 THEN CONCAT(
      ELT(MOD(n + FLOOR((n - 1) / 30), 10) + 1, '서울', '부산', '제주', '강릉', '여수', '경주', '전주', '대구', '광주', '인천'),
      ' 시장 → ',
      ELT(MOD(n + FLOOR((n - 1) / 30), 10) + 1, '서울', '부산', '제주', '강릉', '여수', '경주', '전주', '대구', '광주', '인천'),
      ' 대표 명소 → ',
      ELT(MOD(n + FLOOR((n - 1) / 30), 10) + 1, '서울', '부산', '제주', '강릉', '여수', '경주', '전주', '대구', '광주', '인천'),
      ' 카페거리'
    )
    WHEN n <= 50 THEN CONCAT(
      ELT(MOD(n + FLOOR((n - 1) / 30), 10) + 1, '서울', '부산', '제주', '강릉', '여수', '경주', '전주', '대구', '광주', '인천'),
      ' 해변 → 야시장 → 전망대'
    )
    WHEN n <= 60 THEN CONCAT(
      ELT(MOD(n + FLOOR((n - 1) / 30), 10) + 1, '서울', '부산', '제주', '강릉', '여수', '경주', '전주', '대구', '광주', '인천'),
      ' 코스 A → 코스 B → 코스 C'
    )
    ELSE CONCAT('희망 지역 ', ELT(MOD(n + FLOOR((n - 1) / 30), 10) + 1, '서울', '부산', '제주', '강릉', '여수', '경주', '전주', '대구', '광주', '인천'))
  END,
  IF(n <= 50, CONCAT('가이드 제안 메시지 #', CAST(n AS CHAR)), NULL),
  ELT(
    CASE
      WHEN n <= 35 THEN 1
      WHEN n <= 50 THEN 2
      WHEN n <= 60 THEN 3
      WHEN n <= 75 THEN 4
      ELSE 5
    END,
    'COMPLETED', 'ACCEPTED', 'PAID', 'PENDING', 'CANCELLED'
  ),
  @now,
  @now
FROM seq;

UPDATE guide_schedules gs
JOIN match_request mr ON mr.guide_schedule_id = gs.id
SET
  gs.match_request_id = IF(mr.status = 'CANCELLED', NULL, mr.id),
  gs.status = CASE mr.status
    WHEN 'COMPLETED' THEN 'BOOKED'
    WHEN 'PAID' THEN 'BOOKED'
    WHEN 'ACCEPTED' THEN 'PENDING'
    WHEN 'PENDING' THEN 'PENDING'
    WHEN 'CANCELLED' THEN 'AVAILABLE'
    ELSE 'AVAILABLE'
  END,
  gs.is_paid = CASE WHEN mr.status IN ('COMPLETED', 'PAID') THEN 1 ELSE 0 END,
  gs.meeting_point = CASE
    WHEN mr.status = 'CANCELLED' THEN NULL
    ELSE CONCAT(
      SUBSTRING_INDEX(mr.destination, ' ', 1),
      ' ',
      ELT(MOD(mr.id, 4) + 1, '역 1번 출구', '시청 앞 광장', '공항 도착층', '주차장 입구')
    )
  END,
  gs.guide_message = CASE
    WHEN mr.status IN ('CANCELLED', 'PENDING') THEN NULL
    ELSE CONCAT('일정 안내 · 매칭#', mr.id)
  END,
  gs.course_detail = CASE
    WHEN mr.status IN ('COMPLETED', 'PAID') THEN CONCAT_WS(
      '\n',
      CONCAT(
        '1. ', SUBSTRING_INDEX(mr.destination, ' ', 1), ' ',
        ELT(MOD(mr.id, 5) + 1, '동문시장', '자갈치시장', '동문재래시장', '서문시장', '중앙시장'),
        ' | 오전 10:30 | 로컬 먹거리 탐방'
      ),
      CONCAT(
        '2. ', SUBSTRING_INDEX(mr.destination, ' ', 1), ' ',
        ELT(MOD(mr.id + 1, 5) + 1, '용두암', '해운대', '협재해수욕장', '경포대', '오동도'),
        ' | 오후 2:00 | 산책·사진'
      ),
      CONCAT(
        '3. ', SUBSTRING_INDEX(mr.destination, ' ', 1), ' ',
        ELT(MOD(mr.id + 2, 5) + 1, '카페거리', '남포동', '애월', '주상절리', '이순신공원'),
        ' | 오후 4:30 | 마무리'
      )
    )
    WHEN mr.status = 'ACCEPTED' THEN CONCAT(
      '1. ', SUBSTRING_INDEX(mr.destination, ' ', 1), ' 핵심 코스 | 오전 11:00 | 결제 후 전체 공개\n',
      '2. ', SUBSTRING_INDEX(mr.destination, ' ', 1), ' 산책로 | 오후 3:00 | 결제 후 전체 공개'
    )
    ELSE NULL
  END,
  gs.updated_at = @now
WHERE mr.id BETWEEN 956001 AND 956080;

-- ========================= 결제 =========================
-- COMPLETED 매칭: 동행 + 채팅 결제 완료
INSERT INTO payment (
  id, match_request_id, payer_id, amount, payment_type,
  pg_order_no, pg_transaction_id, status, paid_at, refund_deadline,
  created_at, updated_at
)
SELECT
  970000 + ROW_NUMBER() OVER (ORDER BY id),
  id,
  guest_id,
  180000,
  'ACCOMPANY',
  CONCAT('PG-RICH-', id, '-AC'),
  CONCAT('PG-RICH-TXN-', id),
  'COMPLETED',
  @now,
  DATE_ADD(@now, INTERVAL 2 HOUR),
  @now,
  @now
FROM match_request
WHERE id BETWEEN 956001 AND 956035 AND status = 'COMPLETED';

INSERT INTO payment (
  id, match_request_id, payer_id, amount, payment_type,
  pg_order_no, pg_transaction_id, status, paid_at, refund_deadline,
  created_at, updated_at
)
SELECT
  970100 + ROW_NUMBER() OVER (ORDER BY id),
  id,
  guest_id,
  30000,
  'CHAT',
  CONCAT('PG-RICH-', id, '-CHAT'),
  CONCAT('PG-RICH-CHAT-', id),
  'COMPLETED',
  @now,
  DATE_ADD(@now, INTERVAL 2 HOUR),
  @now,
  @now
FROM match_request
WHERE id BETWEEN 956001 AND 956035 AND status = 'COMPLETED';

-- PAID 매칭: 동행 결제 완료
INSERT INTO payment (
  id, match_request_id, payer_id, amount, payment_type,
  pg_order_no, pg_transaction_id, status, paid_at, refund_deadline,
  created_at, updated_at
)
SELECT
  970200 + ROW_NUMBER() OVER (ORDER BY id),
  id,
  guest_id,
  200000,
  'ACCOMPANY',
  CONCAT('PG-RICH-', id, '-AC'),
  CONCAT('PG-RICH-TXN-', id),
  'COMPLETED',
  @now,
  DATE_ADD(@now, INTERVAL 2 HOUR),
  @now,
  @now
FROM match_request
WHERE id BETWEEN 956051 AND 956060 AND status = 'PAID';

-- ACCEPTED: 동행 결제 PENDING (앱에서 결제 요청 시 Kakao ready 재호출 가능)
INSERT INTO payment (
  id, match_request_id, payer_id, amount, payment_type,
  pg_order_no, pg_transaction_id, status, paid_at, refund_deadline,
  created_at, updated_at
)
SELECT
  970300 + ROW_NUMBER() OVER (ORDER BY id),
  id,
  guest_id,
  190000,
  'ACCOMPANY',
  CONCAT('PG-RICH-PEND-', id, '-AC'),
  NULL,
  'PENDING',
  NULL,
  NULL,
  @now,
  @now
FROM match_request
WHERE id BETWEEN 956036 AND 956050 AND status = 'ACCEPTED';

-- ========================= 리뷰 + 스크랩북 (COMPLETED) =========================
INSERT INTO review (match_request_id, member_id, guide_id, rating, content, deleted, created_at, updated_at)
SELECT
  mr.id,
  mr.guest_id,
  mr.guide_id,
  4 + MOD(mr.id, 2),
  CONCAT('풍부 시드 리뷰 — ', mr.destination, ' 매칭#', CAST(mr.id AS CHAR), ' 좋았어요.'),
  0,
  @now,
  @now
FROM match_request mr
WHERE mr.status = 'COMPLETED' AND mr.id BETWEEN 956001 AND 956035;

INSERT INTO scrapbooks (guest_id, match_request_id, title, content, main_image_url, tags, created_at, updated_at)
SELECT
  mr.guest_id,
  mr.id,
  CONCAT('스크랩: ', mr.destination),
  CONCAT('여행 기록(풍부 시드). ', IFNULL(mr.concept_summary, '')),
  CONCAT('https://picsum.photos/seed/lg-rich-scrap-', CAST(mr.id AS CHAR), '/800/800'),
  CONCAT('#스크랩,#', REPLACE(SUBSTRING_INDEX(mr.destination, ' ', 1), ' ', ''), ',#로컬'),
  @now,
  @now
FROM match_request mr
WHERE mr.status = 'COMPLETED' AND mr.id BETWEEN 956001 AND 956030;

UPDATE guide_profiles gp
LEFT JOIN (
  SELECT guide_id, COUNT(*) AS cnt, ROUND(AVG(rating), 2) AS avg_r
  FROM review WHERE deleted = 0 GROUP BY guide_id
) r ON r.guide_id = gp.id
SET gp.review_count = IFNULL(r.cnt, 0), gp.average_rating = IFNULL(r.avg_r, 0), gp.updated_at = @now;

-- ========================= 채팅방 20 =========================
INSERT INTO chat_rooms (id, room_id, title, last_message, last_message_at, participant_count, owner_email, created_at, updated_at)
WITH RECURSIVE seq(n) AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 20)
SELECT
  983000 + n,
  CONCAT('lg-rich-room-', LPAD(CAST(n AS CHAR), 3, '0'), '-', UUID()),
  CONCAT('매칭 문의 #', CAST(n AS CHAR)),
  '안녕하세요! 일정 문의드려요.',
  @now,
  2,
  CONCAT('guide', LPAD(CAST(((n - 1) MOD 20) + 1 AS CHAR), 2, '0'), '@localGuest.com'),
  @now,
  @now
FROM seq;

INSERT INTO chat_participants (id, chat_room_id, user_id, user_email, user_nickname, last_read_at, is_alarm_on, created_at, updated_at)
WITH RECURSIVE seq(n) AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 20)
SELECT
  984000 + (n - 1) * 2 + 1,
  983000 + n,
  960000 + ((n - 1) MOD 20) + 1,
  CONCAT('guide', LPAD(CAST(((n - 1) MOD 20) + 1 AS CHAR), 2, '0'), '@localGuest.com'),
  CONCAT('gseed_', CAST(((n - 1) MOD 20) + 1 AS CHAR)),
  @now,
  1,
  @now,
  @now
FROM seq;

INSERT INTO chat_participants (id, chat_room_id, user_id, user_email, user_nickname, last_read_at, is_alarm_on, created_at, updated_at)
WITH RECURSIVE seq(n) AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 20)
SELECT
  984000 + (n - 1) * 2 + 2,
  983000 + n,
  961000 + MOD(n + 5, 30) + 1,
  CONCAT('guest', LPAD(CAST(MOD(n + 5, 30) + 1 AS CHAR), 2, '0'), '@localGuest.com'),
  CONCAT('uguest_', CAST(MOD(n + 5, 30) + 1 AS CHAR)),
  @now,
  1,
  @now,
  @now
FROM seq;

ALTER TABLE member AUTO_INCREMENT = 990000;
ALTER TABLE guide_profiles AUTO_INCREMENT = 960000;
ALTER TABLE guide_schedules AUTO_INCREMENT = 960000;
ALTER TABLE match_request AUTO_INCREMENT = 960000;
ALTER TABLE payment AUTO_INCREMENT = 980000;
ALTER TABLE review AUTO_INCREMENT = 900000;
ALTER TABLE scrapbooks AUTO_INCREMENT = 900000;
ALTER TABLE chat_rooms AUTO_INCREMENT = 990000;
ALTER TABLE chat_participants AUTO_INCREMENT = 990000;
ALTER TABLE guide_feeds AUTO_INCREMENT = 953000;
ALTER TABLE guide_careers AUTO_INCREMENT = 953000;
ALTER TABLE guide_images AUTO_INCREMENT = 952500;
