package com.team6.module.ai.policy;

import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import com.team6.module.ai.parser.KeywordNormalizer;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class LanguageMatchPolicy {

    public int score(TravelerPreference pref, GuideAiProfile guide) {
        if (pref.getPreferredLanguages() == null || guide.getLanguages() == null) {
            return 0;
        }

        Set<String> prefLang = pref.getPreferredLanguages().stream()
                .map(KeywordNormalizer::normalizeLanguage)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toSet());

        Set<String> guideLang = guide.getLanguages().stream()
                .map(KeywordNormalizer::normalizeLanguage)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toSet());

        boolean matched = prefLang.stream().anyMatch(guideLang::contains);

        return matched ? ScoreWeight.LANGUAGE : 0;
    }
}