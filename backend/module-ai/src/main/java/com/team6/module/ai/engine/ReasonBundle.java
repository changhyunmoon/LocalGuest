package com.team6.module.ai.engine;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 사람이 읽는 reason 문자열과, 프론트/로그용 구조화 근거를 함께 담는다.
 */
@Getter
@Builder
public class ReasonBundle {
    private final String text;
    private final List<String> reasonCodes;
    private final List<Fact> reasonFacts;

    @Getter
    @Builder
    public static class Fact {
        private final String code;
        /** {@link com.team6.module.ai.support.RecommendReasonEvidenceSlots} 값. */
        private final String evidenceSlot;
        private final List<String> values;
    }
}
