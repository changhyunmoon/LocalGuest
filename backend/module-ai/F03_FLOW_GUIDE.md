# F03 AI Flow Guide

이 문서는 `module-ai` 기준으로 F03 흐름을 초보자도 따라갈 수 있게 정리한 설명서다.

목표는 아래 3가지다.

1. `프롬프트 입력 -> AI 추천` 흐름이 어디서 시작해서 어디서 끝나는지 이해하기
2. 각 클래스가 왜 필요한지 이해하기
3. AI 모듈과 다른 모듈(`module-domain`, `module-chat`)의 경계를 이해하기

---

## 1. F03를 한 줄로 설명하면

F03는 사용자가 입력한 여행 프롬프트를 해석해서, 조건에 맞는 가이드를 추천해주는 흐름이다.

아주 크게 보면 이렇게 움직인다.

1. 사용자가 자연어 프롬프트를 입력한다.
2. AI 추천 API가 요청을 받는다.
3. 서버가 추천 대상이 될 가이드 후보군을 준비한다.
4. 프롬프트를 지역, 스타일, 예산, 활동, 언어 같은 구조화된 조건으로 바꾼다.
5. 각 가이드 후보에게 점수를 매긴다.
6. 추천 이유와 함께 상위 가이드를 응답으로 내려준다.
7. 프론트는 그 결과를 카드 형태로 보여준다.
8. 이후 사용자는 가이드를 선택하고, 매칭 요청 생성 단계로 넘어간다.

즉 AI 모듈의 핵심 역할은 `추천까지`다.

---

## 2. 전체 흐름

최신 코드 기준으로 F03 흐름은 이렇게 이해하면 된다.

1. 프론트가 `POST /ai/recommend` 호출
2. [AiController.java](src/main/java/com/team6/module/ai/controller/AiController.java) 가 요청 수신
3. `GuideCandidateProvider`가 후보 가이드 목록 준비
4. [PromptRecommendationService.java](src/main/java/com/team6/module/ai/service/PromptRecommendationService.java) 가 추천 전체 흐름 조립
5. [PromptParser.java](src/main/java/com/team6/module/ai/parser/PromptParser.java) 가 프롬프트 해석
6. 인접 지역 확장, fallback, notice 조립
7. [AiRecommendationServiceImpl.java](src/main/java/com/team6/module/ai/service/AiRecommendationServiceImpl.java) 가 엔진 입력 형태로 변환
8. [MatchingEngine.java](src/main/java/com/team6/module/ai/engine/MatchingEngine.java) 이 최종 추천 카드 생성
9. [GuideRecommendResponse.java](src/main/java/com/team6/module/ai/dto/response/GuideRecommendResponse.java) 형태로 응답 반환

---

## 3. 초보자용 코드 읽는 순서

아래 순서로 읽는 것이 가장 이해하기 쉽다.

### 3-1. 1단계: 시작점 보기

[AiController.java](src/main/java/com/team6/module/ai/controller/AiController.java)

여기서 먼저 확인할 것:

- 어떤 URL로 요청을 받는지
- 어떤 request DTO를 받는지
- 어떤 서비스로 넘기는지
- 최종적으로 어떤 response를 돌려주는지

초보자 관점에서는 이 파일을 `입구`라고 생각하면 된다.

### 3-2. 2단계: 추천 흐름 전체 보기

[PromptRecommendationService.java](src/main/java/com/team6/module/ai/service/PromptRecommendationService.java)

여기서 먼저 확인할 것:

- 프롬프트를 어떻게 파싱하는지
- 지역이 없으면 왜 종료하는지
- 인접 지역 확장은 왜 하는지
- fallback은 왜 하는지
- `conceptSummary`, `keywords`, `matchRequestDraft`는 왜 만드는지

이 파일은 `흐름 조정자`다.

### 3-3. 3단계: 프롬프트 해석 보기

[PromptParser.java](src/main/java/com/team6/module/ai/parser/PromptParser.java)

여기서 먼저 확인할 것:

- 지역 추출
- 예산 추출
- 스타일 추출
- 활동 태그 추출
- 언어 추출
- 기간/날짜 추출
- 제외 태그 / soft penalty 태그 추출

이 파일은 `자연어 -> 구조화 데이터 변환기`다.

### 3-4. 4단계: 후보 가이드가 어디서 오는지 보기

[DbBackedGuideCandidateProvider.java](../module-ai-integration/src/main/java/com/team6/integration/ai/DbBackedGuideCandidateProvider.java)

여기서 먼저 확인할 것:

- 프론트에서 후보를 안 보내면 어떻게 하는지
- DB에서 어떤 가이드를 읽어오는지
- 일정 필터가 어떻게 걸리는지
- `GuideCandidateBundle`이 왜 필요한지

