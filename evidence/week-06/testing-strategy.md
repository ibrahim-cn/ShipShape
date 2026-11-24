# Week 6 Testing Strategy

## Scope and principles

Week 6 validates the current ShipShape implementation only. Development of new analysis modules is paused; Task 4 (Secret & Credential Detection) is not implemented or tested as working functionality. The authoritative requirements are the Week 2/Week 3 report PDFs for FR-01..FR-06 and NFR-01..NFR-03, with README, API design, and architecture documents used for the implemented website, Dockerfile, Compose, and shared project pipeline additions.

The strategy follows four principles:

1. Prefer real functionality: exercise actual services, endpoints, files, and browser UI rather than illustrative examples.
2. Preserve evidence: capture sanitized observed output under `evidence/week-06/`.
3. Separate verified results from assumptions and unperformed tests.
4. Add tests only when an actual requirement gap or defect needs a repeatable regression guard.
5. Derive evidence verdicts from expected-vs-observed assertions; do not write PASS manually.

## 1. Unit testing

- **Why appropriate:** Fast, deterministic validation of pure rules, parsers, validation logic, and status aggregation.
- **Components:** `WebsiteCheckService`, URL validation and SSRF guards, `DockerfileAnalysisService`, `ComposeAnalysisService`, `ProjectInputService`, `ProjectAnalysisService`, `FindingStatus`, DTO behavior.
- **Tools:** JUnit 5 and Maven Surefire under Java 17.
- **Evidence:** `backend/backend-tests.txt`, `backend/backend-tests.exit-code.txt`, `backend/test-summary.txt`.
- **Limitations:** Mocked boundaries do not prove live network, servlet, multipart, or browser behavior; those are covered separately.

## 2. API testing

- **Why appropriate:** Verifies the real HTTP contract, status codes, JSON fields, error bodies, and live behavior of the running Spring Boot application.
- **Components:** `GET /api/health`, `POST /api/checks/website`, `POST /api/checks/dockerfile`, `POST /api/projects/analyze/files`.
- **Tools:** `curl` against `http://127.0.0.1:8080`.
- **Evidence:** `api/health.txt`, `api/website-*.json`, `api/dockerfile-*.json`, `api/project-*.json`, `api/request-summary.md`, `api/assertion-report.txt`, `api/generate-request-summary.py`.
- **Limitations:** Local API tests do not prove production deployment behavior; public website tests depend on external availability.

## 3. Integration testing

- **Why appropriate:** Verifies Spring MVC wiring, multipart parsing, shared pipeline behavior, and analyzer isolation together.
- **Components:** `ProjectAnalysisIntegrationTest`, `ProjectAnalysisControllerTest`, Spring context loading.
- **Tools:** Spring Boot Test, MockMvc, JUnit 5, real test fixtures.
- **Evidence:** Included in the full Maven test run and summarized in `backend/test-summary.txt`.
- **Limitations:** MockMvc is not a real socket-level client; live multipart requests are additionally captured in `api/`.

## 4. Functional testing

- **Why appropriate:** Confirms that each implemented feature produces the observable behavior required by FR-01..FR-06 and the additive Compose/project rules.
- **Components:** Website reachability/status/HTTPS/response time/headers, Dockerfile rules, Compose rules, status presentation.
- **Tools:** Live API requests plus browser UI checks where the UI exists.
- **Evidence:** `api/`, `frontend/`, `test-case-matrix.md`, `requirements-traceability.md`.
- **Limitations:** Functionality not exposed in the UI (project upload and Compose analysis) is validated only through the backend API.

## 5. Negative testing

- **Why appropriate:** Confirms graceful handling of invalid URLs, unsupported schemes, empty inputs, malformed files, unsupported files, and missing request parts.
- **Components:** URL validation, Dockerfile validation, Compose parser, project input validation, global error handler.
- **Tools:** Unit tests and live API requests.
- **Evidence:** `api/negative-*.json`, `security/security-results.txt`.
- **Limitations:** A negative response proves rejection behavior, not exhaustive input coverage.

## 6. Boundary testing

- **Why appropriate:** Confirms limits at documented thresholds rather than only typical values.
- **Components:** 100,000-character Dockerfile input, 200,000-character Compose input, project upload size/count/path limits, redirect limit, response-time target.
- **Tools:** Unit tests, generated local fixtures, live API requests.
- **Evidence:** `api/boundary-*.json`, `backend/`, `performance/`.
- **Limitations:** Very large upload behavior is primarily validated through unit tests to avoid unnecessary duplicate network payloads.

## 7. Security testing

- **Why appropriate:** NFR-02 requires URL validation and private/internal address protection; the project pipeline also sanitizes file paths and evidence.
- **Components:** URL scheme/host validation, DNS/redirect revalidation, multipart path normalization, duplicate detection, YAML parser restrictions, sensitive evidence redaction.
- **Tools:** Unit tests, controlled live API requests, synthetic credentials, synthetic unsafe YAML.
- **Evidence:** `security/security-results.txt`, `security/redaction-check.txt`, relevant `api/` JSON files.
- **Limitations:** No real cloud metadata endpoint or private host is contacted. DNS rebinding is documented by the application as an unresolved limitation; Week 6 does not claim complete SSRF protection beyond implemented checks.

## 8. Performance testing

- **Why appropriate:** NFR-01 defines a 10-second proposed target for a single website check.
- **Components:** Website check endpoint, local Dockerfile/Compose/project analysis requests.
- **Tools:** `curl` with measured total API duration, ShipShape-reported `responseTimeMs`, Python statistics.
- **Evidence:** `performance/website-performance.csv`, `performance/local-analysis-performance.csv`, `performance/performance-summary.txt`, `performance/generate-performance-evidence.py`.
- **Limitations:** Public website measurements are affected by external network conditions. They are low-volume observations, not load tests. `responseTimeMs` is the total application check duration including redirects; full curl latency includes HTTP client startup and transfer.

## 9. Reliability testing

- **Why appropriate:** NFR-03 requires graceful handling of unreachable URLs, timeouts, invalid responses, and continued operation after errors.
- **Components:** Website failure paths, malformed uploads, analyzer failure isolation, repeated requests.
- **Tools:** Deterministic unit tests, live negative requests, repeated health checks.
- **Evidence:** `api/reliability-*.txt`, `security/security-results.txt`, `backend/`.
- **Limitations:** Live connection timeout against an external server is not forced; timeout behavior is verified deterministically in unit tests. SSRF protection prevents using localhost as a controlled slow server.

## 10. Browser-based UI testing

- **Why appropriate:** FR-06 and the Week 5 UI work require status presentation and error handling in the browser, not only in JSON.
- **Components:** Website Check form, Dockerfile Analysis form, result panels, loading/error states, desktop and mobile layouts.
- **Tools:** Installed Google Chrome with Playwright Python (available in the environment), production build output, screenshots where actually captured.
- **Evidence:** `frontend/frontend-build.txt`, `frontend/browser-verification.json`, screenshots and metadata under `frontend/`.
- **Limitations:** The shared project upload and Docker Compose UI are not implemented; they are explicitly marked not applicable rather than tested through the UI. If browser automation cannot drive a scenario, it is recorded as unperformed with a manual checklist.
