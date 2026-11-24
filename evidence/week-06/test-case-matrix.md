# Week 6 Test Case Matrix

PASS means the observed result matched the expectation. Application finding values such as WARNING, FAIL, ERROR and NOT_CHECKED are reported values, not test failures. UNEXECUTED and PARTIAL rows are not counted as PASS.

This matrix is generated from `backend/test-summary.txt`, `api/request-summary.md`, and `frontend/browser-verification.json` by `generate-test-case-matrix.py`.

## Automated backend tests

| ID | Level | Test class / scenario | Expected | Actual | Result |
|---|---|---|---|---|---|
| UNIT-01 | Unit/Integration | com.shipshape.backend.BackendApplicationTests | failures=0, errors=0, skipped=0 | tests=1, failures=0, errors=0, skipped=0 | PASS |
| UNIT-02 | Unit/Integration | com.shipshape.backend.controller.DockerfileAnalysisControllerTest | failures=0, errors=0, skipped=0 | tests=2, failures=0, errors=0, skipped=0 | PASS |
| UNIT-03 | Unit/Integration | com.shipshape.backend.controller.HealthControllerTest | failures=0, errors=0, skipped=0 | tests=1, failures=0, errors=0, skipped=0 | PASS |
| UNIT-04 | Unit/Integration | com.shipshape.backend.controller.ProjectAnalysisControllerTest | failures=0, errors=0, skipped=0 | tests=2, failures=0, errors=0, skipped=0 | PASS |
| UNIT-05 | Unit/Integration | com.shipshape.backend.controller.WebsiteCheckControllerTest | failures=0, errors=0, skipped=0 | tests=1, failures=0, errors=0, skipped=0 | PASS |
| UNIT-06 | Unit/Integration | com.shipshape.backend.dto.FindingStatusTest | failures=0, errors=0, skipped=0 | tests=4, failures=0, errors=0, skipped=0 | PASS |
| UNIT-07 | Unit/Integration | com.shipshape.backend.integration.ProjectAnalysisIntegrationTest | failures=0, errors=0, skipped=0 | tests=6, failures=0, errors=0, skipped=0 | PASS |
| UNIT-08 | Unit/Integration | com.shipshape.backend.service.ComposeAnalysisServiceTest | failures=0, errors=0, skipped=0 | tests=36, failures=0, errors=0, skipped=0 | PASS |
| UNIT-09 | Unit/Integration | com.shipshape.backend.service.DockerfileAnalysisServiceTest | failures=0, errors=0, skipped=0 | tests=53, failures=0, errors=0, skipped=0 | PASS |
| UNIT-10 | Unit/Integration | com.shipshape.backend.service.ProjectAnalysisServiceTest | failures=0, errors=0, skipped=0 | tests=11, failures=0, errors=0, skipped=0 | PASS |
| UNIT-11 | Unit/Integration | com.shipshape.backend.service.ProjectInputServiceTest | failures=0, errors=0, skipped=0 | tests=13, failures=0, errors=0, skipped=0 | PASS |
| UNIT-12 | Unit/Integration | com.shipshape.backend.service.WebsiteCheckServiceMoreTest | failures=0, errors=0, skipped=0 | tests=14, failures=0, errors=0, skipped=0 | PASS |
| UNIT-13 | Unit/Integration | com.shipshape.backend.service.WebsiteCheckServiceTest | failures=0, errors=0, skipped=0 | tests=13, failures=0, errors=0, skipped=0 | PASS |

## Live API cases