이 클래스는 `추천 엔진이 비교할 후보군을 만드는 담당자`다.

### 3-5. 5단계: 엔진 입력으로 바꾸는 부분 보기

[AiRecommendationServiceImpl.java](src/main/java/com/team6/module/ai/service/AiRecommendationServiceImpl.java)

[AiRecommendationMapper.java](src/main/java/com/team6/module/ai/support/AiRecommendationMapper.java)

여기서 먼저 확인할 것:

- `GuideRecommendRequest`를 `TravelerPreference`로 어떻게 바꾸는지
- 후보 DTO를 `GuideAiProfile`로 어떻게 바꾸는지

이 단계는 `API/파서 결과 -> 엔진 전용 모델` 변환 단계다.

### 3-6. 6단계: 추천 카드가 만들어지는 곳 보기

[MatchingEngine.java](src/main/java/com/team6/module/ai/engine/MatchingEngine.java)

여기서 먼저 확인할 것:

- 각 후보에게 점수를 어떻게 매기는지
- 추천 이유를 어떻게 만드는지
- 왜 diversity penalty를 쓰는지
- `GuideRecommendItem`이 어떻게 만들어지는지

이 파일은 `최종 추천 엔진`이다.

### 3-7. 7단계: 점수 계산 세부 보기

[ScoreCalculator.java](src/main/java/com/team6/module/ai/engine/ScoreCalculator.java)

정책 클래스들:

- [RegionMatchPolicy.java](src/main/java/com/team6/module/ai/policy/RegionMatchPolicy.java)
- [StyleMatchPolicy.java](src/main/java/com/team6/module/ai/policy/StyleMatchPolicy.java)
- [BudgetMatchPolicy.java](src/main/java/com/team6/module/ai/policy/BudgetMatchPolicy.java)
- [ActivityMatchPolicy.java](src/main/java/com/team6/module/ai/policy/ActivityMatchPolicy.java)
- [LanguageMatchPolicy.java](src/main/java/com/team6/module/ai/policy/LanguageMatchPolicy.java)
- [FeedbackMatchPolicy.java](src/main/java/com/team6/module/ai/policy/FeedbackMatchPolicy.java)

이 단계는 `왜 이 가이드가 몇 점인지`를 이해하는 단계다.

---

## 4. 핵심 클래스별 역할

### AiController

역할:

- API 요청 받기
- 날짜 범위 확정
- 후보 가이드 번들 조회
- 메인 추천 실행
- specialSuggestion 조립
- 최종 응답 반환

쉽게 말하면 `입구 + 최상위 조립`이다.

### PromptRecommendationService

역할:

- 프롬프트 파싱
- 지역 검증
- 후보 확장
- 추천 1차 실행
- 전략적 fallback
- notice / noticeCodes / confidence 생성
- `conceptSummary`, `keywords`, `matchRequestDraft` 생성

쉽게 말하면 `추천 흐름 조정자`다.

### PromptParser

역할:

- 자유로운 자연어를 규칙 기반으로 해석
- 사용자의 의도를 구조화된 필드로 추출

쉽게 말하면 `번역기`다.

### DbBackedGuideCandidateProvider

역할:

- DB에서 후보 가이드 목록 만들기
- 일정 충돌 가이드 걸러내기
- 비필터 후보도 따로 보관해서 specialSuggestion 지원

쉽게 말하면 `비교 대상 준비 담당자`다.

### AiRecommendationServiceImpl

역할:

- 파서 결과를 엔진이 이해하는 모델로 변환
- `MatchingEngine` 호출

쉽게 말하면 `중간 어댑터`다.

### MatchingEngine

역할:

- 후보별 점수 계산
- 이유 생성
- 다양성 보정
- 추천 카드 DTO 생성

쉽게 말하면 `최종 추천 결과 생산기`다.

### ScoreCalculator

역할:

- 각 정책 점수 합산

쉽게 말하면 `채점기`다.

---

## 5. request와 response를 쉽게 이해하기

### 입력 request

주요 입력은 [PromptRecommendApiRequest.java](src/main/java/com/team6/module/ai/dto/request/PromptRecommendApiRequest.java) 다.

중요 필드:

- `prompt`: 사용자의 자연어 요청
- `topN`: 몇 명 추천할지
- `guideCandidates`: 선택적 후보 목록
- `desiredTourDate`, `desiredTourDateFrom`, `desiredTourDateTo`: 일정 필터용 날짜

실서비스에서는 보통 `prompt` 중심으로 쓰고, 후보는 서버가 채운다.

### 내부 request

[GuideRecommendRequest.java](src/main/java/com/team6/module/ai/dto/request/GuideRecommendRequest.java)

이건 파싱 이후 추천 엔진에 넘기기 위한 내부 구조다.

예:

