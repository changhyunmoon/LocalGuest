package com.team6.module.ai.policy;

import com.team6.module.ai.config.ScoringPolicySnapshot;
import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import com.team6.module.ai.parser.KeywordNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ActivityMatchPolicy {

    private final ScoringPolicySnapshot scoring;

    public int score(TravelerPreference pref, GuideAiProfile guide) {
        if (guide.getSpecialtyTags() == null) {
            return 0;
        }

        Set<String> guideTags = guide.getSpecialtyTags().stream()
                .map(KeywordNormalizer::normalizeTag)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toSet());

        int positive = 0;
        if (pref.getActivityTags() != null) {
            Set<String> prefTags = pref.getActivityTags().stream()
                    .map(KeywordNormalizer::normalizeTag)
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.toSet());

            long matchedCount = prefTags.stream()
                    .filter(guideTags::contains)
                    .count();

            positive = (int) matchedCount * scoring.weightActivity();
        }

        int penalty = softPenaltyDeduction(pref.getSoftPenaltyActivityTags(), guideTags);

        return Math.max(0, positive - penalty);
    }

    private int softPenaltyDeduction(List<String> softPenaltyTags, Set<String> guideTags) {
        if (softPenaltyTags == null || softPenaltyTags.isEmpty()) {
            return 0;
        }
        Set<String> soft = softPenaltyTags.stream()
                .map(KeywordNormalizer::normalizeTag)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toSet());
        long hits = guideTags.stream().filter(soft::contains).count();
        return (int) hits * scoring.softActivityPenaltyPerTag();
    }
}
