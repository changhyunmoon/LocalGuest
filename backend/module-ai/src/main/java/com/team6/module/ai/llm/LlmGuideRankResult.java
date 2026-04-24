package com.team6.module.ai.llm;

import java.util.List;
import java.util.Map;

/**
 * LLM 순위 호출이 성공했을 때의 정규화 결과.
 */
public record LlmGuideRankResult(List<Long> orderedGuideIds, Map<Long, String> reasonByGuideId) {
}