- `region`
- `travelStyle`
- `budgetLevel`
- `activityTags`
- `preferredLanguages`
- `durationDays`
- `excludedActivityTags`
- `softPenaltyActivityTags`

### 최종 response

[GuideRecommendResponse.java](src/main/java/com/team6/module/ai/dto/response/GuideRecommendResponse.java)

주요 필드:

- `conceptSummary`
- `keywords`
- `matchRequestDraft`
- `notice`
- `noticeCodes`
- `policyVersion`
- `recommendations`
- `specialSuggestion`

### 추천 카드 1장

[GuideRecommendItem.java](src/main/java/com/team6/module/ai/dto/response/GuideRecommendItem.java)

주요 필드:

- `guideId`
- `guideName`
- `representativeImageUrl`
- `region`
- `priceLevel`
- `averageRating`
- `reviewCount`
- `publicFeedThumbnailUrls`
- `score`
- `reason`
- `reasonCodes`
- `matched`

즉 프론트는 이 객체만으로도 추천 카드 UI를 꽤 많이 그릴 수 있다.

---

## 6. F03에서 AI 모듈이 실제로 하는 일

현재 코드 기준으로 AI 모듈이 직접 하는 일은 여기까지다.

1. 프롬프트 해석
2. 후보군 준비 요청
3. 후보 점수 계산
4. 추천 이유 생성
5. 추천 카드 응답 생성
6. 매칭 요청 화면용 draft 생성

반대로 AI 모듈이 직접 하지 않는 일은 이렇다.

- 가이드 피드 절반 공개
- 매칭 요청 저장
- 가이드 제안 수락/거절 처리
- 결제 처리
- 채팅방 관리

이건 다른 모듈이 맡는다.

---

## 7. AI 모듈과 다른 모듈의 경계

### module-ai

담당:

- 프롬프트 해석
- 추천 점수 계산
- 추천 응답 생성

### module-ai-integration

담당:

- 도메인 DB 데이터를 AI 후보 DTO로 연결
- DB 기반 후보 제공

### module-domain

담당:

- 가이드 피드 조회
- 매칭 요청 생성
- 가이드 제안
- 게스트 수락/거절

### module-chat

담당:

- 채팅방 생성/조회
- 채팅 메시지 처리

즉 이 프로젝트는 `AI가 전부 다 하는 구조`가 아니라, `추천은 AI`, `거래/상태 변경은 domain`, `대화는 chat`으로 나눠져 있다.

---

## 8. specialSuggestion이란?

최신 코드에서는 메인 추천과 별도로 `specialSuggestion`이 있다.

이건 이런 경우를 위한 기능이다.

- 조건은 매우 잘 맞는 가이드가 있음
- 그런데 사용자가 고른 날짜에는 이미 예약이 있음
- 메인 추천에서는 빠짐
- 대신 “일정만 아니면 추천됐을 가이드”를 별도 카드로 보여줌

즉:

- `recommendations` = 지금 바로 추천 가능한 가이드
- `specialSuggestion` = 조건은 맞지만 일정 때문에 메인 추천에서 빠진 가이드

---

## 9. fallback은 왜 필요한가?

사용자가 프롬프트를 너무 빡세게 쓰면 결과가 0개가 될 수 있다.

예:

- 지역도 정확함
- 스타일도 요구
- 활동도 많이 요구
- 언어도 요구
- 후보 수는 적음

이럴 때 그냥 빈 결과를 반환하면 UX가 안 좋다.

그래서 현재는 전략적으로 순서대로 완화한다.

1. 활동 태그 완화
2. 스타일 완화
3. 지역 완화

단, 언어는 중요도가 높다고 보고 유지한다.

즉 fallback은 `추천 실패를 줄이기 위한 안전장치`다.

---

## 10. 현재 F03 구현 상태를 실무적으로 보면

현재 기준으로 F03의 추천 핵심은 거의 구현돼 있다.

구현된 것:

- 프롬프트 입력
- 추천 API
- 파서 고도화
- 후보 DB 조회
- 점수 계산
- 이유 생성
- 카드용 응답 정보
- 매칭 요청 draft 생성
- specialSuggestion

부분 구현 또는 외부 연계 의존:

- 추천 결과 -> 실제 매칭 요청 생성은 프론트 연결 필요
- 게스트 최종 수락 후 자동 채팅방 생성은 AI 모듈 범위 밖

즉 AI 모듈 관점에서는 F03의 추천 파이프라인은 상당히 완성된 편이다.

---

## 11. F03를 공부할 때 추천하는 읽기 방식

처음엔 코드 전체를 다 이해하려 하지 말고, 아래 방식으로 보자.

### 1차 읽기

- `AiController`
- `PromptRecommendationService`
- `MatchingEngine`

목표:

