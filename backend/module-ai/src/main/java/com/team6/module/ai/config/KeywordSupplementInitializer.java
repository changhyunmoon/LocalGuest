package com.team6.module.ai.config;

import com.team6.module.ai.parser.KeywordNormalizer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * {@link LocalGuestAiProperties#getParser()}의 태그 동의어를 {@link KeywordNormalizer}에 반영한다.
 */
@Component
@RequiredArgsConstructor
public class KeywordSupplementInitializer {

    private final LocalGuestAiProperties properties;

    @PostConstruct
    public void applySynonyms() {
        if (properties.getParser() == null || properties.getParser().getTagSynonyms() == null) {
            KeywordNormalizer.applyTagSynonymSupplement(Map.of());
        } else {
            KeywordNormalizer.applyTagSynonymSupplement(properties.getParser().getTagSynonyms());
        }
    }
}
