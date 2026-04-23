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
            guideBullets는 가이드에게 보여줄 짧은 한국어 반말 불릿(한 줄당 한 가지). specialRequests는 따로 전달해야 할 문단이 있을 때만(없으면 null).
            모르면 null. 배열은 JSON 배열(빈 배열 가능). region은 한국어 지역명이면 그대로(예: 제주, 서울, 부산). 숫자는 정수.
            연락처·이메일·SNS·상세 주소는 넣지 말 것.
            """;
}