- 전체 큰 흐름 잡기

### 2차 읽기

- `PromptParser`
- `DbBackedGuideCandidateProvider`
- `AiRecommendationMapper`

목표:

- 입력이 어떻게 구조화되고 후보가 어떻게 들어오는지 이해

### 3차 읽기

- `ScoreCalculator`
- 정책 클래스들
- `ReasonGenerator`

목표:

- 왜 이 가이드가 추천됐는지 이해

---

## 12. 한 줄 최종 요약

F03 AI 모듈은 `사용자 프롬프트를 해석하고, 비교할 가이드 후보를 준비한 뒤, 점수를 계산해서 추천 카드와 후속 매칭용 요약 정보를 내려주는 추천 엔진`이다.

---

## 13. F03-01 ~ F03-06 단계별 매핑

| 단계 | 의미 | 관련 API | 주요 클래스 | 현재 상태 | 비고 |
| --- | --- | --- | --- | --- | --- |
| F03-01 | 사용자가 AI 추천용 여행 프롬프트 입력 | `POST /ai/recommend` | [AiController.java](src/main/java/com/team6/module/ai/controller/AiController.java), [PromptRecommendApiRequest.java](src/main/java/com/team6/module/ai/dto/request/PromptRecommendApiRequest.java) | 구현 | 프롬프트, `topN`, 날짜 필드 등을 함께 받을 수 있음 |
| F03-02 | 프롬프트 해석 후 가이드 추천 결과 생성 | `POST /ai/recommend` 내부 처리 | [PromptRecommendationService.java](src/main/java/com/team6/module/ai/service/PromptRecommendationService.java), [PromptParser.java](src/main/java/com/team6/module/ai/parser/PromptParser.java), [DbBackedGuideCandidateProvider.java](../module-ai-integration/src/main/java/com/team6/integration/ai/DbBackedGuideCandidateProvider.java), [MatchingEngine.java](src/main/java/com/team6/module/ai/engine/MatchingEngine.java) | 구현 | 추천 카드, 이유, `conceptSummary`, `matchRequestDraft`, `specialSuggestion`까지 생성 |
| F03-03 | 추천된 가이드 상세/피드 일부 공개 확인 | `GET /guides/{guideId}/feeds?isMatched=false` | [GuideFeedController.java](../module-domain/src/main/java/com/team6/domain/guide/controller/GuideFeedController.java), [GuideFeedService.java](../module-domain/src/main/java/com/team6/domain/guide/service/GuideFeedService.java) | 구현 | 매칭 전에는 절반 공개, 매칭 후에는 전체 공개 |
| F03-04 | 게스트가 특정 가이드에게 매칭 요청 생성 | `POST /matching/requests` | [MatchRequestController.java](../module-domain/src/main/java/com/team6/domain/matching/controller/MatchRequestController.java), [MatchRequestService.java](../module-domain/src/main/java/com/team6/domain/matching/service/MatchRequestService.java) | 구현 | AI 응답의 `matchRequestDraft`를 프론트가 읽어 요청 폼 기본값으로 활용 가능 |
| F03-05 | 가이드가 제시안(일정/메시지) 등록 | `PATCH /matching/requests/{requestId}/propose` | [MatchRequestController.java](../module-domain/src/main/java/com/team6/domain/matching/controller/MatchRequestController.java), [MatchRequestService.java](../module-domain/src/main/java/com/team6/domain/matching/service/MatchRequestService.java) | 구현 | `proposedSchedule`, `proposeMessage` 저장, 상태는 `ACCEPTED` 계열로 이동 |
| F03-06 | 게스트가 가이드 제안을 최종 수락 또는 거절 | `PATCH /matching/requests/{requestId}/accept`, `PATCH /matching/requests/{requestId}/decline` | [MatchRequestController.java](../module-domain/src/main/java/com/team6/domain/matching/controller/MatchRequestController.java), [MatchRequestService.java](../module-domain/src/main/java/com/team6/domain/matching/service/MatchRequestService.java) | 부분 구현 | 수락/거절 API는 있으나, 최종 수락 직후 채팅방 자동 생성까지는 아직 직접 연결되어 있지 않음 |

### 단계별로 아주 짧게 보면

- `F03-01 ~ F03-02`는 주로 `module-ai`, `module-ai-integration`
- `F03-03 ~ F03-06`은 주로 `module-domain`
- 이후 채팅/실시간 대화는 `module-chat`

즉 F03 전체는 하나의 모듈이 다 맡는 게 아니라, 추천과 매칭 진행이 분리된 구조다.

---

## 14. 실제 API 호출 예시

아래 예시는 프론트가 F03 흐름을 따라갈 때 어떻게 호출할 수 있는지 보여주는 샘플이다.

### 14-1. AI 추천 요청

