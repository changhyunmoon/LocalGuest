package com.team6.module.ai.policy;

import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import org.springframework.stereotype.Component;

@Component
public class RegionMatchPolicy {

    public int score(TravelerPreference pref, GuideAiProfile guide) {
        if (pref.getRegion() == null || guide.getRegion() == null) {
            return 0;
        }
        return pref.getRegion().equalsIgnoreCase(guide.getRegion()) ? ScoreWeight.REGION : 0;
    }
}