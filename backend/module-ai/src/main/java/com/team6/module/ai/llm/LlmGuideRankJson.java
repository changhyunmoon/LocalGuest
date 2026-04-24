package com.team6.module.ai.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 등에서 오는 가이드 순위 LLM 응답(JSON object).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmGuideRankJson {

    @JsonProperty("orderedGuideIds")
    private List<Long> orderedGuideIds = new ArrayList<>();

    @JsonProperty("reasons")
    private Map<String, String> reasons = new LinkedHashMap<>();

    public List<Long> getOrderedGuideIds() {
        return orderedGuideIds == null ? List.of() : orderedGuideIds;
    }

    public void setOrderedGuideIds(List<Long> orderedGuideIds) {
        this.orderedGuideIds = orderedGuideIds;
    }

    public Map<String, String> getReasons() {
        return reasons;
    }

    public void setReasons(Map<String, String> reasons) {
        this.reasons = reasons;
    }

    public Map<Long, String> reasonsByGuideId() {
        Map<Long, String> out = new LinkedHashMap<>();
        if (reasons == null) {
            return out;
        }
        for (Map.Entry<String, String> e : reasons.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            try {
                out.put(Long.parseLong(e.getKey().trim()), e.getValue().strip());
            } catch (NumberFormatException ignored) {
                // skip malformed keys
            }
        }
        return out;
    }
}
