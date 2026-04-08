package com.team6.module.ai.engine;

import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import com.team6.module.ai.parser.KeywordNormalizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ReasonGenerator {

    public static final String CODE_REGION_MATCH = "REGION_MATCH";
    public static final String CODE_STYLE_MATCH = "STYLE_MATCH";
    public static final String CODE_LANGUAGE_MATCH = "LANGUAGE_MATCH";
    public static final String CODE_ACTIVITY_MATCH = "ACTIVITY_MATCH";
    public static final String CODE_BUDGET_MATCH = "BUDGET_MATCH";
    public static final String CODE_GENERAL_FALLBACK = "GENERAL_FALLBACK";

    private static final int MAX_DISPLAY_SEGMENTS = 3;

    /**
     * @param score 현재는 reason 문구 생성에 직접 쓰지 않지만, 추후 임계값/요약에 활용 가능하도록 유지
     */
    public ReasonBundle generate(TravelerPreference pref, GuideAiProfile guide, int score) {
        List<Segment> segments = buildSegments(pref, guide);
        if (segments.isEmpty()) {
            segments = List.of(new Segment(
                    CODE_GENERAL_FALLBACK,
                    "전체 선호도 기준으로 적합",
                    List.of()
            ));
        }

        int limit = Math.min(MAX_DISPLAY_SEGMENTS, segments.size());
        List<Segment> displayed = segments.subList(0, limit);

        String text = displayed.stream()
                .map(Segment::displayText)
                .collect(Collectors.joining(" · "));

        List<String> codes = displayed.stream()
                .map(Segment::code)
                .toList();

        List<ReasonBundle.Fact> facts = displayed.stream()
                .map(s -> ReasonBundle.Fact.builder()
                        .code(s.code)
                        .values(s.factValues)
                        .build())
                .toList();

        return ReasonBundle.builder()
                .text(text)
                .reasonCodes(codes)
                .reasonFacts(facts)
                .build();
    }

    private List<Segment> buildSegments(TravelerPreference pref, GuideAiProfile guide) {
        List<Segment> segments = new ArrayList<>();

        if (safeEquals(pref.getRegion(), guide.getRegion())) {
            segments.add(new Segment(
                    CODE_REGION_MATCH,
                    "선호 지역이 일치",
                    listNonNull(guide.getRegion())
            ));
        }

        if (safeEquals(pref.getTravelStyle(), guide.getGuideStyle())) {
            segments.add(new Segment(
                    CODE_STYLE_MATCH,
                    "여행 스타일이 유사",
                    listNonNull(guide.getGuideStyle())
            ));
        }

        if (pref.getPreferredLanguages() != null && guide.getLanguages() != null) {
            Set<String> guideLang = guide.getLanguages().stream()
                    .map(KeywordNormalizer::normalizeLanguage)
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.toSet());

            LinkedHashSet<String> matchedLanguages = pref.getPreferredLanguages().stream()
                    .map(KeywordNormalizer::normalizeLanguage)
                    .filter(s -> s != null && !s.isBlank())
                    .filter(guideLang::contains)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            if (!matchedLanguages.isEmpty()) {
                List<String> langValues = new ArrayList<>(matchedLanguages);
                segments.add(new Segment(
                        CODE_LANGUAGE_MATCH,
                        "가능 언어(" + String.join("/", langValues) + ")",
                        langValues
                ));
            }
        }

        if (pref.getActivityTags() != null && guide.getSpecialtyTags() != null) {
            Set<String> guideTags = guide.getSpecialtyTags().stream()
                    .map(KeywordNormalizer::normalizeTag)
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.toSet());

            List<String> matched = pref.getActivityTags().stream()
                    .map(KeywordNormalizer::normalizeTag)
                    .filter(s -> s != null && !s.isBlank())
                    .filter(guideTags::contains)
                    .distinct()
                    .toList();

            if (!matched.isEmpty()) {
                String head = matched.stream().limit(3).collect(Collectors.joining("/"));
                String display = "관심 활동(" + head + (matched.size() > 3 ? " 외" : "") + ")";
                segments.add(new Segment(CODE_ACTIVITY_MATCH, display, matched));
            }
        }

        if (safeEquals(pref.getBudgetLevel(), guide.getPriceLevel())) {
            segments.add(new Segment(
                    CODE_BUDGET_MATCH,
                    "예산대가 비슷",
                    listNonNull(guide.getPriceLevel())
            ));
        }

        return segments;
    }

    private static List<String> listNonNull(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.trim());
    }

    private boolean safeEquals(String a, String b) {
        return a != null && a.equalsIgnoreCase(b);
    }

    private record Segment(String code, String displayText, List<String> factValues) {
    }
}
