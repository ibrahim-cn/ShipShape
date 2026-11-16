# ShipShape Week 5 Evidence

Date: 2026-11-16

Branch: `feature/week-05-core-implementation`

HEAD: `9f836bd3227674c40c646a4ad734efa12220bbaf`

This directory records the Week 5 implementation and final verification of the website readiness display and rule-based Dockerfile static analysis. The application was verified locally only. No AWS deployment was performed.

## Week 5 Commits

| Commit | Description |
|---|---|
| `aaa86fd85541496382dcb6de68486117774781d2` | Display complete website readiness analysis |
| `40fe98f8fb1d1dc16bc8b7aac551af465886a8ef` | Add favicon to prevent a missing-resource request |
| `9f836bd3227674c40c646a4ad734efa12220bbaf` | Add rule-based Dockerfile static analysis |

## Implemented Scope

- The Website Check now displays reachability, HTTP status, response time, HTTPS usage, security headers, overall status, and individual findings.
- The Dockerfile Analysis tab accepts pasted Dockerfile text and displays an overall `PASS`, `WARNING`, or `FAIL` status with five findings.
- The five rules cover `FROM` presence, explicit `:latest` tags, the final-stage user, the final-stage health check, and `COPY`/`ADD` usage.
- Dockerfile content is parsed as text and is never executed. The analyzer does not invoke Docker, containers, shell commands, external services, or AI services.
- Backend controller and service tests were added for the Dockerfile workflow.
- The root README and API design documentation were updated.

## Verification Environment

- Java 17.0.12
- Apache Maven 3.9.14
- Node.js 25.8.1
- npm 11.11.0
- Vite 8.3.5
- Spring Boot 4.1.2
- Google Chrome 157.0.8103.52

The backend and frontend were run locally for API and browser verification. No cloud service was used.

## Final Verification Commands

Backend tests:

```bash
cd backend
JAVA_HOME="$(/usr/libexec/java_home -v 17)" PATH="$JAVA_HOME/bin:$PATH" mvn test
```

Frontend production build:

```bash
cd frontend
npm run build
```

Health check:

```bash
curl -sS -i http://127.0.0.1:8080/api/health
```

Website check:

```bash
curl -sS -i -X POST http://127.0.0.1:8080/api/checks/website \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com"}'
```

Dockerfile analysis:

```bash
curl -sS -i -X POST http://127.0.0.1:8080/api/checks/dockerfile \
  -H "Content-Type: application/json" \
  -d '{"content":"FROM node:20-alpine\nUSER node\nHEALTHCHECK CMD true\nCOPY . .\n"}'
```

Browser verification used headless Chrome through the Chrome DevTools Protocol at desktop `1280x900` and mobile `390x844` viewports.

## Final Test and Build Results

- Backend: `49` tests run, `0` failures, `0` errors, `0` skipped; `BUILD SUCCESS`.
- Backend command exit code: `0`.
- Frontend: TypeScript check and Vite production build succeeded; `24` modules transformed.
- Frontend command exit code: `0`.

Artifacts:

- `final-verification/backend-tests-sanitized.txt`
- `final-verification/backend-tests.txt` (local only; contains machine-specific paths and process IDs)
- `final-verification/frontend-build.txt`
- Exit-code files remain local and are not part of the planned GitHub evidence set.

## Final API Verification

All `10` API checks passed.

| Scenario | Actual result |
|---|---|
| Health endpoint | HTTP `200`, `{"service":"shipshape-backend","status":"UP"}` |
| `https://example.com` | HTTP `200`; reachable; HTTP `200`; HTTPS enabled; 3 security headers; 6 findings; overall `WARNING`; response time 237 ms in this run |
| Blocked `http://127.0.0.1/` | HTTP `400`, `{"error":"Host not allowed"}` |
| Valid Dockerfile | Overall `PASS`; all 5 findings `PASS` |
| `FROM node:latest` | Overall `WARNING`; `Base Image Tag` `WARNING` |
| Missing `FROM` | Overall `FAIL`; `FROM Instruction` `FAIL` |
| `USER root` | Overall `WARNING`; `Final Stage User` `WARNING` |
| `HEALTHCHECK NONE` | Overall `WARNING`; `Health Check` `WARNING` |
| Blank Dockerfile | HTTP `400`, `{"error":"Dockerfile content is required"}` |
| Oversized Dockerfile | HTTP `400`, `{"error":"Dockerfile content is too large"}` |

