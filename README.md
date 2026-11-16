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

**Week 5 – Website Readiness Details and Dockerfile Static Analysis**

ShipShape now has a working local prototype with a React and TypeScript frontend connected to a Java 17 and Spring Boot backend. The interface provides two checks:

- **Website Check:** submit a public URL, then review reachability, HTTP status, response time, HTTPS usage, security headers, the overall readiness status, and individual findings.
- **Dockerfile Analysis:** paste Dockerfile content, then review rule-based `PASS`, `WARNING`, and `FAIL` findings for base images, user configuration, health checks, and file-transfer instructions.

The Dockerfile endpoint is `POST /api/checks/dockerfile`. Dockerfile content is analyzed as text and is never executed. Website checks continue to use `POST /api/checks/website`.

Verification for the current implementation recorded:

- The backend test suite completed with **49 passing tests** (0 failures, 0 errors, 0 skipped) under Java 17.
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

The implemented check endpoints are `POST /api/checks/website` and `POST /api/checks/dockerfile`, as documented in the API section below.

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

1. A valid `FROM` instruction is present. Multi-stage builds are supported.
2. Explicit `:latest` base image tags are reported as warnings. A missing tag is not automatically treated as `:latest`.
3. The final stage should set a non-root `USER`. Missing users and `USER root` or `USER 0` are warnings. A `USER` in an earlier build stage does not satisfy the final stage.
4. The final stage should define a `HEALTHCHECK`. Missing health checks and `HEALTHCHECK NONE` are warnings.
5. `ADD` usage is reported as a warning because it can fetch remote URLs and extract archives automatically. `COPY` is recommended where those extra behaviors are not required; `ADD` is not automatically a failure.

Comments, blank lines, leading whitespace, case-insensitive instruction names, CRLF line endings, and backslash line continuations are handled. The maximum accepted content length is 100,000 characters.

### Health Check

`GET /api/health` reports that the backend is running.

### URL Safety

Website URL checks validate user input and block requests to localhost, private and internal IP addresses, and domains that resolve to those addresses. Redirect targets are revalidated before they are followed. The implementation does not claim complete protection against DNS rebinding because DNS validation alone does not pin the destination IP at connection time.

## Planned Functionality

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
- Results with explanations and improvement suggestions
- A web interface for submitting URLs and Dockerfiles
- Deployment on AWS

### Out of Scope

- Penetration testing and advanced security analysis
- Continuous website monitoring
- Kubernetes configuration analysis
- Automatic correction of detected issues
- Execution of uploaded Dockerfiles
- Guaranteeing the complete security of an application

For safety, Dockerfile content is analyzed as text and is never executed. Website URL checks validate user input and block requests to private or internal network addresses, including domains that resolve to these addresses and redirects to internal addresses.

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

The complete response contains five findings in this order: `FROM Instruction`, `Base Image Tag`, `Final Stage User`, `Health Check`, and `COPY and ADD Usage`.

Dockerfile content is never executed. The request is rejected with HTTP 400 when the content is missing, blank, or larger than 100,000 characters.

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
COPY . .
USER node
HEALTHCHECK CMD node -e "process.exit(0)"
CMD ["node", "server.js"]
```

Expected result:

- The overall status is `PASS`.
- The response contains five findings with `PASS` status.

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
- The missing health check is reported as a warning.
- The Dockerfile is analyzed as text and is not executed.

## Testing and Build Commands

### Backend Tests

From the project root:

```bash
cd backend
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn test
```

The current verification run recorded:

```text
Tests run: 49, Failures: 0, Errors: 0, Skipped: 0
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

- Dockerfile analysis is static and rule-based. It does not build an image, run a container, execute Dockerfile instructions, or provide a complete security audit.
- The analyzer covers the five documented rules and does not evaluate every Dockerfile best practice.
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

The plan may be adjusted as development progresses. Week 5 is the current stage; later milestones remain planned work.

## GitHub Issues

The main development tasks are tracked through GitHub Issues:

- [Implement Website Accessibility and HTTPS Checks](https://github.com/ibrahim-cn/ShipShape/issues/1)
- [Add Response Time and Security Header Analysis](https://github.com/ibrahim-cn/ShipShape/issues/2)
- [Implement Rule-Based Dockerfile Analysis](https://github.com/ibrahim-cn/ShipShape/issues/3)

Issue status is maintained on GitHub and is not restated here.

## References

- [Docker Documentation – Building Best Practices](https://docs.docker.com/build/building/best-practices/)
- [OWASP Secure Headers Project](https://owasp.org/projects/secure-headers-project)
