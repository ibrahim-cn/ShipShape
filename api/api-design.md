# ShipShape – API Design

## 1. Overview

ShipShape uses a REST API to connect the React and TypeScript frontend with the Java Spring Boot backend.

The current backend implements a health endpoint, a website readiness check, rule-based static Dockerfile analysis, and a multipart project file upload endpoint. The website readiness check validates a submitted URL, checks whether the website is reachable, and returns the HTTP status code and other readiness information. Dockerfile analysis parses submitted content as text and applies predefined rules without executing the Dockerfile. The project upload endpoint builds a request-scoped shared project context, classifies uploaded files, and runs the implemented Dockerfile and Docker Compose analyzers through a common pipeline.

The implemented check and project analysis APIs return structured findings with `PASS`, `WARNING`, `FAIL`, `NOT_CHECKED`, `NOT_APPLICABLE`, or `ERROR` statuses, rule-specific severities, explanations, and suggested actions where applicable. Incomplete coverage is reported explicitly and is never represented as an overall `PASS`.

### Endpoint Summary

| Method | Path | Status |
|---|---|---|
| `GET` | `/api/health` | Implemented |
| `POST` | `/api/checks/website` | Implemented |
| `POST` | `/api/checks/dockerfile` | Implemented |
| `POST` | `/api/projects/analyze/files` | Implemented |

The implemented endpoints do not use a version prefix. Existing check endpoints are `POST /api/checks/website` and `POST /api/checks/dockerfile`. The shared project analysis endpoint is `POST /api/projects/analyze/files`.

## 2. API Endpoints

### 2.1 Health Check (Implemented)

**Endpoint:** `GET /api/health`

**Purpose:** Reports that the backend service is running.

**Request Body:** None.

**Response:**

```json
{
  "service": "shipshape-backend",
  "status": "UP"
}
```

**Status Codes:**

- `200 OK`: The backend is running.

### 2.2 Website Readiness Check (Implemented)

**Endpoint:** `POST /api/checks/website`

**Content Type:** `application/json`

**Purpose:** Checks whether a submitted website is reachable and evaluates its basic readiness.

**Request Body:**

```json
{
  "url": "https://example.com"
}
```

**Request Fields:**

| Field | Type | Required | Description |
|---|---|---|---|
| `url` | string | Yes | The public website URL to check. Only `http` and `https` URLs are accepted. |

**Processing:**

- Validate the submitted URL before making a request.
- Accept only `http` and `https` schemes.
- Reject URLs that contain credentials.
- Block localhost, private IP addresses, link-local addresses, and other internal or reserved destinations.
- Resolve hostnames and reject domains that resolve to non-public addresses.
- Follow redirects manually and revalidate each redirect target.
- Allow up to five redirects and reject redirect loops.
- Apply a 5-second connection timeout and an 8-second request timeout.
- Send an HTTP `GET` request to the validated destination.
- Check whether the website is reachable and record the HTTP status code.
- Measure response time.
- Check HTTPS usage.
- Inspect the `Strict-Transport-Security`, `Content-Security-Policy`, and `X-Content-Type-Options` response headers.
- Handle timeouts and invalid responses gracefully.

**Reachability Definition:**

A website is reported as reachable when the final HTTP status code is in the `200–399` range. If the target server returns a `4xx` or `5xx` status code, the API still returns HTTP `200` with the actual target status code and `reachable` set to `false`. A redirect response without a `Location` header is not followed and is also reported with `reachable` set to `false`.

**Response Fields:**

| Field | Type | Description |
|---|---|---|
| `url` | string | The URL submitted in the request. |
| `reachable` | boolean | Whether the final HTTP status code is in the `200–399` range. |
| `httpStatusCode` | integer or null | The HTTP status code returned by the target server. |
| `responseTimeMs` | integer or null | The measured response time in milliseconds. |
| `httpsEnabled` | boolean | Whether the submitted URL uses HTTPS. |
| `securityHeaders` | array | Results for the selected security headers. |
| `overallStatus` | string | The combined result: `PASS`, `WARNING`, or `FAIL`. |
| `findings` | array | Individual findings with status, explanation, and suggested action. |

**Security Header Fields:**

| Field | Type | Description |
|---|---|---|
| `headerName` | string | The inspected header name. |
| `present` | boolean | Whether the header was present in the response. |
| `value` | string or null | The first header value when present; otherwise `null`. |

**Finding Fields:**

