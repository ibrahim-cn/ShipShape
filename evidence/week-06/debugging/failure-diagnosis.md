Failure diagnosis, correction, and retest
=========================================

## 1. Week 5 favicon 404 (historical, retested in Week 6)

- Observed in Stage 2 verification: console `404` for `GET /favicon.ico`.
- Diagnosis recorded in `evidence/week-05/README.md:173-182`: `frontend/index.html` had no favicon reference and `frontend/public` did not exist.
- Correction already committed before Week 6: `40fe98f8fb1d1dc16bc8b7aac551af465886a8ef` (`fix(frontend): add favicon to prevent missing resource request`).
- Correction: added `frontend/public/favicon.svg` and referenced it from `frontend/index.html`.
- Week 6 retest evidence: `frontend/browser-verification.json` contains no page errors and no console `404`; the only console error is the expected HTTP `400` produced by the deliberate blocked-URL case.
- Week 6 conclusion: corrected and not recurring.

## 2. Week 6 browser harness false FAIL

- First harness run recorded `website_success` as `FAIL`: `evidence/week-06/frontend/browser-verification-first-run.json`.
- Diagnosis: the harness compared title-cased strings such as `Response time` against browser `inner_text`, which applies CSS text transformation and returns `RESPONSE TIME`. The application result itself showed reachability, HTTP 200, HTTPS enabled, response time, headers, and findings correctly.
- Correction: the harness assertion was changed to case-insensitive matching; no application source was changed for that defect.
- Retest evidence: `evidence/week-06/frontend/browser-verification-first-run.json` preserves the false FAIL; final `frontend/browser-verification.json` shows all 8 browser cases PASS and `page_errors=[]`.
- Conclusion: test-harness defect, corrected; application behavior was unchanged for this issue.

## 3. Week 6 local server startup attempts

- First backend start from the repository root failed with `No plugin found for prefix 'spring-boot'`; the backend was restarted from `backend/`, after which `/api/health` returned HTTP 200.
- First frontend start from the repository root failed with npm `ENOENT` for `<repo>/package.json`; the frontend was restarted from `frontend/`, after which `/` returned HTTP 200.
- Correction: both servers were launched from their module directories; successful logs are `api/backend-run.log` and `frontend/frontend-run.log`.
- Conclusion: process invocation issue only; no application source changed for this issue.

## 4. Website error statuses classified as unreachable

- Original behavior: `evidence/week-06/debugging/before-fix/website/website-not-found.json` recorded `reachable=false` for a server that returned HTTP 404. A failing regression `httpErrorResponseIsReachableAndReportsHttpStatusFailure` asserted the corrected behavior.
- Root cause: `WebsiteCheckService` set `reachable = httpStatusCode >= 200 && httpStatusCode < 400`, so receiving a valid HTTP response was incorrectly treated as network unreachability for 4xx/5xx responses.
- Correction: a received HTTP response now sets `reachable=true`; a separate HTTP Status finding reports 4xx as FAIL and 5xx as FAIL while preserving `httpStatusCode`.
- Retest: `debugging/after-fix-targeted-tests.txt` exit 0; full suite `backend/test-summary.txt` has 156 tests with 0 failures; live `api/website-not-found.json`, `api/website-status-401.json`, `api/website-status-403.json`, and `api/website-status-500.json` show reachable true, the preserved status code, and an HTTP Status FAIL finding.

## 5. HTTPS result based on submitted scheme instead of final connection

- Original behavior: `evidence/week-06/debugging/before-fix/website/website-redirect.json` showed a redirect from HTTP to HTTPS but `httpsEnabled=false`; deterministic regressions `httpToHttpsRedirectReportsSecureFinalConnection` and `httpsToHttpRedirectReportsInsecureFinalConnection` failed before correction.
- Root cause: `WebsiteCheckService` set `httpsEnabled` only from the submitted URI and never updated it after following redirects.
- Correction: `WebsiteCheckResponse` now records `submittedUrlScheme` and `finalUrlScheme`; `httpsEnabled` is derived from the final URI before findings are created. The frontend displays both schemes in `CheckResult.tsx`.
- Retest: targeted tests pass; live `api/website-redirect.json` shows submitted=http and final=https with HTTPS PASS; live `api/website-https-to-http.json` shows submitted=https and final=http with HTTPS FAIL; browser `website_success` asserts the submitted/final scheme display.

