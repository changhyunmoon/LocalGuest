package com.team6.module.openai.prompt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmCopyPiiMaskerTest {

    @Test
    void mask_replacesMobileAndEmail() {
        assertThat(LlmCopyPiiMasker.mask("전화 010-1234-5678")).contains("[연락처 생략]");
        assertThat(LlmCopyPiiMasker.mask("메일은 a@b.co.kr")).contains("[이메일 생략]");
    }
}