| Field | Type | Description |
|---|---|---|
| `name` | string | The name of the check. |
| `status` | string | `PASS`, `WARNING`, or `FAIL`. |
| `explanation` | string | A short explanation of the result. |
| `suggestedAction` | string | A suggested action or confirmation that no action is required. |

**Example Response:**

The following example is illustrative. Actual values depend on the submitted URL and the target server's response.

```json
{
  "url": "https://example.com",
  "reachable": true,
  "httpStatusCode": 200,
  "responseTimeMs": 381,
  "httpsEnabled": true,
  "securityHeaders": [
    {
      "headerName": "Strict-Transport-Security",
      "present": false,
      "value": null
    },
    {
      "headerName": "Content-Security-Policy",
      "present": false,
      "value": null
    },
    {
      "headerName": "X-Content-Type-Options",
      "present": false,
      "value": null
    }
  ],
  "overallStatus": "WARNING",
  "findings": [
    {
      "name": "Reachability",
      "status": "PASS",
      "explanation": "The website responded successfully.",
      "suggestedAction": "No action required."
    },
    {
      "name": "HTTPS",
      "status": "PASS",
      "explanation": "The website uses HTTPS.",
      "suggestedAction": "No action required."
    },
    {
      "name": "Response Time",
      "status": "PASS",
      "explanation": "Response time is within acceptable range.",
      "suggestedAction": "No action required."
    },
    {
      "name": "Strict-Transport-Security",
      "status": "WARNING",
      "explanation": "Strict-Transport-Security header is missing.",
      "suggestedAction": "Consider adding this security header for better protection."
    },
    {
      "name": "Content-Security-Policy",
      "status": "WARNING",
      "explanation": "Content-Security-Policy header is missing.",
      "suggestedAction": "Consider adding this security header for better protection."
    },
    {
      "name": "X-Content-Type-Options",
      "status": "WARNING",
      "explanation": "X-Content-Type-Options header is missing.",
      "suggestedAction": "Consider adding this security header for better protection."
    }
  ]
}
```

**Status Codes:**

- `200 OK`: The check completed and a website-check response was returned. This includes target responses with `4xx` or `5xx` status codes; in those cases, `reachable` is `false` and `httpStatusCode` contains the target status.
- `400 Bad Request`: The URL is missing, malformed, has an invalid host, uses an unsupported scheme, contains credentials, is blocked, times out, or cannot be requested successfully.

### 2.3 Dockerfile Analysis (Implemented)

**Endpoint:** `POST /api/checks/dockerfile`

**Content Type:** `application/json`

**Purpose:** Analyzes submitted Dockerfile content using predefined static rules.

**Request Body:**

```json
{
  "content": "FROM node:latest\nUSER root\nCOPY . .\n"
}
```

**Request Fields:**

| Field | Type | Required | Description |
|---|---|---|---|
| `content` | string | Yes | Dockerfile text to analyze. The maximum accepted length is 100,000 characters. |

**Processing:**

- Reject content that is null, blank, or larger than 100,000 characters.
- Parse the content as text without executing any instruction.
- Ignore blank lines and full-line comments.
- Handle leading whitespace, case-insensitive instruction names, CRLF line endings, and backslash line continuations.
- Build a final-stage chain by following `FROM <earlier-stage-alias>` references; unrelated earlier stages are not treated as inherited configuration.
- Apply the eight rules below.
- Return one finding per rule and an aggregate overall status.
- Return evidence only as `KEYWORD [line N]`. Instruction arguments are never returned.

**Static Analysis Rules:**