```http
POST /ai/recommend
Content-Type: application/json
Authorization: Bearer <token>
```

```json
{
  "prompt": "제주에서 2박3일 동안 친구랑 가는데 바다랑 카페 위주로 조용하게 여행하고 싶어요. 술집은 말고 영어 가능한 가이드면 좋겠어요.",
  "topN": 3,
  "desiredTourDateFrom": "2026-04-20",
  "desiredTourDateTo": "2026-04-22"
}
```

응답 예시 요약:

```json
{
  "conceptSummary": "[제주] 3일, 친구 · 활동: 바다/카페",
  "matchRequestDraft": {
    "destination": "제주",
    "conceptSummary": "[제주] 3일, 친구 · 활동: 바다/카페",
    "budgetHint": "중간",
    "durationDays": 3,
    "activityTags": ["바다", "카페"],
    "preferredLanguages": ["영어"]
  },
  "recommendations": [
    {
      "guideId": 12,
      "guideName": "제주바다가이드",
      "representativeImageUrl": "https://...",
      "region": "제주",
      "priceLevel": "중간",
      "averageRating": 4.8,
      "reviewCount": 31,
      "publicFeedThumbnailUrls": ["https://...", "https://..."],
      "score": 74,
      "reason": "희망 지역과 일치하고 바다/카페 취향이 잘 맞아요."
    }
  ],
  "specialSuggestion": null
}
```

### 14-2. 추천 가이드 피드 절반 공개 조회

```http
GET /guides/12/feeds?isMatched=false
Authorization: Bearer <token>
```

응답 예시 요약:

```json
[
  {
    "id": 101,
    "content": "제주 바다 카페 코스",
    "imageUrl": "https://...",
    "locked": false
  },
  {
    "id": 102,
    "content": null,
    "imageUrl": null,
    "locked": true
  }
]
```

### 14-3. 매칭 요청 생성

AI 추천 응답의 `matchRequestDraft`와 사용자가 선택한 `guideId`를 조합해서 보낼 수 있다.

```http
POST /matching/requests
Content-Type: application/json
Authorization: Bearer <token>
```

```json
{
  "guideId": 12,
  "destination": "제주",
  "concept": "제주에서 2박3일 동안 친구랑 바다랑 카페 위주로 조용한 여행",
  "conceptSummary": "[제주] 3일, 친구 · 활동: 바다/카페",
  "desiredDate": "2026-04-20",
  "desiredBudget": 200000
}
```

### 14-4. 가이드 제시안 등록

```http
PATCH /matching/requests/55/propose
Content-Type: application/json
Authorization: Bearer <guide-token>
```

```json
{
  "proposedSchedule": "1일차 바다 산책 및 카페, 2일차 로컬 맛집, 3일차 감성 포토 스팟",
  "proposeMessage": "조용한 동선 위주로 맞춰봤어요."
}
```

### 14-5. 게스트 최종 수락

```http
PATCH /matching/requests/55/accept
Authorization: Bearer <guest-token>
```

### 14-6. 게스트 최종 거절

```http
PATCH /matching/requests/55/decline
Content-Type: application/json
Authorization: Bearer <guest-token>
```

```json
{
  "reason": "일정이 맞지 않아서 이번에는 진행하지 않을게요."
}
```

### 14-7. 가이드가 받은 요청 목록 조회

```http
GET /matching/requests/guides/me
Authorization: Bearer <guide-token>
```

이 응답 안에서 `conceptSummary`, `desiredDate`, `desiredBudget` 등을 보고 가이드가 제안을 준비한다.

---

## 15. 자주 헷갈리는 DTO 관계도

F03를 보다 보면 `request`, `candidate`, `profile`, `response`, `draft` 이름이 비슷해서 자주 헷갈린다.

아래처럼 단계별로 나눠서 보면 이해가 쉽다.

### 15-1. 가장 큰 그림

```text
프론트 요청 JSON
  -> PromptRecommendApiRequest
  -> PromptParser
  -> GuideRecommendRequest
  -> AiRecommendationMapper
  -> TravelerPreference / GuideAiProfile
  -> MatchingEngine
  -> GuideRecommendItem
  -> GuideRecommendResponse
  -> 프론트 추천 카드 UI
  -> matchRequestDraft 재사용
  -> MatchRequestCreateRequest
```

즉 DTO가 한 개만 있는 게 아니라,

- API 입구용 DTO
- 추천 계산용 내부 DTO
- 엔진용 모델
- API 응답 DTO
- 매칭 요청용 DTO

이렇게 역할별로 나뉘어 있다.

### 15-2. API 입구 DTO

[PromptRecommendApiRequest.java](src/main/java/com/team6/module/ai/dto/request/PromptRecommendApiRequest.java)

역할:

