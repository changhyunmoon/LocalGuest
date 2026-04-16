# Backend Matching (`module-domain` / `com.team6.domain.matching`)

매칭·결제·투어 연장 흐름을 담는 도메인 레이어입니다. 실행 모듈은 `api-server`이고, 코드는 `module-domain/src/main/java/com/team6/domain/matching`에 모여 있습니다.

## 구성 요약

| 영역 | 설명 |
|------|------|
| **Controller** | `MatchRequestController`, `PaymentController`, `TourExtensionController` |
| **Service** | `MatchRequestService`, `PaymentService`, `TourExtensionService` |
| **데이터** | `MatchRequest`, `Payment`, `Refund`, `TourExtension` + 상태 Enum |
| **연동** | Stub/Fake PG, 선택 시 카카오페이(`KakaoPayClient`), 가이드 스케줄 확정 동기화(`GuideScheduleSyncClient`) |
| **예외** | `MatchingException`, `MatchingErrorCode`, `MatchingExceptionHandler` |
| **스케줄** | `@EnableScheduling` + 연장 마감/오픈 배치(`TourExtensionService`) |

API 베이스는 애플리케이션의 `/api` 프리픽스 뒤에 붙는다고 가정하면, 매칭 관련 주 경로는 다음과 같습니다.

- `POST/GET/PATCH … /matching/requests`
- `GET/POST … /matching/payments`
- `GET/PATCH … /matching/extensions`

## 매칭 요청 (`/matching/requests`)

- **생성**: 게스트가 가이드에게 매칭 요청을 올림(가이드 존재 여부 검증).
- **목록**: 게스트 `/guest/list`, 가이드 `/guide/list` — 본인 데이터만.
- **가이드**: `reject`, `propose`(제시 일정·메시지 등 반영).
- **게스트**: `accept`, `decline`(제시안 최종 거절).
- **취소**: `guest/cancel`, `guide/cancel` + 사유(`CancelRequestDto`).

상태는 DB CHECK와 맞춘 `MatchRequestStatus`(예: `PENDING`, `ACCEPTED`, `PAID`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `REJECTED`)로만 전이합니다. 핵심 엔티티는 물리 삭제 대신 상태로 처리합니다.

## 결제·환불 (`/matching/payments`)

- **목록/단건**: 게스트 결제 목록, 역할에 따라 게스트·가이드 단건 조회.
- **생성**: 매칭 요청이 게스트 소유이고 `ACCEPTED`일 때만; `(match_request_id, payment_type)` 중복 방지.
- **확인**: 금액·주문번호 검증 후 완료 처리, 매칭 요청 `ACCEPTED` → `PAID` 반영; 설정에 따라 Fake PG 또는 카카오페이 승인.
- **환불 신청** (`/refunds`): 완료된 결제에 한해, 결제 시점부터 정해진 **환불 마감 시각** 이내에만 가능; 중복 환불 요청 방지.

결제 확정 후 가이드 스케줄 paid-confirm 연동은 실패해도 결제 자체는 롤백하지 않고 로그로 남깁니다.

## 투어 연장 (`/matching/extensions`)

- **조회**: 해당 `match_request`에 연결된 연장 건을 게스트·가이드만 조회.
- **선택** (`PATCH …/select`): 게스트만; 마감(`deadlineAt`) 전에 연장 여부 선택.
- **배치**: 당일 21:00(KST)에 투어일이 오늘인 일부 상태의 요청에 대해 연장 선택 레코드 생성; 마감 후 미선택 건은 분 단위 스케줄로 자동 처리(`AUTO_CANCELLED` 등 도메인 규칙에 따름).

## 인증·응답

- `MatchingAuthenticationSupport`로 현재 게스트 `memberId` / 가이드 `guideProfileId` 등을 해석합니다.
- Service는 엔티티 대신 Response DTO를 반환합니다.

스키마·Enum 상세는 Swagger(`v3/api-docs`)와 SQL 계약을 기준으로 맞춥니다.