| Rule ID | Name | Result logic | Severity |
|---|---|---|---|
| `DOCKER-001` | FROM Instruction | Missing or unreadable base reference produces `FAIL`; a readable base reference passes a limited structural check. | `HIGH` on `FAIL`, `INFO` on `PASS` |
| `DOCKER-002` | Base Image Tag | Explicit `:latest` or a missing tag produces `WARNING`; variable references produce `NOT_CHECKED`; digest, explicit version, `scratch`, and stage-alias references pass with limitations. No registry lookup is performed. | `MEDIUM` on latest/missing tag, `LOW` on `NOT_CHECKED`, `INFO` otherwise |
| `DOCKER-003` | Final Stage User | `USER root` or `USER 0` in the final-stage chain produces `WARNING`; a literal non-root user passes; missing or variable users produce `NOT_CHECKED`. | `HIGH` on root, `MEDIUM` when user is absent, `LOW` when a user value is unresolved, `INFO` on pass |
| `DOCKER-004` | Working Directory | An explicit `WORKDIR` in the final-stage chain passes; missing `WORKDIR` produces `WARNING`. | `LOW` on warning |
| `DOCKER-005` | Startup Command | An explicit `CMD` or `ENTRYPOINT` in the final-stage chain passes; missing explicit command produces `WARNING`. | `LOW` on warning |
| `DOCKER-006` | COPY and ADD Usage | Remote `ADD` produces `WARNING`; broad `COPY . .` without `.dockerignore` produces `WARNING`; archive/other `ADD` produces `WARNING`; `COPY` or no file transfer passes. The presence of `.dockerignore` is detected but its rules are not parsed. | `MEDIUM` on remote `ADD`, `LOW` on other warnings |
| `DOCKER-007` | Dangerous Permissions | Explicit `chmod 777`, `chmod -R 777`, `chmod a+rwx`, or `COPY --chmod=0?777` patterns produce `WARNING`. Arbitrary shell scripts are not interpreted. | `HIGH` on warning |
| `DOCKER.SYNTAX` | Unsupported Syntax | Instruction keywords outside the supported static subset produce `NOT_CHECKED` instead of being silently treated as valid. | `LOW` |

The rules do not produce arbitrary readiness scores. A version tag is not claimed to be immutable, a missing `USER` is not claimed to be definite root execution, and a missing explicit startup command is not claimed to prove the image cannot start.

**Finding Fields:**

| Field | Type | Description |
|---|---|---|
| `ruleId` | string | Stable rule identifier. |
| `category` | string | Currently `DOCKERFILE`. |
| `name` | string | Human-readable rule name. |
| `status` | string | `PASS`, `WARNING`, `FAIL`, `NOT_CHECKED`, `NOT_APPLICABLE`, or `ERROR`. |
| `severity` | string | `INFO`, `LOW`, `MEDIUM`, or `HIGH`; independent from `status`. |
| `explanation` | string | Short explanation of the result. |
| `suggestedAction` | string | Suggested action. |
| `recommendation` | string | Recommendation text for display. |
| `filePath` | string or null | Submitted file path when available. |
| `lineNumber` | integer or null | Physical line number where available. |
| `maskedEvidence` | string or null | Safe evidence such as `FROM [line 1]`. |
| `limitations` | array | Explicit limits of the static rule. |

**Response Fields:**

| Field | Type | Description |
|---|---|---|
| `overallStatus` | string | Aggregate result using the precedence defined in Section 3. |
| `findings` | array | Eight rule findings in rule-catalog order. |

**Example Response:**

```json
{
  "overallStatus": "WARNING",
  "findings": [
    {
      "ruleId": "DOCKER-001",
      "category": "DOCKERFILE",
      "name": "FROM Instruction",
      "status": "PASS",
      "severity": "INFO",
      "explanation": "A FROM instruction with a readable base reference was found.",
      "suggestedAction": "No action required for this structural rule.",
      "recommendation": "No action required for this structural rule.",
      "filePath": null,
      "lineNumber": 1,
      "maskedEvidence": "FROM [line 1]",
      "limitations": ["Dockerfile instructions are parsed as text; no image build, container execution, shell command, or network lookup is performed."]
    },
    {
      "ruleId": "DOCKER-002",
      "category": "DOCKERFILE",
      "name": "Base Image Tag",
      "status": "WARNING",
      "severity": "MEDIUM",
      "explanation": "At least one FROM instruction explicitly uses the :latest tag.",
      "suggestedAction": "Pin a specific version or digest instead of :latest for reproducible builds.",
      "recommendation": "Pin a specific version or digest instead of :latest for reproducible builds.",
      "filePath": null,
      "lineNumber": 1,
      "maskedEvidence": "FROM [line 1]",
      "limitations": ["No registry lookup was performed; a tag does not guarantee an immutable image."]
    }
  ]
}
```

The abbreviated example shows the field shape; a real response includes all eight findings.

**Status Codes:**

- `200 OK`: The Dockerfile text was parsed and analyzed. The response status may be `PASS`, `WARNING`, `FAIL`, or `NOT_CHECKED`.
- `400 Bad Request`: The content is missing, blank, or larger than 100,000 characters.

**Security Requirement:** Dockerfile content is analyzed as text. It is never executed, and the backend does not invoke Docker, containers, shell commands, external services, or AI services for this analysis. Evidence does not include instruction arguments, URLs, credentials, environment values, or shell command text.

### 2.4 Project File Upload Analysis (Implemented)

**Endpoint:** `POST /api/projects/analyze/files`

**Content Type:** `multipart/form-data`

