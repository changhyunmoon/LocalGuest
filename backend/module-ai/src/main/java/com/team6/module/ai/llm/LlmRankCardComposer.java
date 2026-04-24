package com.team6.module.ai.llm;

import com.team6.module.ai.dto.request.GuideRecommendRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * LLM 순위 호출용 후보 카드 텍스트 조립(길이·순서 보조).
 */
public final class LlmRankCardComposer {

    private LlmRankCardComposer() {
    }

    public static long stableSeed(String prompt, List<GuideRecommendRequest.GuideCandidateDto> candidates) {
        long h = prompt == null ? 0L : prompt.hashCode();
        long mix = 0x243F6A8885A308D3L;
        if (candidates != null) {
            for (GuideRecommendRequest.GuideCandidateDto c : candidates) {
                if (c != null && c.getGuideId() != null) {
                    mix ^= c.getGuideId() * 0x9E3779B97F4A7C15L;
                }
            }
        }
        return h ^ mix;
    }

    /**
     * 리뷰·평점·피드 개수로 LLM 입력 목록 순서를 잡아 위치 편향을 줄인다.
     */
    public static List<GuideRecommendRequest.GuideCandidateDto> sortByQualityForPrompt(
            List<GuideRecommendRequest.GuideCandidateDto> in
    ) {
        if (in == null || in.isEmpty()) {
            return List.of();
        }
        List<GuideRecommendRequest.GuideCandidateDto> copy = new ArrayList<>(in);
        Comparator<GuideRecommendRequest.GuideCandidateDto> cmp = Comparator
                .comparingDouble(LlmRankCardComposer::qualityScore)
                .reversed()
                .thenComparing(c -> c.getGuideId() == null ? Long.MAX_VALUE : c.getGuideId());
        copy.sort(cmp);
        return List.copyOf(copy);
    }

    private static double qualityScore(GuideRecommendRequest.GuideCandidateDto c) {
        int rc = c.getReviewCount() == null ? 0 : Math.max(0, c.getReviewCount());
        double rating = 0.0;
        if (c.getAverageRating() != null && c.getAverageRating().signum() > 0) {
            rating = c.getAverageRating().doubleValue();
        }
        int feeds = c.getPublicFeedCount() == null ? 0 : Math.max(0, c.getPublicFeedCount());
        int tagCount = c.getSpecialtyTags() == null ? 0 : c.getSpecialtyTags().size();
        // 태그는 신호로 쓰되 과대평가하지 않도록 매우 약하게만 가점.
        double tagBonus = 0.05d * Math.log1p(Math.max(0, tagCount));
        return Math.log1p(rc) * (rating + 0.15d) + 0.22d * Math.log1p(feeds) + tagBonus;
    }

    /**
     * 최신 피드 1개 + (피드 2개 이상이면) 시드로 고른 또 다른 피드 1개.
     */
    public static int secondFeedIndex(int feedCount, long seed) {
        if (feedCount < 2) {
            return -1;
        }
        int span = feedCount - 1;
        return 1 + (int) (Math.floorMod(seed, span));
    }

