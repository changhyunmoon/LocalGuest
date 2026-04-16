# AI 파서 튜닝 로그 집계 (`[AI_PARSER_TUNE]`)

`PromptRecommendationService`가 **프롬프트 원문 없이** 남기는 튜닝용 로그(`[AI_PARSER_TUNE]`)를 주기적으로 모아, 동의어·제외 키워드 YAML 보강 후보를 빠르게 찾기 위한 도구입니다.

## 무엇이 집계되나

- `policyVer`, `parseConfidence` 빈도
- `ambiguityCodes`, `parserHints` 빈도
- `signals.exclusionIntents`에 잡힌 키워드 빈도 (제외 의도 신호; 실제 `excluded` 태그로 이어지지 않은 케이스도 로그에 포함될 수 있음)

## 사용법

```bash
# 샘플 자기 점검
python3 tools/ai-parser-tuning/aggregate_parser_tune_logs.py --self-test

# 최근 로그 파일에서 CSV 생성 (기본 출력: ./out/parser-tune/)
python3 tools/ai-parser-tuning/aggregate_parser_tune_logs.py /path/to/api-server.log

# 여러 파일 + 출력 디렉터리 지정
python3 tools/ai-parser-tuning/aggregate_parser_tune_logs.py --out-dir ./reports/week12 a.log b.log

# 파이프
grep '[AI_PARSER_TUNE]' /path/to/combined.log | python3 tools/ai-parser-tuning/aggregate_parser_tune_logs.py --out-dir ./out/parser-tune
```

## 주간 운영 루프 (권장)

1. **수집**: 스테이징/프로덕션에서 지난 7일 로그를보내거나, 로그 플랫폼에서 `[AI_PARSER_TUNE]`만 필터링해 파일로 저장합니다.
2. **집계**: 위 스크립트로 `out/parser-tune/*.csv`를 생성합니다.
3. **검토**: `exclusion_intent_keywords.csv` 상위 항목과 `parser_hints.csv` / `ambiguity_codes.csv`를 보고, `backend/...` 의 `localguest.ai.parser` YAML(팀이 쓰는 키)에 반영할 **후보 목록**을 정리합니다.
4. **반영**: YAML 변경은 PR로 올리고, `PromptParserTest` / 필요 시 `AiRecommendationRegressionTest`를 돌려 회귀를 확인합니다.
5. **롤백**: YAML만 되돌리면 됩니다.

## 주의

- 로그에 **사용자 프롬프트 전문**이 포함되지 않도록 유지합니다 (`promptHash`만 사용).
- 스크립트는 **단순 문자열 파싱**이라, 로그 포맷이 바뀌면 함께 수정해야 합니다.