**Purpose:** Accepts individual project files, builds a shared project context, classifies the files, and runs the implemented Dockerfile and Docker Compose analyzers through a common pipeline.

**Request Parts and Fields:**

| Name | Type | Required | Description |
|---|---|---|---|
| `files` | multipart file array | Yes | One or more text-based project files. Each part must use the field name `files`. |
| `projectName` | form field | No | Optional display name. Defaults to `uploaded-project` and is limited to 120 characters. |

**Processing:**

- Reject requests with no files, more than 200 files, duplicate paths, or unsafe paths.
- Normalize Windows separators and accept only safe relative paths.
- Reject absolute paths, drive-letter paths, `..` segments, empty segments, and control characters in paths.
- Limit each file to 512,000 bytes and the complete request to 5,000,000 bytes.
- Configure Spring multipart limits explicitly: `max-file-size=1MB`, `max-request-size=10MB`, and `file-size-threshold=0B`.
- Decode files as UTF-8 text. Files that cannot be decoded, or that contain NUL bytes, are classified as `UNSUPPORTED` and are not analyzed.
- Classify files as `DOCKERFILE`, `COMPOSE`, `PACKAGE_MANIFEST`, `CI_CONFIG`, `CONFIG`, `SOURCE`, `OTHER`, or `UNSUPPORTED`.
- Run implemented analyzers through the shared `ProjectAnalyzer` pipeline and isolate analyzer failures as `ERROR` findings.
- Add explicit `NOT_CHECKED` findings for all unimplemented analysis categories.
- Report missing Dockerfile or Compose input as `NOT_CHECKED` instead of `PASS` or `NOT_APPLICABLE`.
- Report unsupported or non-analyzable input as `NOT_CHECKED` instead of `PASS` or `NOT_APPLICABLE`.
- Return one standardized finding per rule result with `ruleId`, `category`, `name`, `status`, `severity`, `explanation`, `suggestedAction`, `recommendation`, `filePath`, `lineNumber`, `maskedEvidence`, `serviceIdentifier`, and `limitations`.
- Analyze Dockerfile files as text and never execute uploaded files, Docker instructions, shell commands, or build tools.
- Parse Compose files as YAML data with a restricted SnakeYAML configuration; never invoke Docker Compose, container runtimes, network lookups, or host file resolution.

**Compose Static Analysis Rules:**

| Rule ID | Name | Result logic | Severity |
|---|---|---|---|
| `COMPOSE-001` | Compose Structure Validation | A non-empty string-keyed `services` mapping passes; a missing or invalid mapping fails and stops deeper rules. | `HIGH` on `FAIL`, `INFO` otherwise |
| `COMPOSE-002` | Image Version Policy | Explicit version tags and valid digests pass; untagged images and explicit `latest` produce `WARNING`; variables, invalid digests, and build-only services are `NOT_CHECKED`. | `MEDIUM` on `WARNING`, `LOW` on `NOT_CHECKED`, `INFO` otherwise |
| `COMPOSE-003` | Privileged Containers | `privileged: true` fails; explicit `false` or absence passes; unresolved values are `NOT_CHECKED`. | `HIGH` on `FAIL`, `LOW` on `NOT_CHECKED`, `INFO` otherwise |
| `COMPOSE-004` | Container User | Root user/group forms fail; explicit non-root users pass; missing or variable users are `NOT_CHECKED`. | `HIGH` on `FAIL`, `LOW` on `NOT_CHECKED`, `INFO` otherwise |
| `COMPOSE-005` | Docker Socket Access | Recognized socket paths in short or long volume syntax fail, including read-only mounts; variable paths that could resolve to a socket are `NOT_CHECKED`; other mounts are handled by `COMPOSE-010` only. | `HIGH` on `FAIL`, `LOW` on `NOT_CHECKED`, `INFO` otherwise |
| `COMPOSE-006` | Host Network Isolation | `network_mode: host`, `pid: host`, and `ipc: host` produce separate contextual `WARNING` findings; unresolved values are `NOT_CHECKED`. | `MEDIUM` on warning, `LOW` on `NOT_CHECKED`, `INFO` otherwise |
| `COMPOSE-007` | Published Ports | Explicit, loopback, specific-interface, all-interface, IPv6, and long-syntax bindings are distinguished from `expose`; single-token ports and long syntax without `published` produce a dynamic-publication `WARNING`; malformed declarations are `NOT_CHECKED`; no public reachability is inferred. | `HIGH` for broad database/admin ports, `MEDIUM` for other broad or dynamic bindings, `LOW` on `NOT_CHECKED`, `INFO` otherwise |
| `COMPOSE-008` | Healthcheck Configuration | Valid non-trivial `CMD` and `CMD-SHELL` tests pass; missing tests warn; disabled, scalar or list `NONE`, empty, and malformed tests fail; unresolved disable values, variables, and unsupported forms are `NOT_CHECKED`; trivially successful commands warn; image inheritance is disclosed. | `HIGH` on disabled/`NONE`, `MEDIUM` on malformed or trivial commands, `LOW` on other warnings and `NOT_CHECKED`, `INFO` otherwise |
| `COMPOSE-009` | Restart Policy | `no`, `always`, `unless-stopped`, `on-failure`, and `on-failure:<limit>` pass; missing or unrecognized policies warn; variables are `NOT_CHECKED`; `always` is not inherently rejected. | `LOW` on warning or `NOT_CHECKED`, `INFO` otherwise |
| `COMPOSE-010` | Risky Volume Mounts | Host root and sensitive system directories fail or warn by context; named, anonymous, read-only, and ordinary project binds pass with an explicit finding when all evaluated mounts are safe; variables and malformed declarations are `NOT_CHECKED`; Docker socket findings remain in `COMPOSE-005`. | `HIGH` on host root, `MEDIUM` on other risky binds, `LOW` on `NOT_CHECKED`, `INFO` otherwise |

