# CLAUDE.md — LocalGuest Team6 프로젝트 지침

> Claude Code가 이 프로젝트에서 작업할 때 반드시 따라야 할 규칙 모음입니다.
> 이 파일을 항상 먼저 읽고 작업을 시작하세요.

---

## 0. 세션 시작 시 필수 절차

> 매번 새 세션을 시작할 때 아래 순서를 반드시 따릅니다.

1. 이 CLAUDE.md 파일을 끝까지 읽는다
2. `module-domain/` 폴더 구조를 확인한다
3. `guide/` 폴더가 없으면 신규 생성한다
4. 작업 전 현재 구조를 나(수)에게 보여주고 확인받은 뒤 진행한다

```
# 세션 시작 시 Claude Code에게 할 말 (복붙용)
CLAUDE.md 읽고 시작해.
그 다음 module-domain 폴더 구조 보여줘.
내가 확인하고 나서 진행할게.
```

---

## 1. 프로젝트 개요

- **프로젝트명**: LocalGuest (Team 6)
- **목적**: 여행자 ↔ 현지 가이드 매칭 플랫폼
- **기술 스택**: Spring Boot / Java 17 / Gradle 멀티모듈 / MySQL + MongoDB + Redis / JPA / WebSocket
- **프로젝트 경로**: `C:\Team6LocalGuest_3\backend\`

---

## 2. 전체 멀티모듈 구조

```
C:\Team6LocalGuest_3
└── backend/
    ├── api-server/       ← 진입점 (Spring Boot 앱)
    ├── module-ai/        ← AI 추천/매칭 알고리즘
    ├── module-chat/      ← 채팅 (WebSocket + Redis Pub/Sub)
    ├── module-common/    ← 공통 유틸
    ├── module-domain/    ← 핵심 도메인
    ├── build.gradle      ← 창현(팀장) 전담, 절대 수정 금지
    └── settings.gradle   ← 창현(팀장) 전담, 절대 수정 금지
```

### 각 모듈 현재 상태

| 모듈 | 상태 | 주요 내용 |
|---|---|---|
| `api-server` | 존재 | AiController, GuideCandidateProvider, RequestGuideCandidateProvider |
| `module-ai` | 존재 | MatchingEngine, ScoreCalculator, ReasonGenerator, AiRecommendationService, 각종 Policy |
| `module-chat` | 존재 | WebSocket + Redis Pub/Sub, MongoDB(ChatMessage) + MySQL(ChatRoom, ChatParticipant) |
| `module-common` | 존재 | BaseTimeEntity |
| `module-domain/matching` | 존재 | MatchRequest, Payment, Refund, TourExtension 엔티티, DTO 6개, enum, 예외처리 |
| `module-domain/guide` | **미존재 → 수가 신규 생성** | F06 가이드 프로필 시스템 |

### 모듈 의존 방향 (단방향 엄수)

```
api-server
    ↓
module-domain
module-ai        ← domain 접근 가능
module-chat      ← domain 접근 가능
    ↓
module-common
```

> ❌ domain → ai 금지
> ❌ domain → chat 금지
> ❌ 순환 참조 금지

---

## 3. module-domain 내부 구조

```
module-domain/
├── matching/           ← 기존 존재 (절대 건드리지 말 것)
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   ├── dto/
│   ├── enum/
│   └── exception/
│
└── guide/              ← ✅ 수(Soo)가 신규 생성하는 담당 범위
    ├── controller/
    ├── service/
    ├── repository/
    ├── entity/
    ├── dto/
    │   ├── request/
    │   └── response/
    ├── mapper/
    └── exception/
