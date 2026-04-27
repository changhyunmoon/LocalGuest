package com.team6.module.ai.parser;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordNormalizerSupplementTest {

    @AfterEach
    void tearDown() {
        KeywordNormalizer.applyTagSynonymSupplement(Map.of());
    }

    @Test
    void supplement_should_override_before_builtin_map() {
        KeywordNormalizer.applyTagSynonymSupplement(Map.of("커스텀태그", "카페"));
        assertThat(KeywordNormalizer.normalizeTag("커스텀태그")).isEqualTo("카페");
    }
}
