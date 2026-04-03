package com.team6.module.ai.engine;

import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ReasonGenerator {

    public String generate(TravelerPreference pref, GuideAiProfile guide, int score) {
        List<String> reasons = new ArrayList<>();

        if (safeEquals(pref.getRegion(), guide.getRegion())) {
            reasons.add("선호 지역이 일치");
        }

        if (safeEquals(pref.getTravelStyle(), guide.getGuideStyle())) {
            reasons.add("여행 스타일이 유사");
        }

        if (pref.getActivityTags() != null && guide.getSpecialtyTags() != null) {
            long matched = pref.getActivityTags().stream()
                    .filter(guide.getSpecialtyTags()::contains)
                    .count();

            if (matched > 0) {
                reasons.add("관심 활동 태그가 " + matched + "개 일치");
            }
        }

        if (reasons.isEmpty()) {
            reasons.add("전체 선호도 기준으로 적합");
        }

        return String.join(", ", reasons);
    }

    private boolean safeEquals(String a, String b) {
        return a != null && a.equalsIgnoreCase(b);
    }
}