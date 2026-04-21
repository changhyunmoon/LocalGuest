package com.team6.module.ai.parser;

import com.team6.module.ai.config.LocalGuestAiProperties;
import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.support.RecommendationNoticeCodes;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class PromptParser {

    private static final String[] DEFAULT_SOFT_PENALTY_HEAVY_HINTS = {
            "힘든", "힘들", "빡센", "빡세", "무거운", "험한", "무리", "고난이도", "체력", "경사"
    };
    private static final String[] DEFAULT_SOFT_PENALTY_HIKING_CONTEXT = {"등산", "트레킹"};
    private static final String[] DEFAULT_SOFT_PENALTY_NIGHTLIFE_NOISE_HINTS = {
            "시끄", "시끄러", "붐비", "붐벼", "야간", "밤늦", "새벽"
    };
    private static final String[] DEFAULT_SOFT_PENALTY_SHOPPING_CROWD_HINTS = {
            "복잡", "붐비", "붐벼", "인파", "사람 많", "사람많"
    };
    private static final String[] DEFAULT_SOFT_PENALTY_SHOPPING_ACTIVITY_CONTEXT = {"쇼핑", "쇼핑몰", "아울렛"};
    private static final String[] DEFAULT_EXCLUSION_INTENT_KEYWORDS = {
            "빼고", "제외", "말고", "싫어", "안 가", "안가", "원치 않아", "원치않아"
    };

    private final LocalGuestAiProperties aiProperties;
    /** 길이 내림차순: {@code jeju island}가 {@code jeju}보다 먼저 매칭되도록 */
    private final List<Map.Entry<String, String>> regionAliasEntries;

    public PromptParser(LocalGuestAiProperties aiProperties) {
        this.aiProperties = aiProperties;
        this.regionAliasEntries = buildRegionAliasEntries(aiProperties);
    }

    private static final Pattern BUDGET_WON = Pattern.compile("(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*(원|만원)");
    private static final Pattern BUDGET_RANGE_WON = Pattern.compile(
            "(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*(원|만원)\\s*(?:~|\\-|부터|에서)\\s*(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*(원|만원)?"
    );
    /**
     * "15만 안으로", "약 20만 전후", "30만 이내" 등 만 원 단위 구어체.
     */
    private static final Pattern BUDGET_MANWON_ROUGH = Pattern.compile(
            "(?:약|대략)?\\s*(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*만(?:원)?(?:\\s*(?:안(?:으로)?|이내|까지|전후|정도|쯤|수준|선))?"
    );
    private static final Pattern BUDGET_MANWON_BAND = Pattern.compile("(\\d+)\\s*만원\\s*대");
    private static final Pattern BUDGET_MANWON_RANGE = Pattern.compile("(\\d+)\\s*[~-]\\s*(\\d+)\\s*만(?:원)?");
    private static final Pattern HEADCOUNT = Pattern.compile("(\\d+)\\s*(명|인)");
    private static final Pattern DURATION_NIGHTS_DAYS = Pattern.compile("(\\d+)\\s*박\\s*(\\d+)\\s*일");
    private static final Pattern DURATION_NIGHTS_ONLY = Pattern.compile("(\\d+)\\s*박(?!\\s*\\d)");
    private static final Pattern DURATION_DAYS = Pattern.compile("(\\d+)\\s*일");
    private static final Pattern DURATION_DAYS_SUFFIX = Pattern.compile("(\\d+)\\s*일(?:간|동안)");
    private static final Pattern DURATION_RANGE_DAYS = Pattern.compile("(\\d+)\\s*[~-]\\s*(\\d+)\\s*일");
    private static final Pattern DURATION_WEEKS = Pattern.compile("(\\d+)\\s*주");
    private static final Pattern DATE_RANGE_MONTH_DAY = Pattern.compile("(\\d{1,2})\\s*/\\s*(\\d{1,2})\\s*(?:~|\\-|부터)\\s*(\\d{1,2})\\s*/\\s*(\\d{1,2})");
    private static final Pattern DATE_RANGE_DAY_ONLY = Pattern.compile("(\\d{1,2})\\s*일\\s*(?:~|\\-|부터)\\s*(\\d{1,2})\\s*일");
    private static final Pattern DATE_SINGLE_MONTH_DAY_SLASH = Pattern.compile("(\\d{1,2})\\s*/\\s*(\\d{1,2})");
    private static final Pattern DATE_SINGLE_MONTH_DAY_KO = Pattern.compile("(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일");
    private static final Pattern DATE_RANGE_MONTH_DAY_KO = Pattern.compile(
            "(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일\\s*(?:~|\\-|부터)\\s*(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일"
    );
    private static final Pattern DATE_RANGE_SAME_MONTH_KO = Pattern.compile(
            "(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일\\s*(?:~|\\-|부터)\\s*(\\d{1,2})\\s*일"
    );
    private static final int NEGATION_AFTER_WINDOW = 12;

    /**
     * 운영 로그에서 “실패/모호 패턴”을 모아 yml/내장 키워드를 주기적으로 보강하기 위한 신호 묶음.
     * <p>
     * 프롬프트 원문을 그대로 남기지 않고도(민감정보 최소화) “어떤 힌트가 있었는데 결과가 비었는지”를
     * 판별할 수 있도록, 매칭된 트리거만 요약한다.
     */
    public record ParseSignals(
            List<String> matchedExclusionIntentKeywords,
            boolean hasBudgetHint,
            boolean hasDurationHint
    ) {
    }

    public record DesiredDateRange(LocalDate from, LocalDate to) {
    }

    public GuideRecommendRequest parse(
            String prompt,
            Integer topN,
            List<GuideRecommendRequest.GuideCandidateDto> guideCandidates
    ) {
        String normalizedPrompt = normalize(prompt);

        String region = extractRegion(normalizedPrompt);
        String travelStyle = extractTravelStyle(normalizedPrompt);
        String budgetLevel = extractBudgetLevel(normalizedPrompt);
        BudgetRange budgetRange = extractBudgetRangeWon(normalizedPrompt);
        String budgetScope = extractBudgetScope(normalizedPrompt);
        String companionType = extractCompanionType(normalizedPrompt);
        List<String> excludedRaw = extractExcludedActivityTags(normalizedPrompt);
        List<String> excludedActivityTags = relaxExcludedWhenExplicitlyRequired(normalizedPrompt, excludedRaw);
        List<String> excludedRegions = extractExcludedRegions(normalizedPrompt);
        List<String> excludedTravelStyles = extractExcludedTravelStyles(normalizedPrompt);
        List<String> excludedLanguages = extractExcludedLanguages(normalizedPrompt);
        List<String> softPenaltyActivityTags = extractSoftPenaltyActivityTags(normalizedPrompt);
        List<String> activityBeforeExcluded = stripActivityTagsOverlappingSoft(
                extractActivityTags(normalizedPrompt),
                softPenaltyActivityTags
        );
        List<String> activityTags = stripActivityTagsOverlappingExcluded(activityBeforeExcluded, excludedActivityTags);
        ActivityTagStrength tagStrength = classifyActivityTagStrength(normalizedPrompt, activityTags);
        List<String> requiredActivityTags = stripActivityTagsOverlappingSoft(
                stripActivityTagsOverlappingExcluded(new ArrayList<>(tagStrength.required()), excludedActivityTags),
                softPenaltyActivityTags
        );
        List<String> niceToHaveActivityTags = stripActivityTagsOverlappingSoft(
                stripActivityTagsOverlappingExcluded(new ArrayList<>(tagStrength.nice()), excludedActivityTags),
                softPenaltyActivityTags
        );
        List<String> preferredLanguages = extractLanguages(normalizedPrompt);
        LanguageStrength langStrength = classifyLanguageStrength(normalizedPrompt, preferredLanguages);
        Boolean allowAdjacentRegion = extractAllowAdjacentRegion(normalizedPrompt);
        Boolean strictBudget = extractStrictBudgetIntent(normalizedPrompt);
        Integer headcount = extractHeadcount(normalizedPrompt);
        Integer durationDays = extractDurationDays(normalizedPrompt);

        List<String> parserNotices = new ArrayList<>();
        if (excludedActivityTags.size() < excludedRaw.size()
                || activityTags.size() < activityBeforeExcluded.size()) {
            parserNotices.add(RecommendationNoticeCodes.PROMPT_PREFERENCE_CONFLICT_RESOLVED);
        }

        return GuideRecommendRequest.builder()
                .region(region)
                .travelStyle(travelStyle)
                .budgetLevel(budgetLevel)
                .budgetMinWon(budgetRange == null ? null : budgetRange.minWon())
                .budgetMaxWon(budgetRange == null ? null : budgetRange.maxWon())
                .budgetScope(budgetScope)
                .strictBudget(strictBudget)
                .companionType(companionType)
                .activityTags(activityTags)
                .requiredActivityTags(requiredActivityTags.isEmpty() ? null : List.copyOf(requiredActivityTags))
                .niceToHaveActivityTags(niceToHaveActivityTags.isEmpty() ? null : List.copyOf(niceToHaveActivityTags))
                .preferredLanguages(preferredLanguages)
                .requiredLanguages(langStrength.required().isEmpty() ? null : List.copyOf(langStrength.required()))
                .niceToHaveLanguages(langStrength.nice().isEmpty() ? null : List.copyOf(langStrength.nice()))
                .allowAdjacentRegion(allowAdjacentRegion)
                .headcount(headcount)
                .durationDays(durationDays)
                .excludedActivityTags(excludedActivityTags)
                .excludedRegions(excludedRegions)
                .excludedTravelStyles(excludedTravelStyles)
                .excludedLanguages(excludedLanguages)
                .softPenaltyActivityTags(softPenaltyActivityTags)
                .topN(topN)
                .guideCandidates(guideCandidates)
                .parserNoticeCodes(parserNotices.isEmpty() ? null : List.copyOf(parserNotices))
                .build();
    }

    private record BudgetRange(Integer minWon, Integer maxWon) {
    }

    private BudgetRange extractBudgetRangeWon(String prompt) {
        Matcher rangeWon = BUDGET_RANGE_WON.matcher(prompt);
        if (rangeWon.find()) {
            Integer start = convertWon(rangeWon.group(1), rangeWon.group(2));
            String endUnit = rangeWon.group(4) == null ? rangeWon.group(2) : rangeWon.group(4);
            Integer end = convertWon(rangeWon.group(3), endUnit);
            if (start != null && end != null) {
                int min = Math.min(start, end);
                int max = Math.max(start, end);
                return new BudgetRange(min, max);
            }
        }

        Matcher rangeMan = BUDGET_MANWON_RANGE.matcher(prompt);
        if (rangeMan.find()) {
            try {
                int a = Integer.parseInt(rangeMan.group(1));
                int b = Integer.parseInt(rangeMan.group(2));
                int min = Math.min(a, b) * 10_000;
                int max = Math.max(a, b) * 10_000;
                return new BudgetRange(min, max);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        // "예산 하루 10만원" 같이 단일 금액 표현도 범위(min=max)로 보존한다.
        Integer single = extractBudgetAmount(prompt);
        if (single != null && single >= 0) {
            return new BudgetRange(single, single);
        }
        return null;
    }

    /**
     * 예산 단위/범위 해석 힌트(정확한 계산은 후보 가격 계약이 갖춰진 후 확장).
     */
    private String extractBudgetScope(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return null;
        }
        if (containsAny(prompt, "인당", "1인", "1인당", "사람당", "per person")) {
            return "per_person";
        }
        if (containsAny(prompt, "하루", "일당", "per day")) {
            return "per_day";
        }
        if (containsAny(prompt, "총", "전체", "total")) {
            return "total";
        }
        return null;
    }

    private List<String> extractExcludedRegions(String prompt) {
        Set<String> excluded = new LinkedHashSet<>();

        // 한글 고정 목록(대표 도시)
        addExcludedRegionIfNegated(prompt, excluded, "부산", "부산");
        addExcludedRegionIfNegated(prompt, excluded, "서울", "서울");
        addExcludedRegionIfNegated(prompt, excluded, "제주", "제주", "제주도");
        addExcludedRegionIfNegated(prompt, excluded, "강릉", "강릉");
        addExcludedRegionIfNegated(prompt, excluded, "경주", "경주");
        addExcludedRegionIfNegated(prompt, excluded, "여수", "여수");

        // YAML/영문 alias도 포함(예: "jeju 말고")
        for (Map.Entry<String, String> e : regionAliasEntries) {
            String alias = e.getKey();
            String canonical = e.getValue();
            if (alias == null || alias.isBlank() || canonical == null || canonical.isBlank()) {
                continue;
            }
            int idx = prompt.indexOf(alias);
            while (idx >= 0) {
                if (isNegatedAround(prompt, idx, alias.length())) {
                    excluded.add(canonical);
                }
                idx = prompt.indexOf(alias, idx + alias.length());
            }
        }
        return new ArrayList<>(excluded);
    }

    private void addExcludedRegionIfNegated(String prompt, Set<String> excluded, String canonical, String... keywords) {
        for (String keyword : keywords) {
            int idx = prompt.indexOf(keyword.toLowerCase(Locale.ROOT));
            while (idx >= 0) {
                if (isNegatedAround(prompt, idx, keyword.length())) {
                    excluded.add(canonical);
                    return;
                }
                idx = prompt.indexOf(keyword.toLowerCase(Locale.ROOT), idx + keyword.length());
            }
        }
    }

    private List<String> extractExcludedTravelStyles(String prompt) {
        Set<String> excluded = new LinkedHashSet<>();
        Map<String, List<String>> styleKeywords = Map.of(
                "감성", List.of("감성", "감성적인", "감성샷", "핫플", "인스타", "인스타그램"),
                "액티비티", List.of("액티비티", "활동적인", "신나게", "역동적인", "익스트림", "스릴", "짜릿"),
                "힐링", List.of(
                        "조용", "조용한", "힐링", "편안", "여유", "쉬고", "한적", "고즈넉", "한산",
                        "사람 적", "사람적", "적은 곳", "한적하게"
                ),
                "로컬", List.of("로컬", "현지", "동네", "시장", "골목", "문화", "역사", "유적", "문화재", "유네스코")
        );
        for (Map.Entry<String, List<String>> entry : styleKeywords.entrySet()) {
            String style = entry.getKey();
            for (String keyword : entry.getValue()) {
                int idx = prompt.indexOf(keyword.toLowerCase(Locale.ROOT));
                while (idx >= 0) {
                    if (isNegatedAround(prompt, idx, keyword.length())) {
                        excluded.add(style);
                        break;
                    }
                    idx = prompt.indexOf(keyword.toLowerCase(Locale.ROOT), idx + keyword.length());
                }
                if (excluded.contains(style)) {
                    break;
                }
            }
        }
        return new ArrayList<>(excluded);
    }

    private List<String> extractExcludedLanguages(String prompt) {
        Set<String> excluded = new LinkedHashSet<>();
        addExcludedLanguageIfNegated(prompt, excluded, "한국어", "한국어");
        addExcludedLanguageIfNegated(prompt, excluded, "영어", "영어", "english", "eng");
        addExcludedLanguageIfNegated(prompt, excluded, "일본어", "일본어", "japanese", "jp");
        addExcludedLanguageIfNegated(prompt, excluded, "중국어", "중국어", "chinese", "cn");
        addExcludedLanguageIfNegated(prompt, excluded, "프랑스어", "프랑스어", "프랑스", "french", "français");
        addExcludedLanguageIfNegated(prompt, excluded, "스페인어", "스페인어", "스페인", "spanish", "español");
        addExcludedLanguageIfNegated(prompt, excluded, "독일어", "독일어", "독일", "german", "deutsch");
        addExcludedLanguageIfNegated(prompt, excluded, "베트남어", "베트남어", "베트남", "vietnamese", "vn");
        addExcludedLanguageIfNegated(prompt, excluded, "태국어", "태국어", "태국", "thai", "ภาษาไทย");
        addExcludedLanguageIfNegated(prompt, excluded, "이탈리아어", "이탈리아어", "이탈리아", "italian", "italiano");
        return new ArrayList<>(excluded);
    }

    private void addExcludedLanguageIfNegated(String prompt, Set<String> excluded, String language, String... keywords) {
        for (String keyword : keywords) {
            int idx = prompt.indexOf(keyword.toLowerCase(Locale.ROOT));
            while (idx >= 0) {
                if (isNegatedAround(prompt, idx, keyword.length())) {
                    excluded.add(language);
                    return;
                }
                idx = prompt.indexOf(keyword.toLowerCase(Locale.ROOT), idx + keyword.length());
            }
        }
    }

    /**
     * 파싱 실패/모호함 튜닝용 신호를 추출한다(로깅/관측용).
     * <p>
     * - 제외 의도: {@code localguest.ai.parser.exclusion-intent-keywords} + 내장 기본값을 합쳐 탐지
     * - 예산/일정 힌트: 숫자 패턴 및 대표 키워드로 “언급은 있었다” 정도만 판별
     */
    public ParseSignals signals(String prompt) {
        String normalized = normalize(prompt);
        List<String> exclusionIntents = findAllMatchedKeywordsMerged(
                normalized,
                aiProperties.getParser().getExclusionIntentKeywords(),
                DEFAULT_EXCLUSION_INTENT_KEYWORDS
        );

        boolean hasBudgetHint = containsAny(
                normalized,
                "예산", "원", "만원", "가성비", "저렴", "싸게", "럭셔리", "고급", "프리미엄", "비싸"
        ) || BUDGET_WON.matcher(normalized).find()
                || BUDGET_RANGE_WON.matcher(normalized).find()
                || BUDGET_MANWON_ROUGH.matcher(normalized).find()
                || BUDGET_MANWON_RANGE.matcher(normalized).find()
                || BUDGET_MANWON_BAND.matcher(normalized).find();

        boolean hasDurationHint = containsAny(normalized, "일정", "며칠", "박", "일", "주")
                || DURATION_NIGHTS_DAYS.matcher(normalized).find()
                || DURATION_NIGHTS_ONLY.matcher(normalized).find()
                || DURATION_RANGE_DAYS.matcher(normalized).find()
                || DURATION_DAYS_SUFFIX.matcher(normalized).find()
                || DURATION_DAYS.matcher(normalized).find()
                || DURATION_WEEKS.matcher(normalized).find()
                || DATE_RANGE_MONTH_DAY.matcher(normalized).find()
                || DATE_RANGE_DAY_ONLY.matcher(normalized).find();

        return new ParseSignals(exclusionIntents, hasBudgetHint, hasDurationHint);
    }

    /**
     * 프롬프트에서 희망 투어 날짜/기간을 추출한다.
     * <p>
     * 지원: {@code 4/28}, {@code 4월 28일}, {@code 4/28~4/30}, {@code 4월 28일부터 4월 30일},
     * {@code 4월 28일~30일}. 연도 미기재 시 서버 로컬 기준 현재 연도를 사용한다.
     */
    public DesiredDateRange extractDesiredTourDateRange(String prompt) {
        String normalized = normalize(prompt);
        int year = Year.now().getValue();

        DesiredDateRange kOrange = extractKoreanMonthDayRange(normalized, year);
        if (kOrange != null) {
            return kOrange;
        }
        DesiredDateRange slashRange = extractSlashMonthDayRange(normalized, year);
        if (slashRange != null) {
            return slashRange;
        }
        DesiredDateRange single = extractSingleMonthDay(normalized, year);
        return single;
    }

    private DesiredDateRange extractKoreanMonthDayRange(String prompt, int year) {
        Matcher m = DATE_RANGE_MONTH_DAY_KO.matcher(prompt);
        if (m.find()) {
            LocalDate from = safeDate(year, m.group(1), m.group(2));
            LocalDate to = safeDate(year, m.group(3), m.group(4));
            return normalizeRange(from, to);
        }
        Matcher sameMonth = DATE_RANGE_SAME_MONTH_KO.matcher(prompt);
        if (sameMonth.find()) {
            LocalDate from = safeDate(year, sameMonth.group(1), sameMonth.group(2));
            LocalDate to = safeDate(year, sameMonth.group(1), sameMonth.group(3));
            return normalizeRange(from, to);
        }
        return null;
    }

    private DesiredDateRange extractSlashMonthDayRange(String prompt, int year) {
        Matcher range = DATE_RANGE_MONTH_DAY.matcher(prompt);
        if (range.find()) {
            LocalDate from = safeDate(year, range.group(1), range.group(2));
            LocalDate to = safeDate(year, range.group(3), range.group(4));
            return normalizeRange(from, to);
        }
        return null;
    }

    private DesiredDateRange extractSingleMonthDay(String prompt, int year) {
        Matcher ko = DATE_SINGLE_MONTH_DAY_KO.matcher(prompt);
        if (ko.find()) {
            LocalDate d = safeDate(year, ko.group(1), ko.group(2));
            return d == null ? null : new DesiredDateRange(d, d);
        }
        Matcher slash = DATE_SINGLE_MONTH_DAY_SLASH.matcher(prompt);
        if (slash.find()) {
            LocalDate d = safeDate(year, slash.group(1), slash.group(2));
            return d == null ? null : new DesiredDateRange(d, d);
        }
        return null;
    }

    private static LocalDate safeDate(int year, String mm, String dd) {
        try {
            int m = Integer.parseInt(mm);
            int d = Integer.parseInt(dd);
            return LocalDate.of(year, m, d);
        } catch (NumberFormatException | DateTimeException e) {
            return null;
        }
    }

    private static DesiredDateRange normalizeRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return null;
        }
        if (from.isAfter(to)) {
            return new DesiredDateRange(to, from);
        }
        return new DesiredDateRange(from, to);
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
        // 부정(말고/빼고/싫어 등) 주변에 있는 지역은 우선 제외하고, 가장 먼저 등장한 "긍정" 지역을 고른다.
        List<RegionHit> hits = new ArrayList<>();
        addRegionHitIfPresent(prompt, hits, "부산", "부산");
        addRegionHitIfPresent(prompt, hits, "서울", "서울");
        addRegionHitIfPresent(prompt, hits, "제주", "제주", "제주도");
        addRegionHitIfPresent(prompt, hits, "강릉", "강릉");
        addRegionHitIfPresent(prompt, hits, "경주", "경주");
        addRegionHitIfPresent(prompt, hits, "대구", "대구");
        addRegionHitIfPresent(prompt, hits, "전주", "전주");
        addRegionHitIfPresent(prompt, hits, "여수", "여수");
        addRegionHitIfPresent(prompt, hits, "순천", "순천");
        addRegionHitIfPresent(prompt, hits, "군산", "군산");
        addRegionHitIfPresent(prompt, hits, "춘천", "춘천");
        addRegionHitIfPresent(prompt, hits, "통영", "통영");
        addRegionHitIfPresent(prompt, hits, "거제", "거제");
        addRegionHitIfPresent(prompt, hits, "목포", "목포");
        addRegionHitIfPresent(prompt, hits, "태안", "태안");
        addRegionHitIfPresent(prompt, hits, "인천", "인천");
        addRegionHitIfPresent(prompt, hits, "수원", "수원");
        addRegionHitIfPresent(prompt, hits, "속초", "속초");
        addRegionHitIfPresent(prompt, hits, "동해", "동해");
        addRegionHitIfPresent(prompt, hits, "삼척", "삼척");
        addRegionHitIfPresent(prompt, hits, "양양", "양양");
        addRegionHitIfPresent(prompt, hits, "평창", "평창");
        addRegionHitIfPresent(prompt, hits, "홍천", "홍천");
        addRegionHitIfPresent(prompt, hits, "태백", "태백");
        addRegionHitIfPresent(prompt, hits, "울산", "울산");
        addRegionHitIfPresent(prompt, hits, "창원", "창원");
        addRegionHitIfPresent(prompt, hits, "포항", "포항");
        addRegionHitIfPresent(prompt, hits, "안동", "안동");
        addRegionHitIfPresent(prompt, hits, "광주", "광주");
        addRegionHitIfPresent(prompt, hits, "대전", "대전");
        addRegionHitIfPresent(prompt, hits, "남해", "남해");
        addRegionHitIfPresent(prompt, hits, "밀양", "밀양");
        addRegionHitIfPresent(prompt, hits, "김해", "김해");
        addRegionHitIfPresent(prompt, hits, "가평", "가평");
        addRegionHitIfPresent(prompt, hits, "양평", "양평");
        addRegionHitIfPresent(prompt, hits, "파주", "파주");
        addRegionHitIfPresent(prompt, hits, "여주", "여주");
        addRegionHitIfPresent(prompt, hits, "제천", "제천");
        addRegionHitIfPresent(prompt, hits, "단양", "단양");
        addRegionHitIfPresent(prompt, hits, "정선", "정선");
        addRegionHitIfPresent(prompt, hits, "인제", "인제");
        addRegionHitIfPresent(prompt, hits, "하동", "하동");
        addRegionHitIfPresent(prompt, hits, "구례", "구례");
        addRegionHitIfPresent(prompt, hits, "보성", "보성");
        addRegionHitIfPresent(prompt, hits, "익산", "익산");

        RegionHit best = hits.stream()
                .filter(h -> !h.negated())
                .min(Comparator.comparingInt(RegionHit::index))
                .orElse(null);
        if (best != null) {
            return best.canonical();
        }
        return resolveRegionFromAliases(prompt);
    }

    /**
     * 한글 고정 목록에 없을 때 영문·로마자·팀 YAML({@code region-aliases})로 지역을 짐작한다.
     */
    private String resolveRegionFromAliases(String prompt) {
        for (Map.Entry<String, String> e : regionAliasEntries) {
            String alias = e.getKey();
            if (alias == null || alias.isBlank()) continue;
            int idx = prompt.indexOf(alias);
            if (idx >= 0 && !isNegatedAround(prompt, idx, alias.length())) {
                return e.getValue();
            }
        }
        return null;
    }

    private record RegionHit(int index, String canonical, boolean negated) {
    }

    private void addRegionHitIfPresent(String prompt, List<RegionHit> hits, String canonical, String... keywords) {
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) continue;
            String k = keyword.toLowerCase(Locale.ROOT);
            int idx = prompt.indexOf(k);
            while (idx >= 0) {
                boolean neg = isNegatedAround(prompt, idx, k.length());
                hits.add(new RegionHit(idx, canonical, neg));
                idx = prompt.indexOf(k, idx + k.length());
            }
        }
    }

    private static List<Map.Entry<String, String>> buildRegionAliasEntries(LocalGuestAiProperties props) {
        LinkedHashMap<String, String> merged = new LinkedHashMap<>(defaultEnglishRegionAliases());
        Map<String, String> yaml = props.getParser().getRegionAliases();
        if (yaml != null) {
            for (Map.Entry<String, String> e : yaml.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                String k = e.getKey().trim().toLowerCase(Locale.ROOT);
                String v = e.getValue().trim();
                if (!k.isEmpty() && !v.isEmpty()) {
                    merged.put(k, v);
                }
            }
        }
        return merged.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, String> en) -> en.getKey().length()).reversed())
                .toList();
    }

    /**
     * 여행광고·검색에 흔한 영문 표기. 키는 소문자.
     */
    private static LinkedHashMap<String, String> defaultEnglishRegionAliases() {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        String[][] pairs = {
                {"jeju island", "제주"}, {"jeju-do", "제주"}, {"jejudo", "제주"}, {"jeju", "제주"},
                {"busan", "부산"}, {"pusan", "부산"},
                {"seoul", "서울"},
                {"gangneung", "강릉"}, {"kangnung", "강릉"},
                {"gyeongju", "경주"}, {"kyongju", "경주"},
                {"daegu", "대구"}, {"taegu", "대구"},
                {"jeonju", "전주"},
                {"yeosu", "여수"},
                {"suncheon", "순천"},
                {"gunsan", "군산"},
                {"chuncheon", "춘천"},
                {"tongyeong", "통영"},
                {"geojedo", "거제"}, {"geoje", "거제"},
                {"mokpo", "목포"},
                {"taean", "태안"},
                {"incheon", "인천"},
                {"suwon", "수원"},
                {"sokcho", "속초"},
                {"donghae", "동해"},
                {"samcheok", "삼척"},
                {"yangyang", "양양"},
                {"pyeongchang", "평창"},
                {"hongcheon", "홍천"},
                {"taebaek", "태백"},
                {"ulsan", "울산"},
                {"changwon", "창원"},
                {"pohang", "포항"},
                {"andong", "안동"},
                {"gwangju", "광주"},
                {"daejeon", "대전"},
                {"namhae", "남해"},
                {"miryang", "밀양"},
                {"gimhae", "김해"},
                {"gapyeong", "가평"},
                {"yangpyeong", "양평"},
                {"paju", "파주"},
                {"yeoju", "여주"},
                {"jecheon", "제천"},
                {"danyang", "단양"},
                {"jeongseon", "정선"},
                {"inje", "인제"},
                {"hadong", "하동"},
                {"gurye", "구례"},
                {"boseong", "보성"},
                {"iksan", "익산"}
        };
        for (String[] p : pairs) {
            m.put(p[0].toLowerCase(Locale.ROOT), p[1]);
        }
        return m;
    }

    private String extractTravelStyle(String prompt) {
        Map<String, List<String>> styleKeywords = Map.of(
                "감성", List.of("감성", "감성적인", "감성샷", "핫플", "인스타", "인스타그램"),
                "액티비티", List.of("액티비티", "활동적인", "신나게", "역동적인", "익스트림", "스릴", "짜릿"),
                "힐링", List.of(
                        "조용", "조용한", "힐링", "편안", "여유", "쉬고", "한적", "고즈넉", "한산",
                        "사람 적", "사람적", "적은 곳", "한적하게"
                ),
                "로컬", List.of("로컬", "현지", "동네", "시장", "골목", "문화", "역사", "유적", "문화재", "유네스코")
        );
        Map<String, Integer> scores = new LinkedHashMap<>();
        styleKeywords.keySet().forEach(style -> scores.put(style, 0));

        for (Map.Entry<String, List<String>> entry : styleKeywords.entrySet()) {
            String style = entry.getKey();
            for (String keyword : entry.getValue()) {
                int idx = prompt.indexOf(keyword.toLowerCase(Locale.ROOT));
                while (idx >= 0) {
                    if (!isNegatedAround(prompt, idx, keyword.length())) {
                        int score = scores.get(style) + 1;
                        if (hasPriorityHintAround(prompt, idx, keyword.length())) {
                            score += 2;
                        }
                        scores.put(style, score);
                    }
                    idx = prompt.indexOf(keyword.toLowerCase(Locale.ROOT), idx + keyword.length());
                }
            }
        }

        return scores.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .max(Map.Entry.<String, Integer>comparingByValue()
                        .thenComparing(e -> stylePriority(e.getKey())))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private String extractBudgetLevel(String prompt) {
        Integer budget = extractBudgetAmount(prompt);
        if (budget != null) {
            return budgetTierFromAbsoluteWon(budget);
        }

        Integer roughManwon = extractManwonRoughBudgetWon(prompt);
        if (roughManwon != null) {
            return budgetTierFromAbsoluteWon(roughManwon);
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

    private static String budgetTierFromAbsoluteWon(int won) {
        if (won <= 100_000) {
            return "낮음";
        }
        if (won <= 300_000) {
            return "중간";
        }
        return "높음";
    }

    /**
     * "N만 안으로" 등 {@link #BUDGET_WON}으로 잡히지 않는 구어체 금액(원 단위).
     */
    private Integer extractManwonRoughBudgetWon(String prompt) {
        Matcher m = BUDGET_MANWON_ROUGH.matcher(prompt);
        if (!m.find()) {
            return null;
        }
        String number = m.group(1).replace(",", "");
        try {
            long man = Long.parseLong(number);
            if (man <= 0L || man > 1_000_000L) {
                return null;
            }
            long value = man * 10_000L;
            if (value > Integer.MAX_VALUE) {
                return null;
            }
            return (int) value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String extractCompanionType(String prompt) {
        if (containsAny(prompt, "혼자", "혼행", "1인", "솔로")) return "혼자";
        if (containsAny(prompt, "친구", "친구랑", "우정", "동창")) return "친구";
        if (containsAny(prompt,
                "가족", "부모님", "엄마", "아빠", "아이", "애기",
                "아이랑", "애랑", "아기랑", "키즈", "유아", "육아", "아동", "토들러", "영유아"
        )) return "가족";
        if (containsAny(prompt, "반려견", "강아지", "댕댕이", "반려동물")) return "반려견";
        if (containsAny(prompt, "연인", "커플", "데이트", "여자친구", "남자친구")) return "연인";
        if (containsAny(prompt, "단체", "워크샵", "워크숍", "회사", "동호회", "모임")) return "단체";
        return null;
    }

    private List<String> extractActivityTags(String prompt) {
        Set<String> tags = new LinkedHashSet<>();

        addTagIfMentioned(prompt, tags, "카페", "카페");
        addTagIfMentioned(
                prompt,
                tags,
                "야경",
                "야경",
                "밤거리",
                "경치",
                "전망",
                "조망",
                "뷰",
                "루프탑",
                "전망대",
                "sky",
                "view",
                "scenic",
                "viewpoint"
        );
        addTagIfMentioned(prompt, tags, "맛집", "맛집", "먹방", "음식", "식도락");
        addTagIfMentioned(prompt, tags, "산책", "산책", "걷기", "걷고");
        addTagIfMentioned(prompt, tags, "등산", "등산", "트레킹");
        addTagIfMentioned(prompt, tags, "사진", "사진", "포토", "인생샷");
        addTagIfMentioned(prompt, tags, "쇼핑", "쇼핑");
        addTagIfMentioned(prompt, tags, "바다", "바다", "해변", "오션뷰", "해수욕장", "스노클링", "다이빙", "스쿠버");
        addTagIfMentioned(prompt, tags, "일몰", "일몰", "노을", "야경명소");
        addTagIfMentioned(
                prompt,
                tags,
                "전시",
                "박물관",
                "미술관",
                "전시",
                "실내",
                "우천",
                "뮤지엄",
                "갤러리",
                "indoor",
                "rainy",
                "비오는날",
                "비오는",
                "비 오는",
                "장마철",
                "장마",
                "폭우"
        );
        addTagIfMentioned(prompt, tags, "시장", "시장", "전통시장", "로컬시장", "야시장");
        addTagIfMentioned(prompt, tags, "술집", "술집", "클럽", "바");
        addTagIfMentioned(prompt, tags, "브런치", "브런치", "카페투어");
        addTagIfMentioned(prompt, tags, "쇼핑몰", "쇼핑몰", "아울렛");
        addTagIfMentioned(prompt, tags, "온천", "온천", "노천탕", "스파");
        addTagIfMentioned(prompt, tags, "골프", "골프", "골프장");
        addTagIfMentioned(prompt, tags, "서핑", "서핑", "서핑하기");
        addTagIfMentioned(prompt, tags, "드라이브", "드라이브", "드라이브코스");
        addTagIfMentioned(prompt, tags, "한옥", "한옥", "한옥마을", "한옥스테이");
        addTagIfMentioned(prompt, tags, "테마파크", "테마파크", "놀이공원", "놀이동산");
        addTagIfMentioned(prompt, tags, "캠핑", "캠핑", "글램핑", "오토캠핑");
        addTagIfMentioned(prompt, tags, "사찰", "사찰", "템플스테이", "사찰순례");
        addTagIfMentioned(prompt, tags, "유적", "유적", "고분", "왕릉");
        addTagIfMentioned(prompt, tags, "자전거", "자전거", "라이딩", "사이클");
        addTagIfMentioned(prompt, tags, "래프팅", "래프팅", "레포츠", "짚라인");
        addTagIfMentioned(prompt, tags, "스키", "스키", "보드", "스노보드", "슬로프");
        addTagIfMentioned(prompt, tags, "계곡", "계곡", "폭포", "계곡트레킹");
        addTagIfMentioned(prompt, tags, "패러글라이딩", "패러글라이딩", "패러");
        addTagIfMentioned(prompt, tags, "낚시", "낚시", "바다낚시");

        return normalizeTagList(tags);
    }

    private void addTagIfMentioned(String prompt, Set<String> tags, String tag, String... keywords) {
        for (String keyword : keywords) {
            int idx = prompt.indexOf(keyword.toLowerCase(Locale.ROOT));
            while (idx >= 0) {
                if (!isNegatedAround(prompt, idx, keyword.length())) {
                    tags.add(tag);
                    return;
                }
                idx = prompt.indexOf(keyword.toLowerCase(Locale.ROOT), idx + keyword.length());
            }
        }
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
        Set<String> excluded = new LinkedHashSet<>();
        addExcludedIfNegated(prompt, excluded, "술집", "술집", "클럽", "바");
        addExcludedIfNegated(prompt, excluded, "등산", "등산", "트레킹");
        addExcludedIfNegated(prompt, excluded, "쇼핑", "쇼핑", "쇼핑몰", "아울렛");
        addExcludedIfNegated(prompt, excluded, "맛집", "맛집", "먹방", "식도락");
        addExcludedIfNegated(prompt, excluded, "카페", "카페", "브런치", "카페투어");
        addExcludedIfNegated(prompt, excluded, "바다", "바다", "해변", "오션뷰", "해수욕장");
        addExcludedIfNegated(prompt, excluded, "전시", "전시", "박물관", "미술관", "뮤지엄", "갤러리");
        LinkedHashSet<String> normalized = excluded.stream()
                .map(KeywordNormalizer::normalizeTag)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new ArrayList<>(normalized);
    }

    private void addExcludedIfNegated(String prompt, Set<String> excluded, String tag, String... keywords) {
        for (String keyword : keywords) {
            int idx = prompt.indexOf(keyword.toLowerCase(Locale.ROOT));
            while (idx >= 0) {
                if (isNegatedAround(prompt, idx, keyword.length())) {
                    excluded.add(tag);
                    return;
                }
                idx = prompt.indexOf(keyword.toLowerCase(Locale.ROOT), idx + keyword.length());
            }
        }
    }

    /**
     * soft 부정 태그와 겹치는 활동은 선호 활동에서 제외(요약/매칭 모순 방지).
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
        boolean heavy = containsAnyMerged(
                prompt,
                aiProperties.getParser().getSoftPenaltyHeavyHints(),
                DEFAULT_SOFT_PENALTY_HEAVY_HINTS
        );
        boolean hikingCtx = containsAnyMerged(
                prompt,
                aiProperties.getParser().getSoftPenaltyHikingContext(),
                DEFAULT_SOFT_PENALTY_HIKING_CONTEXT
        );
        if (heavy && hikingCtx) {
            raw.add("등산");
        }
        boolean noisyNight = containsAnyMerged(
                prompt,
                aiProperties.getParser().getSoftPenaltyNightlifeNoiseHints(),
                DEFAULT_SOFT_PENALTY_NIGHTLIFE_NOISE_HINTS
        );
        if (noisyNight && containsAny(prompt, "술집", "클럽", "바")) {
            raw.add("술집");
        }
        boolean crowded = containsAnyMerged(
                prompt,
                aiProperties.getParser().getSoftPenaltyShoppingCrowdHints(),
                DEFAULT_SOFT_PENALTY_SHOPPING_CROWD_HINTS
        );
        boolean shoppingCtx = containsAnyMerged(
                prompt,
                aiProperties.getParser().getSoftPenaltyShoppingActivityContext(),
                DEFAULT_SOFT_PENALTY_SHOPPING_ACTIVITY_CONTEXT
        );
        if (crowded && shoppingCtx) {
            raw.add("쇼핑");
        }
        return normalizeTagList(raw);
    }

    /**
     * YAML 목록이 비어 있으면 {@code defaults}만, 있으면 defaults + YAML을 합쳐 매칭한다.
     */
    private static List<String> mergeKeywordList(List<String> yaml, String[] defaults) {
        if (yaml == null || yaml.isEmpty()) {
            return Arrays.asList(defaults);
        }
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        for (String d : defaults) {
            merged.add(d);
        }
        for (String y : yaml) {
            if (y != null && !y.isBlank()) {
                merged.add(y.trim());
            }
        }
        return new ArrayList<>(merged);
    }

    private List<String> findAllMatchedKeywordsMerged(String prompt, List<String> yaml, String[] defaults) {
        List<String> merged = mergeKeywordList(yaml, defaults);
        LinkedHashSet<String> matched = new LinkedHashSet<>();
        for (String keyword : merged) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            String k = keyword.trim().toLowerCase(Locale.ROOT);
            if (!k.isEmpty() && prompt.contains(k)) {
                matched.add(keyword.trim());
            }
        }
        return new ArrayList<>(matched);
    }

    private boolean containsAnyMerged(String prompt, List<String> yaml, String[] defaults) {
        List<String> merged = mergeKeywordList(yaml, defaults);
        return containsAny(prompt, merged.toArray(new String[0]));
    }

    private List<String> extractLanguages(String prompt) {
        Set<String> languages = new LinkedHashSet<>();

        addLanguageIfMentioned(prompt, languages, "한국어", "한국어");
        addLanguageIfMentioned(prompt, languages, "영어", "영어", "english", "eng");
        addLanguageIfMentioned(prompt, languages, "일본어", "일본어", "japanese", "jp");
        addLanguageIfMentioned(prompt, languages, "중국어", "중국어", "chinese", "cn");
        addLanguageIfMentioned(prompt, languages, "프랑스어", "프랑스어", "프랑스", "french", "français");
        addLanguageIfMentioned(prompt, languages, "스페인어", "스페인어", "스페인", "spanish", "español");
        addLanguageIfMentioned(prompt, languages, "독일어", "독일어", "독일", "german", "deutsch");
        addLanguageIfMentioned(prompt, languages, "베트남어", "베트남어", "베트남", "vietnamese", "vn");
        addLanguageIfMentioned(prompt, languages, "태국어", "태국어", "태국", "thai", "ภาษาไทย");
        addLanguageIfMentioned(prompt, languages, "이탈리아어", "이탈리아어", "이탈리아", "italian", "italiano");

        return new ArrayList<>(languages);
    }

    private void addLanguageIfMentioned(String prompt, Set<String> languages, String language, String... keywords) {
        for (String keyword : keywords) {
            int idx = prompt.indexOf(keyword.toLowerCase(Locale.ROOT));
            while (idx >= 0) {
                if (!isNegatedAround(prompt, idx, keyword.length())) {
                    languages.add(language);
                    return;
                }
                idx = prompt.indexOf(keyword.toLowerCase(Locale.ROOT), idx + keyword.length());
            }
        }
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
        Matcher range = BUDGET_RANGE_WON.matcher(prompt);
        if (range.find()) {
            Integer start = convertWon(range.group(1), range.group(2));
            String endUnit = range.group(4) == null ? range.group(2) : range.group(4);
            Integer end = convertWon(range.group(3), endUnit);
            if (start != null && end != null) {
                return (start + end) / 2;
            }
        }

        Matcher m = BUDGET_WON.matcher(prompt);
        if (!m.find()) {
            return null;
        }
        return convertWon(m.group(1), m.group(2));
    }

    private Integer convertWon(String rawNumber, String unit) {
        String number = rawNumber.replace(",", "");
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
        Integer dateRangeDays = extractDateRangeDays(prompt);
        if (dateRangeDays != null) {
            return dateRangeDays;
        }
        if (containsAny(prompt, "당일치기", "당일")) {
            return 1;
        }
        if (containsAny(prompt, "주말", "이번 주말", "다음 주말", "다다음 주말")) {
            return 2;
        }
        if (containsAny(prompt, "1박2일")) {
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
        Matcher nightsOnly = DURATION_NIGHTS_ONLY.matcher(prompt);
        if (nightsOnly.find()) {
            try {
                int nights = Integer.parseInt(nightsOnly.group(1));
                int days = nights + 1;
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
        Matcher range = DURATION_RANGE_DAYS.matcher(prompt);
        if (range.find()) {
            try {
                int start = Integer.parseInt(range.group(1));
                int end = Integer.parseInt(range.group(2));
                int days = Math.max(start, end);
                return (days <= 0 || days > 30) ? null : days;
            } catch (NumberFormatException ignored) {
            }
        }
        Matcher dSuffix = DURATION_DAYS_SUFFIX.matcher(prompt);
        if (dSuffix.find()) {
            try {
                int days = Integer.parseInt(dSuffix.group(1));
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
        Matcher range = BUDGET_MANWON_RANGE.matcher(prompt);
        if (range.find()) {
            try {
                int start = Integer.parseInt(range.group(1));
                int end = Integer.parseInt(range.group(2));
                return Math.max(start, end);
            } catch (NumberFormatException ignored) {
            }
        }
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

    private Integer extractDateRangeDays(String prompt) {
        Matcher monthDay = DATE_RANGE_MONTH_DAY.matcher(prompt);
        if (monthDay.find()) {
            try {
                int startMonth = Integer.parseInt(monthDay.group(1));
                int startDay = Integer.parseInt(monthDay.group(2));
                int endMonth = Integer.parseInt(monthDay.group(3));
                int endDay = Integer.parseInt(monthDay.group(4));
                if (startMonth == endMonth && endDay >= startDay) {
                    int days = endDay - startDay + 1;
                    return (days <= 0 || days > 30) ? null : days;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        Matcher dayOnly = DATE_RANGE_DAY_ONLY.matcher(prompt);
        if (dayOnly.find()) {
            try {
                int startDay = Integer.parseInt(dayOnly.group(1));
                int endDay = Integer.parseInt(dayOnly.group(2));
                if (endDay >= startDay) {
                    int days = endDay - startDay + 1;
                    return (days <= 0 || days > 30) ? null : days;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private boolean isNegatedAround(String prompt, int idx, int keywordLength) {
        String after = prompt.substring(idx + keywordLength, Math.min(prompt.length(), idx + keywordLength + NEGATION_AFTER_WINDOW));
        return containsAnyMerged(
                after,
                aiProperties.getParser().getExclusionIntentKeywords(),
                DEFAULT_EXCLUSION_INTENT_KEYWORDS
        ) || containsAny(after, "부담", "별로", "안 좋아", "안좋아", "원하지 않", "원하지않", "아니어도", "안 해도", "안해도");
    }

    private boolean hasPriorityHintAround(String prompt, int idx, int keywordLength) {
        int start = Math.max(0, idx - 10);
        int end = Math.min(prompt.length(), idx + keywordLength + 10);
        String window = prompt.substring(start, end);
        return containsAny(window, "위주", "중심", "우선", "선호", "좋아", "좋고", "좋겠", "좋은");
    }

    private int stylePriority(String style) {
        return switch (style) {
            case "힐링" -> 4;
            case "감성" -> 3;
            case "로컬" -> 2;
            case "액티비티" -> 1;
            default -> 0;
        };
    }

    /**
     * 제외 목록과 겹치는 선호 활동은 제외를 우선한다(요청 일관성).
     */
    private List<String> stripActivityTagsOverlappingExcluded(List<String> activityTags, List<String> excluded) {
        if (activityTags == null || activityTags.isEmpty() || excluded == null || excluded.isEmpty()) {
            return activityTags == null ? List.of() : activityTags;
        }
        Set<String> ex = excluded.stream()
                .map(KeywordNormalizer::normalizeTag)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        return activityTags.stream()
                .filter(a -> !ex.contains(KeywordNormalizer.normalizeTag(a)))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private record ActivityTagStrength(List<String> required, List<String> nice) {
    }

    private record LanguageStrength(List<String> required, List<String> nice) {
    }

    private LanguageStrength classifyLanguageStrength(String normalizedLowerPrompt, List<String> preferredLanguages) {
        if (preferredLanguages == null || preferredLanguages.isEmpty()) {
            return new LanguageStrength(List.of(), List.of());
        }
        LinkedHashSet<String> required = new LinkedHashSet<>();
        LinkedHashSet<String> nice = new LinkedHashSet<>();
        String p = normalizedLowerPrompt;
        for (String raw : preferredLanguages) {
            String lang = KeywordNormalizer.normalizeLanguage(raw);
            if (lang == null || lang.isBlank()) {
                continue;
            }
            boolean strong = false;
            boolean weak = false;
            String needle = raw.toLowerCase(Locale.ROOT);
            int idx = 0;
            while ((idx = p.indexOf(needle, idx)) >= 0) {
                int winStart = Math.max(0, idx - 36);
                int winEnd = Math.min(p.length(), idx + needle.length() + 18);
                String win = p.substring(winStart, winEnd);
                if (containsAnyIntent(win, STRONG_ACTIVITY_INTENT_FRAGMENTS)) {
                    strong = true;
                }
                if (containsAnyIntent(win, NICE_ACTIVITY_INTENT_FRAGMENTS)) {
                    weak = true;
                }
                idx = idx + 1;
            }
            if (strong) {
                required.add(lang);
            } else if (weak) {
                nice.add(lang);
            }
        }
        nice.removeAll(required);
        return new LanguageStrength(List.copyOf(required), List.copyOf(nice));
    }

    private Boolean extractAllowAdjacentRegion(String normalizedLowerPrompt) {
        String p = normalizedLowerPrompt;
        if (containsAny(p,
                "근처", "주변", "인접", "가까운 곳", "가까운곳",
                "근방", "옆동네", "근교", "근처도", "인접도", "주변도", "근방도",
                "상관없", "괜찮", "무관"
        )) {
            if (containsAny(p, "인접", "근처", "주변", "가까운", "근교", "옆동네", "근방")) {
                return true;
            }
        }
        return null;
    }

    private Boolean extractStrictBudgetIntent(String normalizedLowerPrompt) {
        if (normalizedLowerPrompt == null || normalizedLowerPrompt.isBlank()) {
            return null;
        }
        if (!containsAny(normalizedLowerPrompt, "예산", "가격", "가성비", "럭셔리", "비싸", "저렴", "싸게", "비용")) {
            return null;
        }
        return containsAny(normalizedLowerPrompt, "꼭", "반드시", "필수", "무조건", "최우선") ? true : null;
    }

    private static final String[] STRONG_ACTIVITY_INTENT_FRAGMENTS = {
            "꼭", "반드시", "필수", "무조건", "최우선"
    };
    private static final String[] NICE_ACTIVITY_INTENT_FRAGMENTS = {
            "있으면 좋", "되면 좋", "가능하면", "괜찮으면", "부담없으면"
    };

    private static final Map<String, List<String>> ACTIVITY_KEYWORD_ALIASES = new HashMap<>();

    static {
        ACTIVITY_KEYWORD_ALIASES.put("바다", List.of("바다", "해변", "오션뷰", "해수욕장"));
        ACTIVITY_KEYWORD_ALIASES.put("카페", List.of("카페", "브런치", "카페투어"));
        ACTIVITY_KEYWORD_ALIASES.put("등산", List.of("등산", "트레킹"));
        ACTIVITY_KEYWORD_ALIASES.put("맛집", List.of("맛집", "먹방", "식도락"));
        ACTIVITY_KEYWORD_ALIASES.put("쇼핑", List.of("쇼핑", "쇼핑몰", "아울렛"));
        ACTIVITY_KEYWORD_ALIASES.put("전시", List.of("전시", "박물관", "미술관", "뮤지엄", "갤러리"));
        ACTIVITY_KEYWORD_ALIASES.put("야경", List.of("야경", "밤거리", "전망", "루프탑"));
        ACTIVITY_KEYWORD_ALIASES.put("산책", List.of("산책", "걷기"));
        ACTIVITY_KEYWORD_ALIASES.put("사진", List.of("사진", "포토", "인생샷"));
        ACTIVITY_KEYWORD_ALIASES.put("술집", List.of("술집", "클럽", "바"));
        ACTIVITY_KEYWORD_ALIASES.put("시장", List.of("시장", "전통시장", "야시장"));
        ACTIVITY_KEYWORD_ALIASES.put("온천", List.of("온천", "노천탕", "스파"));
        ACTIVITY_KEYWORD_ALIASES.put("캠핑", List.of("캠핑", "글램핑"));
    }

    /**
     * 활동 태그별로 문맥 창에서 강한 의도(꼭/반드시) vs 약한 선호(되면 좋고)를 나눈다.
     */
    private ActivityTagStrength classifyActivityTagStrength(String normalizedLowerPrompt, List<String> normalizedActivityTags) {
        if (normalizedActivityTags == null || normalizedActivityTags.isEmpty()) {
            return new ActivityTagStrength(List.of(), List.of());
        }
        LinkedHashSet<String> required = new LinkedHashSet<>();
        LinkedHashSet<String> nice = new LinkedHashSet<>();
        String p = normalizedLowerPrompt;
        for (String rawTag : normalizedActivityTags) {
            String tag = KeywordNormalizer.normalizeTag(rawTag);
            if (tag == null || tag.isBlank()) {
                continue;
            }
            boolean strong = false;
            boolean weak = false;
            for (String kw : activityKeywordsForCanonicalTag(tag)) {
                String needle = kw.toLowerCase(Locale.ROOT);
                int idx = 0;
                while ((idx = p.indexOf(needle, idx)) >= 0) {
                    int winStart = Math.max(0, idx - 48);
                    int winEnd = Math.min(p.length(), idx + needle.length() + 14);
                    String win = p.substring(winStart, winEnd);
                    if (containsAnyIntent(win, STRONG_ACTIVITY_INTENT_FRAGMENTS)) {
                        strong = true;
                    }
                    if (containsAnyIntent(win, NICE_ACTIVITY_INTENT_FRAGMENTS)) {
                        weak = true;
                    }
                    idx = idx + 1;
                }
            }
            if (strong) {
                required.add(tag);
            } else if (weak) {
                nice.add(tag);
            }
        }
        nice.removeAll(required);
        return new ActivityTagStrength(List.copyOf(required), List.copyOf(nice));
    }

    private static List<String> activityKeywordsForCanonicalTag(String canonicalTag) {
        List<String> extra = ACTIVITY_KEYWORD_ALIASES.get(canonicalTag);
        if (extra != null) {
            return extra;
        }
        return List.of(canonicalTag);
    }

    private static boolean containsAnyIntent(String haystack, String[] fragments) {
        for (String f : fragments) {
            if (haystack.contains(f)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 같은 활동에 대해 ‘빼고’로 제외됐다가 뒤에서 ‘꼭/위주’로 다시 선호하는 경우, 제외를 완화한다.
     */
    private List<String> relaxExcludedWhenExplicitlyRequired(String prompt, List<String> excluded) {
        if (excluded == null || excluded.isEmpty()) {
            return excluded == null ? List.of() : excluded;
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String tag : excluded) {
            String normalizedTag = KeywordNormalizer.normalizeTag(tag);
            if (normalizedTag == null || normalizedTag.isBlank()) {
                continue;
            }
            if (exclusionOverriddenByExplicitRequirement(prompt, normalizedTag)) {
                continue;
            }
            out.add(normalizedTag);
        }
        return new ArrayList<>(out);
    }

    private boolean exclusionOverriddenByExplicitRequirement(String prompt, String normalizedTag) {
        String[][] groups = {
                {"술집", "술집", "클럽"},
                {"등산", "등산", "트레킹"},
                {"쇼핑", "쇼핑", "쇼핑몰", "아울렛"},
                {"맛집", "맛집", "먹방", "식도락"},
                {"카페", "카페", "브런치", "카페투어"},
                {"바다", "바다", "해변", "오션뷰", "해수욕장"},
                {"전시", "전시", "박물관", "미술관", "뮤지엄", "갤러리"}
        };
        for (String[] g : groups) {
            if (!normalizedTag.equals(KeywordNormalizer.normalizeTag(g[0]))) {
                continue;
            }
            for (int i = 1; i < g.length; i++) {
                if (keywordHasStrongRequirement(prompt, g[i])) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * ‘위주’만으로는 앞선 ‘빼고’와 충돌하는 경우가 많아, 제외 완화는 강한 의도 표현에만 적용한다.
     */
    private boolean keywordHasStrongRequirement(String prompt, String keyword) {
        if (keyword == null || keyword.length() < 2) {
            return false;
        }
        String kw = keyword.toLowerCase(Locale.ROOT);
        int idx = prompt.indexOf(kw);
        while (idx >= 0) {
            if (isNegatedAround(prompt, idx, kw.length())) {
                idx = prompt.indexOf(kw, idx + kw.length());
                continue;
            }
            int start = Math.max(0, idx - 18);
            int end = Math.min(prompt.length(), idx + kw.length() + 18);
            String win = prompt.substring(start, end);
            if (containsAny(win,
                    "꼭", "반드시", "필수",
                    "가고 싶", "가고싶", "가야", "잡아주", "가보고 싶", "가보고싶"
            )) {
                return true;
            }
            idx = prompt.indexOf(kw, idx + kw.length());
        }
        return false;
    }
}