## 6. Security headers checked only for presence

- Original behavior: before-fix regressions `hstsMaxAgeZeroIsReportedAsMisconfigured`, `invalidXContentTypeOptionsIsReportedAsMisconfigured`, and `emptyCspIsReportedAsMisconfigured` failed because the service reported PASS for present-but-invalid values.
- Root cause: `HeaderCheckService` returned only a boolean `present` flag, and `WebsiteCheckService` converted presence directly to PASS.
- Correction: `HeaderCheckResult` carries the header value; `WebsiteCheckService.headerMisconfiguration` performs narrow checks for HSTS `max-age=0`/missing or invalid max-age, non-`nosniff` X-Content-Type-Options, and empty or clearly malformed CSP. The evidence and traceability do not claim exhaustive CSP validation.
- Retest: all three regressions pass; full suite passes; built-in live-host header findings remain generated from observed values in `api/website-*.json`.

## 7. Response time and total deadline semantics

- Original behavior: before-fix `responseTimeMsMeasuresWholeRedirectChain` failed with `responseTimeMs=65` for two intentionally slow redirect hops; `totalDeadlineIsEnforcedAcrossRedirects` failed with `Redirect loop detected` instead of a deadline error. The copied before evidence is `debugging/before-fix/timing/performance-summary.txt`.
- Root cause: timing was taken around the current request and overwritten on each redirect, and only a per-request 8-second timeout existed. Ten redirects could each be under the per-request timeout while exceeding the intended 10-second total deadline.
- Correction: timing starts once, `responseTimeMs` is set once at the end, and each request uses `remainingTimeout(startTime)` clamped by the per-request timeout and the remaining total deadline.
- Retest: `responseTimeMsMeasuresWholeRedirectChain` and `totalDeadlineIsEnforcedAcrossRedirects` pass; `performance/performance-summary.txt` is regenerated from live responses and states that responseTimeMs is total check duration; five-URL maximum was 962 ms, below the 10-second target.
- Final self-review refinement: the first correction set `responseTimeMs` after findings were built, so the Response Time finding still saw `null` and produced WARNING on fast responses. The setter was moved before finding construction, regression `responseTimeFindingUsesMeasuredDuration` was added, and the full suite now has 157 tests with 0 failures. Live retests show Response Time PASS for fast website checks while slower or missing-header cases retain their appropriate findings.

## 8. Unconditional PASS in evidence generation

- Original behavior: prior `api/request-summary.md` and `test-case-matrix.md` rows were generated without expected-vs-observed assertions, and older redirect/404 expectations encoded corrected defects as if they were intended behavior.
- Root cause: the generators wrote PASS directly rather than deriving verdicts from evidence.
- Correction: added `api/generate-request-summary.py`, `performance/generate-performance-evidence.py`, `frontend/browser_verify.py`, and `generate-test-case-matrix.py`. They assert expected values, detect duplicate IDs, and fail on mismatches. Matrix sections now distinguish PASS, UNEXECUTED, and PARTIAL rows.
- Retest: `api/assertion-report.txt` reports 44 rows with no failures and unique IDs; `matrix-assertion-report.txt` reports 44 API PASS rows, 13 backend PASS rows, 8 browser PASS rows, 6 unexecuted rows, 3 partial rows, no assertion failures, and unique IDs.

## 9. Requirement wording refinement

- Original reporting gap: FR-05 was previously summarized as fully verified from JSON text input and API multipart upload, while the frontend only provides a textarea and no file picker.
- Root cause: the report wording did not distinguish “Dockerfile as a text file” at the API layer from a browser file-selection workflow.
- Correction: `requirements-traceability.md` and the matrix now mark FR-05 as Partially Verified, with JSON text input and multipart text-file upload verified, frontend textarea verified, frontend file picker unexecuted/not present, and no execution path found in backend main source.
- Retest: no source change; evidence is a reporting correction only.

## 10. Final outcome

- All newly found application defects have a before-fix regression, a minimal correction, and an after-fix retest.
- No historical weekly evidence was modified.
- No Task 4/Week 7 analysis module was implemented.
- No commit, push, merge, reset, or branch switch was performed.