- 프론트가 `/ai/recommend` 호출할 때 보내는 입력값을 받음

주요 필드:

- `prompt`
- `topN`
- `guideCandidates`
- `desiredTourDate`
- `desiredTourDateFrom`
- `desiredTourDateTo`

헷갈리기 쉬운 포인트:

- 이 객체는 `프론트 요청 원본`이다.
- 아직 추천 계산용으로 정리된 상태가 아니다.

### 15-3. 추천 계산용 내부 DTO

[GuideRecommendRequest.java](src/main/java/com/team6/module/ai/dto/request/GuideRecommendRequest.java)

역할:

- `PromptParser`가 프롬프트를 해석한 뒤 만드는 내부 추천 요청 객체

주요 필드:

- `region`
- `travelStyle`
- `budgetLevel`
- `companionType`
- `activityTags`
- `preferredLanguages`
- `headcount`
- `durationDays`
- `excludedActivityTags`
- `softPenaltyActivityTags`
- `guideCandidates`

헷갈리기 쉬운 포인트:

- 이름에 `Request`가 들어가지만, 프론트가 직접 보내는 API request와는 다르다.
- 이건 `추천 엔진에 넘기기 위한 내부 구조`다.

### 15-4. 후보 가이드 DTO

[GuideRecommendRequest.GuideCandidateDto](src/main/java/com/team6/module/ai/dto/request/GuideRecommendRequest.java)

역할:

- 추천 비교 대상이 되는 가이드 1명의 요약 프로필

주요 필드:

- `guideId`
- `guideName`
- `region`
- `guideStyle`
- `priceLevel`
- `specialtyTags`
- `languages`
- `averageRating`
- `reviewCount`
- `approvedRefundCount`
- `matchRequestCount`
- `progressedMatchCount`
- `chatStartCount`

어디서 만들어지나:

- 보통 [DbBackedGuideCandidateProvider.java](../module-ai-integration/src/main/java/com/team6/integration/ai/DbBackedGuideCandidateProvider.java)
- 내부적으로 [GuideProfileAiCandidateMapper.java](../module-ai-integration/src/main/java/com/team6/integration/ai/GuideProfileAiCandidateMapper.java) 를 거침

헷갈리기 쉬운 포인트:

- 이건 도메인 엔티티 `GuideProfile` 자체가 아니다.
- 엔진에 넘기기 좋게 정제된 `후보용 DTO`다.

### 15-5. 엔진용 모델

[TravelerPreference.java](src/main/java/com/team6/module/ai/model/TravelerPreference.java)

[GuideAiProfile.java](src/main/java/com/team6/module/ai/model/GuideAiProfile.java)

역할:

- `MatchingEngine`가 직접 사용하는 모델

변환 담당:

- [AiRecommendationMapper.java](src/main/java/com/team6/module/ai/support/AiRecommendationMapper.java)

헷갈리기 쉬운 포인트:

- `GuideCandidateDto`와 `GuideAiProfile`은 비슷해 보이지만 다르다.
- `GuideCandidateDto`는 API/서비스 경계 쪽 DTO
- `GuideAiProfile`은 엔진 내부 모델

즉 관계는 이렇다.

```text
GuideCandidateDto -> GuideAiProfile
GuideRecommendRequest -> TravelerPreference
```

### 15-6. 추천 카드 DTO

[GuideRecommendItem.java](src/main/java/com/team6/module/ai/dto/response/GuideRecommendItem.java)

역할:

- 프론트가 카드 UI를 그릴 때 사용하는 추천 결과 1건

주요 필드:

- `guideId`
- `guideName`
- `representativeImageUrl`
- `region`
- `priceLevel`
- `averageRating`
- `reviewCount`
- `publicFeedThumbnailUrls`
- `score`
- `reason`
- `reasonCodes`
- `reasonFacts`
- `matched`

헷갈리기 쉬운 포인트:

- 이건 `후보 가이드 DTO`가 아니다.
- 점수 계산과 이유 생성이 끝난 `최종 추천 카드`다.

### 15-7. 최종 응답 DTO

[GuideRecommendResponse.java](src/main/java/com/team6/module/ai/dto/response/GuideRecommendResponse.java)

역할:

- `/ai/recommend`의 최종 응답 전체

주요 필드:

- `conceptSummary`
- `keywords`
- `matchRequestDraft`
- `notice`
- `noticeCodes`
- `promptParseConfidence`
- `policyVersion`
- `recommendations`
- `specialSuggestion`

즉 관계는:

```text
GuideRecommendResponse
  └─ recommendations: List<GuideRecommendItem>
```

### 15-8. 매칭 요청 연결용 draft DTO

[GuideRecommendResponse.MatchRequestDraft](src/main/java/com/team6/module/ai/dto/response/GuideRecommendResponse.java)

