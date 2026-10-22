# Test and coverage baseline

Reference point for "no regression" in every following MR of the hexagonal refactor.
The current reference is **after MR 1**. The MR 0 measurement is kept below for history.

## After MR 1 (order-service hexagonal refactor)

- **Date:** 2026-10-22
- **Measured commit on `main`:** `f8af9a8` (merge of PR #3)
- **Command:** `mvn clean verify -pl <pr.yml module list> -am` from the repo root (green, 1m27s).
  CI `pr.yml` run 49 reports the same numbers.

| Module              | Tests | Failures | Errors | Skipped |
|---------------------|------:|---------:|-------:|--------:|
| order-service       |   104 |        0 |      0 |       0 |
| inventory-service   |    35 |        0 |      0 |       0 |
| payment-service     |    27 |        0 |      0 |       0 |
| gateway-service     |    19 |        0 |      0 |       0 |
| auth-service        |    52 |        0 |      0 |       0 |
| notification-service|    31 |        0 |      0 |       0 |
| cart-service        |    17 |        0 |      0 |       0 |
| audit-trail-service |     8 |        0 |      0 |       0 |
| **Total**           | **293** |    **0** |  **0** |   **0** |

| Metric      | Aggregate                    | order-service alone (before → after MR 1) |
|-------------|------------------------------|-------------------------------------------|
| Instruction | **91.01 %** (5579 / 6130)    | 74.38 % → 96.65 %                          |
| Line        | **91.13 %** (1079 / 1184)    | 70.52 % → 96.92 %                          |

## MR 0 baseline

- **Date:** 2026-10-22
- **Measured commit on `main`:** `5646245e111ccec2575ecb07321dd6dd050c4232`
- **Command:** `mvn clean verify` from the repo root (green, 1m25s)

### Tests per module

Summed from `target/surefire-reports/TEST-*.xml`. Integration tests (`*ITTest`) run under surefire too.

| Module              | Tests | Failures | Errors | Skipped |
|---------------------|------:|---------:|-------:|--------:|
| order-service       |    38 |        0 |      0 |       0 |
| inventory-service   |    35 |        0 |      0 |       0 |
| payment-service     |    27 |        0 |      0 |       0 |
| gateway-service     |    19 |        0 |      0 |       0 |
| auth-service        |    52 |        0 |      0 |       0 |
| notification-service|    31 |        0 |      0 |       0 |
| cart-service        |    17 |        0 |      0 |       0 |
| audit-trail-service |     8 |        0 |      0 |       0 |
| **Total**           | **227** |    **0** |  **0** |   **0** |

`config-server` has no tests and is deliberately left out of the aggregate.

### Aggregate coverage

From `coverage-report/target/site/jacoco-aggregate/jacoco.csv`.

| Metric      | Before (7 modules)         | After (+ audit-trail-service) |
|-------------|----------------------------|-------------------------------|
| Instruction | 85.16 % (4533 / 5323)      | **85.66 %** (4867 / 5682)     |
| Line        | 84.05 % (864 / 1028)       | **84.77 %** (941 / 1110)      |

"Before" is `main` as measured; "after" adds `audit-trail-service` to `coverage-report/pom.xml`.

## Coverage gate

`scripts/check-coverage.sh` fails when aggregate instruction coverage is below its minimum.

- Measured after MR 1: **91.01 %**
- Default minimum: **0.90**, raised from 0.85 after MR 1. It is deliberately not 0.91: a margin of 0.01 points would fail on any single untested line.
- MR 0 measured **85.66 %** and set the minimum to **0.85** (the measured value floored to the whole percent, never below the 0.83 hard floor).

The gate runs in `.gitea/workflows/main.yml` (push to `main`) and `.gitea/workflows/pr.yml` (pull requests).
