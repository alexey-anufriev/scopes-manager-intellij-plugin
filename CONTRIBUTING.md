# Contributing

Thank you for contributing to Scopes Manager IntelliJ Plugin.

## Ground Rules

- Keep changes focused and well described.
- Add or update tests when behavior changes.
- Do not include unrelated refactors in the same pull request.

## Developer Certificate of Origin

By contributing to this project, you agree that your contributions are covered by the
[Developer Certificate of Origin (DCO) 1.1](https://developercertificate.org/).

This means that, for each commit, you certify that the contribution is yours
to submit under the project's license.

All commits in pull requests should include a `Signed-off-by` trailer.

## How To Sign Off Commits

Use Git's signoff flag when creating commits:

```bash
git commit -s -m "Describe the change"
```

If you forgot to sign off your most recent commit, amend it:

```bash
git commit --amend -s
```

If a pull request contains multiple unsigned commits, rewrite them so each commit
has a valid signoff before requesting review.

The signoff must match the commit author identity and has this form:

```text
Signed-off-by: Your Name <you@example.com>
```

## Pull Requests

- Explain the problem and the approach.
- Include screenshots for UI changes when useful.
- Confirm that the work is ready to merge.
- Confirm that every commit is signed off.

Maintainers may require follow-up changes before merge.

## Integration Tests

Run IDE integration tests in a disposable container to isolate them
from host JetBrains IDE, Toolbox, license, and previous test state:

```bash
scripts/run-isolated-integration-test.sh integrationTestCLionLatest
```

The runner builds a local Docker image, mounts this repository read-only,
copies it into a fresh workspace inside the container, excludes host build
and IDE cache directories, and runs the Gradle task headlessly.