| ID | Level | Scenario | Method | Expected | Actual | HTTP | Result |
|---|---|---|---|---|---|---|---|
| TC-API-01 | API | health | GET /api/health | 200 UP | {"service":"shipshape-backend","status":"UP"}; HTTP_STATUS=200 | 200 | PASS |
| TC-API-02 | API | website success | POST /api/checks/website | 200; reachable, HTTPS PASS | overall=WARNING, reachable=True, https=True, submitted=https, final=https, httpStatus=200, responseTimeMs=139 | 200 | PASS |
| TC-API-03 | API | direct HTTP URL | POST /api/checks/website | 200; final HTTP reported insecure | overall=FAIL, reachable=True, https=False, submitted=http, final=http, httpStatus=200, responseTimeMs=47 | 200 | PASS |
| TC-API-04 | API | HTTP to HTTPS redirect | POST /api/checks/website | 200; final HTTPS reported secure | overall=PASS, reachable=True, https=True, submitted=http, final=https, httpStatus=200, responseTimeMs=962 | 200 | PASS |
| TC-API-05 | API | 404 website | POST /api/checks/website | 200; reachable true, HTTP Status FAIL, code preserved | overall=FAIL, reachable=True, https=True, submitted=https, final=https, httpStatus=404, responseTimeMs=20 | 200 | PASS |
| TC-API-19 | API | 401 website | POST /api/checks/website | 200; reachable true, HTTP Status FAIL, code preserved | overall=FAIL, reachable=True, https=True, submitted=https, final=https, httpStatus=401, responseTimeMs=454 | 200 | PASS |
| TC-API-20 | API | 403 website | POST /api/checks/website | 200; reachable true, HTTP Status FAIL, code preserved | overall=FAIL, reachable=True, https=True, submitted=https, final=https, httpStatus=403, responseTimeMs=135 | 200 | PASS |
| TC-API-21 | API | 500 website | POST /api/checks/website | 200; reachable true, HTTP Status FAIL, code preserved | overall=FAIL, reachable=True, https=True, submitted=https, final=https, httpStatus=500, responseTimeMs=135 | 200 | PASS |
| TC-API-22 | API | HTTPS to HTTP redirect | POST /api/checks/website | 200; final HTTP reported insecure | overall=FAIL, reachable=True, https=False, submitted=https, final=http, httpStatus=200, responseTimeMs=156 | 200 | PASS |
| TC-API-15 | API | Wikipedia URL | POST /api/checks/website | 200; reachable and HTTPS checked | overall=WARNING, reachable=True, https=True, submitted=https, final=https, httpStatus=200, responseTimeMs=490 | 200 | PASS |
| TC-API-16 | API | Cloudflare URL | POST /api/checks/website | 200; reachable and HTTPS checked | overall=PASS, reachable=True, https=True, submitted=https, final=https, httpStatus=200, responseTimeMs=451 | 200 | PASS |
| TC-API-17 | API | Mozilla URL | POST /api/checks/website | 200; reachable and HTTPS checked | overall=PASS, reachable=True, https=True, submitted=https, final=https, httpStatus=200, responseTimeMs=274 | 200 | PASS |
| TC-API-18 | API | non-resolving domain | POST /api/checks/website | 400 Host not allowed | Host not allowed | 400 | PASS |
| TC-API-06 | API | invalid URL | POST /api/checks/website | 400 Unsupported scheme | Unsupported scheme | 400 | PASS |
| TC-API-07 | API | empty URL | POST /api/checks/website | 400 URL is required | URL is required | 400 | PASS |
| TC-API-08 | API | missing url field | POST /api/checks/website | 400 URL is required | URL is required | 400 | PASS |
| TC-API-09 | API | unsupported FTP scheme | POST /api/checks/website | 400 Unsupported scheme | Unsupported scheme | 400 | PASS |
| TC-API-10 | API | localhost block | POST /api/checks/website | 400 Host not allowed | Host not allowed | 400 | PASS |
| TC-API-11 | API | IPv6 loopback block | POST /api/checks/website | 400 Host not allowed | Host not allowed | 400 | PASS |
| TC-API-12 | API | private IPv4 block | POST /api/checks/website | 400 Host not allowed | Host not allowed | 400 | PASS |
| TC-API-13 | API | metadata IP block | POST /api/checks/website | 400 Host not allowed | Host not allowed | 400 | PASS |
| TC-API-14 | API | whitespace URL | POST /api/checks/website | 200; accepted and checked | overall=WARNING, reachable=True, https=True, submitted=https, final=https, httpStatus=200, responseTimeMs=29 | 200 | PASS |
| TC-DF-01 | API | correct Dockerfile | POST /api/checks/dockerfile | 200 overall PASS | overall=PASS; findings=PASS=8 | 200 | PASS |
| TC-DF-02 | API | risky Dockerfile | POST /api/checks/dockerfile | 200 overall WARNING | overall=WARNING; findings=NOT_CHECKED=1,PASS=1,WARNING=6 | 200 | PASS |
| TC-DF-03 | API | empty Dockerfile | POST /api/checks/dockerfile | 400 content required | Dockerfile content is required | 400 | PASS |
| TC-DF-04 | API | null Dockerfile | POST /api/checks/dockerfile | 400 content required | Dockerfile content is required | 400 | PASS |
| TC-DF-05 | API | missing content field | POST /api/checks/dockerfile | 400 content required | Dockerfile content is required | 400 | PASS |
| TC-DF-06 | API | 100000-char Dockerfile | POST /api/checks/dockerfile | 200 accepted at boundary | overall=WARNING; findings=NOT_CHECKED=2,PASS=3,WARNING=4 | 200 | PASS |
| TC-DF-07 | API | 100001-char Dockerfile | POST /api/checks/dockerfile | 400 too large | Dockerfile content is too large | 400 | PASS |
| TC-PROJ-01 | API | correct project files | POST /api/projects/analyze/files | 200 COMPOSE/DOCKERFILE PASS, overall NOT_CHECKED | overall=NOT_CHECKED, coverageComplete=False; COMPOSE:PASS,DOCKERFILE:PASS,SECRETS:NOT_CHECKED,ENVIRONMENT:NOT_CHECKED,IGNORE:NOT_CHECKED,SPRING_CONFIG:NOT_CHECKED,DEPENDENCIES:NOT_CHECKED,HEALTH_RELIABILITY:NOT_CHECKED,CI_CD:NOT_CHECKED,VULNERABILITIES:NOT_CHECKED | 200 | PASS |
| TC-PROJ-02 | API | risky project files | POST /api/projects/analyze/files | 200 overall FAIL | overall=FAIL, coverageComplete=False; COMPOSE:FAIL,DOCKERFILE:WARNING,SECRETS:NOT_CHECKED,ENVIRONMENT:NOT_CHECKED,IGNORE:NOT_CHECKED,SPRING_CONFIG:NOT_CHECKED,DEPENDENCIES:NOT_CHECKED,HEALTH_RELIABILITY:NOT_CHECKED,CI_CD:NOT_CHECKED,VULNERABILITIES:NOT_CHECKED | 200 | PASS |
| TC-PROJ-03 | API | unsupported file only | POST /api/projects/analyze/files | 200 overall NOT_CHECKED | overall=NOT_CHECKED, coverageComplete=False; COMPOSE:NOT_CHECKED,DOCKERFILE:NOT_CHECKED,SECRETS:NOT_CHECKED,ENVIRONMENT:NOT_CHECKED,IGNORE:NOT_CHECKED,SPRING_CONFIG:NOT_CHECKED,DEPENDENCIES:NOT_CHECKED,HEALTH_RELIABILITY:NOT_CHECKED,CI_CD:NOT_CHECKED,VULNERABILITIES:NOT_CHECKED | 200 | PASS |
| TC-PROJ-04 | API | unsafe YAML tag | POST /api/projects/analyze/files | 200 ERROR parser finding | overall=ERROR, coverageComplete=False; COMPOSE:ERROR,DOCKERFILE:NOT_CHECKED,SECRETS:NOT_CHECKED,ENVIRONMENT:NOT_CHECKED,IGNORE:NOT_CHECKED,SPRING_CONFIG:NOT_CHECKED,DEPENDENCIES:NOT_CHECKED,HEALTH_RELIABILITY:NOT_CHECKED,CI_CD:NOT_CHECKED,VULNERABILITIES:NOT_CHECKED | 200 | PASS |
| TC-PROJ-05 | API | malformed Compose YAML | POST /api/projects/analyze/files | 200 ERROR parser finding | overall=ERROR, coverageComplete=False; COMPOSE:ERROR,DOCKERFILE:NOT_CHECKED,SECRETS:NOT_CHECKED,ENVIRONMENT:NOT_CHECKED,IGNORE:NOT_CHECKED,SPRING_CONFIG:NOT_CHECKED,DEPENDENCIES:NOT_CHECKED,HEALTH_RELIABILITY:NOT_CHECKED,CI_CD:NOT_CHECKED,VULNERABILITIES:NOT_CHECKED | 200 | PASS |
| TC-PROJ-06 | API | binary upload | POST /api/projects/analyze/files | 200 NOT_CHECKED | overall=NOT_CHECKED, coverageComplete=False; COMPOSE:NOT_CHECKED,DOCKERFILE:NOT_CHECKED,SECRETS:NOT_CHECKED,ENVIRONMENT:NOT_CHECKED,IGNORE:NOT_CHECKED,SPRING_CONFIG:NOT_CHECKED,DEPENDENCIES:NOT_CHECKED,HEALTH_RELIABILITY:NOT_CHECKED,CI_CD:NOT_CHECKED,VULNERABILITIES:NOT_CHECKED | 200 | PASS |
| TC-PROJ-07 | API | invalid UTF-8 upload | POST /api/projects/analyze/files | 200 NOT_CHECKED | overall=NOT_CHECKED, coverageComplete=False; COMPOSE:NOT_CHECKED,DOCKERFILE:NOT_CHECKED,SECRETS:NOT_CHECKED,ENVIRONMENT:NOT_CHECKED,IGNORE:NOT_CHECKED,SPRING_CONFIG:NOT_CHECKED,DEPENDENCIES:NOT_CHECKED,HEALTH_RELIABILITY:NOT_CHECKED,CI_CD:NOT_CHECKED,VULNERABILITIES:NOT_CHECKED | 200 | PASS |
| TC-PROJ-08 | API | 600000-byte upload | POST /api/projects/analyze/files | 400 too large | Uploaded file is too large | 400 | PASS |
| TC-PROJ-09 | API | missing files part | POST /api/projects/analyze/files | 400 Request failed | Request failed | 400 | PASS |
| TC-SEC-01 | API | path traversal filename | POST /api/projects/analyze/files | 400 Unsafe file path | Unsafe file path | 400 | PASS |
| TC-SEC-02 | API | absolute filename | POST /api/projects/analyze/files | 400 Unsafe file path | Unsafe file path | 400 | PASS |
| TC-SEC-03 | API | duplicate normalized paths | POST /api/projects/analyze/files | 400 Unsafe file path | Unsafe file path | 400 | PASS |
| TC-REL-01 | API | health after errors #1 | GET /api/health | 200 UP | {"service":"shipshape-backend","status":"UP"} | 200 | PASS |
| TC-REL-02 | API | health after errors #2 | GET /api/health | 200 UP | {"service":"shipshape-backend","status":"UP"} | 200 | PASS |
| TC-REL-03 | API | health after errors #3 | GET /api/health | 200 UP | {"service":"shipshape-backend","status":"UP"} | 200 | PASS |

