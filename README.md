# ShipShape: Pre-Deployment Readiness Checker

## Project Description

Preparing a web application for deployment involves several checks, such as verifying website accessibility, HTTPS usage, security headers, and Docker configuration. Developers may overlook some of these issues before publishing their applications. ShipShape aims to make these basic checks easier to perform in one place.

## Objective

The objective of ShipShape is to develop a web-based tool that helps developers identify potential issues before deploying their applications. The system will check a given website URL and analyze Docker configuration files, presenting the results with explanations and possible solutions.

## Target Users

- Software engineering students
- Individual developers
- Small development teams

## Current Status

**Local prototype with website readiness, Dockerfile analysis, Compose analysis, and shared backend project analysis**

ShipShape now has a working local prototype with a React and TypeScript frontend connected to a Java 17 and Spring Boot backend. The interface provides two checks:

- **Website Check:** submit a public URL, then review reachability, HTTP status, response time, HTTPS usage, security headers, the overall readiness status, and individual findings.
- **Dockerfile Analysis:** paste Dockerfile content, then review rule-based findings for base images, final-stage users, working directories, startup commands, file-transfer instructions, dangerous permissions, health checks, and unsupported syntax.

The Dockerfile endpoint is `POST /api/checks/dockerfile`. Dockerfile content is analyzed as text and is never executed. Website checks continue to use `POST /api/checks/website`. The backend-only project endpoint is `POST /api/projects/analyze/files`; it applies the Dockerfile and Compose rule sets to uploaded project files.

The shared project pipeline reports `PASS`, `WARNING`, `FAIL`, `NOT_CHECKED`, `NOT_APPLICABLE`, and `ERROR`, with rule-specific severities and explicit coverage fields. It separates `implementedModules`, `incompleteModules`, and `notCheckedModules`; an overall `PASS` cannot hide incomplete coverage, and a missing Dockerfile or Compose file is reported as `NOT_CHECKED` rather than treated as a pass.

Verification for the current implementation recorded:

- The backend test suite completed with **157 passing tests** (0 failures, 0 errors, 0 skipped) under Java 17.
- The frontend production build completed successfully with `npm run build`.

The application currently runs locally only. It has not been deployed publicly.

## Technologies

### Implemented

- **Frontend:** React 19, TypeScript, and Vite 8
- **Backend:** Java 17 and Spring Boot 4.1.2
- **Build tools:** Maven for the backend and npm/Vite for the frontend
- **Version control:** Git and GitHub

### Planned

- **Deployment:** AWS. The application has not been deployed to AWS yet.

## Architecture and API Design

The project design documents are available in the repository:

- [Component design](architecture/component-design.md)
- [Architecture diagram (Mermaid)](architecture/shipshape-architecture.mmd)
- [Architecture diagram (PNG)](architecture/shipshape-architecture.png)
- [API design](api/api-design.md)
- [AWS service selection](architecture/aws-service-selection.md)

The implemented endpoints are `GET /api/health`, `POST /api/checks/website`, `POST /api/checks/dockerfile`, and `POST /api/projects/analyze/files`, as documented in [api-design.md](api/api-design.md).

## Project Structure

```text
ShipShape/
├── backend/        Java 17 + Spring Boot REST API
├── frontend/       React + TypeScript + Vite application
├── api/            API design documentation
├── architecture/   Architecture and AWS service selection documents
└── report/         Weekly academic reports
```

## Implemented Functionality

### FR-01: Website URL Submission, Reachability, and Readiness

- `frontend/src/components/UrlCheckForm.tsx` provides the URL input and submit action.
- `frontend/src/App.tsx` manages the selected check, loading, error, and result state.
- `frontend/src/api/websiteCheck.ts` sends the request to `POST /api/checks/website`.
- `frontend/src/components/CheckResult.tsx` displays the submitted URL, reachability, HTTP status, response time, HTTPS usage, security headers, overall status, and findings.
- `backend/src/main/java/com/shipshape/backend/controller/WebsiteCheckController.java` exposes the endpoint.
- `backend/src/main/java/com/shipshape/backend/service/WebsiteCheckService.java` performs the HTTP request and determines reachability, status, and readiness findings.
- `backend/src/main/java/com/shipshape/backend/util/UrlValidator.java` validates the submitted URL before any request is made.

### Dockerfile Static Analysis