The response time is environment-dependent and will vary between runs.

Artifacts:

- `final-verification/api-verification.txt`
- `final-verification/api-results.json`
- Individual `final-verification/api-*.json` files remain local; the combined files above contain the final matrix.

## Final Browser Verification

All `28` automated browser checks passed.

- Initial page displayed two tabs with Website Check selected.
- Website loading state displayed `Checking...`.
- Website success rendered the submitted URL, reachability, HTTP status, response time, HTTPS, security headers, overall status, and findings.
- Blocked website input displayed the `Host not allowed` error without a stale result.
- Dockerfile loading state displayed `Analyzing...`.
- Empty and oversized Dockerfile submissions displayed client-side validation.
- Valid Dockerfile rendered overall `PASS` with five `PASS` findings.
- Warning Dockerfile rendered overall `WARNING`.
- Missing-`FROM` Dockerfile rendered overall `FAIL`.
- `USER root` and `HEALTHCHECK NONE` each rendered the expected warning.
- Website and Dockerfile views were checked at desktop and mobile sizes.
- No horizontal overflow was recorded.
- No uncaught JavaScript exceptions or console errors were recorded.
- No unexpected HTTP 4xx/5xx responses were recorded. The only non-2xx response was the expected blocked-website HTTP `400`.
- The safety note stating that the Dockerfile was not executed was present in the Dockerfile result.

Artifacts:

- `final-verification/browser-verification.txt`
- `final-verification/browser-verification.json`

## Screenshots

Final verification screenshots:

| File | Dimensions | Scenario |
|---|---|---|
| `final-verification/website-success-desktop.png` | 1265x2102 | Website success, desktop |
| `final-verification/website-blocked-error-desktop.png` | 1280x900 | Blocked URL error, desktop |
| `final-verification/dockerfile-empty-error-desktop.png` | 1265x945 | Empty Dockerfile validation, desktop |
| `final-verification/dockerfile-pass-desktop.png` | 1265x1964 | Dockerfile `PASS`, desktop |
| `final-verification/dockerfile-warning-desktop.png` | 1265x1964 | Dockerfile `WARNING`, desktop |
| `final-verification/dockerfile-fail-desktop.png` | 1265x1964 | Dockerfile `FAIL`, desktop |
| `final-verification/website-success-mobile.png` | 390x2825 | Website success, mobile |
| `final-verification/dockerfile-pass-mobile.png` | 390x2582 | Dockerfile `PASS`, mobile |

Stage 2 screenshots retained for the favicon correction and original readiness UI:

| File | Dimensions | Scenario |
|---|---|---|
| `stage-02/browser-success-desktop.png` | 1265x2040 | Website success, desktop |
| `stage-02/browser-success-mobile.png` | 390x2766 | Website success, mobile |
| `stage-02/browser-error-desktop.png` | 1280x900 | Blocked URL error, desktop |

Screenshot metadata and nonblank statistics are recorded in `final-verification/screenshot-metadata.txt`.

### Visual Review Limitation

The screenshot contents were not visually inspected because image input is unsupported in the verification environment. No visual-review claim is made. The screenshots were validated as nonblank PNG artifacts, and automated DOM and layout checks were used to verify the rendered states, controls, statuses, and horizontal overflow behavior.

## Stage 2 Favicon Correction

Stage 2 verification identified a console `404` for `GET /favicon.ico`. The HTML had no favicon reference and `frontend/public` did not exist. The correction added `frontend/public/favicon.svg` and referenced it from `frontend/index.html`.

After the correction:

- `GET /favicon.svg` returned HTTP `200`.
- Headless Chrome recorded no `404` responses.
- The frontend production build passed.
- The correction was committed separately as `40fe98f8fb1d1dc16bc8b7aac551af465886a8ef`.

