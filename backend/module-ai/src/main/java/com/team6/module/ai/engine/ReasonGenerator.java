package com.team6.module.ai.engine;

import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

        if (safeEquals(pref.getBudgetLevel(), guide.getPriceLevel())) {
            reasons.add("예산대가 비슷");
        }

        if (pref.getPreferredLanguages() != null && guide.getLanguages() != null) {
            Set<String> matchedLanguages = pref.getPreferredLanguages().stream()
                    .filter(guide.getLanguages()::contains)
                    .collect(Collectors.toSet());
            if (!matchedLanguages.isEmpty()) {
                reasons.add("가능 언어(" + String.join("/", matchedLanguages) + ")");
            }
        }

        if (pref.getActivityTags() != null && guide.getSpecialtyTags() != null) {
            List<String> matched = pref.getActivityTags().stream()
                    .filter(guide.getSpecialtyTags()::contains)
                    .distinct()
                    .toList();

            if (!matched.isEmpty()) {
                String head = matched.stream().limit(3).collect(Collectors.joining("/"));
                reasons.add("관심 활동(" + head + (matched.size() > 3 ? " 외" : "") + ")");
            }
        }

        if (reasons.isEmpty()) {
            reasons.add("전체 선호도 기준으로 적합");
        }

        return String.join(" · ", reasons);
    }

    private boolean safeEquals(String a, String b) {
        return a != null && a.equalsIgnoreCase(b);
    }
}