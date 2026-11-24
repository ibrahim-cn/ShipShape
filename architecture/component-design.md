# ShipShape – Component Design

## 1. Overview

ShipShape is a Pre-Deployment Readiness Checker that evaluates a website URL and analyzes Dockerfile and Docker Compose text using predefined static rules. The system consists of a React and TypeScript frontend and a Java Spring Boot backend.

The current implementation reports results with `PASS`, `WARNING`, `FAIL`, `NOT_CHECKED`, `NOT_APPLICABLE`, and `ERROR` statuses. Rule-specific severities are `INFO`, `LOW`, `MEDIUM`, and `HIGH`. Explanations, recommendations, evidence, limitations, and explicit coverage metadata are returned where applicable.

## 2. System Components

### 2.1 Frontend

**Technology:** React and TypeScript

The frontend provides the user interface for submitting a website URL, pasting Dockerfile content, and viewing analysis results. It displays the status of each check and provides explanations and improvement suggestions where applicable. The frontend currently provides no project upload interface.

### 2.2 Backend REST API

**Technology:** Java and Spring Boot

The backend receives requests from the frontend and coordinates the analysis process. It routes website-check requests to the URL Safety Validator and Website Readiness Checker, standalone Dockerfile requests to the Dockerfile Rule Analyzer, and multipart project requests to the shared project analysis pipeline.

It also handles validation errors, status aggregation, and response formatting.

### 2.3 URL Safety Validator

This component validates submitted URLs before the system sends requests to target websites.

It checks for localhost, private IP addresses, link-local addresses, and hostnames that resolve to non-public destinations. Redirect destinations are also validated to prevent requests to internal resources.

URL validation does not guarantee connection-time IP pinning, so complete protection against DNS rebinding is not claimed.

### 2.4 Website Readiness Checker

This component performs the website checks defined in the project requirements:

- Website reachability and HTTP status code
- Response time measurement
- HTTPS usage
- Selected HTTP security header checks

The component returns the findings to the backend for result formatting.

### 2.5 Project Input Service

This component accepts multipart file arrays, validates the project name and relative paths, enforces file count and size limits, decodes text as strict UTF-8, and classifies each file into a project file type.

Binary files, files containing NUL bytes, and invalid UTF-8 content are classified as `UNSUPPORTED` and are not analyzed. Uploaded bytes are read only for the request and are not persisted by the application. The servlet container may spool multipart parts to temporary storage.

### 2.6 Shared Project Analysis Pipeline

The shared pipeline builds a request-scoped `ProjectContext`, runs every registered `ProjectAnalyzer`, isolates analyzer failures as `ERROR` findings, and adds explicit `NOT_CHECKED` findings for declared modules that are not implemented. Analyzers also declare which uploaded files they analyzed, allowing inventory entries to distinguish `ANALYZED`, `PARSE_ERROR`, `UNSUPPORTED`, and `NOT_ANALYZED` states.

The response includes per-module summaries, a `coverageComplete` flag, `implementedModules`, `incompleteModules`, a `notCheckedModules` list, the file inventory, limitations, and standardized findings. An overall `PASS` is impossible when any module contains an `ERROR` or `NOT_CHECKED` finding.

### 2.7 Dockerfile Rule Analyzer

This component analyzes an uploaded Dockerfile as text and checks it against eight static rules:

1. `DOCKER-001`: readable `FROM` instruction.
2. `DOCKER-002`: base image version or digest handling.
3. `DOCKER-003`: non-root user in the final-stage chain.
4. `DOCKER-004`: explicit working directory in the final-stage chain.
5. `DOCKER-005`: explicit startup command in the final-stage chain.
6. `DOCKER-006`: `COPY` and `ADD` usage.
7. `DOCKER-007`: explicit broad permission patterns.
8. `DOCKER.SYNTAX`: unsupported instruction keywords.

The analyzer follows `FROM <earlier-stage-alias>` references to evaluate inherited configuration. It does not treat unrelated earlier stages as part of the final runtime stage. It parses comments, blank lines, leading whitespace, case-insensitive instruction names, CRLF endings, and backslash continuations.

Evidence is limited to `KEYWORD [line N]`; instruction arguments, URLs, credentials, environment values, and shell command text are never returned.

### 2.8 Docker Compose Analyzer

This component analyzes uploaded Compose files in the shared project pipeline. It recognizes `compose.yaml`, `compose.yml`, `docker-compose.yaml`, and `docker-compose.yml`, then parses each file as YAML data with a restricted SnakeYAML configuration. It emits per-service findings with a sanitized `serviceIdentifier` and applies ten required production-readiness rules:

