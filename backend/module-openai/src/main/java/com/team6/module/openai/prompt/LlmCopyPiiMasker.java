package com.team6.module.openai.prompt;

/**
 * LLM이 만든 가이드 노출용 문자열에서 흔한 PII 패턴만 얕게 가린다.
 */
public final class LlmCopyPiiMasker {

    private LlmCopyPiiMasker() {
    }

    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String t = text;
        t = t.replaceAll("(?i)(010|01[016789])[-.\\s]?\\d{3,4}[-.\\s]?\\d{4}", "[연락처 생략]");
        t = t.replaceAll("\\d{2,3}-\\d{3,4}-\\d{4}", "[연락처 생략]");
        t = t.replaceAll("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b", "[이메일 생략]");
        return t;
    }
}