- `frontend/src/components/DockerfileCheckForm.tsx` provides the Dockerfile textarea and analyze action.
- `frontend/src/components/DockerfileResult.tsx` displays the overall status and individual findings.
- `frontend/src/api/dockerfileAnalysis.ts` sends the request to `POST /api/checks/dockerfile`.
- `backend/src/main/java/com/shipshape/backend/controller/DockerfileAnalysisController.java` exposes the endpoint.
- `backend/src/main/java/com/shipshape/backend/service/DockerfileAnalysisService.java` parses the Dockerfile as text and applies the rules.

The analyzer checks:

1. `DOCKER-001`: a readable `FROM` instruction is present. Multi-stage builds are supported through final-stage aliases.
2. `DOCKER-002`: explicit `:latest` and missing tags are warnings; variable references are `NOT_CHECKED`; digest, explicit version, `scratch`, and stage-alias references pass with limitations.
3. `DOCKER-003`: `USER root` or `USER 0` in the final-stage chain is a warning; a literal non-root user passes; missing or variable users are `NOT_CHECKED`.
4. `DOCKER-004`: an explicit `WORKDIR` in the final-stage chain passes; a missing working directory is a warning.
5. `DOCKER-005`: an explicit `CMD` or `ENTRYPOINT` in the final-stage chain passes; a missing explicit startup command is a warning.
6. `DOCKER-006`: remote `ADD`, broad `COPY` without `.dockerignore`, and archive `ADD` produce warnings; plain `COPY` passes. `.dockerignore` rules are not parsed.
7. `DOCKER-007`: explicit broad permission patterns such as `chmod 777` or `COPY --chmod=0777` produce warnings.
8. `DOCKER.SYNTAX`: unsupported instruction keywords are reported as `NOT_CHECKED` instead of being silently treated as valid.

Comments, blank lines, leading whitespace, case-insensitive instruction names, CRLF line endings, and backslash line continuations are handled. Evidence contains only `KEYWORD [line N]`; instruction arguments and secrets are never returned. The maximum accepted content length is 100,000 characters.

### Docker Compose Static Analysis

Compose analysis is implemented in the shared multipart project pipeline. It accepts `compose.yaml`, `compose.yml`, `docker-compose.yaml`, and `docker-compose.yml`, then parses each Compose file as data with a restricted SnakeYAML parser. No Compose command, container runtime, shell command, network lookup, or host file resolution is performed. Findings are per service where applicable and include a sanitized `serviceIdentifier` field.

The ten required production-readiness checks are:

1. `COMPOSE-001`: Compose structure validation with a non-empty, string-keyed `services` mapping.
2. `COMPOSE-002`: image version policy; untagged images and explicit `latest` are warnings, digests and explicit version tags pass, and variables or build-only services are `NOT_CHECKED`.
3. `COMPOSE-003`: privileged containers; explicit `true` fails, explicit `false` passes, and unresolved values are `NOT_CHECKED`.
4. `COMPOSE-004`: container user; root forms fail, explicit non-root users pass, and missing or variable users are `NOT_CHECKED`.
5. `COMPOSE-005`: Docker socket access through short and long volume syntax, including read-only mounts; variable paths that could resolve to a socket are `NOT_CHECKED` rather than an unconditional pass.
6. `COMPOSE-006`: host network isolation; host network, PID, and IPC usage are separate contextual findings rather than a blanket failure.
7. `COMPOSE-007`: published ports; explicit bindings, dynamically assigned host ports, `expose`, IPv6, malformed declarations, and identifiable database/administrative ports are distinguished.
8. `COMPOSE-008`: healthcheck configuration; missing, disabled, `NONE` scalar or list, unresolved disable, malformed, trivial always-successful, inherited, and variable forms are distinguished without claiming runtime success.
9. `COMPOSE-009`: restart policy; supported policies pass, missing or unrecognized values produce contextual warnings, and `always` is not treated as inherently unsuitable.
10. `COMPOSE-010`: risky volume mounts; host root, sensitive directories, named, anonymous, read-only, variable, malformed, and Docker socket mounts are distinguished, with an explicit pass when all evaluated mounts are safe.

Supporting checks are reported separately: `COMPOSE.SUPPORT.FILE_NAME`, `COMPOSE.SUPPORT.SAFE_YAML`, `COMPOSE.SUPPORT.DOCUMENT_COUNT`, `COMPOSE.SUPPORT.IMAGE_OR_BUILD`, and `COMPOSE.SUPPORT.ENVIRONMENT_SECRETS`. Unsafe YAML tags, duplicate keys, recursive keys, excessive aliases, excessive nesting, blank content, and content over 200,000 characters produce `COMPOSE.PARSE_ERROR` without raw parser messages. Environment references with literal fallback defaults fail the narrow secret check; references without literal fallbacks, inheritance, and empty values pass. Environment values and fallback literals are never returned as evidence.

