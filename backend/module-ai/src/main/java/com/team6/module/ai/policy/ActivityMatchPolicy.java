package com.team6.module.ai.policy;

import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import org.springframework.stereotype.Component;

@Component
public class ActivityMatchPolicy {

    public int score(TravelerPreference pref, GuideAiProfile guide) {
        if (pref.getActivityTags() == null || guide.getSpecialtyTags() == null) {
            return 0;
        }

        long matchedCount = pref.getActivityTags().stream()
                .filter(guide.getSpecialtyTags()::contains)
                .count();

        return (int) matchedCount * ScoreWeight.ACTIVITY;
    }
}