package com.team6.module.ai.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetTierTest {

    @Test
    void exactMatch_is_case_insensitive_trimmed() {
        assertThat(BudgetTier.exactMatch(" 중간 ", "중간")).isTrue();
        assertThat(BudgetTier.exactMatch("낮음", "높음")).isFalse();
    }

    @Test
    void adjacentTiers_only_one_step() {
        assertThat(BudgetTier.adjacentTiers("낮음", "중간")).isTrue();
        assertThat(BudgetTier.adjacentTiers("중간", "높음")).isTrue();
        assertThat(BudgetTier.adjacentTiers("낮음", "높음")).isFalse();
    }

    @Test
    void tierIndex_unknown_returns_negative() {
        assertThat(BudgetTier.tierIndex("무료")).isEqualTo(-1);
    }
}
