# Test and coverage baseline

Reference point for "no regression" in every following MR of the hexagonal refactor.

- **Date:** 2026-10-22
- **Measured commit on `main`:** `5646245e111ccec2575ecb07321dd6dd050c4232`
- **Command:** `mvn clean verify` from the repo root (green, 1m25s)

## Tests per module

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

## Aggregate coverage

From `coverage-report/target/site/jacoco-aggregate/jacoco.csv`.

| Metric      | Before (7 modules)         | After (+ audit-trail-service) |
|-------------|----------------------------|-------------------------------|
| Instruction | 85.16 % (4533 / 5323)      | **85.66 %** (4867 / 5682)     |
| Line        | 84.05 % (864 / 1028)       | **84.77 %** (941 / 1110)      |

"Before" is `main` as measured; "after" adds `audit-trail-service` to `coverage-report/pom.xml`.

## Coverage gate

`scripts/check-coverage.sh` fails when aggregate instruction coverage is below its minimum.

- Measured after including `audit-trail-service`: **85.66 %**
- Default minimum: **0.85** (measured value floored to the whole percent, never below the 0.83 hard floor)

The gate runs in `.gitea/workflows/main.yml` (push to `main`) and `.gitea/workflows/pr.yml` (pull requests).