역할:

- AI 추천 응답을 매칭 요청 생성 화면으로 자연스럽게 넘기기 위한 초안 데이터

주요 필드:

- `destination`
- `concept`
- `conceptSummary`
- `budgetHint`
- `headcount`
- `durationDays`
- `travelStyle`
- `companionType`
- `activityTags`
- `excludedActivityTags`
- `preferredLanguages`

헷갈리기 쉬운 포인트:

- 이건 실제 매칭 요청 저장 DTO가 아니다.
- 프론트가 읽어서 `MatchRequestCreateRequest`로 옮겨 담기 쉽게 만든 중간 재료다.

즉 관계는:

```text
GuideRecommendResponse.matchRequestDraft
  -> 프론트가 읽음
  -> MatchRequestCreateRequest 로 매핑
```

### 15-9. 실제 매칭 요청 DTO

[MatchRequestCreateRequest.java](../module-domain/src/main/java/com/team6/domain/matching/dto/request/MatchRequestCreateRequest.java)

역할:

- `/matching/requests` 생성 API가 실제로 받는 DTO

주요 필드:

- `guideId`
- `destination`
- `concept`
- `conceptSummary`
- `desiredDate`
- `desiredBudget`

헷갈리기 쉬운 포인트:

- AI 응답 안의 `matchRequestDraft`와 완전히 같은 객체가 아니다.
- 프론트가 필요한 값만 골라서 최종 요청으로 다시 조립해야 한다.

---

## 16. DTO 관계를 한 번에 보면

```text
[프론트 입력]
PromptRecommendApiRequest

    |
    v

[파서 결과 / 내부 추천 요청]
GuideRecommendRequest
  └─ GuideCandidateDto

    |
    v

[엔진 전용 모델]
TravelerPreference
GuideAiProfile

    |
    v

[추천 결과 카드]
GuideRecommendItem

    |
    v

[최종 AI 응답]
GuideRecommendResponse
  ├─ recommendations: List<GuideRecommendItem>
  └─ matchRequestDraft

    |
    v

[프론트가 재조립]
MatchRequestCreateRequest
```

이 흐름만 기억해도 DTO가 왜 여러 개 있는지 훨씬 덜 헷갈린다.

---

## 17. 클래스 관계도

이번에는 DTO가 아니라 `클래스가 서로 어떻게 연결되는지`를 본다.

즉 "누가 누구를 호출하는가?"를 따라가는 섹션이다.

### 17-1. 가장 큰 클래스 흐름

```text
AiController
  -> GuideCandidateProvider
      -> DbBackedGuideCandidateProvider
          -> GuideProfileRepository
          -> GuideFeedRepository
          -> GuideCareerRepository
          -> MatchRequestRepository
          -> RefundRepository
          -> ChatRoomRepository
          -> GuideProfileAiCandidateMapper
  -> PromptRecommendationService
      -> PromptParser
      -> RegionCandidateExpansion
      -> AiRecommendationService
          -> AiRecommendationServiceImpl
              -> AiRecommendationMapper
              -> MatchingEngine
                  -> ScoreCalculator
                      -> RegionMatchPolicy
                      -> StyleMatchPolicy
                      -> BudgetMatchPolicy
                      -> ActivityMatchPolicy
                      -> LanguageMatchPolicy
                      -> FeedbackMatchPolicy
                  -> ReasonGenerator
```

이 그림이 F03 추천 핵심 구조다.

### 17-2. 가장 바깥쪽 입구

[AiController.java](src/main/java/com/team6/module/ai/controller/AiController.java)

역할:

- API 요청 받기
- 후보군 준비
- 추천 실행
- specialSuggestion 조립
- 최종 응답 반환

즉 클래스 관계의 시작점은 항상 `AiController`다.

### 17-3. 후보군 준비 라인

```text
AiController
  -> GuideCandidateProvider
      -> DbBackedGuideCandidateProvider
          -> PromptParser
          -> 각종 Repository
          -> GuideProfileAiCandidateMapper
```

설명:

- `AiController`는 인터페이스인 `GuideCandidateProvider`만 안다.
- 실제 기본 구현은 `DbBackedGuideCandidateProvider`
- 여기서 DB에서 가이드 후보를 읽고 AI 후보 DTO로 만든다.

즉 이 라인은 `추천 대상 만들기` 라인이다.

### 17-4. 추천 흐름 조립 라인

```text
AiController
  -> PromptRecommendationService
      -> PromptParser
      -> RegionCandidateExpansion
      -> AiRecommendationService
```

설명:

- `PromptRecommendationService`는 추천의 전체 순서를 관리한다.
- 프롬프트를 파싱하고
- 지역이 부족하면 인접 지역을 확장하고
- 필요하면 fallback을 돌리고
- 최종 응답을 조립한다.

