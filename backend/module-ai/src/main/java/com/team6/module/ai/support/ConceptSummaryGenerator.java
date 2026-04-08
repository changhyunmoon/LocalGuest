package com.team6.module.ai.support;

import com.team6.module.ai.dto.request.GuideRecommendRequest;

import java.util.List;
import java.util.StringJoiner;

public final class ConceptSummaryGenerator {

    private ConceptSummaryGenerator() {
    }

    public static String generate(GuideRecommendRequest req) {
        if (req == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        if (notBlank(req.getRegion())) {
            sb.append('[').append(req.getRegion().trim()).append("] ");
        }

        StringJoiner core = new StringJoiner(", ");
        if (req.getDurationDays() != null) {
            core.add(req.getDurationDays() + "일");
        }
        if (req.getHeadcount() != null) {
            core.add(req.getHeadcount() + "명");
        }
        if (notBlank(req.getBudgetLevel())) {
            core.add("예산 " + req.getBudgetLevel().trim());
        }
        if (notBlank(req.getTravelStyle())) {
            core.add(req.getTravelStyle().trim() + " 스타일");
        }
        if (notBlank(req.getCompanionType())) {
            core.add(req.getCompanionType().trim());
        }

        String coreText = core.toString();
        if (!coreText.isBlank()) {
            sb.append(coreText);
        }

        String tags = join(req.getActivityTags());
        if (notBlank(tags)) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("활동: ").append(tags);
        }

        String excluded = join(req.getExcludedActivityTags());
        if (notBlank(excluded)) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("제외: ").append(excluded);
        }

        String soft = join(req.getSoftPenaltyActivityTags());
        if (notBlank(soft)) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("부담 최소: ").append(soft);
        }

        String langs = join(req.getPreferredLanguages());
        if (notBlank(langs)) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("언어: ").append(langs);
        }

        String result = sb.toString().trim();
        return result.isBlank() ? null : result;
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream()
                .filter(ConceptSummaryGenerator::notBlank)
                .map(String::trim)
                .distinct()
                .limit(5)
                .reduce((a, b) -> a + "/" + b)
                .orElse(null);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}