Supporting findings are reported separately as `COMPOSE.SUPPORT.FILE_NAME`, `COMPOSE.SUPPORT.SAFE_YAML`, `COMPOSE.SUPPORT.DOCUMENT_COUNT`, `COMPOSE.SUPPORT.IMAGE_OR_BUILD`, and `COMPOSE.SUPPORT.ENVIRONMENT_SECRETS`. Parser and missing-input conditions use `COMPOSE.PARSE_ERROR`, `COMPOSE.NOT_CHECKED`, and `COMPOSE.ERROR`. The parser rejects unsafe YAML tags and applies alias, nesting-depth, and document-size limits. Raw parser exception messages, environment values, and environment fallback literals are not included in API responses.

**Task 3.1 Traceability:**

| Requirement | Implementation method | Primary test | Demonstration fixture | Remaining limitation |
|---|---|---|---|---|
| `COMPOSE-001` structure validation | Parsed `services` mapping check | `missingServicesFailsInsteadOfPassing` | Both fixtures | Override files are not merged. |
| `COMPOSE-002` image version policy | Static image/tag/digest classification | `imageTagsDistinguishDefaultLatestExplicitLatestDigestAndBuilds` | Fixture B `db`, Fixture A | No registry lookup or immutability proof. |
| `COMPOSE-003` privileged containers | Boolean and variable classification | `findingsIncludeRuleMetadataAndSafeEvidence` | Fixture B `api` | Runtime capability behavior is not inspected. |
| `COMPOSE-004` container user | Literal user/group parsing with root variants | `rootUserVariantsProduceFindings` | Fixture B `db`, Fixture A | Image-level `USER` inheritance is not resolved. |
| `COMPOSE-005` Docker socket access | Short/long volume source matching | `dockerSocketMountsInShortAndLongSyntaxAreDetected` | Fixture B `api` | Daemon exploitability is not claimed. |
| `COMPOSE-006` host network isolation | `network_mode`, `pid`, and `ipc` checks | `hostNetworkModeIsContextualWarningAndNamespacesAreSeparate` | Fixture B `api`, `worker` | Actual host namespace permissions are not inspected. |
| `COMPOSE-007` published ports | Short/long binding and database-port classification | `publishedPortsDistinguishBindings` | Fixture B | Public reachability is not inferred without network/firewall evidence. |
| `COMPOSE-008` healthcheck configuration | Healthcheck block and test-form classification | `missingAndDisabledHealthchecksAreDistinguished` | Fixture B `worker` | Runtime success and image inheritance are not verified. |
| `COMPOSE-009` restart policy | Literal policy and YAML boolean normalization | `validRestartPoliciesPassAndAlwaysIsNotInherentlyRejected` | Fixture B `worker` | Platform restart outcomes are not evaluated. |
| `COMPOSE-010` risky volume mounts | Short/long volume, bind, named, read-only, and variable classification | `riskyVolumeMountsDistinguishBindsVolumesAndVariables` | Fixture B `api`, Fixture A `db` | Host paths and mounted contents are not resolved. |

**Task 3.2 Regression Coverage:**

