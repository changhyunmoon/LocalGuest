package com.team6.module.ai.parser;

import com.team6.module.ai.dto.request.GuideRecommendRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class PromptParser {

    public GuideRecommendRequest parse(
            String prompt,
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
                .topN(3)
                .guideCandidates(guideCandidates)
                .build();
    }

    private String normalize(String prompt) {
        if (prompt == null) {
            return "";
        }
        return prompt.trim().toLowerCase(Locale.ROOT);
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
        if (containsAny(prompt, "감성", "감성적인")) return "감성";
        if (containsAny(prompt, "액티비티", "활동적인", "신나게", "역동적인")) return "액티비티";
        if (containsAny(prompt, "조용", "힐링", "편안", "여유")) return "힐링";
        if (containsAny(prompt, "로컬", "현지", "동네 느낌")) return "로컬";
        return null;
    }

    private String extractBudgetLevel(String prompt) {
        if (containsAny(prompt, "저렴", "가성비", "싸게", "예산 적게")) return "낮음";
        if (containsAny(prompt, "적당", "무난", "보통", "중간")) return "중간";
        if (containsAny(prompt, "럭셔리", "고급", "비싸도", "프리미엄")) return "높음";
        return null;
    }

    private String extractCompanionType(String prompt) {
        if (containsAny(prompt, "혼자", "혼행", "1인")) return "혼자";
        if (containsAny(prompt, "친구", "친구랑")) return "친구";
        if (containsAny(prompt, "가족", "부모님", "엄마", "아빠")) return "가족";
        if (containsAny(prompt, "연인", "커플", "여자친구", "남자친구")) return "연인";
        return null;
    }

    private List<String> extractActivityTags(String prompt) {
        List<String> tags = new ArrayList<>();

        if (containsAny(prompt, "카페")) tags.add("카페");
        if (containsAny(prompt, "야경", "밤거리")) tags.add("야경");
        if (containsAny(prompt, "맛집", "먹방", "음식", "식도락")) tags.add("맛집");
        if (containsAny(prompt, "산책", "걷기", "걷고")) tags.add("산책");
        if (containsAny(prompt, "등산", "트레킹")) tags.add("등산");
        if (containsAny(prompt, "사진", "포토", "인생샷")) tags.add("사진");
        if (containsAny(prompt, "쇼핑")) tags.add("쇼핑");
        if (containsAny(prompt, "바다", "해변", "오션뷰")) tags.add("바다");

        return tags;
    }

    private List<String> extractLanguages(String prompt) {
        List<String> languages = new ArrayList<>();

        if (containsAny(prompt, "한국어")) languages.add("한국어");
        if (containsAny(prompt, "영어", "english")) languages.add("영어");
        if (containsAny(prompt, "일본어", "japanese")) languages.add("일본어");
        if (containsAny(prompt, "중국어", "chinese")) languages.add("중국어");

        return languages;
    }

    private boolean containsAny(String prompt, String... keywords) {
        for (String keyword : keywords) {
            if (prompt.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}