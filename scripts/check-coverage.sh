#!/usr/bin/env bash
# Fails when aggregate JaCoCo instruction coverage is below the minimum.
# Usage: scripts/check-coverage.sh [jacoco.csv] [minimum ratio]
set -euo pipefail

CSV="${1:-coverage-report/target/site/jacoco-aggregate/jacoco.csv}"
MIN="${2:-0.85}"

if [[ ! -f "$CSV" ]]; then
  echo "Coverage report not found: $CSV (run mvn verify first)" >&2
  exit 1
fi

awk -F, -v min="$MIN" '
  NR > 1 { missed += $4; covered += $5 }
  END {
    total = missed + covered
    if (total == 0) { print "No coverage data in report" > "/dev/stderr"; exit 1 }
    ratio = covered / total
    printf "Aggregate instruction coverage: %.2f%% (minimum %.2f%%)\n", ratio * 100, min * 100
    if (ratio < min) exit 1
  }' "$CSV"
