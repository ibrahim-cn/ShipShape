# ShipShape – API Design

## 1. Overview

ShipShape uses a REST API to connect the React and TypeScript frontend with the Java Spring Boot backend. The API supports website readiness checks and static Dockerfile analysis.

The API returns structured results with Pass, Warning, or Fail statuses, explanations, and improvement suggestions where applicable.

## 2. API Endpoints

### 2.1 Website Readiness Check

**Endpoint:** `POST /api/v1/website-checks`

**Purpose:** Checks whether a submitted website is reachable and evaluates its basic readiness.

**Request Body:**

```json
{
  "url": "https://example.com"
}
```

**Processing:**
- Validate the submitted URL before making a request.
- Block localhost, private IP addresses, and internal resources.
- Check website reachability and HTTP status code.
- Measure response time.
- Check HTTPS usage.
- Inspect selected HTTP security headers.
- Handle timeouts and invalid responses gracefully.

**Example Response:**

```json
{
  "url": "https://example.com",
  "results": [
    {
      "check": "Reachability",
      "status": "Pass",
      "message": "The website responded successfully."
    },
    {
      "check": "HTTPS",
      "status": "Pass",
      "message": "The website uses HTTPS."
    }
  ]
}
```

The response is illustrative. Actual results depend on the submitted URL and the implemented checks.

### 2.2 Dockerfile Analysis

**Endpoint:** `POST /api/v1/dockerfile-analysis`

**Purpose:** Analyzes an uploaded Dockerfile using predefined static rules.

**Request:** Multipart form data containing a Dockerfile text file.

**Processing:**
- Validate the uploaded file.
- Read the Dockerfile as text.
- Apply predefined static analysis rules.
- Return findings with statuses and improvement suggestions.

**Security Requirement:** The Dockerfile must never be executed.

**Example Response:**

```json
{
  "results": [
    {
      "check": "Base Image",
      "status": "Warning",
      "message": "Review the selected base image.",
      "suggestion": "Consider using a trusted and specific base image."
    }
  ]
}
```

This response is illustrative; the check will be included only if the corresponding rule is implemented.

## 3. Result Statuses

- **Pass:** The check passed.
- **Warning:** A potential issue or improvement opportunity was identified.
- **Fail:** The check failed or a significant issue was detected.

Each result should include a check name and an explanatory message. An improvement suggestion should be included where applicable.

## 4. Error Handling

The API should handle invalid URLs, blocked destinations, unreachable websites, timeouts, invalid responses, and invalid uploaded files.

Errors should return a suitable HTTP status code and a readable error message. Expected errors must not crash the backend.

## 5. Security Considerations

- Validate URLs before sending requests.
- Revalidate redirect destinations.
- Block localhost, private IP addresses, and internal network destinations.
- Apply request timeouts.
- Never execute uploaded Dockerfiles.
- Avoid exposing internal implementation details in error messages.

## 6. Scope and Limitations

The API supports the website checks and Dockerfile analysis defined in the project requirements. It does not provide penetration testing, continuous monitoring, Kubernetes analysis, automatic remediation, or a guarantee of complete security.

## 7. Implementation Status

This document describes the planned API contract. The endpoints, response models, validation rules, and error handling will be implemented and tested during subsequent development weeks.