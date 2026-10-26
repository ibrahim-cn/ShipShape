# ShipShape – Component Design

## 1. Overview

ShipShape is a Pre-Deployment Readiness Checker that evaluates a website URL and analyzes a Dockerfile using predefined static rules. The system consists of a React and TypeScript frontend and a Java Spring Boot backend.

The system reports website and Dockerfile analysis results using Pass, Warning, and Fail statuses, together with explanations and improvement suggestions where applicable.

## 2. System Components

### 2.1 Frontend

**Technology:** React and TypeScript

The frontend provides the user interface for submitting a website URL, uploading a Dockerfile, and viewing analysis results. It displays the status of each check and provides explanations and improvement suggestions where applicable.

### 2.2 Backend REST API

**Technology:** Java and Spring Boot

The backend receives requests from the frontend and coordinates the analysis process. It routes website-check requests to the URL Safety Validator and Website Readiness Checker, and Dockerfile-analysis requests to the Dockerfile Rule Analyzer.

It also handles errors and prepares results for the frontend.

### 2.3 URL Safety Validator

This component validates submitted URLs before the system sends requests to target websites.

It checks for localhost, private and internal IP addresses, and hostnames that resolve to private or internal addresses. Redirect destinations must also be validated to prevent requests to internal resources.

URL validation must be enforced during DNS resolution and connection handling.

### 2.4 Website Readiness Checker

This component performs the website checks defined in the project requirements:

- Website reachability and HTTP status code
- Response time measurement
- HTTPS usage
- Selected HTTP security header checks

The component returns the findings to the backend for result formatting. Under normal network conditions, the website checks should complete within 10 seconds.

### 2.5 Dockerfile Rule Analyzer

This component analyzes an uploaded Dockerfile as text and checks it against predefined rules. It identifies potential issues and provides explanations or improvement suggestions where applicable.

The Dockerfile is never executed. Docker image building and container execution are outside the project scope.

### 2.6 Result Formatter

This component combines the findings from website checks and Dockerfile analysis into a consistent result structure.

Each check is presented with a Pass, Warning, or Fail status, a short explanation, and an improvement suggestion where applicable. The formatted results are returned to the frontend.

## 3. Component Interactions

1. The user submits a website URL or uploads a Dockerfile through the frontend.
2. The frontend sends the request to the backend REST API.
3. For website checks, the backend uses the URL Safety Validator before initiating website requests.
4. If the URL is accepted, the Website Readiness Checker performs the defined checks.
5. For Dockerfile analysis, the backend sends the uploaded text to the Dockerfile Rule Analyzer.
6. The Result Formatter organizes the findings into a consistent response.
7. The frontend displays the results to the user.

## 4. Error Handling and Security

The backend must handle unreachable websites, request timeouts, invalid responses, and other expected errors without crashing. Informative error messages should be returned to the frontend.

The URL Safety Validator must block requests to localhost, private IP addresses, and internal resources. Redirect destinations must be revalidated before further requests are made.

Dockerfile analysis is limited to static text inspection. The uploaded file must never be executed.

## 5. Scope and Limitations

ShipShape focuses on basic website readiness checks and predefined Dockerfile rules. It does not perform penetration testing, continuous monitoring, Kubernetes analysis, or automatic remediation. Its results do not guarantee complete website or infrastructure security.

## 6. Implementation Status

This document describes the planned system architecture and component responsibilities. The components and checks are design elements at this stage; their implementation and testing will take place during subsequent project weeks.