# ShipShape – API Design

## 1. Overview

ShipShape uses a REST API to connect the React and TypeScript frontend with the Java Spring Boot backend.

The current backend implements a health endpoint, a website readiness check, and rule-based static Dockerfile analysis. The website readiness check validates a submitted URL, checks whether the website is reachable, and returns the HTTP status code and other readiness information. Dockerfile analysis parses submitted content as text and applies predefined rules without executing the Dockerfile.

Both implemented check APIs return structured results with `PASS`, `WARNING`, or `FAIL` statuses, explanations, and suggested actions where applicable.

### Endpoint Summary

| Method | Path | Status |
|---|---|---|
| `GET` | `/api/health` | Implemented |
| `POST` | `/api/checks/website` | Implemented |
| `POST` | `/api/checks/dockerfile` | Implemented |

The implemented check endpoints do not use a version prefix. Their paths are `POST /api/checks/website` and `POST /api/checks/dockerfile`.

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
- Support multi-stage Dockerfiles by selecting the last `FROM` instruction as the start of the final stage.
- Apply the five rules below.
- Return one finding per rule and an overall status.

**Static Analysis Rules:**

1. **FROM instruction:** A `FROM` instruction must be present and name a base image. Missing or invalid `FROM` instructions produce `FAIL`.
2. **Base image tag:** An explicit `:latest` tag produces `WARNING`. A missing tag is not automatically treated as `:latest`.
3. **Final-stage user:** A missing `USER`, `USER root`, or `USER 0` in the final stage produces `WARNING`. A `USER` in an earlier build stage does not satisfy this rule.
4. **Final-stage health check:** A missing `HEALTHCHECK` or `HEALTHCHECK NONE` in the final stage produces `WARNING`.
5. **COPY and ADD usage:** `COPY` passes this rule. Any `ADD` produces `WARNING`, but not `FAIL`, because `ADD` can fetch remote URLs and extract archives automatically. A Dockerfile with no `COPY` or `ADD` instructions passes this rule.

**Response Fields:**

| Field | Type | Description |
|---|---|---|
| `overallStatus` | string | The combined result: `PASS`, `WARNING`, or `FAIL`. |
| `findings` | array | Five findings using the same `name`, `status`, `explanation`, and `suggestedAction` fields defined for website checks. |

The finding names are returned in this order: `FROM Instruction`, `Base Image Tag`, `Final Stage User`, `Health Check`, and `COPY and ADD Usage`.

**Example Response:**

```json
{
  "overallStatus": "WARNING",
  "findings": [
    {
      "name": "FROM Instruction",
      "status": "PASS",
      "explanation": "The Dockerfile contains a valid FROM instruction.",
      "suggestedAction": "No action required."
    },
    {
      "name": "Base Image Tag",
      "status": "WARNING",
      "explanation": "At least one FROM instruction explicitly uses the :latest tag.",
      "suggestedAction": "Pin a specific base image version instead of using :latest for reproducible builds."
    },
    {
      "name": "Final Stage User",
      "status": "WARNING",
      "explanation": "The final stage explicitly runs as root through USER root or USER 0.",
      "suggestedAction": "Create and use a non-root user in the final stage."
    },
    {
      "name": "Health Check",
      "status": "WARNING",
      "explanation": "No HEALTHCHECK instruction was found in the final stage.",
      "suggestedAction": "Add a HEALTHCHECK CMD instruction so container health can be monitored."
    },
    {
      "name": "COPY and ADD Usage",
      "status": "PASS",
      "explanation": "The Dockerfile uses COPY for file and directory transfers.",
      "suggestedAction": "No action required."
    }
  ]
}
```

**Status Codes:**

- `200 OK`: The Dockerfile text was parsed and analyzed.
- `400 Bad Request`: The content is missing, blank, or larger than 100,000 characters.

**Security Requirement:** Dockerfile content is analyzed as text. It is never executed, and the backend does not invoke Docker, containers, shell commands, external services, or AI services for this analysis.

## 3. Result Statuses

Both implemented check APIs use the following uppercase status values:

- **PASS:** The check passed.
- **WARNING:** A potential issue or improvement opportunity was identified.
- **FAIL:** The check failed or a significant issue was detected.

Each finding includes a check name, a status, and an explanatory message. A suggested action is included when applicable.

The `overallStatus` is computed from the findings:

- `FAIL` if any finding has status `FAIL`.
- `WARNING` if there are no `FAIL` findings but at least one `WARNING`.
- `PASS` if all findings have status `PASS`.

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
- Do not invoke Docker, containers, shell commands, external services, or AI services for Dockerfile analysis.
- Avoid exposing internal implementation details in error messages.
- DNS validation does not guarantee that the destination IP remains pinned at connection time. Complete protection against DNS rebinding is therefore not claimed.

## 6. Scope and Limitations

The implemented API supports the health check, the website readiness check, and rule-based Dockerfile static analysis. Dockerfile analysis does not build an image, run a container, execute instructions, or perform a complete security audit. The API does not provide penetration testing, continuous monitoring, Kubernetes analysis, automatic remediation, or a guarantee of complete security.

The frontend displays the complete website-check response and provides a separate Dockerfile analysis view for the five implemented rules.

## 7. Implementation Status

**Implemented:**

- `GET /api/health`
- `POST /api/checks/website`
- `POST /api/checks/dockerfile`
- URL validation and SSRF protection
- Redirect target revalidation
- Reachability and HTTP status reporting
- Response-time measurement
- HTTPS detection
- Security-header inspection
- Static Dockerfile parsing with multi-stage support
- Dockerfile rules for `FROM`, explicit `:latest` tags, final-stage `USER`, final-stage `HEALTHCHECK`, and `COPY`/`ADD` usage
- `PASS`, `WARNING`, and `FAIL` findings with an overall status

The website-check and Dockerfile-analysis endpoints are implemented in the backend and covered by automated backend tests.

**Planned, not implemented:**

- A versioned `/api/v1` endpoint; the implemented endpoints are `/api/checks/website` and `/api/checks/dockerfile`
- Broader frontend and end-to-end automated testing

AWS deployment and other future work are outside the scope of this API document and remain planned.