즉 이 라인은 `추천 흐름 관리` 라인이다.

### 17-5. 엔진 호출 라인

```text
PromptRecommendationService
  -> AiRecommendationServiceImpl
      -> AiRecommendationMapper
      -> MatchingEngine
```

설명:

- `AiRecommendationServiceImpl`은 엔진 바로 앞 단계의 어댑터다.
- 파서 결과를 엔진용 모델로 바꾸고
- `MatchingEngine`을 호출한다.

즉 이 라인은 `엔진에 넣기 전 변환` 라인이다.

### 17-6. 점수 계산 라인

```text
MatchingEngine
  -> ScoreCalculator
      -> RegionMatchPolicy
      -> StyleMatchPolicy
      -> BudgetMatchPolicy
      -> ActivityMatchPolicy
      -> LanguageMatchPolicy
      -> FeedbackMatchPolicy
```

설명:

- `MatchingEngine`은 각 가이드에게 점수를 매겨야 하므로
- `ScoreCalculator`에 계산을 맡긴다
- `ScoreCalculator`는 정책 클래스를 각각 호출해 점수를 더한다

즉 이 라인은 `채점` 라인이다.

### 17-7. 추천 이유 생성 라인

```text
MatchingEngine
  -> ReasonGenerator
```

설명:

- 점수만 있으면 프론트나 사용자 입장에서 이유를 알 수 없다
- 그래서 `ReasonGenerator`가
  - 사람용 이유 문장
  - 코드형 이유
  - 구조화된 근거값
  을 만든다

즉 이 라인은 `설명 가능한 추천` 라인이다.

### 17-8. 응답 생성 라인

```text
MatchingEngine
  -> GuideRecommendItem
PromptRecommendationService
  -> GuideRecommendResponse
```

설명:

- `MatchingEngine`은 추천 카드 1장씩(`GuideRecommendItem`) 만든다
- `PromptRecommendationService`는 그 카드 리스트를 감싸서
  `GuideRecommendResponse`를 만든다

즉 관계는:

```text
GuideRecommendItem = 카드 1장
GuideRecommendResponse = 카드 묶음 + 부가 정보
```

### 17-9. AI 모듈 밖으로 이어지는 라인

추천 응답이 끝나면 다음 단계는 다른 모듈로 넘어간다.

```text
GuideRecommendResponse
  -> 프론트
      -> GuideFeedController / GuideFeedService
      -> MatchRequestController / MatchRequestService
      -> ChatRoomController / ChatRoomService (이후 단계)
```

설명:

- 피드 절반 공개는 `guide`
- 매칭 요청 생성/수락/거절은 `matching`
- 채팅은 `chat`

즉 AI 추천이 끝난 뒤의 흐름은 AI 모듈 밖에서 이어진다.

---

## 18. 클래스를 층(layer)으로 보면

F03를 층으로 이해하면 훨씬 덜 헷갈린다.

### 18-1. API 층

- `AiController`

역할:

- 요청 받고 응답 반환

### 18-2. 흐름 조립 층

- `PromptRecommendationService`
- `DbBackedGuideCandidateProvider`

역할:

- 후보군 준비
- 추천 순서 조립
- 응답 포장

### 18-3. 파싱 층

- `PromptParser`
- `KeywordNormalizer`

역할:

- 프롬프트 해석
- 표현 정규화

### 18-4. 엔진 층

- `AiRecommendationServiceImpl`
- `MatchingEngine`
- `ScoreCalculator`
- `ReasonGenerator`

역할:

- 점수 계산
- 이유 생성
- 최종 카드 생성

### 18-5. 정책 층

- `RegionMatchPolicy`
- `StyleMatchPolicy`
- `BudgetMatchPolicy`
- `ActivityMatchPolicy`
- `LanguageMatchPolicy`
- `FeedbackMatchPolicy`

역할:

- 각각의 기준으로 점수 계산

### 18-6. 도메인 연동 층

- `DbBackedGuideCandidateProvider`
- `GuideProfileAiCandidateMapper`

역할:

- DB 데이터를 AI 후보 데이터로 번역

---

## 19. 클래스 관계를 외우는 가장 쉬운 문장

아래 한 문장만 기억해도 관계가 많이 정리된다.

```text
AiController가 요청을 받고,
PromptRecommendationService가 흐름을 조립하고,
PromptParser가 문장을 해석하고,
DbBackedGuideCandidateProvider가 후보를 준비하고,
AiRecommendationServiceImpl이 엔진용 모델로 바꾸고,
MatchingEngine이 점수와 이유를 만들어 최종 추천 카드를 만든다.
```

이 문장을 기준으로 각 파일을 다시 보면 구조가 훨씬 덜 복잡하게 느껴진다.