    public static String buildRankUserContent(
            String userPrompt,
            List<GuideRecommendRequest.GuideCandidateDto> sortedCandidates,
            long tieBreakSeed
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("사용자 요청:\n").append(userPrompt == null ? "" : userPrompt.strip()).append("\n\n");
        sb.append("아래는 동일 지역(및 서버 게이트)을 통과한 가이드 후보이다. 각 블록의 guideId는 반드시 그대로 orderedGuideIds에만 사용한다.\n\n");
        int idx = 1;
        for (GuideRecommendRequest.GuideCandidateDto c : sortedCandidates) {
            if (c == null || c.getGuideId() == null) {
                continue;
            }
            sb.append("--- 후보 #").append(idx++).append(" ---\n");
            sb.append("guideId=").append(c.getGuideId()).append('\n');
            sb.append("이름=").append(nullToEmpty(c.getGuideName())).append('\n');
            sb.append("지역=").append(nullToEmpty(c.getRegion())).append('\n');
            sb.append("스타일=").append(nullToEmpty(c.getGuideStyle())).append('\n');
            sb.append("가격대=").append(nullToEmpty(c.getPriceLevel())).append('\n');
            appendPriceRangeLine(sb, c.getPriceMinWon(), c.getPriceMaxWon(), c.getPriceScope());
            appendRatingLine(sb, c.getAverageRating(), c.getReviewCount());
            sb.append("공개피드수=").append(c.getPublicFeedCount() == null ? 0 : c.getPublicFeedCount()).append('\n');
            if (c.getLatestPublicFeedDate() != null && !c.getLatestPublicFeedDate().isBlank()) {
                sb.append("최근피드일=").append(c.getLatestPublicFeedDate().strip()).append('\n');
            }
            if (Boolean.TRUE.equals(c.getColdStart())) {
                sb.append("콜드스타트=리뷰없음").append('\n');
            }
            if (c.getResidenceYears() != null && c.getResidenceYears() > 0) {
                sb.append("거주연수=").append(c.getResidenceYears()).append("년").append('\n');
            }
            appendTrustSignalLine(sb, c);
            String core = nullToEmpty(c.getCoreSpecialtyTagsTop3());
            if (!core.isEmpty()) {
                sb.append("핵심태그=").append(core).append('\n');
            }
            sb.append("태그=").append(joinTags(c.getSpecialtyTags())).append('\n');
            sb.append("언어=").append(joinTags(c.getLanguages())).append('\n');
            String kw = nullToEmpty(c.getLlmKeywordsSnippet());
            if (!kw.isEmpty()) {
                sb.append("키워드=").append(kw).append('\n');
            }
            String intro = nullToEmpty(c.getLlmIntroSnippet());
            if (!intro.isEmpty()) {
                sb.append("소개:\n").append(intro).append('\n');
            }
            appendFeedSection(sb, c, tieBreakSeed ^ (c.getGuideId() * 31L));
            String course = nullToEmpty(c.getLlmDefaultCourseSnippet());
            if (!course.isEmpty()) {
                sb.append("디폴트코스:\n").append(course).append('\n');
            }
            String careers = nullToEmpty(c.getLlmCareerSnippet());
            if (!careers.isEmpty()) {
                sb.append("경력:\n").append(careers).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static void appendPriceRangeLine(StringBuilder sb, Integer minWon, Integer maxWon, String scope) {
        if (minWon == null && maxWon == null) {
            return;
        }
        sb.append("가격범위=");
        if (minWon != null) {
            sb.append(minWon);
        } else {
            sb.append("-");
        }
        sb.append("~");
        if (maxWon != null) {
            sb.append(maxWon);
        } else {
            sb.append("-");
        }
        if (scope != null && !scope.isBlank()) {
            sb.append(" (").append(scope.strip()).append(")");
        }
        sb.append('\n');
    }

    private static void appendTrustSignalLine(StringBuilder sb, GuideRecommendRequest.GuideCandidateDto c) {
        if (c == null) {
            return;
        }
        int refunds = c.getApprovedRefundCount() == null ? 0 : Math.max(0, c.getApprovedRefundCount());
        int req = c.getMatchRequestCount() == null ? 0 : Math.max(0, c.getMatchRequestCount());
        int prog = c.getProgressedMatchCount() == null ? 0 : Math.max(0, c.getProgressedMatchCount());
        int chat = c.getChatStartCount() == null ? 0 : Math.max(0, c.getChatStartCount());
        int clicks = c.getRecommendClickCount() == null ? 0 : Math.max(0, c.getRecommendClickCount());
        int exposures = c.getRecommendExposureCount() == null ? 0 : Math.max(0, c.getRecommendExposureCount());
        int debiased = c.getRecommendClickDebiasedScore() == null ? 0 : Math.max(0, c.getRecommendClickDebiasedScore());

        if (refunds + req + prog + chat + clicks + exposures + debiased == 0) {
            return;
        }
        sb.append("신뢰신호=");
        boolean first = true;
        if (refunds > 0) {
            sb.append("환불승인 ").append(refunds);
            first = false;
        }
        if (req > 0 || prog > 0 || chat > 0) {
            if (!first) sb.append(", ");
            sb.append("매칭요청 ").append(req).append("/진행 ").append(prog).append("/채팅 ").append(chat);
            first = false;
        }
        if (exposures > 0 || clicks > 0 || debiased > 0) {
            if (!first) sb.append(", ");
            sb.append("노출 ").append(exposures).append("/클릭 ").append(clicks);
            if (debiased > 0) {
                sb.append(" (보정 ").append(debiased).append(")");
            }
        }
        sb.append('\n');
    }

    private static void appendFeedSection(
            StringBuilder sb,
            GuideRecommendRequest.GuideCandidateDto c,
            long perGuideSeed
    ) {
        List<String> bodies = c.getLlmFeedBodiesNewestFirst();
        if (bodies == null || bodies.isEmpty()) {
            return;
        }
        sb.append("피드(발췌):\n");
        sb.append("- [최신] ").append(bodies.get(0)).append('\n');
        int n = bodies.size();
        int second = secondFeedIndex(n, perGuideSeed);
        if (second >= 0 && second < n && second != 0) {
            sb.append("- [추가] ").append(bodies.get(second)).append('\n');
        }
    }

    private static void appendRatingLine(StringBuilder sb, BigDecimal avg, Integer reviewCount) {
        if (avg == null && (reviewCount == null || reviewCount == 0)) {
            return;
        }
        sb.append("평점/리뷰수=");
        if (avg != null) {
            sb.append(avg.stripTrailingZeros().toPlainString());
        } else {
            sb.append("-");
        }
        sb.append(" / ").append(reviewCount == null ? 0 : reviewCount).append('\n');
    }

    private static String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        return String.join(", ", tags.stream().filter(Objects::nonNull).map(String::strip).filter(s -> !s.isEmpty()).toList());
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