```

---

## 4. module-ai 내부 구조 (참고용)

```
module-ai/
├── parser/       ← PromptParser
├── engine/       ← MatchingEngine, ScoreCalculator, ReasonGenerator
├── policy/       ← ActivityMatchPolicy, BudgetMatchPolicy, LanguageMatchPolicy,
│                    RegionMatchPolicy, StyleMatchPolicy, ScoreWeight
├── service/      ← AiRecommendationService/Impl, PromptRecommendationService
└── model/        ← GuideAiProfile, TravelerPreference
```

---

## 5. module-chat 내부 구조 (참고용)

```
module-chat/
├── controller/   ← ChatRoomController, ChatMessageController
├── service/
├── repository/
├── entity/
├── dto/
├── websocket/
└── messaging/
```

---

## 6. guide 도메인 ERD 및 구현 범위

> ⚠️ ERD에 정의된 컬럼만 구현한다. ERD에 없는 필드/기능은 임의로 추가하지 않는다.
> 추가가 필요하면 반드시 수(Soo)에게 먼저 확인을 받는다.

### ERD 테이블 정의 (구현 기준)

#### guide_profiles
```sql
CREATE TABLE guide_profiles (
    guide_id        BIGINT NOT NULL AUTO_INCREMENT,
    user_id         BIGINT NOT NULL,
    nickname        VARCHAR(50) NOT NULL,
    profile_image   VARCHAR(500) NULL,
    bio             TEXT NULL,
    region          VARCHAR(100) NOT NULL,
    language        VARCHAR(200) NOT NULL,
    price_per_hour  DECIMAL(10,2) NOT NULL,
    average_rating  DECIMAL(3,2) NULL,
    review_count    INT NOT NULL DEFAULT 0,
    is_approved     BOOLEAN NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (guide_id)
);
```

#### guide_images
```sql
CREATE TABLE guide_images (
    image_id    BIGINT NOT NULL AUTO_INCREMENT,
    guide_id    BIGINT NOT NULL,
    image_url   VARCHAR(500) NOT NULL,
    sort_order  INT NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (image_id),
    FOREIGN KEY (guide_id) REFERENCES guide_profiles(guide_id)
);
```

#### guide_careers
```sql
CREATE TABLE guide_careers (
    career_id   BIGINT NOT NULL AUTO_INCREMENT,
    guide_id    BIGINT NOT NULL,
    title       VARCHAR(200) NOT NULL,
    description TEXT NULL,
    acquired_at DATE NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (career_id),
    FOREIGN KEY (guide_id) REFERENCES guide_profiles(guide_id)
);
```

### Entity 구현 기준

| Entity | 테이블 | 구현할 필드 |
|---|---|---|
| `GuideProfile` | guide_profiles | guideId, userId, nickname, profileImage, bio, region, language, pricePerHour, averageRating, reviewCount, isApproved, isActive, createdAt, updatedAt |
| `GuideImage` | guide_images | imageId, guideId, imageUrl, sortOrder, createdAt |
| `GuideCareer` | guide_careers | careerId, guideId, title, description, acquiredAt, createdAt |

> ❌ ERD에 없는 필드(ex. residenceYears, localStory, isMain 등)는 구현하지 않는다
> ❌ ERD에 없는 테이블/연관관계는 임의로 추가하지 않는다

---

## 7. guide 도메인 상세 파일 구조

> `module-domain/src/main/java/.../guide/` 를 신규 생성합니다.

```
guide/

├── controller/
│   ├── GuideProfileController.java       // 프로필 조회/수정 API
│   ├── GuideImageController.java         // 이미지 API
│   └── GuideCareerController.java        // 경력 API
│
├── service/
│   ├── GuideProfileService.java
│   ├── GuideImageService.java
│   └── GuideCareerService.java
│
├── repository/
│   ├── GuideProfileRepository.java       // guide_profiles 테이블
│   ├── GuideImageRepository.java         // guide_images 테이블
│   └── GuideCareerRepository.java        // guide_careers 테이블
│
├── entity/
│   ├── GuideProfile.java                 // guide_profiles ERD 컬럼만
│   ├── GuideImage.java                   // guide_images ERD 컬럼만
│   └── GuideCareer.java                  // guide_careers ERD 컬럼만
│
├── dto/
│   ├── request/
│   │   ├── CreateGuideProfileRequest.java
│   │   ├── UpdateGuideProfileRequest.java
│   │   ├── CreateGuideImageRequest.java
│   │   ├── CreateGuideCareerRequest.java
│   │   └── UpdateGuideCareerRequest.java
│   └── response/
│       ├── GuideProfileResponse.java
│       ├── GuideImageResponse.java
│       └── GuideCareerResponse.java
│
├── mapper/
│   └── GuideMapper.java
│
└── exception/
    ├── GuideException.java
    └── GuideErrorCode.java
