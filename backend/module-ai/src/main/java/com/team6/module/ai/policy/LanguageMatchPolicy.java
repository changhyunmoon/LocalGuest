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
public class LanguageMatchPolicy {

    private final ScoringPolicySnapshot scoring;

    public int score(TravelerPreference pref, GuideAiProfile guide) {
        if (guide.getLanguages() == null) {
            return 0;
        }

        Set<String> required = normalizeLangSet(pref.getRequiredLanguages());
        Set<String> nice = normalizeLangSet(pref.getNiceToHaveLanguages());
        Set<String> prefLang = normalizeLangSet(pref.getPreferredLanguages());

        Set<String> guideLang = guide.getLanguages().stream()
                .map(KeywordNormalizer::normalizeLanguage)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toSet());

        if (!required.isEmpty()) {
            boolean requiredMatched = required.stream().anyMatch(guideLang::contains);
            if (!requiredMatched) {
                return -scoring.requiredLanguageMissPenalty();
            }
        }

        boolean matched = prefLang.stream().anyMatch(guideLang::contains);
        if (!matched) {
            return 0;
        }
        if (!nice.isEmpty()) {
            boolean niceMatched = nice.stream().anyMatch(guideLang::contains);
            if (niceMatched) {
                int pct = Math.max(0, Math.min(100, scoring.niceToHaveLanguageWeightPercent()));
                return scoring.weightLanguage() * pct / 100;
            }
        }

        return scoring.weightLanguage();
    }

    private static Set<String> normalizeLangSet(List<String> langs) {
        if (langs == null || langs.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (String l : langs) {
            String n = KeywordNormalizer.normalizeLanguage(l);
            if (n != null && !n.isBlank()) {
                out.add(n);
            }
        }
        return out;
    }
}
