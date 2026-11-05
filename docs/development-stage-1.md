# ShipShape — Development Stage 1 (Week 4)

## 1. Stage Information

| Item | Detail |
|---|---|
| Project name | ShipShape: Pre-Deployment Readiness Checker |
| Stage | Development Stage 1 — Week 4 |
| Main requirement | FR-01: website URL submission, reachability checking, and HTTP status display |
| Repository | [ibrahim-cn/ShipShape](https://github.com/ibrahim-cn/ShipShape) |
| Development dates | Backend endpoint and test foundation: 26 October 2026. Week 4 frontend, README, and API documentation: 4 November 2026. These dates are reconstructed from Git commit timestamps. |
| Verification dates | Backend test log: 4 November 2026 at 16:48 +03:00. Screenshot and API evidence files carry local filesystem timestamps from 5 November 2026. |

The Week 4 work followed the project plan and the FR-01 workflow described in the [project README](../README.md). The stage produced the first working local prototype rather than a deployed application.

## 2. Objective

The objective of Development Stage 1 was to turn the existing project design into a working end-to-end prototype. The prototype needed to accept a public website URL in a browser interface, send it to the Spring Boot backend, perform a real reachability check, and return the result to the user.

The stage therefore focused on demonstrating a complete request path: user input in the React frontend, a request through the Vite development proxy, URL validation and website checking in the backend, and a visible result in the frontend. The backend was also required to reject private and internal addresses so that the URL checker could not be used to probe local network resources.

The scope of this stage was deliberately limited. It was intended to prove the core workflow and establish a stable base for later checks, not to complete the full ShipShape feature set.

## 3. Implemented Functionality

### 3.1 Frontend

The frontend is a React and TypeScript application built with Vite. The main user interface is defined in [App.tsx](../frontend/src/App.tsx). The form component in [UrlCheckForm.tsx](../frontend/src/components/UrlCheckForm.tsx) collects the website URL and performs a basic empty-field check before submission. The application keeps loading, error, and result state in `App.tsx`.

The API client in [websiteCheck.ts](../frontend/src/api/websiteCheck.ts) sends a `POST` request to `/api/checks/website` with a JSON body containing the submitted URL. It also reads JSON error responses from the backend and exposes them to the interface. The result component in [CheckResult.tsx](../frontend/src/components/CheckResult.tsx) displays the submitted URL, the reachability result, and the HTTP status code.

### 3.2 Backend REST API

The backend is a Java 17 and Spring Boot application. The health endpoint is implemented in [HealthController.java](../backend/src/main/java/com/shipshape/backend/controller/HealthController.java). A successful `GET /api/health` returns:

```json
{
  "service": "shipshape-backend",
  "status": "UP"
}
```

The website-check endpoint is implemented in [WebsiteCheckController.java](../backend/src/main/java/com/shipshape/backend/controller/WebsiteCheckController.java) as `POST /api/checks/website`. The controller accepts a JSON request containing a `url` field and delegates the check to `WebsiteCheckService`.

The service in [WebsiteCheckService.java](../backend/src/main/java/com/shipshape/backend/service/WebsiteCheckService.java) sends an HTTP `GET` request to the validated destination, records the target HTTP status code, and treats a final status in the `200–399` range as reachable. If the target server returns a `4xx` or `5xx` response, the ShipShape API still returns HTTP `200` with `reachable` set to `false` and the target status recorded in `httpStatusCode`.

### 3.3 URL Validation and Private-Address Rejection

URL validation is implemented in [UrlValidator.java](../backend/src/main/java/com/shipshape/backend/util/UrlValidator.java). The validator accepts only `http` and `https` URLs, rejects URLs containing credentials, and blocks localhost, private, link-local, multicast, and other non-public destinations. Hostnames are resolved before a request is made, and redirect targets are validated again before they are followed.

The service also limits redirects, detects redirect loops, applies a five-second connection timeout and an eight-second request timeout, and returns structured JSON errors. For example, a request containing `http://127.0.0.1/` is rejected with HTTP `400` and:

```json
{
  "error": "Host not allowed"
}
```

### 3.4 Backend Results Not Yet Displayed

The backend already calculates response time, HTTPS usage, selected security-header results, an overall `PASS`/`WARNING`/`FAIL` status, and individual findings. These fields are present in the API response and are represented in the frontend TypeScript type, but the Week 4 interface displays only the submitted URL, reachability, and HTTP status code. The additional results are therefore implemented in the backend but are not yet part of the visible user workflow.

## 4. Architecture and Technical Decisions

The actual Week 4 workflow is:

```text
User
  -> React frontend (App.tsx, UrlCheckForm.tsx)
  -> Vite development proxy for /api
  -> Spring Boot WebsiteCheckController
  -> WebsiteCheckService
  -> UrlValidator and target website request
  -> WebsiteCheckResponse
  -> React CheckResult
```

The Vite configuration in [vite.config.ts](../frontend/vite.config.ts) runs the frontend on port `5173` and proxies requests beginning with `/api` to the backend on `http://localhost:8080`. This allowed the frontend to use a relative API path during local development without adding a separate CORS configuration to the backend.

The backend separates the HTTP controller, service logic, DTOs, validation, and error handling. `WebsiteCheckController` handles the request mapping, `WebsiteCheckService` performs the check, and `UrlValidator` enforces the URL safety rules. This separation keeps the controller small and makes the validation and checking logic testable.

The repository architecture documents describe a broader system that also includes Dockerfile analysis and a separate result-formatting responsibility. The relevant design references are [component-design.md](../architecture/component-design.md) and the [Mermaid architecture diagram](../architecture/shipshape-architecture.mmd). The Week 4 implementation covers only the website-check part of that design. Dockerfile analysis and the AWS deployment described in [aws-service-selection.md](../architecture/aws-service-selection.md) remain planned.

The API contract is documented in [api-design.md](../api/api-design.md). It records the implemented paths as `GET /api/health` and `POST /api/checks/website`, and marks Dockerfile analysis as planned.

## 5. Verification and Evidence

The results below use the seven evidence files selected for the planned GitHub submission and indexed in [evidence/week-04](../evidence/week-04/README.md). The health and environment results are recorded directly in this document because their raw capture files are local verification artifacts and are not part of that selected evidence set. No new results were invented for this document.

| Check | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|
| Backend tests | Test suite passes under Java 17 | 21 tests run, 0 failures, 0 errors, 0 skipped; `BUILD SUCCESS`; exit code 0 | PASS | [backend-tests.txt](../evidence/week-04/backend-tests.txt) |
| Frontend production build | TypeScript check and Vite build succeed | Exit code 0; Vite 8.3.5; 19 modules transformed; built in 150 ms | PASS | [frontend-build.txt](../evidence/week-04/frontend-build.txt) |
| Backend health | Backend responds with HTTP 200 | HTTP 200; `{"service":"shipshape-backend","status":"UP"}` | PASS | Recorded in this document |
| Public URL check | Public GitHub URL is reachable | API HTTP 200; `reachable=true`; target `httpStatusCode=200`; `overallStatus=PASS`; `responseTimeMs=940` | PASS | [api-success.txt](../evidence/week-04/api-success.txt) |
| Blocked private URL | Private address is rejected | API HTTP 400; `{"error":"Host not allowed"}` | PASS | [api-blocked-url.txt](../evidence/week-04/api-blocked-url.txt) |
| Environment | Java 17, Maven, Node.js, and npm are available | Java 17.0.12; Maven 3.9.14; Node.js v25.8.1; npm 11.11.0 | PASS | Recorded in this document |
| Success screenshot | Existing success screenshot is available | `succes-image.png` exists and is a valid PNG | Present | [succes-image.png](../evidence/week-04/succes-image.png) |
| Error screenshot | Existing error screenshot is available | `fail-image.png` exists and is a valid PNG | Present | [fail-image.png](../evidence/week-04/fail-image.png) |

The two screenshots were already present in the evidence directory. They were existing evidence files, were not generated as part of this documentation step, were not renamed, and were not edited.

The commit history in Section 7 is based on the verified local Git log and is summarised in the table. No local-only Git log file is linked from this document.

## 6. Technical Challenges and Solutions

No specific debugging incident with a recorded diagnosis is documented in the commit history or Week 4 evidence. This section therefore does not invent one. The main technical constraints that are directly visible in the implementation and tests are described below.

### 6.1 Restricting Outbound Requests

**Problem:** A public URL checker can become a route for probing localhost, private networks, or cloud metadata services. The backend must validate the destination before making an outbound request.

**Implementation:** `UrlValidator` checks the scheme, rejects credentials in the URL, blocks localhost and internal hostnames, resolves the host, and rejects non-public addresses. `WebsiteCheckService` validates the URL again before each request and revalidates redirect targets.

**Outcome:** The automated tests cover localhost, loopback, private IPv4 addresses, link-local addresses, multicast, and unspecified addresses. The live blocked-URL capture returns HTTP `400` with `Host not allowed`.

### 6.2 Controlled Redirect Handling

**Problem:** Automatic redirect following could allow a validated public URL to redirect to an internal destination.

**Implementation:** The HTTP client is configured not to follow redirects automatically. `WebsiteCheckService` follows a limited number of redirects manually, validates each new target, detects loops, and applies connection and request timeouts.

**Outcome:** The service has explicit redirect validation and timeout handling. These behaviours are covered by the backend test suite.

### 6.3 Local Frontend and Backend Integration

**Problem:** The frontend development server runs on a different port from the Spring Boot backend, so the browser cannot call the backend directly without either a proxy or CORS configuration.

**Implementation:** The Vite development server proxies `/api` requests to `http://localhost:8080`, as configured in `vite.config.ts`. The frontend API client uses the relative path `/api/checks/website`.

**Outcome:** The local frontend can call the backend through port `5173`, and no additional backend CORS configuration is required for this development setup.

## 7. GitHub Development Record

The following commits form the verifiable development record for this stage.

| Commit | Date | Subject | Role in the stage | State |
|---|---|---|---|---|
| `3bf2e2f` | 26 October 2026 | `endpoints and test files were added.` | Added the backend controllers, service, DTOs, validator, error handler, and automated tests that support the website check | Committed to the repository |
| `42b8185` | 4 November 2026 | `implement Week 4 website check frontend` | Added the React and TypeScript frontend, URL form, API client, result display, and Vite configuration | Committed to the repository |
| `cb6de43` | 4 November 2026 | `update README for Week 4 prototype.` | Updated the root README with the Week 4 status, local run instructions, verification scenarios, and limitations | Committed to the repository |
| `d0df580` | 4 November 2026 | `align API design with implemented endpoints` | Updated the API documentation to match the implemented health and website-check endpoints | Committed to the repository |

At the time of writing, local `main` is at `d0df580`. The Week 4 evidence directory and the Week 4 documentation files are not yet committed. They are therefore development records prepared locally for a future selective commit, not part of the current committed source history.

## 8. Current Limitations

- The frontend displays only the submitted URL, reachability, and HTTP status code.
- Response time, HTTPS usage, security headers, overall status, and individual findings are calculated by the backend but are not displayed in the current interface.
- Dockerfile static analysis is not implemented.
- AWS deployment is not completed, and the application is not publicly deployed.
- Frontend automated tests have not been implemented.
- Complete protection against DNS rebinding is not claimed because DNS validation does not guarantee that the destination IP remains pinned at connection time.

## 9. Next Development Stage

The next stage should extend the existing working prototype rather than replace it. The most direct improvement is to display the response time, HTTPS result, security-header findings, overall status, and individual findings that the backend already returns. This would turn the current narrow result view into a more complete FR-01 check result.

After that, the frontend and backend need additional automated coverage. Frontend tests should cover form submission, error handling, and result rendering, while backend tests can be extended around the response-time and security-header findings.

The following development stage should also add the planned rule-based Dockerfile analysis. That work must remain static text analysis; uploaded Dockerfiles should never be executed. Deployment preparation and AWS configuration remain later work and should not be presented as complete until they are actually implemented and verified.