```

---

## 8. application.yml 작성 전략

### 기본 원칙
- 각 모듈은 자신의 `src/main/resources/` 하위에 yml 파일을 소유한다
- 다른 모듈의 설정이 필요하면 중복을 허용하고 가져온다 (공유 파일 만들지 않음)
- `api-server/application.yml` 은 각 모듈의 yml을 조합하는 compositor 역할만 한다
- `application-local.yml` 은 `.gitignore` 에 반드시 포함 (PR 체크리스트 항목)

### 네이밍 규칙

| 파일명 | 용도 |
|---|---|
| `application-{모듈명}.yml` | 모든 환경 공통 설정 (변하지 않는 기본값) |
| `application-{모듈명}-local.yml` | 로컬 개발 환경 — 외부 연동에 필요한 설정 (로컬 DB, 로컬 Redis 등) |
| `application-{모듈명}-prod.yml` | 배포용 — 운영 MySQL, 실제 API Key 등 (창현이 GitHub Secrets로 관리) |

### module-domain 예시

```yaml
# application-domain.yml (공통 — JPA 전략 등)
spring:
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false

# application-domain-local.yml (로컬 — 외부 연동 설정)
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/localguest
    username: root
    password: (로컬 비밀번호)
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true

# application-domain-prod.yml (배포용 MySQL — 창현 관리)
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
```

### api-server/application.yml (compositor 역할만)

```yaml
spring:
  profiles:
    include:
      - domain
      - ai
      - chat
      - common
```

---

## 9. Claude Code 행동 규칙 (필수 준수)

### ✅ 해도 되는 것
- `module-domain/guide/` 하위 파일 생성/수정 (신규 생성 포함)
- `api-server` 에서 guide 관련 라우팅 추가 (창현 확인 후)

### ❌ 절대 건드리지 말 것
- `build.gradle` (루트 및 각 모듈) — 창현(팀장) 전담
- `settings.gradle`, `gradle.properties` 등 루트 설정 파일
- `module-domain/matching/` — 다른 팀원 담당, 기존 코드 존재
- `module-ai/` — 다른 팀원 담당
- `module-chat/` — 다른 팀원 담당
- `*-prod.yml` 파일 — 창현이 GitHub Secrets로 관리

### ❌ ERD에 없는 것은 구현하지 말 것
- ERD에 정의되지 않은 컬럼 추가 금지
- ERD에 정의되지 않은 테이블/연관관계 추가 금지
- 기능 확장이 필요하면 먼저 수(Soo)에게 확인 후 진행

### 코드 작성 규칙
1. **한국어 인라인 주석 필수** — 모든 메서드, 필드에 `// 설명` 형태로 달기
2. **userId는 JWT에서 추출** — 절대 하드코딩 금지
   ```java
   // 올바른 방법
   Long userId = jwtTokenProvider.getUserId(token);

   // 하드코딩 금지
   Long userId = 1L;
   ```
3. **공통 응답 포맷 사용** — `module-common`의 `ApiResponse<T>` 사용
4. **예외는 GuideErrorCode로 관리** — `guide/exception/` 하위에서 정의
5. **Entity에 비즈니스 로직 금지** — Service 계층에서 처리
6. **BaseTimeEntity 상속** — `module-common`의 `BaseTimeEntity` 상속해서 생성/수정 시각 자동 관리

---

## 10. 의존성 최소화 원칙

> 나중에 matching, ai, chat이 연동될 때 guide 코드를 최대한 건드리지 않아도 되도록,
> 처음부터 결합도를 낮게 유지합니다.

### 엔티티 간 직접 참조 금지

```java
// 잘못된 방법 — MatchRequest 엔티티를 직접 참조
@ManyToOne
@JoinColumn(name = "match_request_id")
private MatchRequest matchRequest;

// 올바른 방법 — ID(Long)만 보관
@Column(name = "user_id")
private Long userId;
```

### 다른 도메인 Service 직접 주입 금지

```java
// 잘못된 방법
@Service
public class GuideProfileService {
    private final MatchingService matchingService; // 금지
}

// 올바른 방법 — 이벤트 발행으로 간접 협력
@Service
public class GuideProfileService {
    private final ApplicationEventPublisher eventPublisher;
}
```

### DTO 도메인 간 공유 금지

```java
// 잘못된 방법
import com.localguest.matching.dto.response.MatchRequestResponse; // 금지

// 올바른 방법 — guide 자신의 DTO에 필요한 필드만 정의
public class GuideProfileResponse {
    private Long userId;
    private String nickname;
}
```

### 인터페이스로 외부 의존 격리

