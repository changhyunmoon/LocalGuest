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

/**
 * 예산·스타일·활동 태그 조합 등 제품 룰 보너스({@link com.team6.module.ai.config.ScoringPolicySettings#getComboRules()}).
 */
@Component
@RequiredArgsConstructor
public class ComboMatchPolicy {

    private final ScoringPolicySnapshot scoring;

    public int score(TravelerPreference pref, GuideAiProfile guide) {
        if (pref == null || scoring.comboRules() == null || scoring.comboRules().isEmpty()) {
            return 0;
        }
        int total = 0;
        for (ScoringPolicySnapshot.ComboRule rule : scoring.comboRules()) {
            if (rule == null || rule.bonusPoints() == 0) {
                continue;
            }
            if (matchesPreference(pref, rule)) {
                total += rule.bonusPoints();
            }
        }
        return total;
    }

    private static boolean matchesPreference(TravelerPreference pref, ScoringPolicySnapshot.ComboRule rule) {
        if (rule.requireActivityTagsAll() == null || rule.requireActivityTagsAll().isEmpty()) {
            return false;
        }
        if (rule.budgetLevel() != null && !rule.budgetLevel().isBlank()) {
            if (pref.getBudgetLevel() == null
                    || !pref.getBudgetLevel().trim().equalsIgnoreCase(rule.budgetLevel().trim())) {
                return false;
            }
        }
        if (rule.travelStyle() != null && !rule.travelStyle().isBlank()) {
            if (pref.getTravelStyle() == null
                    || !pref.getTravelStyle().trim().equalsIgnoreCase(rule.travelStyle().trim())) {
                return false;
            }
        }
        Set<String> prefTags = toNormalizedTagSet(pref.getActivityTags());
        for (String required : rule.requireActivityTagsAll()) {
            String n = KeywordNormalizer.normalizeTag(required);
            if (n == null || n.isBlank() || !prefTags.contains(n)) {
                return false;
            }
        }
        return true;
    }

    private static Set<String> toNormalizedTagSet(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Set.of();
        }
        return tags.stream()
                .map(KeywordNormalizer::normalizeTag)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toCollection(HashSet::new));
    }
}
