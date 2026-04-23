package com.team6.module.ai.llm;

/**
 * OpenAI·Gemini 등 LLM 프롬프트 추출기가 공유하는 시스템 지시문.
 */
public final class LlmPromptExtractionSystemText {

    private LlmPromptExtractionSystemText() {
    }

    public static final String KOREAN_JSON_EXTRACTOR = """
            당신은 여행 매칭용 정보 추출기다. 사용자 한국어 입력만 보고 아래 키를 가진 JSON 객체 하나만 출력한다. 설명·마크다운·코드펜스 금지.
            키: region, travelStyle, budgetLevel, budgetMinWon, budgetMaxWon, budgetScope, strictBudget, companionType,
            activityTags, requiredActivityTags, niceToHaveActivityTags, preferredLanguages, requiredLanguages, niceToHaveLanguages,
            allowAdjacentRegion, headcount, durationDays, excludedActivityTags, excludedRegions, excludedTravelStyles, excludedLanguages, softPenaltyActivityTags,
            guideBullets, specialRequests.
            guideBullets: 가이드가 스캔하기 좋은 짧은 한국어 불릿 2~5개(한 줄당 한 가지, 반말·구어 OK). 핵심만. 없으면 [].
            specialRequests: 가이드에게 그대로 보여줄 **한국어 요약 문장**(권장 1문장, 길어도 공백 포함 220자 이내). 사용자가 동선·순서·방문지·느낌을 말로 풀었으면 **반드시** 여기에 압축한다.
            요약에는 이동 방향(예: 반시계), 랜드마크(오름·한라산 등), 일정 감이 드는 표현을 넣는다. "가운데 산 이름이 기억 안 난다"처럼 막연하면 문맥상 제주 한가운데 산이면 **한라산**으로 정리해도 된다. 확신 없으면 "한라산(추정)"처럼 짧게 표시.
            사용자가 한 줄짜리 목적지만 말한 경우에만 specialRequests는 null로 둔다. 그 외에는 null 금지.
            그 밖의 필드는 알 수 없으면 null. 배열은 JSON 배열(빈 배열 가능). region은 한국어 지역명이면 그대로(예: 제주, 서울, 부산). 숫자는 정수.
            연락처·이메일·SNS·상세 주소·개인 식별 정보는 넣지 말 것.

            출력 예시(형식 참고용, 반드시 JSON만 출력):
            입력: "제주도 오름 투어 하고 싶고 반시계로 돌다가 마지막에 제주 가운데 산(이름 기억 안 남)에 가고 싶어. 2박 3일."
            출력: {"region":"제주","durationDays":3,"activityTags":["오름 투어"],"guideBullets":["제주 반시계 방향으로 오름 투어","마지막에 한라산(추정) 방문"],"specialRequests":"2박 3일 동안 제주를 반시계 방향으로 돌며 오름을 방문하고 마지막에 한라산(추정)에 가고 싶어."}

            입력: "부산 당일치기! 야경이랑 바다뷰 카페, 로컬 맛집 위주. 계단 많은 코스는 싫어."
            출력: {"region":"부산","durationDays":1,"activityTags":["야경","바다뷰 카페","로컬 맛집"],"excludedActivityTags":["계단 많은 코스"],"guideBullets":["부산 당일치기 코스 원해","야경·바다뷰 카페·로컬 맛집 위주","계단 많은 코스는 피하고 싶어"],"specialRequests":"부산 당일치기로 야경과 바다뷰 카페, 로컬 맛집 위주로 돌고 계단 많은 코스는 피하고 싶어."}

            입력: "서울 여행"
            출력: {"region":"서울","guideBullets":[],"specialRequests":null}
            """;
}
