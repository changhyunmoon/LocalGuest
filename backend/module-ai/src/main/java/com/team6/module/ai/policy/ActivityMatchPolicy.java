package com.team6.module.ai.policy;

import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import com.team6.module.ai.parser.KeywordNormalizer;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ActivityMatchPolicy {

    public int score(TravelerPreference pref, GuideAiProfile guide) {
        if (pref.getActivityTags() == null || guide.getSpecialtyTags() == null) {
            return 0;
        }

        Set<String> prefTags = pref.getActivityTags().stream()
                .map(KeywordNormalizer::normalizeTag)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toSet());

        Set<String> guideTags = guide.getSpecialtyTags().stream()
                .map(KeywordNormalizer::normalizeTag)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toSet());

        long matchedCount = prefTags.stream()
                .filter(guideTags::contains)
                .count();

        return (int) matchedCount * ScoreWeight.ACTIVITY;
    }
}