## Browser UI cases

| ID | Level | Scenario | Expected | Actual | Result |
|---|---|---|---|---|---|
| TC-UI-01 | Browser UI | initial_page | Brand and both check tabs | SS | ShipShape |  | Pre-Deployment Readiness Checker |  | Check a website before deployment |  | Submit a public URL to verify that it is reachable and review its HTTP and security | PASS |
| TC-UI-02 | Browser UI | website_success | Reachability, HTTP status, HTTPS, submitted/final scheme, response time, headers, findings | READINESS RESULT |  | Website readiness check needs attention | WARNING | Website Overview | SUBMITTED URL | https://example.com | REACHABILITY | Reachable | HTTP STATUS CODE | 200 | PASS |
| TC-UI-03 | Browser UI | website_blocked_url | Host not allowed alert | Check failed | Host not allowed | PASS |
| TC-UI-04 | Browser UI | website_empty_validation | Client-side empty URL validation | Enter a website URL to check. | PASS |
| TC-UI-05 | Browser UI | dockerfile_success | Dockerfile PASS result and findings | DOCKERFILE ANALYSIS |  | Dockerfile analysis passed | PASS | Analysis Summary | 8 findings | OVERALL RESULT | PASS | PASSED CHECKS | 8 | WARNINGS | 0 | FAILED CHECKS | 0 |  | Stati | PASS |
| TC-UI-06 | Browser UI | dockerfile_empty_validation | Client-side empty Dockerfile validation | Paste Dockerfile content to analyze. | PASS |
| TC-UI-07 | Browser UI | dockerfile_boundary_validation | Client-side 100,000-character boundary | Dockerfile content must be 100,000 characters or fewer. | PASS |
| TC-UI-08 | Browser UI | mobile_layout | No horizontal overflow at 390x844 | horizontal_overflow=False | PASS |

