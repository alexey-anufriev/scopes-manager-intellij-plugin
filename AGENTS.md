# Codex Instructions

- Run IntelliJ UI and integration tests headlessly with `xvfb-run -a`.
- Do not run IDE integration tests directly without Xvfb unless the user explicitly asks for foreground UI.
- If a trial/license activation dialog appears during IDE test execution, interrupt the test run and report it; do not add skips, hacks, or workarounds for licensing state.
