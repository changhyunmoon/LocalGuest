#!/usr/bin/env python3
"""
Aggregate [AI_PARSER_TUNE] lines from application logs (stdin or files).

No third-party dependencies. Intended for weekly ops tuning (synonyms / exclusion keywords).

Log format is emitted by PromptRecommendationService (module-ai), e.g.:
  [AI_PARSER_TUNE] policyVer=... promptHash=... parseConfidence=... ambiguityCodes=[...] parserHints=[...] signals={exclusionIntents=[...], budgetHint=..., durationHint=...} extracted={...}
"""

from __future__ import annotations

import argparse
import csv
import re
import sys
from collections import Counter
from pathlib import Path
from typing import Iterable, TextIO


MARKER = "[AI_PARSER_TUNE]"

RE_POLICY = re.compile(r"policyVer=([^\s]+)")
RE_CONF = re.compile(r"parseConfidence=([^\s]+)")


def _parse_bracket_list_fragment(text: str, key: str) -> str | None:
    """Return inner of key=[...]  (first match)."""
    idx = text.find(key + "=[")
    if idx < 0:
        return None
    start = idx + len(key) + 1  # points at '['
    depth = 0
    for i in range(start, len(text)):
        c = text[i]
        if c == "[":
            depth += 1
        elif c == "]":
            depth -= 1
            if depth == 0:
                return text[start + 1 : i]
    return None


def _split_csvish(inner: str) -> list[str]:
    inner = inner.strip()
    if not inner:
        return []
    parts = []
    for p in inner.split(","):
        t = p.strip()
        if t:
            parts.append(t)
    return parts


def parse_tune_line(line: str) -> dict | None:
    if MARKER not in line:
        return None
    pos = line.find(MARKER)
    tail = line[pos + len(MARKER) :].strip()

    m_pol = RE_POLICY.search(tail)
    m_conf = RE_CONF.search(tail)
    amb_inner = _parse_bracket_list_fragment(tail, "ambiguityCodes")
    hint_inner = _parse_bracket_list_fragment(tail, "parserHints")

    sig_start = tail.find("signals={")
    excl_inner = None
    if sig_start >= 0:
        sig_tail = tail[sig_start:]
        excl_inner = _parse_bracket_list_fragment(sig_tail, "exclusionIntents")

    return {
        "policyVer": m_pol.group(1) if m_pol else "",
        "parseConfidence": m_conf.group(1) if m_conf else "",
        "ambiguityCodes": _split_csvish(amb_inner or ""),
        "parserHints": _split_csvish(hint_inner or ""),
        "exclusionIntents": _split_csvish(excl_inner or ""),
    }


def iter_lines(files: list[Path], stdin: TextIO) -> Iterable[str]:
    if files:
        for path in files:
            with path.open("r", encoding="utf-8", errors="replace") as f:
                yield from f
    else:
        yield from stdin


def write_counter_csv(path: Path, header: tuple[str, str], counter: Counter[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(header)
        for k, v in counter.most_common():
            w.writerow([k, v])


def run_self_test() -> None:
    samples = [
        (
            '[AI_PARSER_TUNE] policyVer=2026.04.20 promptHash=-1 parseConfidence=LOW '
            'ambiguityCodes=[REGION_TOO_GENERIC, BUDGET_AMBIGUOUS] parserHints=[BUDGET_TOKEN_MULTISENSE] '
            'signals={exclusionIntents=[단체, 관광버스], budgetHint=true, durationHint=false} '
            'extracted={region=제주, style=힐링, budget=MID, durationDays=2, tags=[맛집], excluded=[], langs=[한국어], headcount=2}'
        ),
        (
            '[AI_PARSER_TUNE] policyVer=2026.04.20 promptHash=42 parseConfidence=MED '
            'ambiguityCodes=[] parserHints=[] '
            'signals={exclusionIntents=[], budgetHint=false, durationHint=true} extracted={region=, style=, budget=, durationDays=, tags=[], excluded=[], langs=[], headcount=}'
        ),
    ]
    for s in samples:
        p = parse_tune_line(s)
        assert p is not None, s
        assert p["policyVer"] == "2026.04.20", p
    first = parse_tune_line(samples[0])
    assert first is not None
    assert "REGION_TOO_GENERIC" in first["ambiguityCodes"]
    assert "BUDGET_TOKEN_MULTISENSE" in first["parserHints"]
    assert "단체" in first["exclusionIntents"]
    print("self-test ok", file=sys.stderr)


def main() -> int:
    ap = argparse.ArgumentParser(description="Aggregate [AI_PARSER_TUNE] log lines.")
    ap.add_argument(
        "inputs",
        nargs="*",
        type=Path,
        help="Log files (UTF-8). If omitted, read stdin.",
    )
    ap.add_argument(
        "--out-dir",
        type=Path,
        default=Path("out/parser-tune"),
        help="Directory for CSV reports (default: ./out/parser-tune)",
    )
    ap.add_argument("--self-test", action="store_true", help="Run embedded samples and exit.")
    args = ap.parse_args()

    if args.self_test:
        run_self_test()
        return 0

    files: list[Path] = list(args.inputs)
    c_policy = Counter()
    c_conf = Counter()
    c_amb = Counter()
    c_hint = Counter()
    c_excl = Counter()
    lines_seen = 0
    events = 0

    for line in iter_lines(files, sys.stdin):
        lines_seen += 1
        rec = parse_tune_line(line)
        if not rec:
            continue
        events += 1
        if rec["policyVer"]:
            c_policy[rec["policyVer"]] += 1
        if rec["parseConfidence"]:
            c_conf[rec["parseConfidence"]] += 1
        for x in rec["ambiguityCodes"]:
            c_amb[x] += 1
        for x in rec["parserHints"]:
            c_hint[x] += 1
        for x in rec["exclusionIntents"]:
            c_excl[x] += 1

    out = args.out_dir
    write_counter_csv(out / "policy_version.csv", ("policy_version", "count"), c_policy)
    write_counter_csv(out / "parse_confidence.csv", ("parse_confidence", "count"), c_conf)
    write_counter_csv(out / "ambiguity_codes.csv", ("ambiguity_code", "count"), c_amb)
    write_counter_csv(out / "parser_hints.csv", ("parser_hint", "count"), c_hint)
    write_counter_csv(out / "exclusion_intent_keywords.csv", ("keyword", "count"), c_excl)

    summary = out / "summary.txt"
    summary.parent.mkdir(parents=True, exist_ok=True)
    with summary.open("w", encoding="utf-8") as f:
        f.write(f"lines_read={lines_seen}\n")
        f.write(f"ai_parser_tune_events={events}\n")

    print(f"Wrote reports under {out.resolve()}", file=sys.stderr)
    print(f"lines_read={lines_seen} events={events}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
