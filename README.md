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

**Week 4 – First Working Prototype**

ShipShape now has a working local prototype with a React and TypeScript frontend connected to a Java 17 and Spring Boot backend.

The current user-facing workflow implements FR-01:

- A user can submit a public website URL through the frontend.
- The frontend sends the URL to the backend through the Vite development proxy.
- The backend checks whether the website is reachable.
- The frontend displays the submitted URL, the reachability result, and the HTTP status code returned by the server.

The backend also calculates response time, HTTPS usage, selected security headers, and Pass/Warning/Fail findings. These additional results are returned by the API but are not displayed in the current Week 4 interface.

Earlier Week 4 verification recorded the following results:

- The backend test suite completed with **21 passing tests** (0 failures, 0 errors, 0 skipped) under Java 17.
- The frontend production build completed successfully with `npm run build`.

These results were recorded during earlier verification and are not presented as a new test run for this README update.

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

The implemented website-check endpoint is `POST /api/checks/website`, as documented in the API section below.

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

### FR-01: Website URL Submission, Reachability, and HTTP Status

- `frontend/src/components/UrlCheckForm.tsx` provides the URL input and submit action.
- `frontend/src/App.tsx` manages loading, error, and result state.
- `frontend/src/api/websiteCheck.ts` sends the request to `POST /api/checks/website`.
- `frontend/src/components/CheckResult.tsx` displays the submitted URL, reachability, and HTTP status code.
- `backend/src/main/java/com/shipshape/backend/controller/WebsiteCheckController.java` exposes the endpoint.
- `backend/src/main/java/com/shipshape/backend/service/WebsiteCheckService.java` performs the HTTP request and determines reachability and status.
- `backend/src/main/java/com/shipshape/backend/util/UrlValidator.java` validates the submitted URL before any request is made.

### Health Check

`GET /api/health` reports that the backend is running.

### URL Safety

Website URL checks validate user input and block requests to localhost, private and internal IP addresses, and domains that resolve to those addresses. Redirect targets are revalidated before they are followed. The implementation does not claim complete protection against DNS rebinding because DNS validation alone does not pin the destination IP at connection time.

### Additional Backend Results

The website-check response also includes:

- Response time in milliseconds
- HTTPS usage
- Results for selected security headers
- Overall Pass/Warning/Fail status
- Individual findings with explanations and suggested actions

These values are available from the backend but are not currently displayed in the Week 4 interface.

## Planned Functionality

- Display the already-calculated response time, HTTPS result, and security-header findings in the frontend.
- Add rule-based Dockerfile static analysis. Uploaded Dockerfiles will be analyzed as text and will never be executed.
- Complete testing and validation evidence for later milestones.
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

For safety, uploaded Dockerfiles will be analyzed as text and will never be executed. Website URL checks validate user input and block requests to private or internal network addresses, including domains that resolve to these addresses and redirects to internal addresses.

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

The backend must be running before a website check can succeed.

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

As a result, the frontend calls `/api/checks/website` on port 5173, and Vite forwards the request to the backend on port 8080. No backend CORS configuration is required for local development.

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

## Week 4 Verification Scenarios

With both applications running, open `http://localhost:5173` and use the URL form.

### Scenario 1: Public Website

Submit:

```text
https://example.com
```

Expected result:

- The submitted URL is displayed.
- The reachability result is shown as reachable.
- The HTTP status code is displayed as `200`.

### Scenario 2: Blocked Private Address

Submit:

```text
http://127.0.0.1/
```

Expected result:

- The backend rejects the request with HTTP 400.
- The frontend displays the error message `Host not allowed`.
- No request is sent to the private address.

## Testing and Build Commands

### Backend Tests

From the project root:

```bash
cd backend
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn test
```

Earlier Week 4 verification recorded:

```text
Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Frontend Build

From the project root:

```bash
cd frontend
npm run build
```

This runs the TypeScript check and the Vite production build. Earlier Week 4 verification recorded a successful build.

## Limitations

- The current Week 4 user interface displays only the submitted URL, reachability, and HTTP status code.
- Response time, HTTPS usage, security headers, overall status, and findings are calculated by the backend but are not displayed in the current interface.
- Dockerfile static analysis is not implemented yet.
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
| Week 5 | HTTPS verification, response-time measurement, and security header analysis |
| Week 6 | Rule-based Dockerfile analysis, testing, and validation evidence |
| Week 7 | Frontend/backend integration and reproducibility |
| Week 8 | Report completion, README refinement, and AWS deployment preparation |
| Week 9 | AWS deployment, final demonstration, and presentation |

The plan may be adjusted as development progresses. Week 4 is the current stage; later milestones remain planned work.

## GitHub Issues

The main development tasks are tracked through GitHub Issues:

- [Implement Website Accessibility and HTTPS Checks](https://github.com/ibrahim-cn/ShipShape/issues/1)
- [Add Response Time and Security Header Analysis](https://github.com/ibrahim-cn/ShipShape/issues/2)
- [Implement Rule-Based Dockerfile Analysis](https://github.com/ibrahim-cn/ShipShape/issues/3)

Issue status is maintained on GitHub and is not restated here.

## References

- [Docker Documentation – Building Best Practices](https://docs.docker.com/build/building/best-practices/)
- [OWASP Secure Headers Project](https://owasp.org/projects/secure-headers-project)