## Unexecuted cases

| ID | Level | Scenario | Reason | Actual | Result |
|---|---|---|---|---|---|
| TC-ENV-01 | Unexecuted | Live private-DNS result resolving to an internal address | Requires controlled DNS | Not executed | UNEXECUTED |
| TC-ENV-02 | Unexecuted | Live public redirect to an internal address | Requires controlled public redirect | Not executed | UNEXECUTED |
| TC-ENV-03 | Unexecuted | Pinned connection-stage address guard | No separate guard was demonstrated | Not executed | UNEXECUTED |
| TC-ENV-04 | Unexecuted | Live connection timeout against a controlled slow endpoint | No controlled slow public endpoint | Not executed | UNEXECUTED |
| TC-FR05-01 | Unexecuted | Frontend file picker for Dockerfile text file input | Frontend provides a textarea, not a file picker | Not executed | UNEXECUTED |
| TC-TASK4-01 | Unexecuted | Task 4/Week 7 analysis modules | Explicitly out of scope | Not executed | UNEXECUTED |

## Partially validated requirements

| ID | Status | Requirement | Evidence boundary | Result |
|---|---|---|---|---|
| PART-NFR-02 | Partial | NFR-02 SSRF validation | Live literal-IP/DNS and deterministic redirect tests passed; private-DNS, public-redirect-to-internal, and pinned connection-stage cases remain unexecuted | PARTIAL |
| PART-NFR-03 | Partial | NFR-03 timeouts and invalid responses | Deterministic timeout and structured error responses passed; live controlled timeout not executed | PARTIAL |
| PART-FR-05 | Partial | FR-05 Dockerfile as a text file | JSON text input and multipart text-file upload verified at API level; frontend accepts pasted text but has no file picker; no execution path found | PARTIAL |

Counts: backend=13 PASS, API=44 PASS, browser=8 PASS, unexecuted=6, partial=3. Assertion failures: none.