| Correction | Primary test |
|---|---|
| Socket-like variable mounts are `NOT_CHECKED` | `variableSocketLikeMountsAreNotCheckedAndConfirmedSocketsStillFail` |
| Dynamic and explicit port bindings, plus `expose` | `publishedPortsDistinguishBindingsDynamicPortsAndExpose`, `singleTokenDatabasePortIsDynamicPublicationNotContainerOnly`, `malformedPortDeclarationsAreNotChecked` |
| Healthcheck `NONE` lists, unresolved disable, and trivial commands | `missingAndDisabledHealthchecksAreDistinguished`, `triviallySuccessfulHealthcheckCommandsAreWarned` |
| Safe and malformed volume findings | `allSafeVolumeMountsStillEmitPassFinding`, `malformedVolumeDeclarationsAreNotChecked` |
| Environment fallback defaults | `hardcodedAndReferencedCredentialsAreDistinguished` |

**Response Fields:**

| Field | Type | Description |
|---|---|---|
| `source` | string | The input source. Currently `MULTIPART_FILES`. |
| `projectName` | string | The submitted or default project name. |
| `overallStatus` | string | Aggregate status using the precedence defined in Section 3. |
| `coverageComplete` | boolean | `true` only when every declared module was implemented and no module contains an `ERROR` or `NOT_CHECKED` finding. |
| `implementedModules` | array | Module names with a registered analyzer in the current pipeline. |
| `incompleteModules` | array | Module names containing at least one `ERROR` or `NOT_CHECKED` finding. |
| `notCheckedModules` | array | Module names whose aggregate status is `NOT_CHECKED`. |
| `findings` | array | Standardized findings from analyzers and input classification. |
| `modules` | array | Module name, aggregate status, and finding count for each declared module. |
| `inventory` | array | Path, type, size, text status, analyzed flag, and `analysisStatus` (`ANALYZED`, `PARSE_ERROR`, `UNSUPPORTED`, or `NOT_ANALYZED`) for each uploaded file. |
| `limitations` | array | Explicit statements about current coverage and unsupported input. |

**Example Response:**

```json
{
  "source": "MULTIPART_FILES",
  "projectName": "demo",
  "overallStatus": "WARNING",
  "coverageComplete": false,
  "implementedModules": ["DOCKERFILE", "COMPOSE"],
  "incompleteModules": ["COMPOSE", "SECRETS", "ENVIRONMENT", "IGNORE", "SPRING_CONFIG", "DEPENDENCIES", "HEALTH_RELIABILITY", "CI_CD", "VULNERABILITIES"],
  "notCheckedModules": ["COMPOSE", "SECRETS", "ENVIRONMENT", "IGNORE", "SPRING_CONFIG", "DEPENDENCIES", "HEALTH_RELIABILITY", "CI_CD", "VULNERABILITIES"],
  "findings": [
    {
      "ruleId": "DOCKER-001",
      "category": "DOCKERFILE",
      "name": "FROM Instruction",
      "status": "PASS",
      "severity": "INFO",
      "explanation": "A FROM instruction with a readable base reference was found.",
      "suggestedAction": "No action required for this structural rule.",
      "recommendation": "No action required for this structural rule.",
      "filePath": "Dockerfile",
      "lineNumber": 1,
      "maskedEvidence": "FROM [line 1]",
      "limitations": ["Dockerfile instructions are parsed as text; no image build, container execution, shell command, or network lookup is performed."]
    },
    {
      "ruleId": "COMPOSE.NOT_CHECKED",
      "category": "COMPOSE",
      "name": "Compose Analysis",
      "status": "NOT_CHECKED",
      "severity": "LOW",
      "explanation": "No recognized Compose file was found in the uploaded project files.",
      "suggestedAction": "Upload a compose.yaml, compose.yml, docker-compose.yaml, or docker-compose.yml file.",
      "recommendation": "Upload a compose.yaml, compose.yml, docker-compose.yaml, or docker-compose.yml file.",
      "filePath": null,
      "lineNumber": null,
      "maskedEvidence": null,
      "limitations": ["Compose files are analyzed as YAML data and are never executed with Docker Compose."]
    }
  ],
  "modules": [
    {
      "module": "DOCKERFILE",
      "status": "WARNING",
      "findingCount": 8
    },
    {
      "module": "COMPOSE",
      "status": "NOT_CHECKED",
      "findingCount": 1
    }
  ],
  "inventory": [
    {
      "path": "Dockerfile",
      "type": "DOCKERFILE",
      "sizeBytes": 20,
      "textContent": true,
      "analyzed": true,
      "analysisStatus": "ANALYZED"
    }
  ],
  "limitations": [
    "Servlet multipart may spool uploaded parts to temporary storage; the application reads bytes only for the request and does not persist uploaded files.",
    "Only Dockerfile and Docker Compose text rules are currently implemented in the shared project pipeline.",
    "ZIP input and GitHub repository input are not implemented.",
    "Uploaded files are not executed, built, or sent to external services."
  ]
}
```