```java
// guide/service/ 에 인터페이스 정의
public interface GuideProfileQueryService {
    GuideProfileResponse findById(Long guideId);
}

@Service
public class GuideProfileQueryServiceImpl implements GuideProfileQueryService { ... }
```

---

## 11. Git 컨벤션

### 11.1 이슈 운영 규칙

형식: `태그/파트: 작업 내용 요약`

```
Feat/be: 가이드 프로필 조회 API 구현
Fix/be: 프로필 이미지 저장 오류 수정
Chore/root: .gitignore application-local.yml 추가
```

이슈 템플릿:
```
📝 작업 내용
이번 이슈에서 처리할 작업 내용을 간단하게 작성해주세요

⚙️ 상세 설명
작업 방식이나 로직에 대한 추가 설명이 필요하다면 적어주세요.
참고할 링크나 UI 디자인(Figma)이 있다면 첨부해 주세요.

📅 예상 마감 기한
2026-0X-XX
```

### 11.2 브랜치 생성 규칙

형식: `Type/[be|fe|root]#이슈번호`

| Type | 용도 |
|---|---|
| `Feat/` | 새로운 기능 개발 |
| `Fix/` | 버그 수정 |
| `Refactor/` | 코드 개선 (기능 변화 없음) |
| `Docs/` | 문서 작업 |
| `Chore/` | 단순 설정 변경 |

브랜치 예시:
```
Feat/be#12     ← 백엔드 기능
Fix/be#21      ← 버그 수정
Feat/root#01   ← 공통 설정
```

### 11.3 커밋 메시지 규칙

형식: `Type/Scope#이슈번호: 작업 내용 요약`

| Scope | 의미 | 대상 |
|---|---|---|
| `be` | 백엔드 | `backend/` |
| `fe` | 프런트엔드 | `frontend/` |
| `root` | 공통/루트 | `.gitignore`, `README.md` 등 |

커밋 예시:
```bash
git commit -m "Feat/be#12: 가이드 프로필 조회 API 구현"
git commit -m "Fix/be#21: 프로필 이미지 저장 오류 수정"
git commit -m "Refactor/be#15: GuideProfileService 리팩토링"
git commit -m "Chore/root#1: .gitignore application-local.yml 추가"
```

### 11.4 PR 규칙

제목 형식: `[Type/Scope#이슈번호] 작업 내용 요약`

```
[Feat/be#12] 가이드 프로필 조회 API 구현
[Fix/be#21] 프로필 이미지 저장 오류 수정
```

PR 템플릿:
```
🔍 관련 이슈
#이슈번호 (예: #12)

💡 주요 변경 사항
작업한 핵심 내용을 간단히 요약해 주세요.

⚠️ 주의 사항 / 질문
리뷰어가 중점적으로 봐주었으면 하는 부분이나 고민되는 지점을 적어주세요.

✅ 체크리스트
- [ ] 빌드가 정상적으로 되는가?
- [ ] 컨벤션에 맞게 커밋 메시지를 작성했는가?
- [ ] .gitignore에 설정 파일이 잘 포함되었는가? (application-local.yml 등)
```

**PR 추가 규칙**
- **기능 하나씩 잘게 쪼개서 PR** — 여러 기능 묶어서 한번에 PR 금지
  - 예: 엔티티 생성 → PR / 조회 API → PR / 수정 API → PR
- `develop` 브랜치로 PR
- **PR 전에 반드시 `develop` 최신 코드 pull 받고 충돌 확인**
- 창현(팀장) 리뷰 후 머지

---

## 12. 주의사항

- Claude Code가 `build.gradle`이나 루트 설정을 수정했다면 **커밋 전에 반드시 창현에게 확인**받을 것
- 새로운 의존성 추가가 필요하면 직접 추가하지 말고 창현에게 요청할 것
- `prod` 환경 설정값(DB URL, 비밀번호, API Key 등)은 코드에 절대 넣지 말 것
- `module-domain/matching/` 은 이미 존재하는 코드이므로 절대 건드리지 말 것
- 창현이 `build.gradle` 수정 후 merge했으면 작업 전 반드시 `git pull` 먼저 할 것
- `application-local.yml` 은 `.gitignore` 에 포함되어야 함 — 절대 커밋하지 말 것
- **ERD에 없는 필드/테이블은 절대 임의로 추가하지 말 것** — 충돌 및 스키마 불일치 원인
