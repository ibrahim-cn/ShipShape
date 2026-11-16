ShipShape Week 5 Stage 3 evidence
Date: 2026-11-16
Branch: feature/week-05-core-implementation

Commits
- Stage 3: 9f836bd3227674c40c646a4ad734efa12220bbaf feat(dockerfile): add rule-based static analysis
- Stage 2 favicon correction: 40fe98f8fb1d1dc16bc8b7aac551af465886a8ef fix(frontend): add favicon to prevent missing resource request

Scope
- Added POST /api/checks/dockerfile.
- Added deterministic, rule-based static analysis for FROM, explicit :latest tags, final-stage USER, final-stage HEALTHCHECK, and COPY/ADD usage.
- Added Dockerfile Analysis tab, textarea form, validation, loading/error handling, result summary, and findings.
- Added backend service and controller tests.
- Updated README and API design documentation.
- Dockerfile content is parsed as text and is never executed. The feature does not invoke Docker, containers, shell commands, external services, or AI services.

Verification results
- Backend tests: 49 run, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS under Java 17.
  Artifact: backend-tests.txt
- Frontend production build: successful TypeScript check and Vite build; 24 modules transformed.
  Artifact: frontend-build.txt
- API verification: all checks passed.
  - Health endpoint returned HTTP 200.
  - Valid Dockerfile returned PASS with five PASS findings.
  - Warning Dockerfile returned WARNING with :latest, root-user, and missing-health-check warnings.
  - Missing FROM returned FAIL.
  - Blank content returned HTTP 400 with "Dockerfile content is required".
  - Oversized content returned HTTP 400 with "Dockerfile content is too large".
  - Website check still returned HTTP 200 with its existing findings.
  - Blocked website URL still returned HTTP 400 with "Host not allowed".
  Artifact: api-verification.txt and api-*.json
- Browser verification: PASS.
  - Initial page showed two tabs with Website Check selected.
  - Blank Dockerfile submission showed client-side validation.
  - Oversized Dockerfile submission showed client-side validation.
  - Valid Dockerfile analysis rendered PASS, five findings, and five PASS badges.
  - Website Check still rendered its existing result and findings.
  - Desktop and mobile layouts had no horizontal overflow.
  - No console errors, uncaught exceptions, or HTTP 4xx/5xx responses were recorded.
  Artifacts: browser-verification.txt and browser-verification.json
- Screenshots:
  - browser-dockerfile-success-desktop.png: 1265x1964, nonblank
  - browser-dockerfile-success-mobile.png: 390x2582, nonblank

Visual review limitation
- Screenshot contents could not be visually inspected because image input is not supported in this environment. The screenshots are recorded as nonblank image artifacts, and automated DOM/layout checks were used instead. No visual-review claim is made.

Process cleanup
- Backend, Vite, and Chrome verification processes were stopped after testing.
- Ports 8080 and 5173 were confirmed free after cleanup.

Commit hygiene
- The Stage 3 commit contains only Stage 3 source, test, frontend, and documentation files.
- Week 4 evidence, Week 5 evidence, and report files remain untracked local artifacts and were not committed.
- No push was performed.
