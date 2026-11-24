# Week 6 Academic Report Material

## 1. Initial state
The repository was on `feature/week-05-core-implementation` with Week 5 evidence and commits preserved. The working tree contained uncommitted Task 3.2 Compose corrections, related documentation changes, and previously added backend packages/tests. No branch switch, reset, merge, commit, or push was performed.

## 2. Testing environment
macOS 26.6.2 on arm64; Java 17.0.12 LTS; Maven 3.9.14 configured with JAVA_HOME 17; Node v25.8.1; npm 11.11.0; Google Chrome 158.0.8127.38 with Playwright Python. No listeners existed on the selected ports at the start of the initial validation.

## 3. Requirements reviewed
FR-01..FR-06 and NFR-01..NFR-03 were extracted from the Week 2/Week 3 report PDFs. README, API design, and component design supplied the added Compose/project-pipeline scope. No numbered FR/NFR IDs for those additions existed in Weeks 1-5 Markdown docs. Exact wording was rechecked before final traceability, especially FR-05.

## 4. Testing strategy
Unit, integration, API, functional, negative, boundary, security, performance, reliability, and browser UI methods were used. No new analysis module was implemented. Evidence verdicts are derived from assertions in generator scripts, not written manually.

## 5. Backend automated results
`mvn test` under Java 17 returned exit code 0 with 157 tests, 0 failures, 0 errors, and 0 skipped across 13 test classes. Compose tests numbered 36; project integration tests numbered 6; website regression test classes contributed 14+13 tests.

## 6. Live API results
Forty-four live cases covered health, website success/HTTP/redirect/401/403/404/500/HTTPS-to-HTTP/DNS failure/invalid inputs, Dockerfile positive/negative/boundary cases, correct and risky project uploads, unsupported files, unsafe YAML, malformed YAML, binary input, invalid UTF-8, and missing request parts. All observed results matched assertions in `api/generate-request-summary.py`.

## 7. Security validation
Localhost, 127.0.0.1, IPv6 loopback, private IPv4, metadata IP, path traversal, absolute paths, duplicate normalized paths, oversized uploads, and unsafe YAML tags were rejected or safely reported. Synthetic secret values were absent from checked outputs. SSRF revalidation before each redirect remains partially verified because private-DNS and pinned-connection-stage scenarios were not executed.

## 8. Performance results
Five distinct public URLs produced total app-reported durations of 139, 962, 490, 451, and 274 ms; the maximum was 962 ms. Full API curl latency maximum was 0.967 s. The 10-second target was met by all observations. `responseTimeMs` now measures the whole check including redirects, and the Response Time finding uses that measured value.

## 9. Frontend results
The production build passed. Eight browser cases passed: initial page, website success with submitted/final scheme display, blocked URL alert, empty URL validation, Dockerfile success, empty Dockerfile validation, 100,000-character boundary validation, and 390x844 mobile layout. No page errors occurred.

## 10. Test case matrix
`evidence/week-06/test-case-matrix.md` contains 74 generated rows: 13 backend class rows, 44 live API rows, 8 browser rows, 6 unexecuted rows, and 3 partially validated requirement rows. `matrix-assertion-report.txt` records unique IDs and no assertion failures.

## 11. Failure diagnosis, correction, and retest
The historical favicon 404 was already fixed and did not recur. A Week 6 harness false FAIL was caused by case-sensitive label assertions and was corrected. Two server-start failures were caused by launching from the repository root instead of module directories. A focused audit then found five real application behavior defects: error HTTP statuses classified as unreachable, HTTPS classification based on submitted rather than final scheme, presence-only header checks, per-redirect response timing without a total deadline, and unconditional PASS in evidence generation. Each was reproduced before correction, corrected minimally, and retested; see `debugging/failure-diagnosis.md`.

## 12. Requirements traceability
FR-01, FR-02, FR-03, FR-04, and FR-06 are verified. FR-05 is partially verified because API JSON/multipart Dockerfile text input and no-execution behavior are verified, but the frontend provides a textarea rather than a file picker. NFR-01 is fully verified for low-volume observations. NFR-02 and NFR-03 are partially verified because controlled live private-DNS, public-to-internal redirect, pinned connection-stage, and slow-endpoint timeout scenarios were not executed.

## 13. Development Stage 4 record
Stage 4 material documents the Compose/shared-project implementation present in the working tree, its validation evidence, deferred modules, and the absence of a Stage 4 commit created during Week 6.

## 14. Evidence tree
`evidence/week-06/` contains 152 files including strategy/environment documents, extracted requirement wording, traceability, matrix, generator scripts and assertion reports, before-fix and after-fix correction evidence, backend logs, API responses, performance CSVs, security checks, browser screenshots/JSON/script, Stage 4 material, and final verification files.

## 15. Files created/modified
Week 6 created evidence files and scripts under `evidence/week-06/`. The focused audit also modified production/test files: `WebsiteCheckResponse.java`, `WebsiteCheckService.java`, `WebsiteCheckServiceMoreTest.java`, `frontend/src/types/websiteCheck.ts`, and `frontend/src/components/CheckResult.tsx`. Pre-existing Task 3.2 source and documentation changes remain preserved.

## 16. Limitations
Public website results depend on external availability. Response-time findings are based on low-volume observations. URL DNS rebinding and connection-stage pinning remain unresolved. Security header validation is intentionally limited to missing values and a few basic invalid values, not exhaustive CSP semantics. Project upload and Compose UI do not exist; those features were validated only through the API.

## 17. Unperformed tests
No load test, ZIP/GitHub input test, AWS deployment test, Task 4 Secret Detection test, live public-to-internal redirect, live domain-to-private-DNS test, controlled live timeout, or frontend Dockerfile file-picker test was performed.

## 18. Git status
HEAD was `0f264bc`. Branch was `feature/week-05-core-implementation`. Final `git status --short` had 111 lines: pre-existing Task 3.2/Stage 4 changes, Week 6 website corrections, and untracked evidence/report directories. `git diff --check` exited 0. No commit or push was made.

## 19. Suggested commit organization
If approved later: first commit the pre-existing Task 3.2/Stage 4 source and documentation changes; then commit the Week 6 website correctness corrections and their tests; then commit `evidence/week-06/` as a separate documentation/evidence commit. Do not combine historical evidence edits because none were made.

## 20. Submission checklist
Submit the Week 6 report with links to `evidence/week-06/README.md`, `testing-strategy.md`, `test-case-matrix.md`, `requirements-traceability.md`, `development-stage-4.md`, `debugging/failure-diagnosis.md`, `performance/performance-summary.txt`, and `frontend/browser-verification.json`; confirm servers are stopped; confirm no commit/push occurred without approval.