Demonstration fixtures are in `backend/src/test/resources/compose-analysis/`: `fixture-a/compose.yaml` is the corrected comparable configuration and `fixture-b/docker-compose.yml` contains multiple deliberately risky services. Detailed evidence and remaining limitations are documented in that directory's README.

| Requirement | Implementation method | Primary test | Demonstration fixture | Remaining limitation |
|---|---|---|---|---|
| `COMPOSE-001` structure validation | Parsed `services` mapping check in `ComposeAnalysisService` | `missingServicesFailsInsteadOfPassing` | Both fixtures | Override files are not merged. |
| `COMPOSE-002` image version policy | Static image/tag/digest classification | `imageTagsDistinguishDefaultLatestExplicitLatestDigestAndBuilds` | Fixture B (`postgres:latest`), Fixture A (versioned images) | No registry lookup or immutability proof. |
| `COMPOSE-003` privileged containers | Boolean and variable classification | `findingsIncludeRuleMetadataAndSafeEvidence` | Fixture B `api` | Runtime capability behavior is not inspected. |
| `COMPOSE-004` container user | Literal user/group parsing with root variants | `rootUserVariantsProduceFindings` | Fixture B `db`, Fixture A (non-root users) | Image-level `USER` inheritance is not resolved. |
| `COMPOSE-005` Docker socket access | Short/long volume source matching with socket-like variable detection | `variableSocketLikeMountsAreNotCheckedAndConfirmedSocketsStillFail` | Fixture B `api` | Daemon exploitability is not claimed. |
| `COMPOSE-006` host network isolation | `network_mode`, `pid`, and `ipc` checks | `hostNetworkModeIsContextualWarningAndNamespacesAreSeparate` | Fixture B `api`, `worker` | Actual host namespace permissions are not inspected. |
| `COMPOSE-007` published ports | Explicit/dynamic port binding, `expose`, and database-port classification | `publishedPortsDistinguishBindingsDynamicPortsAndExpose`, `singleTokenDatabasePortIsDynamicPublicationNotContainerOnly` | Fixture B | Public reachability and random port allocation are not inferred. |
| `COMPOSE-008` healthcheck configuration | Healthcheck block, test form, disable, and trivial-command classification | `missingAndDisabledHealthchecksAreDistinguished`, `triviallySuccessfulHealthcheckCommandsAreWarned` | Fixture B `worker` | Runtime success and image inheritance are not verified. |
| `COMPOSE-009` restart policy | Literal policy and YAML boolean normalization | `validRestartPoliciesPassAndAlwaysIsNotInherentlyRejected` | Fixture B `worker` | Platform restart outcomes are not evaluated. |
| `COMPOSE-010` risky volume mounts | Short/long volume, bind, named, read-only, variable, and malformed classification | `riskyVolumeMountsDistinguishBindsVolumesAndVariables`, `allSafeVolumeMountsStillEmitPassFinding` | Fixture B `api`, Fixture A `db` | Host paths and mounted contents are not resolved. |

### Health Check

`GET /api/health` reports that the backend is running.

### URL Safety

Website URL checks validate user input and block requests to localhost, private and internal IP addresses, and domains that resolve to those addresses. Redirect targets are revalidated before they are followed. The implementation does not claim complete protection against DNS rebinding because DNS validation alone does not pin the destination IP at connection time.

## Planned Functionality

- Add the remaining analyzers for dependencies, secrets, environment configuration, ignore files, production Spring configuration, CI/CD, health/reliability, and vulnerability feeds.
- Complete broader frontend and end-to-end validation evidence.
- Prepare and complete AWS deployment.
- Refine the README and final project documentation.

## Project Scope

### In Scope

- Website accessibility and HTTP status checks
- HTTPS verification
- Response-time measurement
- Checks for selected HTTP security headers
- Rule-based Dockerfile analysis
- Rule-based Docker Compose static analysis
- Results with explanations and improvement suggestions
- A web interface for submitting URLs and Dockerfiles
- Deployment on AWS

### Out of Scope

- Penetration testing and advanced security analysis
- Continuous website monitoring
- Kubernetes configuration analysis
- Automatic correction of detected issues
- Execution of uploaded Dockerfiles or Compose files
- Guaranteeing the complete security of an application

For safety, Dockerfile and Compose content is analyzed as text and is never executed. Website URL checks validate user input and block requests to private or internal network addresses, including domains that resolve to these addresses and redirects to internal addresses.

## Local Development

### Prerequisites

- Java 17
- Maven 3.9 or newer
- Node.js 20.19+ or 22.12+ with npm, as required by Vite 8
- Internet access for testing public website URLs
- Ports 8080 and 5173 available

