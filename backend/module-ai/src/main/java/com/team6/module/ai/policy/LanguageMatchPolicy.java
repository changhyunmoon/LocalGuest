package com.team6.module.ai.policy;

import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import org.springframework.stereotype.Component;

@Component
public class LanguageMatchPolicy {

    public int score(TravelerPreference pref, GuideAiProfile guide) {
        if (pref.getPreferredLanguages() == null || guide.getLanguages() == null) {
            return 0;
        }

        boolean matched = pref.getPreferredLanguages().stream()
                .anyMatch(lang -> guide.getLanguages().contains(lang));

        return matched ? 10 : 0;
    }
}