1. `COMPOSE-001`: Compose structure validation through a non-empty string-keyed `services` mapping.
2. `COMPOSE-002`: image version policy for explicit tags, digests, untagged images, `latest`, variables, and build-only services.
3. `COMPOSE-003`: privileged container handling with literal booleans and unresolved values distinguished.
4. `COMPOSE-004`: explicit non-root user handling, including root user/group variants and `NOT_CHECKED` inheritance.
5. `COMPOSE-005`: Docker socket access detection in short and long volume syntax, including read-only mounts and socket-like variable paths.
6. `COMPOSE-006`: contextual host network, PID namespace, and IPC namespace isolation findings.
7. `COMPOSE-007`: explicit and dynamic published-port analysis with `expose` separated from `ports`, IPv4/IPv6, malformed declarations, and database/admin port distinctions.
8. `COMPOSE-008`: healthcheck declaration, test-form, disable, scalar or list `NONE`, unresolved disable, malformed, trivial-command, and variable handling.
9. `COMPOSE-009`: restart policy validation and YAML boolean normalization without treating `always` as inherently unsuitable.
10. `COMPOSE-010`: risky volume mount classification for host root, sensitive directories, named, anonymous, read-only, ordinary bind, variable, and malformed mounts, with an explicit pass when all evaluated mounts are safe.

Supporting parser and inventory checks are kept separate: `COMPOSE.SUPPORT.FILE_NAME`, `COMPOSE.SUPPORT.SAFE_YAML`, `COMPOSE.SUPPORT.DOCUMENT_COUNT`, `COMPOSE.SUPPORT.IMAGE_OR_BUILD`, and `COMPOSE.SUPPORT.ENVIRONMENT_SECRETS`. Parser failures use `COMPOSE.PARSE_ERROR`; missing input uses `COMPOSE.NOT_CHECKED`; analyzer failures use `COMPOSE.ERROR`. Unsafe tags, duplicate keys, recursive keys, excessive aliases, excessive nesting, blank content, and oversized content produce `COMPOSE.PARSE_ERROR` without raw parser details. Environment values and fallback literals are never included in explanations or evidence. No Compose command, container runtime, host file, registry, or network lookup is performed.

### 2.9 Result Formatter

This component combines findings into a consistent result structure.

Each finding includes a stable rule ID, category, name, status, severity, explanation, suggested action, recommendation, file path, line number, masked evidence, service identifier, and limitations. Aggregate status precedence is `ERROR`, `FAIL`, `WARNING`, `NOT_CHECKED`, `PASS`, then `NOT_APPLICABLE`.

## 3. Component Interactions

1. The user submits a website URL or pastes Dockerfile content through the frontend.
2. The frontend sends the request to the backend REST API.
3. For website checks, the backend uses the URL Safety Validator before initiating website requests.
4. If the URL is accepted, the Website Readiness Checker performs the defined checks.
5. For standalone Dockerfile analysis, the backend sends the submitted text to the Dockerfile Rule Analyzer.
6. For multipart project analysis, the backend uses the Project Input Service, shared pipeline, Dockerfile analyzer, and Compose analyzer where applicable.
7. The Result Formatter organizes the findings into a consistent response.
8. The frontend displays the results to the user.

## 4. Error Handling and Security

The backend handles unreachable websites, request timeouts, invalid responses, validation failures, and analyzer exceptions without crashing. Expected errors return structured JSON responses. Internal exception details are not exposed.

The URL Safety Validator blocks requests to localhost, private IP addresses, and internal resources. Redirect destinations are revalidated before further requests are made.

Multipart input is limited by file count, per-file size, total size, path length, and project name length. Paths are normalized and checked for absolute paths, drive letters, traversal segments, empty segments, and control characters.

Dockerfile analysis is limited to static text inspection. Compose analysis is limited to restricted data-only YAML parsing. Uploaded content is never executed, built, or sent to external services.

## 5. Scope and Limitations

ShipShape focuses on basic website readiness checks, rule-based Dockerfile analysis, rule-based Docker Compose analysis, and a shared backend pipeline for future analyzers. It does not perform penetration testing, continuous monitoring, Kubernetes analysis, automatic remediation, registry lookup, image signature verification, `.dockerignore` rule evaluation, Compose override merging, variable resolution, `env_file` resolution, or a complete security audit. Its results do not guarantee complete website, image, or infrastructure security.

Only Dockerfile and Compose text rules are implemented in the shared project pipeline. Other declared modules are explicitly reported as `NOT_CHECKED`; missing Dockerfile or Compose input is also reported as `NOT_CHECKED` rather than as a pass.

## 6. Implementation Status

This document describes both the current architecture and the implemented components. The website check, standalone Dockerfile check, multipart project endpoint, shared findings model, coverage reporting, Dockerfile rules, and Compose rules are implemented in the backend and covered by automated tests. ZIP input, GitHub repository input, the remaining analyzers, project upload UI, and AWS deployment remain planned.
