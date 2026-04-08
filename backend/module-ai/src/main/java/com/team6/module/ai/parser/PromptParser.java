package com.team6.module.ai.parser;

import com.team6.module.ai.dto.request.GuideRecommendRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class PromptParser {

    private static final Pattern BUDGET_WON = Pattern.compile("(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*(원|만원)");
    private static final Pattern BUDGET_MANWON_BAND = Pattern.compile("(\\d+)\\s*만원\\s*대");
    private static final Pattern HEADCOUNT = Pattern.compile("(\\d+)\\s*(명|인)");
    private static final Pattern DURATION_NIGHTS_DAYS = Pattern.compile("(\\d+)\\s*박\\s*(\\d+)\\s*일");
    private static final Pattern DURATION_DAYS = Pattern.compile("(\\d+)\\s*일");
    private static final Pattern DURATION_WEEKS = Pattern.compile("(\\d+)\\s*주");

    public GuideRecommendRequest parse(
            String prompt,
            Integer topN,
            List<GuideRecommendRequest.GuideCandidateDto> guideCandidates
    ) {
        String normalizedPrompt = normalize(prompt);

        String region = extractRegion(normalizedPrompt);
        String travelStyle = extractTravelStyle(normalizedPrompt);
        String budgetLevel = extractBudgetLevel(normalizedPrompt);
        String companionType = extractCompanionType(normalizedPrompt);
        List<String> excludedActivityTags = extractExcludedActivityTags(normalizedPrompt);
        List<String> softPenaltyActivityTags = extractSoftPenaltyActivityTags(normalizedPrompt);
        List<String> activityTags = stripActivityTagsOverlappingSoft(
                extractActivityTags(normalizedPrompt),
                softPenaltyActivityTags
        );
        List<String> preferredLanguages = extractLanguages(normalizedPrompt);
        Integer headcount = extractHeadcount(normalizedPrompt);
        Integer durationDays = extractDurationDays(normalizedPrompt);

        return GuideRecommendRequest.builder()
                .region(region)
                .travelStyle(travelStyle)
                .budgetLevel(budgetLevel)
                .companionType(companionType)
                .activityTags(activityTags)
                .preferredLanguages(preferredLanguages)
                .headcount(headcount)
                .durationDays(durationDays)
                .excludedActivityTags(excludedActivityTags)
                .softPenaltyActivityTags(softPenaltyActivityTags)
                .topN(topN)
                .guideCandidates(guideCandidates)
                .build();
    }

    private String normalize(String prompt) {
        if (prompt == null) {
            return "";
        }
        return prompt.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private String extractRegion(String prompt) {
        if (containsAny(prompt, "부산")) return "부산";
        if (containsAny(prompt, "서울")) return "서울";
        if (containsAny(prompt, "제주", "제주도")) return "제주";
        if (containsAny(prompt, "강릉")) return "강릉";
        if (containsAny(prompt, "경주")) return "경주";
        if (containsAny(prompt, "대구")) return "대구";
        if (containsAny(prompt, "전주")) return "전주";
        if (containsAny(prompt, "여수")) return "여수";
        if (containsAny(prompt, "순천")) return "순천";
        if (containsAny(prompt, "군산")) return "군산";
        if (containsAny(prompt, "춘천")) return "춘천";
        if (containsAny(prompt, "통영")) return "통영";
        if (containsAny(prompt, "거제")) return "거제";
        if (containsAny(prompt, "목포")) return "목포";
        if (containsAny(prompt, "태안")) return "태안";
        if (containsAny(prompt, "인천")) return "인천";
        if (containsAny(prompt, "수원")) return "수원";
        return null;
    }

    private String extractTravelStyle(String prompt) {
        if (containsAny(prompt, "감성", "감성적인", "감성샷")) return "감성";
        if (containsAny(prompt, "액티비티", "활동적인", "신나게", "역동적인", "익스트림")) return "액티비티";
        if (containsAny(prompt, "조용", "힐링", "편안", "여유", "쉬고")) return "힐링";
        if (containsAny(prompt, "로컬", "현지", "동네", "시장", "골목")) return "로컬";
        return null;
    }

    private String extractBudgetLevel(String prompt) {
        Integer budget = extractBudgetAmount(prompt);
        if (budget != null) {
            if (budget <= 100_000) return "낮음";
            if (budget <= 300_000) return "중간";
            return "높음";
        }

        Integer band = extractBudgetBand(prompt);
        if (band != null) {
            // ex) "10만원대" -> 대략 10~19만원 구간으로 간주
            int approx = band * 10_000;
            if (approx <= 100_000) return "낮음";
            if (approx <= 300_000) return "중간";
            return "높음";
        }

        if (containsAny(prompt, "저렴", "가성비", "싸게", "예산 적게", "착한 가격", "가격 부담")) return "낮음";
        if (containsAny(prompt, "적당", "무난", "보통", "중간")) return "중간";
        if (containsAny(prompt, "럭셔리", "고급", "비싸도", "프리미엄", "플렉스")) return "높음";
        return null;
    }

    private String extractCompanionType(String prompt) {
        if (containsAny(prompt, "혼자", "혼행", "1인")) return "혼자";
        if (containsAny(prompt, "친구", "친구랑", "우정", "동창")) return "친구";
        if (containsAny(prompt, "가족", "부모님", "엄마", "아빠", "아이", "애기")) return "가족";
        if (containsAny(prompt, "반려견", "강아지", "댕댕이", "반려동물")) return "반려견";
        if (containsAny(prompt, "연인", "커플", "데이트", "여자친구", "남자친구")) return "연인";
        return null;
    }

    private List<String> extractActivityTags(String prompt) {
        Set<String> tags = new LinkedHashSet<>();

        if (containsAny(prompt, "카페")) tags.add("카페");
        if (containsAny(prompt, "야경", "밤거리")) tags.add("야경");
        if (containsAny(prompt, "맛집", "먹방", "음식", "식도락")) tags.add("맛집");
        if (containsAny(prompt, "산책", "걷기", "걷고")) tags.add("산책");
        if (containsAny(prompt, "등산", "트레킹")) tags.add("등산");
        if (containsAny(prompt, "사진", "포토", "인생샷")) tags.add("사진");
        if (containsAny(prompt, "쇼핑")) tags.add("쇼핑");
        if (containsAny(prompt, "바다", "해변", "오션뷰", "해수욕장")) tags.add("바다");
        if (containsAny(prompt, "일몰", "노을", "야경명소")) tags.add("일몰");
        if (containsAny(prompt, "박물관", "미술관", "전시")) tags.add("전시");
        if (containsAny(prompt, "시장", "전통시장", "로컬시장", "야시장")) tags.add("시장");
        if (containsAny(prompt, "술집", "클럽", "바")) tags.add("술집");
        if (containsAny(prompt, "브런치", "카페투어")) tags.add("브런치");
        if (containsAny(prompt, "쇼핑몰", "아울렛")) tags.add("쇼핑몰");
        if (containsAny(prompt, "온천", "노천탕", "스파")) tags.add("온천");
        if (containsAny(prompt, "골프", "골프장")) tags.add("골프");
        if (containsAny(prompt, "서핑", "서핑하기")) tags.add("서핑");
        if (containsAny(prompt, "드라이브", "드라이브코스")) tags.add("드라이브");

        return normalizeTagList(tags);
    }

    /**
     * 추출된 표현을 {@link KeywordNormalizer}로 통일해 매칭 정확도를 맞춘다.
     */
    private List<String> normalizeTagList(Set<String> rawTags) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String t : rawTags) {
            String n = KeywordNormalizer.normalizeTag(t);
            if (n != null && !n.isBlank()) {
                out.add(n);
            }
        }
        return new ArrayList<>(out);
    }

    private List<String> extractExcludedActivityTags(String prompt) {
        // 간단 룰: 제외 의도 + 태그 키워드가 같이 등장하면 제외 태그로 등록
        Set<String> excluded = new LinkedHashSet<>();
        if (containsAny(prompt, "빼고", "제외", "말고", "싫어", "안 가", "안가", "원치 않아", "원치않아")) {
            if (containsAny(prompt, "술집", "클럽", "바")) excluded.add("술집");
            if (containsAny(prompt, "등산", "트레킹")) excluded.add("등산");
            if (containsAny(prompt, "쇼핑", "쇼핑몰", "아울렛")) excluded.add("쇼핑");
        }
        LinkedHashSet<String> normalized = excluded.stream()
                .map(KeywordNormalizer::normalizeTag)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new ArrayList<>(normalized);
    }

    /**
     * 하드 제외가 아니라, 해당 활동을 가이드가 강하게 내세울 때 순위를 낮추기 위한 soft 부정 힌트.
     */
    /**
     * soft 부정 태그와 겹치는 활동은 '선호 활동'에서 제외(요약/매칭 모순 방지).
     */
    private List<String> stripActivityTagsOverlappingSoft(List<String> activityTags, List<String> softPenaltyTags) {
        if (activityTags == null || activityTags.isEmpty() || softPenaltyTags == null || softPenaltyTags.isEmpty()) {
            return activityTags == null ? List.of() : activityTags;
        }
        Set<String> soft = softPenaltyTags.stream()
                .map(KeywordNormalizer::normalizeTag)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toSet());
        return activityTags.stream()
                .filter(a -> !soft.contains(KeywordNormalizer.normalizeTag(a)))
                .collect(Collectors.toList());
    }

    private List<String> extractSoftPenaltyActivityTags(String prompt) {
        Set<String> raw = new LinkedHashSet<>();
        boolean heavy = containsAny(prompt, "힘든", "힘들", "빡센", "빡세", "무거운", "험한", "무리");
        if (heavy && containsAny(prompt, "등산", "트레킹")) {
            raw.add("등산");
        }
        if (containsAny(prompt, "시끄", "시끄러", "붐비", "붐벼") && containsAny(prompt, "술집", "클럽", "바")) {
            raw.add("술집");
        }
        if (containsAny(prompt, "복잡", "붐비", "붐벼") && containsAny(prompt, "쇼핑")) {
            raw.add("쇼핑");
        }
        return normalizeTagList(raw);
    }

    private List<String> extractLanguages(String prompt) {
        Set<String> languages = new LinkedHashSet<>();

        if (containsAny(prompt, "한국어")) languages.add("한국어");
        if (containsAny(prompt, "영어", "english", "eng")) languages.add("영어");
        if (containsAny(prompt, "일본어", "japanese", "jp")) languages.add("일본어");
        if (containsAny(prompt, "중국어", "chinese", "cn")) languages.add("중국어");
        if (containsAny(prompt, "프랑스어", "프랑스", "french", "français")) languages.add("프랑스어");
        if (containsAny(prompt, "스페인어", "스페인", "spanish", "español")) languages.add("스페인어");
        if (containsAny(prompt, "독일어", "독일", "german", "deutsch")) languages.add("독일어");

        return new ArrayList<>(languages);
    }

    private boolean containsAny(String prompt, String... keywords) {
        for (String keyword : keywords) {
            if (prompt.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private Integer extractBudgetAmount(String prompt) {
        Matcher m = BUDGET_WON.matcher(prompt);
        if (!m.find()) {
            return null;
        }
        String number = m.group(1).replace(",", "");
        String unit = m.group(2);
        try {
            long value = Long.parseLong(number);
            if ("만원".equals(unit)) {
                value *= 10_000L;
            }
            if (value <= 0L || value > Integer.MAX_VALUE) {
                return null;
            }
            return (int) value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer extractHeadcount(String prompt) {
        Matcher m = HEADCOUNT.matcher(prompt);
        if (!m.find()) {
            return null;
        }
        try {
            int n = Integer.parseInt(m.group(1));
            return (n <= 0 || n > 20) ? null : n;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer extractDurationDays(String prompt) {
        if (containsAny(prompt, "당일치기", "당일")) {
            return 1;
        }
        if (containsAny(prompt, "주말")) {
            return 2;
        }

        Matcher m = DURATION_NIGHTS_DAYS.matcher(prompt);
        if (m.find()) {
            try {
                int days = Integer.parseInt(m.group(2));
                return (days <= 0 || days > 30) ? null : days;
            } catch (NumberFormatException ignored) {
            }
        }
        Matcher w = DURATION_WEEKS.matcher(prompt);
        if (w.find()) {
            try {
                int weeks = Integer.parseInt(w.group(1));
                int days = weeks * 7;
                return (days <= 0 || days > 30) ? null : days;
            } catch (NumberFormatException ignored) {
            }
        }
        Matcher d = DURATION_DAYS.matcher(prompt);
        if (d.find()) {
            try {
                int days = Integer.parseInt(d.group(1));
                return (days <= 0 || days > 30) ? null : days;
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private Integer extractBudgetBand(String prompt) {
        Matcher m = BUDGET_MANWON_BAND.matcher(prompt);
        if (!m.find()) {
            return null;
        }
        try {
            int n = Integer.parseInt(m.group(1));
            return (n <= 0 || n > 10_000) ? null : n;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
