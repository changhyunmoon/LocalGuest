package com.team6.module.ai.policy;

import com.team6.module.ai.config.ScoringPolicySnapshot;
import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import com.team6.module.ai.parser.KeywordNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
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

        Set<String> required = normalizeTagSet(pref.getRequiredActivityTags());
        Set<String> nice = normalizeTagSet(pref.getNiceToHaveActivityTags());

        int positive = 0;
        if (pref.getActivityTags() != null) {
            Set<String> prefTags = pref.getActivityTags().stream()
                    .map(KeywordNormalizer::normalizeTag)
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.toSet());

            int w = scoring.weightActivity();
            int nicePct = Math.max(0, Math.min(100, scoring.niceToHaveActivityWeightPercent()));
            for (String tag : prefTags) {
                if (!guideTags.contains(tag)) {
                    continue;
                }
                if (required.contains(tag)) {
                    positive += w;
                } else if (nice.contains(tag)) {
                    positive += w * nicePct / 100;
                } else {
                    positive += w;
                }
            }
        }

        int requiredMiss = 0;
        for (String r : required) {
            if (!guideTags.contains(r)) {
                requiredMiss += scoring.requiredActivityMissPenaltyPerTag();
            }
        }

        int penalty = softPenaltyDeduction(pref.getSoftPenaltyActivityTags(), guideTags);

        return Math.max(0, positive - requiredMiss - penalty);
    }

    private static Set<String> normalizeTagSet(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (String t : tags) {
            String n = KeywordNormalizer.normalizeTag(t);
            if (n != null && !n.isBlank()) {
                out.add(n);
            }
        }
        return out;
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
