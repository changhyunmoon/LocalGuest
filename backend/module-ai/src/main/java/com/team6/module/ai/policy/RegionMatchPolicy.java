package com.team6.module.ai.policy;

import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import com.team6.module.ai.support.AdjacentRegionProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RegionMatchPolicy {

    private final AdjacentRegionProvider adjacentRegionProvider;

    public int score(TravelerPreference pref, GuideAiProfile guide) {
        if (pref.getRegion() == null || guide.getRegion() == null) {
            return 0;
        }
        if (pref.getRegion().equalsIgnoreCase(guide.getRegion())) {
            return ScoreWeight.REGION;
        }
        return adjacentRegionProvider.isAdjacentTo(pref.getRegion(), guide.getRegion())
                ? ScoreWeight.REGION_ADJACENT
                : 0;
    }
}