package com.team6.module.ai.parser;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompt/프로필의 다양한 표현을 canonical 형태로 통일한다.
 * (LLM 없이 룰 기반으로 매칭 정확도만 올리기 위한 유틸)
 */
public final class KeywordNormalizer {

    private KeywordNormalizer() {
    }

    /** {@code localguest.ai.parser.tag-synonyms}에서 주입(보조 맵이 있으면 내장보다 우선). */
    private static final Map<String, String> DYNAMIC_TAG_SYNONYMS = new ConcurrentHashMap<>();

    /**
     * 설정 기반 동의어를 덮어쓴다. 운영에서 yml만 바꿔 반영할 때 사용.
     */
    public static void applyTagSynonymSupplement(Map<String, String> extra) {
        DYNAMIC_TAG_SYNONYMS.clear();
        if (extra == null || extra.isEmpty()) {
            return;
        }
        extra.forEach((k, v) -> {
            if (k == null || v == null) {
                return;
            }
            String key = k.trim();
            String val = v.trim();
            if (!key.isEmpty() && !val.isEmpty()) {
                DYNAMIC_TAG_SYNONYMS.put(key, val);
            }
        });
    }

    private static final Map<String, String> TAG_SYNONYMS = Map.ofEntries(
            Map.entry("오션뷰", "바다"),
            Map.entry("해변", "바다"),
            Map.entry("바닷가", "바다"),
            Map.entry("해수욕장", "바다"),
            Map.entry("식도락", "맛집"),
            Map.entry("먹방", "맛집"),
            Map.entry("브런치", "카페"),
            Map.entry("카페투어", "카페"),
            Map.entry("포토", "사진"),
            Map.entry("인생샷", "사진"),
            Map.entry("전시", "전시"),
            Map.entry("미술관", "전시"),
            Map.entry("박물관", "전시"),
            Map.entry("전통시장", "시장"),
            Map.entry("로컬시장", "시장"),
            Map.entry("야시장", "시장"),
            Map.entry("트레킹", "등산"),
            Map.entry("일몰", "야경"),
            Map.entry("노을", "야경"),
            Map.entry("야경명소", "야경"),
            Map.entry("쇼핑몰", "쇼핑"),
            Map.entry("아울렛", "쇼핑"),
            Map.entry("노천탕", "온천"),
            Map.entry("스파", "온천"),
            Map.entry("서핑", "바다"),
            Map.entry("서핑하기", "바다"),
            Map.entry("드라이브", "산책"),
            Map.entry("드라이브코스", "산책"),
            Map.entry("골목", "산책"),
            Map.entry("골목투어", "산책"),
            Map.entry("먹거리", "맛집")
    );

    public static String normalizeTag(String tag) {
        if (tag == null) {
            return null;
        }
        String trimmed = tag.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String fromDynamic = DYNAMIC_TAG_SYNONYMS.get(trimmed);
        if (fromDynamic != null) {
            return fromDynamic;
        }
        String canonical = TAG_SYNONYMS.get(trimmed);
        return canonical != null ? canonical : trimmed;
    }

    public static String normalizeLanguage(String lang) {
        if (lang == null) {
            return null;
        }
        String trimmed = lang.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "eng", "english", "en", "영어" -> "영어";
            case "jp", "japanese", "ja", "일본어" -> "일본어";
            case "cn", "chinese", "zh", "중국어" -> "중국어";
            case "kr", "korean", "ko", "한국어" -> "한국어";
            case "fr", "french", "français", "프랑스어", "프랑스" -> "프랑스어";
            case "es", "spanish", "español", "스페인어", "스페인" -> "스페인어";
            case "de", "german", "deutsch", "독일어", "독일" -> "독일어";
            default -> trimmed;
        };
    }
}