The abbreviated example shows field shape only and is not an observed response; a real response includes all declared modules and their findings.

**Status Codes:**

- `200 OK`: The uploaded project was accepted and analyzed.
- `400 Bad Request`: No files were supplied, a limit was exceeded, a path was unsafe, a file was unreadable, or the request was malformed.

**Security Requirement:** The application reads uploaded bytes only for the request and does not persist uploaded files. The servlet container may spool multipart parts to temporary storage before application code reads them, so the API does not claim that upload handling is exclusively memory-based. The backend does not write project files to its own storage, execute them, build images, invoke Docker Compose or other container runtimes, or send them to external services. Compose YAML is parsed as data only with restricted tags and parser limits.

## 3. Result Statuses

The implemented APIs use the following uppercase status values:

- **PASS:** The check passed.
- **WARNING:** A potential issue or improvement opportunity was identified.
- **FAIL:** The check failed or a significant issue was detected.
- **NOT_CHECKED:** A rule exists conceptually but was not run.
- **NOT_APPLICABLE:** The rule was not relevant to the submitted input.
- **ERROR:** Input could not be analyzed safely or correctly.

Each finding includes a check name, a status, a rule-specific severity, and an explanatory message. A suggested action and recommendation are included when applicable. Severity is independent from status; an unresolved rule can be `NOT_CHECKED` with `LOW` severity, and a structural failure can be `FAIL` with `HIGH` severity. The website endpoint continues to return only `PASS`, `WARNING`, and `FAIL`; the standalone Dockerfile endpoint can also return `NOT_CHECKED`.

The `overallStatus` is computed from the findings in this precedence order:

1. `ERROR` if any finding has status `ERROR`.
2. `FAIL` if there are no `ERROR` findings but at least one `FAIL`.
3. `WARNING` if there are no `ERROR` or `FAIL` findings but at least one `WARNING`.
4. `NOT_CHECKED` if there are no higher-priority findings but at least one rule was not checked or input could not be evaluated.
5. `PASS` if every evaluated rule passed and there are no `NOT_CHECKED` findings.
6. `NOT_APPLICABLE` if no actionable finding exists and all findings are not applicable.

An empty finding set also produces `NOT_CHECKED`. An overall `PASS` therefore does not hide incomplete analysis coverage.

## 4. Error Handling

The implemented API returns JSON error responses in the following form:

```json
{
  "error": "Host not allowed"
}
```

Validation errors, blocked destinations, timeouts, and request failures return HTTP `400 Bad Request`. Unexpected exceptions are also returned as HTTP `400` with the generic message `Request failed`.

Current error messages include:

| Situation | Example Error Message |
|---|---|
| Missing or blank URL | `URL is required` |
| Malformed URL | `Malformed URL` |
| Missing or invalid host in URL | `Invalid host` |
| Missing or unsupported scheme | `Unsupported scheme` |
| Credentials in URL | `Credentials not allowed in URL` |
| Blocked host or resolved address | `Host not allowed` |
| Request timeout | `Request timed out` |
| Network or request failure | `Request failed` |
| Redirect loop | `Redirect loop detected` |
| More than five redirects | `Too many redirects` |
| Invalid redirect location | `Invalid redirect location` |
| Missing or blank Dockerfile content | `Dockerfile content is required` |
| Dockerfile content over 100,000 characters | `Dockerfile content is too large` |
| No uploaded files | `At least one file is required` |
| More than 200 uploaded files | `Too many uploaded files` |
| Duplicate uploaded file path | `Duplicate uploaded file path` |
| Unsafe uploaded file path | `Unsafe file path` |
| Uploaded file over 512,000 bytes | `Uploaded file is too large` |
| Uploaded files over 5,000,000 bytes in total | `Uploaded files are too large in total` |

Example request for a blocked destination:

```json
{
  "url": "http://127.0.0.1/"
}
```

Example response:

```json
{
  "error": "Host not allowed"
}
```

