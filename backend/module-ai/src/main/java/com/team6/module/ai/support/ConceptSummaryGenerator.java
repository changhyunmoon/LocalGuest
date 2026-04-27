package com.team6.module.ai.support;

import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.parser.KeywordNormalizer;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

public final class ConceptSummaryGenerator {

    private ConceptSummaryGenerator() {
    }

    public static String generate(GuideRecommendRequest req) {
        if (req == null) {
            return null;
        }

        if (hasLlmGuideCopy(req)) {
            String llm = formatLlmGuideCopy(req.getLlmGuideBullets(), req.getLlmSpecialRequests());
            if (StringUtils.hasText(llm)) {
                return llm;
            }
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

    private static boolean hasLlmGuideCopy(GuideRecommendRequest req) {
        return (req.getLlmGuideBullets() != null && !req.getLlmGuideBullets().isEmpty())
                || StringUtils.hasText(req.getLlmSpecialRequests());
    }

    /**
     * LLM이 채운 불릿·특별 요청을 한 덩어리 요약 문자열로 만든다(가이드/응답 요약 공용).
     */
    public static String formatLlmGuideCopy(List<String> bullets, String specialRequests) {
        String cleanedSpecial = normalizeGuideText(specialRequests);
        List<String> cleanedBullets = normalizeBullets(bullets, cleanedSpecial);

        StringBuilder sb = new StringBuilder();
        for (String b : cleanedBullets) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("• ").append(b);
        }
        if (StringUtils.hasText(cleanedSpecial)) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(cleanedSpecial);
        }
        String out = sb.toString().trim();
        return out.isBlank() ? null : out;
    }

    private static String normalizeGuideText(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String s = raw.trim()
                .replaceAll("[\\r\\t]+", " ")
                .replaceAll(" +", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return s.isBlank() ? null : s;
    }

    private static List<String> normalizeBullets(List<String> raw, String specialRequests) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        String special = specialRequests == null ? "" : specialRequests;
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        for (String b : raw) {
            if (!StringUtils.hasText(b)) {
                continue;
            }
            String t = b.trim()
                    .replaceAll("^[-•\\s]+", "")
                    .replaceAll("[\\r\\t]+", " ")
                    .replaceAll(" +", " ")
                    .trim();
            if (!StringUtils.hasText(t)) {
                continue;
            }
            // specialRequests에 그대로 포함된 불릿은 중복이므로 제거
            if (StringUtils.hasText(special) && special.contains(t)) {
                continue;
            }
            set.add(t);
            if (set.size() >= 5) {
                break;
            }
        }
        return List.copyOf(set);
    }

    public static String generateMatchRequestConcept(GuideRecommendRequest req) {
        if (req == null) {
            return null;
        }

        List<String> parts = new java.util.ArrayList<>();
        if (notBlank(req.getRegion())) {
            parts.add(req.getRegion().trim() + " 여행");
        }
        if (req.getDurationDays() != null) {
            parts.add(req.getDurationDays() + "일 일정");
        }
        if (req.getHeadcount() != null) {
            parts.add(req.getHeadcount() + "명");
        }
        if (notBlank(req.getCompanionType())) {
            parts.add(req.getCompanionType().trim() + " 여행");
        }
        if (notBlank(req.getTravelStyle())) {
            parts.add(req.getTravelStyle().trim() + " 스타일");
        }
        if (notBlank(req.getBudgetLevel())) {
            parts.add("예산 " + req.getBudgetLevel().trim());
        }
        List<String> preferredActivities = preferredActivities(req);
        if (!preferredActivities.isEmpty()) {
            parts.add("희망 활동 " + join(preferredActivities));
        }
        if (req.getExcludedActivityTags() != null && !req.getExcludedActivityTags().isEmpty()) {
            parts.add("제외 활동 " + join(req.getExcludedActivityTags()));
        }
        if (req.getPreferredLanguages() != null && !req.getPreferredLanguages().isEmpty()) {
            parts.add("희망 언어 " + join(req.getPreferredLanguages()));
        }

        String result = String.join(" / ", parts).trim();
        return result.isBlank() ? null : result;
    }

    private static List<String> preferredActivities(GuideRecommendRequest req) {
        if (req.getActivityTags() == null || req.getActivityTags().isEmpty()) {
            return List.of();
        }
        Set<String> excluded = normalizeSet(req.getExcludedActivityTags());
        Set<String> softPenalty = normalizeSet(req.getSoftPenaltyActivityTags());

        return req.getActivityTags().stream()
                .filter(ConceptSummaryGenerator::notBlank)
                .map(String::trim)
                .filter(tag -> {
                    String normalized = KeywordNormalizer.normalizeTag(tag);
                    return normalized != null && !excluded.contains(normalized) && !softPenalty.contains(normalized);
                })
                .distinct()
                .toList();
    }

    private static Set<String> normalizeSet(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (!notBlank(value)) {
                continue;
            }
            String tag = KeywordNormalizer.normalizeTag(value.trim());
            if (tag != null && !tag.isBlank()) {
                normalized.add(tag);
            }
        }
        return normalized;
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
