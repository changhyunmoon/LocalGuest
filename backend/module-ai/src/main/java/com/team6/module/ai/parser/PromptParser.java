package com.team6.module.ai.parser;

import com.team6.module.ai.dto.request.GuideRecommendRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PromptParser {

    private static final Pattern BUDGET_WON = Pattern.compile("(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*(원|만원)");

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
        List<String> activityTags = extractActivityTags(normalizedPrompt);
        List<String> preferredLanguages = extractLanguages(normalizedPrompt);

        return GuideRecommendRequest.builder()
                .region(region)
                .travelStyle(travelStyle)
                .budgetLevel(budgetLevel)
                .companionType(companionType)
                .activityTags(activityTags)
                .preferredLanguages(preferredLanguages)
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

        if (containsAny(prompt, "저렴", "가성비", "싸게", "예산 적게")) return "낮음";
        if (containsAny(prompt, "적당", "무난", "보통", "중간")) return "중간";
        if (containsAny(prompt, "럭셔리", "고급", "비싸도", "프리미엄")) return "높음";
        return null;
    }

    private String extractCompanionType(String prompt) {
        if (containsAny(prompt, "혼자", "혼행", "1인")) return "혼자";
        if (containsAny(prompt, "친구", "친구랑", "우정", "동창")) return "친구";
        if (containsAny(prompt, "가족", "부모님", "엄마", "아빠", "아이", "애기")) return "가족";
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
        if (containsAny(prompt, "바다", "해변", "오션뷰")) tags.add("바다");
        if (containsAny(prompt, "박물관", "미술관", "전시")) tags.add("전시");
        if (containsAny(prompt, "시장", "전통시장", "로컬시장")) tags.add("시장");

        return new ArrayList<>(tags);
    }

    private List<String> extractLanguages(String prompt) {
        Set<String> languages = new LinkedHashSet<>();

        if (containsAny(prompt, "한국어")) languages.add("한국어");
        if (containsAny(prompt, "영어", "english", "eng")) languages.add("영어");
        if (containsAny(prompt, "일본어", "japanese", "jp")) languages.add("일본어");
        if (containsAny(prompt, "중국어", "chinese", "cn")) languages.add("중국어");

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
}