Stage 2 artifacts:

- `stage-02/stage2-404-investigation.txt`
- `stage-02/stage2-404-after-fix.txt`
- `stage-02/stage2-final-review.txt`

## Planned GitHub Evidence Set

The recommended, non-redundant GitHub evidence set is:

- `evidence/week-05/README.md`
- `evidence/week-05/stage-02/stage2-404-investigation.txt`
- `evidence/week-05/stage-02/stage2-404-after-fix.txt`
- `evidence/week-05/stage-02/stage2-final-review.txt`
- `evidence/week-05/stage-02/browser-success-desktop.png`
- `evidence/week-05/stage-02/browser-success-mobile.png`
- `evidence/week-05/stage-02/browser-error-desktop.png`
- `evidence/week-05/stage-03/README.md`
- `evidence/week-05/final-verification/backend-tests-sanitized.txt`
- `evidence/week-05/final-verification/frontend-build.txt`
- `evidence/week-05/final-verification/api-verification.txt`
- `evidence/week-05/final-verification/api-results.json`
- `evidence/week-05/final-verification/browser-verification.txt`
- `evidence/week-05/final-verification/browser-verification.json`
- `evidence/week-05/final-verification/screenshot-metadata.txt`
- `evidence/week-05/final-verification/website-success-desktop.png`
- `evidence/week-05/final-verification/website-blocked-error-desktop.png`
- `evidence/week-05/final-verification/dockerfile-empty-error-desktop.png`
- `evidence/week-05/final-verification/dockerfile-pass-desktop.png`
- `evidence/week-05/final-verification/dockerfile-warning-desktop.png`
- `evidence/week-05/final-verification/dockerfile-fail-desktop.png`
- `evidence/week-05/final-verification/website-success-mobile.png`
- `evidence/week-05/final-verification/dockerfile-pass-mobile.png`
- `evidence/week-05/final-verification/git-review.txt`

The Stage 3 screenshots and individual Stage 3 verification logs remain local because the final verification artifacts supersede them and reproduce the same commit state.

## Local-Only Artifacts

The following are preserved locally but are not recommended for GitHub because they are redundant, generated, or contain machine-specific details:

- PID files, runtime logs, and `chrome-profile-path.txt`.
- Raw Maven test logs containing absolute workspace paths, the local username, and process IDs.
- `environment.txt` files and exit-code files.
- Individual API response JSON files superseded by `api-results.json`.
- Stage 3 duplicate screenshots and logs.
- `report/` PDFs and academic material.
- Week 4 generated `frontend-dist/` build output.
- `.DS_Store` files.

## Sensitive-Information Review

Selected evidence was reviewed for local paths, usernames, process IDs, credential material, and private keys. The selected files contain none of these. The raw Maven logs were excluded and a sanitized copy was created for the GitHub set. The Stage 2 Chrome profile path and runtime logs were excluded.

No credentials were added to the repository.

## Git State

- The branch is `feature/week-05-core-implementation`.
- `HEAD` is `9f836bd3227674c40c646a4ad734efa12220bbaf`.
- The expected commits `aaa86fd`, `40fe98f`, and `9f836bd` are present.
- Tracked files have no uncommitted modifications.
- No evidence or report files were staged, committed, or pushed during final verification.
- `evidence/week-05/` and `report/` remain untracked until a selective staging step is approved.

## Process Cleanup

The backend, frontend development server, and headless Chrome verification instance were stopped after testing. Ports `8080`, `5173`, and the temporary Chrome debugging port were confirmed to have no remaining listeners.

## Limitations

- Screenshot contents were not visually inspected; see the visual review limitation above.
- Dockerfile analysis is static and rule-based. It does not build an image, run a container, execute instructions, or provide a complete security audit.
- The application is not deployed to AWS.
- Frontend automated tests are not implemented.
- Public-website availability, response time, and header findings can change between runs.
- The browser verification script was a temporary local harness and is not part of the application source.