The error handler avoids exposing internal implementation details. Expected errors do not crash the backend. Dockerfile validation errors use the same JSON error shape.

## 5. Security Considerations

- Validate URLs before sending requests.
- Revalidate redirect destinations.
- Block localhost, private IP addresses, and internal network destinations.
- Apply connection and request timeouts.
- Analyze Dockerfile content as text and never execute it.
- Limit Dockerfile content to 100,000 characters.
- Limit multipart uploads by file count, per-file size, total size, path length, and project name length.
- Configure Spring multipart limits explicitly and set `file-size-threshold=0B` so the application does not add application-level disk spooling for small parts.
- Normalize and validate uploaded paths before use; reject absolute paths, drive-letter paths, traversal segments, and control characters.
- Read uploaded bytes only for the request and do not persist project files; acknowledge that the servlet container may spool multipart parts to temporary storage.
- Return evidence only as `KEYWORD [line N]`; never return instruction arguments, URLs, credentials, environment values, JSON secrets, or shell command text.
- Do not invoke Docker, containers, shell commands, external services, or AI services for Dockerfile analysis.
- Avoid exposing internal implementation details in error messages.
- DNS validation does not guarantee that the destination IP remains pinned at connection time. Complete protection against DNS rebinding is therefore not claimed.

## 6. Scope and Limitations

The implemented API supports the health check, the website readiness check, rule-based Dockerfile static analysis, and shared multipart project analysis with Dockerfile and Compose analyzers. Dockerfile analysis does not build an image, run a container, execute instructions, check registry state, parse `.dockerignore` rules, or perform a complete security audit. Compose analysis parses each file independently, does not merge override files, and does not resolve variables, `env_file` contents, registry state, or host paths. The API does not provide penetration testing, continuous monitoring, Kubernetes analysis, automatic remediation, or a guarantee of complete security.

The frontend displays the complete website-check response and provides a separate Dockerfile analysis view. The standalone Dockerfile view renders standardized findings generically and can display the eight current rule results. The multipart project analysis endpoint is backend-only in this implementation step; no project upload UI is provided yet.

## 7. Implementation Status

**Implemented:**

- `GET /api/health`
- `POST /api/checks/website`
- `POST /api/checks/dockerfile`
- `POST /api/projects/analyze/files`
- Shared `ProjectContext`, standardized `Finding`, `FindingStatus`, `FindingSeverity`, and `ProjectAnalyzer` pipeline
- Explicit aggregate status precedence with `PASS`, `WARNING`, `FAIL`, `NOT_CHECKED`, `NOT_APPLICABLE`, and `ERROR`
- Rule-specific severity values `INFO`, `LOW`, `MEDIUM`, and `HIGH`
- Multipart input limits, explicit Spring multipart configuration, path validation, and file classification
- Analyzer failure isolation with non-sensitive `ERROR` findings
- Explicit coverage reporting through `coverageComplete`, `implementedModules`, `incompleteModules`, and `notCheckedModules`
- URL validation and SSRF protection
- Redirect target revalidation
- Reachability and HTTP status reporting
- Response-time measurement
- HTTPS detection
- Security-header inspection
- Static Dockerfile parsing with comments, continuations, CRLF input, case-insensitive instructions, and final-stage-chain inheritance
- Eight Dockerfile rules: `FROM`, base image tag, final-stage user, working directory, startup command, `COPY`/`ADD`, dangerous permissions, and unsupported syntax
- Restricted SnakeYAML parsing with safe tags, duplicate-key rejection, alias limits, nesting-depth limits, and document-size limits
- Ten production-readiness Compose rules: structure, image version policy, privileged containers, container user, Docker socket access, host network isolation, published ports, healthcheck configuration, restart policy, and risky volume mounts, with supporting parser, image/build, and narrow environment-secret findings
- Secret-safe evidence that never includes instruction arguments or environment values

The website-check, Dockerfile-analysis, and project-analysis endpoints are implemented in the backend and covered by automated backend tests.

**Planned, not implemented:**

- A versioned `/api/v1` endpoint; the implemented endpoints are `/api/checks/website`, `/api/checks/dockerfile`, and `/api/projects/analyze/files`
- ZIP and GitHub repository input
- Analyzers for dependencies, secrets, environment configuration, ignore files, production Spring configuration, health/reliability beyond Dockerfile rules, CI, and vulnerability feeds
- A project upload UI in the frontend
- `.dockerignore` rule parsing and registry/signature verification
- Broader frontend and end-to-end automated testing

AWS deployment and other future work are outside the scope of this API document and remain planned.
