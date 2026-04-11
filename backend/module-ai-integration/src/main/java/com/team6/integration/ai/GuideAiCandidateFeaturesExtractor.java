package com.team6.integration.ai;

import com.team6.domain.guide.entity.GuideCareer;
import com.team6.domain.guide.entity.GuideFeed;
import com.team6.domain.guide.entity.GuideProfile;
import com.team6.module.ai.parser.KeywordNormalizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 가이드 도메인 데이터를 AI 후보 특징값(스타일/전문 태그)으로 요약한다.
 */
public final class GuideAiCandidateFeaturesExtractor {

    private GuideAiCandidateFeaturesExtractor() {
    }

    private static final Map<String, List<String>> STYLE_KEYWORDS = new LinkedHashMap<>();
    private static final Map<String, List<String>> TAG_KEYWORDS = new LinkedHashMap<>();

    static {
        STYLE_KEYWORDS.put("감성", List.of("감성", "감성샷", "인스타", "핫플", "사진", "카페", "브런치"));
        STYLE_KEYWORDS.put("액티비티", List.of("액티비티", "익스트림", "스릴", "트레킹", "등산", "서핑", "래프팅", "패러글라이딩", "레포츠"));
        STYLE_KEYWORDS.put("힐링", List.of("힐링", "여유", "조용", "편안", "휴식", "산책", "온천", "노천탕", "스파"));
        STYLE_KEYWORDS.put("로컬", List.of("로컬", "현지", "골목", "시장", "동네", "문화", "역사", "유적", "전통"));

        TAG_KEYWORDS.put("카페", List.of("카페", "브런치", "카페투어"));
        TAG_KEYWORDS.put("야경", List.of("야경", "일몰", "노을", "밤거리", "야경명소"));
        TAG_KEYWORDS.put("맛집", List.of("맛집", "먹방", "식도락", "음식", "먹거리"));
        TAG_KEYWORDS.put("산책", List.of("산책", "걷기", "걷고", "드라이브", "골목"));
        TAG_KEYWORDS.put("등산", List.of("등산", "트레킹"));
        TAG_KEYWORDS.put("사진", List.of("사진", "포토", "인생샷", "감성샷"));
        TAG_KEYWORDS.put("쇼핑", List.of("쇼핑", "쇼핑몰", "아울렛"));
        TAG_KEYWORDS.put("바다", List.of("바다", "해변", "오션뷰", "해수욕장", "스노클링", "다이빙", "스쿠버", "서핑"));
        TAG_KEYWORDS.put("전시", List.of("전시", "박물관", "미술관"));
        TAG_KEYWORDS.put("시장", List.of("시장", "전통시장", "야시장", "로컬시장"));
        TAG_KEYWORDS.put("술집", List.of("술집", "클럽", "바"));
        TAG_KEYWORDS.put("온천", List.of("온천", "노천탕", "스파"));
        TAG_KEYWORDS.put("골프", List.of("골프", "골프장"));
        TAG_KEYWORDS.put("드라이브", List.of("드라이브", "드라이브코스"));
        TAG_KEYWORDS.put("한옥", List.of("한옥", "한옥마을", "한옥스테이"));
        TAG_KEYWORDS.put("테마파크", List.of("테마파크", "놀이공원", "놀이동산"));
        TAG_KEYWORDS.put("캠핑", List.of("캠핑", "글램핑", "오토캠핑"));
        TAG_KEYWORDS.put("사찰", List.of("사찰", "템플스테이"));
        TAG_KEYWORDS.put("유적", List.of("유적", "고분", "왕릉"));
        TAG_KEYWORDS.put("자전거", List.of("자전거", "라이딩", "사이클"));
        TAG_KEYWORDS.put("래프팅", List.of("래프팅", "레포츠", "짚라인"));
        TAG_KEYWORDS.put("스키", List.of("스키", "보드", "스노보드", "슬로프"));
        TAG_KEYWORDS.put("계곡", List.of("계곡", "폭포"));
        TAG_KEYWORDS.put("패러글라이딩", List.of("패러글라이딩", "패러"));
        TAG_KEYWORDS.put("낚시", List.of("낚시", "바다낚시"));
    }

    public static Features extract(
            GuideProfile profile,
            List<GuideFeed> feeds,
            List<GuideCareer> careers
    ) {
        String styleCorpus = buildStyleCorpus(profile, feeds, careers);
        String tagCorpus = buildTagCorpus(profile, feeds, careers);
        return new Features(inferStyle(styleCorpus), inferTags(tagCorpus));
    }

    private static String buildStyleCorpus(
            GuideProfile profile,
            List<GuideFeed> feeds,
            List<GuideCareer> careers
    ) {
        List<String> parts = new ArrayList<>();
        addIfNotBlank(parts, profile == null ? null : profile.getBio());
        if (feeds != null) {
            feeds.stream()
                    .map(GuideFeed::getContent)
                    .forEach(text -> addIfNotBlank(parts, text));
        }
        if (careers != null) {
            careers.forEach(career -> {
                addIfNotBlank(parts, career.getTitle());
                addIfNotBlank(parts, career.getDescription());
            });
        }
        return String.join(" ", parts).toLowerCase(Locale.ROOT);
    }

    private static String buildTagCorpus(
            GuideProfile profile,
            List<GuideFeed> feeds,
            List<GuideCareer> careers
    ) {
        List<String> parts = new ArrayList<>();
        addIfNotBlank(parts, profile == null ? null : profile.getBio());
        addIfNotBlank(parts, profile == null ? null : profile.getLocalStory());
        if (feeds != null) {
            feeds.stream()
                    .map(GuideFeed::getContent)
                    .forEach(text -> addIfNotBlank(parts, text));
        }
        if (careers != null) {
            careers.forEach(career -> {
                addIfNotBlank(parts, career.getTitle());
                addIfNotBlank(parts, career.getDescription());
            });
        }
        return String.join(" ", parts).toLowerCase(Locale.ROOT);
    }

    private static String inferStyle(String corpus) {
        if (corpus == null || corpus.isBlank()) {
            return null;
        }
        String bestStyle = null;
        int bestScore = 0;
        for (Map.Entry<String, List<String>> entry : STYLE_KEYWORDS.entrySet()) {
            int score = countMatches(corpus, entry.getValue());
            if (corpus.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                score += 10;
            }
            if (score > bestScore) {
                bestStyle = entry.getKey();
                bestScore = score;
            }
        }
        return bestStyle;
    }

    private static List<String> inferTags(String corpus) {
        if (corpus == null || corpus.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : TAG_KEYWORDS.entrySet()) {
            if (containsAny(corpus, entry.getValue())) {
                String normalized = KeywordNormalizer.normalizeTag(entry.getKey());
                if (normalized != null && !normalized.isBlank()) {
                    tags.add(normalized);
                }
            }
        }
        return new ArrayList<>(tags);
    }

    private static int countMatches(String corpus, List<String> keywords) {
        int score = 0;
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && corpus.contains(keyword.toLowerCase(Locale.ROOT))) {
                score++;
            }
        }
        return score;
    }

    private static boolean containsAny(String corpus, List<String> keywords) {
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && corpus.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static void addIfNotBlank(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value.trim());
        }
    }

    public record Features(
            String guideStyle,
            List<String> specialtyTags
    ) {
    }
}
