#!/usr/bin/env bash
set -euo pipefail

SCRIPT_NAME="${SCRIPT_NAME:-matching-guest-list-rps-standardized-test.js}"
RATE="${RATE:-160}"
DURATION="${DURATION:-10m}"
ENDPOINT="${ENDPOINT:-/matching/requests/guest/list/slice-projected?page=0&size=20}"
RUNS="${RUNS:-3}"

if [[ -z "${TOKEN:-}" ]]; then
  echo "TOKEN is required. Export TOKEN first."
  exit 1
fi

if [[ -z "${BASE_URL:-}" ]]; then
  echo "BASE_URL is required. Export BASE_URL first."
  exit 1
fi

echo "== Standardized experiment =="
echo "SCRIPT_NAME=${SCRIPT_NAME}"
echo "RATE=${RATE}, DURATION=${DURATION}, ENDPOINT=${ENDPOINT}, RUNS=${RUNS}"
echo

for i in $(seq 1 "${RUNS}"); do
  echo "---- Run ${i}/${RUNS} ----"
  RATE="${RATE}" DURATION="${DURATION}" ENDPOINT="${ENDPOINT}" \
    k6 run "${SCRIPT_NAME}" --summary-export "result-${i}.json"
done

python3 - <<'PY'
import json
import math
import statistics

runs = []
i = 1
while True:
    name = f"result-{i}.json"
    try:
        with open(name, "r", encoding="utf-8") as f:
            data = json.load(f)
    except FileNotFoundError:
        break
    metrics = data.get("metrics", {})
    duration = metrics.get("http_req_duration", {})
    failed = metrics.get("http_req_failed", {})
    dropped = metrics.get("dropped_iterations", {})
    vus = metrics.get("vus_max", {})

    run = {
        "run": i,
        "avg_ms": duration.get("avg"),
        "p95_ms": duration.get("p(95)"),
        "p99_ms": duration.get("p(99)"),
        "failed_rate": failed.get("rate"),
        "dropped": dropped.get("count", 0),
        "max_vus": vus.get("max"),
    }
    runs.append(run)
    i += 1

if not runs:
    print("No result-*.json files found.")
    raise SystemExit(1)

def mean_std(key):
    vals = [r[key] for r in runs if r[key] is not None]
    if not vals:
        return None, None
    mean = statistics.mean(vals)
    std = statistics.pstdev(vals) if len(vals) > 1 else 0.0
    return mean, std

print("\n== Per-run metrics ==")
for r in runs:
    print(
        f"run={r['run']}, avg={r['avg_ms']:.2f}ms, p95={r['p95_ms']:.2f}ms, "
        f"p99={r['p99_ms']:.2f}ms, failed_rate={r['failed_rate']:.4f}, "
        f"dropped={r['dropped']}, max_vus={r['max_vus']}"
    )

print("\n== Aggregated (mean ± std) ==")
for key, label, scale in [
    ("avg_ms", "avg_ms", 1.0),
    ("p95_ms", "p95_ms", 1.0),
    ("p99_ms", "p99_ms", 1.0),
    ("failed_rate", "failed_rate", 1.0),
    ("dropped", "dropped_iterations", 1.0),
    ("max_vus", "max_vus", 1.0),
]:
    m, s = mean_std(key)
    if m is None:
        continue
    print(f"{label}: {m*scale:.4f} ± {s*scale:.4f}")
PY

echo
echo "Done. result-*.json files were generated."
