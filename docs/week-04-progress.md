# ShipShape — Week 4 Progress Record

## 1. Week 4 Objective and FR-01

Week 4 covered Development Stage 1 of ShipShape: Pre-Deployment Readiness Checker. The stage objective was to produce the first working end-to-end prototype for the project's first functional requirement, FR-01. FR-01 requires a user to submit a website URL, have the backend check whether the website is reachable, and receive the HTTP status result in the browser interface.

The intended scope was intentionally limited to this core workflow. The stage had to demonstrate a complete path from user input to a verified backend response, while also preventing the checker from being used against private or internal network addresses. It was not intended to complete the wider ShipShape feature set, Dockerfile analysis, or cloud deployment.

The work dates in this record are reconstructed from Git commit timestamps and saved verification evidence. No daily activity log was maintained, so the record does not claim a day-by-day sequence beyond what those sources support.

## 2. Work Completed

The verified commit history for Week 4 is summarised below.

| Commit | Date | Verified work |
|---|---|---|
| `3bf2e2f` | 26 October 2026 | Added the backend endpoints and automated test foundation for the website check. |
| `42b8185` | 4 November 2026 | Implemented the Week 4 React and TypeScript frontend for website checking. |
| `cb6de43` | 4 November 2026 | Updated the root README for the Week 4 prototype, including local run and verification information. |
| `d0df580` | 4 November 2026 | Aligned the API design document with the implemented health and website-check endpoints. |

The backend work produced `GET /api/health` and `POST /api/checks/website`. The website-check endpoint accepts a URL, validates it, performs an HTTP request, and returns fields including `reachable`, `httpStatusCode`, `responseTimeMs`, `httpsEnabled`, `securityHeaders`, `overallStatus`, and `findings`. The backend also includes automated tests for URL validation, private-address rejection, redirect handling, timeouts, HTTP status handling, and security-header checks.

The frontend work produced a React and TypeScript interface built with Vite. It includes a URL form, an API client, loading and error states, and a result view. The Week 4 interface displays the submitted URL, reachability, and HTTP status code.

The project documentation was also updated during the stage. The root README records the Week 4 status and local verification workflow, while the API design document now reflects the implemented endpoint paths. The fuller Development Stage 1 record is available in [development-stage-1.md](development-stage-1.md).

## 3. Frontend and Backend Integration

The frontend runs on port `5173` through the Vite development server. The backend runs on port `8080` under Spring Boot. The Vite configuration proxies requests beginning with `/api` to `http://localhost:8080`, so the frontend can use the relative path `/api/checks/website` instead of a hard-coded backend origin.

The request flow is:

```text
URL form
  -> App.tsx state and submission handling
  -> POST /api/checks/website through the Vite proxy
  -> WebsiteCheckController
  -> WebsiteCheckService and UrlValidator
  -> JSON response
  -> CheckResult component
```

This arrangement kept local development straightforward and avoided adding a separate CORS configuration for the Week 4 setup. The frontend API client reads successful JSON responses and also extracts the backend error message when a request is rejected.

## 4. Verification Results

The verification results below are limited to facts recorded in the saved evidence. No test count, API value, date, or build result has been invented.

| Check | Recorded result | Evidence |
|---|---|---|
| Backend tests | 21 tests run, 0 failures, 0 errors, 0 skipped; `BUILD SUCCESS`; exit code 0 | [backend-tests.txt](../evidence/week-04/backend-tests.txt) |
| Frontend build | Exit code 0; Vite 8.3.5; 19 modules transformed; built in 150 ms | [frontend-build.txt](../evidence/week-04/frontend-build.txt) |
| Backend health | HTTP 200 with `{"service":"shipshape-backend","status":"UP"}` | Recorded in this document |
| Successful website check | API HTTP 200; `reachable=true`; target `httpStatusCode=200`; `overallStatus=PASS`; `responseTimeMs=940` | [api-success.txt](../evidence/week-04/api-success.txt) |
| Blocked private address | API HTTP 400 with `{"error":"Host not allowed"}` | [api-blocked-url.txt](../evidence/week-04/api-blocked-url.txt) |
| Success screenshot | `succes-image.png` exists and is a valid PNG | [succes-image.png](../evidence/week-04/succes-image.png) |
| Error screenshot | `fail-image.png` exists and is a valid PNG | [fail-image.png](../evidence/week-04/fail-image.png) |

The local verification environment used Java 17.0.12, Apache Maven 3.9.14 running on Java 17.0.12, Node.js v25.8.1, and npm 11.11.0. The public URL used for the successful API check was `https://github.com/ibrahim-cn/ShipShape/`, and the blocked URL was `http://127.0.0.1/`.

The two browser screenshots already existed in the evidence directory. Their filenames and PNG formats were verified, but their visual contents were not independently inspected during documentation.

## 5. Technical Decisions and Genuine Challenges

The main integration decision was to use the Vite development proxy for `/api` requests. This kept the frontend and backend on separate development ports while allowing the browser to call a relative API path. It also avoided an additional CORS configuration for the local prototype.

The main backend decision was to treat URL validation as a security boundary. `UrlValidator` accepts only HTTP and HTTPS URLs, rejects credentials in URLs, resolves hostnames, and blocks localhost, private, link-local, multicast, and other non-public addresses. `WebsiteCheckService` validates the destination again before making a request and revalidates redirect targets. This reduces the risk that the checker could be used to probe internal services.

Redirects are handled manually rather than being followed automatically. The service limits the number of redirects, detects loops, validates each redirect target, and applies a five-second connection timeout and an eight-second request timeout. These constraints are visible in the implementation and covered by the backend tests.

No specific debugging incident with a recorded diagnosis appears in the commit history or Week 4 evidence. The challenges above are therefore presented as design and security constraints identified from the implementation, not as a reconstructed debugging narrative.

## 6. Current Limitations

- The frontend displays only the submitted URL, reachability, and HTTP status code.
- Response time, HTTPS usage, security headers, overall status, and individual findings are calculated by the backend but are not yet displayed in the interface.
- Dockerfile static analysis is not implemented.
- AWS deployment is not completed, and the application is not publicly deployed.
- Frontend automated tests have not been implemented.
- Complete protection against DNS rebinding is not claimed, because DNS validation does not guarantee that the destination IP remains pinned at connection time.

## 7. Week 5 Priorities

The first Week 5 priority should be to display the backend results that are already available. Showing response time, HTTPS usage, security-header findings, overall status, and individual findings would make the FR-01 result more useful without changing the existing API contract.

The next priority should be additional automated coverage. Frontend tests should cover form submission, loading and error states, and result rendering. Backend tests can be extended around response-time calculation and security-header findings.

A later priority is the planned rule-based Dockerfile analysis. That analysis must remain static text inspection, and uploaded Dockerfiles must never be executed. Deployment preparation and AWS configuration remain later work and should only be described as complete after they are implemented and verified.

The documentation and selected evidence in this record are prepared locally for a future selective GitHub commit. They have not been committed or pushed.