### Start the Backend

From the project root:

```bash
cd backend
mvn spring-boot:run
```

The backend starts at `http://localhost:8080`.

If Java 17 is not the default JDK on macOS, set `JAVA_HOME` before starting Maven:

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
mvn spring-boot:run
```

Verify that the backend is running:

```bash
curl http://localhost:8080/api/health
```

Expected response:

```json
{"service":"shipshape-backend","status":"UP"}
```

### Start the Frontend

In a second terminal, from the project root:

```bash
cd frontend
npm install
npm run dev
```

The frontend starts at `http://localhost:5173`.

The backend must be running before either check can succeed.

### Vite Development Proxy

The Vite development server proxies requests beginning with `/api` to the backend:

```ts
server: {
  port: 5173,
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
  },
}
```

As a result, the frontend calls `/api/checks/website` and `/api/checks/dockerfile` on port 5173, and Vite forwards the requests to the backend on port 8080. No backend CORS configuration is required for local development.

## API Endpoints

### `GET /api/health`

Returns the backend health status.

Response:

```json
{
  "service": "shipshape-backend",
  "status": "UP"
}
```

### `POST /api/checks/website`

Accepts a website URL and returns the reachability result and HTTP status code.

Request:

```bash
curl -X POST http://localhost:8080/api/checks/website \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com"}'
```

Relevant response fields:

```json
{
  "url": "https://example.com",
  "reachable": true,
  "httpStatusCode": 200
}
```

The complete response also includes `responseTimeMs`, `httpsEnabled`, `securityHeaders`, `overallStatus`, and `findings`.

If the submitted URL is blocked, the backend returns HTTP 400 with a JSON error:

```json
{
  "error": "Host not allowed"
}
```

### `POST /api/checks/dockerfile`

Accepts Dockerfile content as text and returns rule-based static analysis findings.

Request:

```bash
curl -X POST http://localhost:8080/api/checks/dockerfile \
  -H "Content-Type: application/json" \
  -d '{"content":"FROM node:latest\nUSER root\nCOPY . .\n"}'
```

Response fields:

```json
{
  "overallStatus": "WARNING",
  "findings": [
    {
      "name": "Base Image Tag",
      "status": "WARNING",
      "explanation": "At least one FROM instruction explicitly uses the :latest tag.",
      "suggestedAction": "Pin a specific base image version instead of using :latest for reproducible builds."
    }
  ]
}
```

The complete response contains eight findings in rule-catalog order: `FROM Instruction`, `Base Image Tag`, `Final Stage User`, `Working Directory`, `Startup Command`, `COPY and ADD Usage`, `Dangerous Permissions`, and `Unsupported Syntax` when applicable.

Dockerfile content is never executed. The request is rejected with HTTP 400 when the content is missing, blank, or larger than 100,000 characters.

### `POST /api/projects/analyze/files`

Accepts multipart project files and returns shared-pipeline analysis results. This endpoint is backend-only; no upload UI is provided yet.

Request:

```bash
curl -X POST http://localhost:8080/api/projects/analyze/files \
  -F "files=@Dockerfile" \
  -F "files=@compose.yaml" \
  -F "projectName=demo"
```

Abbreviated response shape (not an observed response):

```json
{
  "source": "MULTIPART_FILES",
  "projectName": "demo",
  "overallStatus": "WARNING",
  "coverageComplete": false,
  "implementedModules": ["DOCKERFILE", "COMPOSE"],
  "incompleteModules": ["SECRETS", "ENVIRONMENT", "IGNORE", "SPRING_CONFIG", "DEPENDENCIES", "HEALTH_RELIABILITY", "CI_CD", "VULNERABILITIES"],
  "notCheckedModules": ["SECRETS", "ENVIRONMENT", "IGNORE", "SPRING_CONFIG", "DEPENDENCIES", "HEALTH_RELIABILITY", "CI_CD", "VULNERABILITIES"],
  "inventory": [
    { "path": "Dockerfile", "type": "DOCKERFILE", "analyzed": true, "analysisStatus": "ANALYZED" },
    { "path": "compose.yaml", "type": "COMPOSE", "analyzed": true, "analysisStatus": "ANALYZED" }
  ]
}
```

The complete response includes standardized findings, per-module summaries, the file inventory, and limitations. Uploaded bytes are read only for the request and are not persisted by the application; the servlet container may spool multipart parts to temporary storage.

## Verification Scenarios

With both applications running, open `http://localhost:5173` and select either check.

### Scenario 1: Public Website

Submit:

```text
https://example.com
```

Expected result:

