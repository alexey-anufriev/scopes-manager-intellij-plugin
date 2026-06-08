# Codex Instructions

- Run IDE integration tests with `scripts/run-isolated-integration-test.sh <gradle-task>` so they execute headlessly in a disposable container.
- Do not mount host JetBrains, Toolbox, IDE cache, or license directories into the container so each test runs in complete isolation.