- The submitted URL is displayed.
- The reachability result is shown as reachable.
- The HTTP status code is displayed as `200`.
- Response time, HTTPS status, security headers, overall status, and findings are displayed.

### Scenario 2: Blocked Private Address

Submit:

```text
http://127.0.0.1/
```

Expected result:

- The backend rejects the request with HTTP 400.
- The frontend displays the error message `Host not allowed`.
- No request is sent to the private address.

### Scenario 3: Valid Dockerfile

Open the Dockerfile Analysis tab and submit:

```dockerfile
FROM node:20-alpine
WORKDIR /app
COPY package.json .
USER node
HEALTHCHECK CMD node -e "process.exit(0)"
CMD ["node", "server.js"]
```

Expected result:

- The overall status is `PASS`.
- The response contains eight findings; evaluated rules return `PASS`.

### Scenario 4: Dockerfile with Warnings

Submit:

```dockerfile
FROM node:latest
USER root
COPY . .
```

Expected result:

- The overall status is `WARNING`.
- The explicit `:latest` tag and root user are reported as warnings.
- The missing working directory, missing startup command, broad copy, and missing health check are reported with their rule-specific severities.
- The Dockerfile is analyzed as text and is not executed.

### Scenario 5: Compose File Through the Project Endpoint

From a directory containing the saved Compose file, submit a controlled multipart request:

```bash
curl -X POST http://localhost:8080/api/projects/analyze/files \
  -F "files=@compose.yaml" \
  -F "projectName=compose-demo"
```

For a file containing `nginx:latest`, `privileged: true`, and `DB_PASSWORD=hunter2`, the expected result is:

- The response contains findings for the Compose rule set.
- `:latest`, privileged mode, host namespaces, the secret-like environment key, and `restart: always` are reported explicitly.
- The fake environment value is not included in explanations or evidence.
- The Compose file is parsed as YAML data and is never executed with Docker Compose.

## Testing and Build Commands

### Backend Tests

From the project root:

```bash
cd backend
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn test
```

The current verification run recorded:

```text
Tests run: 117, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Frontend Build

From the project root:

```bash
cd frontend
npm run build
```

This runs the TypeScript check and the Vite production build. The current implementation builds successfully.

## Limitations

- Dockerfile analysis is static and rule-based. It does not build an image, run a container, execute Dockerfile instructions, check registry state, parse `.dockerignore` rules, or provide a complete security audit.
- The Dockerfile analyzer covers the eight documented rules and does not evaluate every Dockerfile best practice.
- Compose analysis is static and rule-based. It parses each file independently, does not merge override files, and does not resolve variables, `env_file` contents, registry state, or host paths.
- Only Dockerfile and Compose text rules are implemented in the shared project pipeline; other declared modules are reported as `NOT_CHECKED`.
- ZIP input, GitHub repository input, and project upload UI are not implemented.
- AWS deployment is not completed, and the application is not publicly deployed.
- The frontend currently relies on the Vite development proxy and requires the backend to be running locally.
- Frontend automated tests are not implemented yet.
- Complete protection against DNS rebinding is not claimed because DNS validation alone does not guarantee connection-time IP pinning.

## Development Plan

| **Week** | **Planned Work** |
|---|---|
| Week 1 | Project definition and initial research |
| Week 2 | Requirements analysis and GitHub issue planning |
| Week 3 | System architecture, API design, and project structure |
| Week 4 | First working prototype with website reachability and HTTP status checks |
| Week 5 | Website readiness details and rule-based Dockerfile static analysis |
| Week 6 | Testing, validation evidence, and integration refinements |
| Week 7 | Reproducibility and deployment preparation |
| Week 8 | Report completion, README refinement, and AWS deployment preparation |
| Week 9 | AWS deployment, final demonstration, and presentation |

The plan may be adjusted as development progresses. The Week 5 website and Dockerfile work and the Task 3 Compose analysis are implemented; later milestones remain planned work.

## GitHub Issues

The main development tasks are tracked through GitHub Issues:

- [Implement Website Accessibility and HTTPS Checks](https://github.com/ibrahim-cn/ShipShape/issues/1)
- [Add Response Time and Security Header Analysis](https://github.com/ibrahim-cn/ShipShape/issues/2)
- [Implement Rule-Based Dockerfile Analysis](https://github.com/ibrahim-cn/ShipShape/issues/3)

Issue status is maintained on GitHub and is not restated here.

## References

- [Docker Documentation – Building Best Practices](https://docs.docker.com/build/building/best-practices/)
- [OWASP Secure Headers Project](https://owasp.org/projects/secure-headers-project)
