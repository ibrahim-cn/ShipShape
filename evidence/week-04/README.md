# ShipShape Week 4 Evidence

This directory contains genuine environment information, test/build output, API responses, and existing browser screenshots captured for the Week 4 submission.

No screenshots, successful results, test counts, dates, or API responses were fabricated. All captured values come from the commands run in the local workspace or from files that already existed in this directory.

## Environment

The verification environment was recorded locally with these versions:

- Java 17.0.12
- Apache Maven 3.9.14, running with Java 17.0.12
- Node.js v25.8.1
- npm 11.11.0

The raw `environment.txt` capture remains available locally but is not part of the planned GitHub evidence set.

## Evidence Included in the Planned GitHub Submission

| File | What it demonstrates |
|---|---|
| `README.md` | This evidence index and verification summary |
| [backend-tests.txt](backend-tests.txt) | Complete Maven test console output |
| [frontend-build.txt](frontend-build.txt) | Complete frontend build console output |
| [succes-image.png](succes-image.png) | Existing browser screenshot named for the successful website-check scenario |
| [fail-image.png](fail-image.png) | Existing browser screenshot named for the blocked-URL/error scenario |
| [api-success.txt](api-success.txt) | Genuine successful website-check API response, HTTP status, and curl exit code |
| [api-blocked-url.txt](api-blocked-url.txt) | Genuine blocked private-address API response, HTTP status, and curl exit code |

## Additional Local Verification Artifacts

These files remain available locally for audit and reproducibility. They are not part of the planned GitHub submission and will not be staged.

| File | What it demonstrates |
|---|---|
| `environment.txt` | Java 17, Maven, Node.js, and npm versions |
| `backend-tests.exit-code.txt` | Maven test command exit code |
| `frontend-build.exit-code.txt` | Frontend build command exit code |
| `git-commits.txt` | Output of `git log -5 --oneline` |
| `ports.txt` | Local listeners on ports 8080 and 5173 before checks |
| `backend-health.txt` | Genuine `GET /api/health` response and HTTP status |
| `backend-health.exit-code.txt` | Health-check curl exit code |

The local `frontend-dist/` directory contains generated build output from the frontend build. It is ignored by Git and is not part of the submission.

## Verification Commands

### Backend tests

Run from `backend/` with Java 17:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home \
PATH="$JAVA_HOME/bin:$PATH" \
mvn test
```

Actual result: exit code `0`.

```text
Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Frontend build

Run from `frontend/`:

```bash
npm run build -- --outDir ../evidence/week-04/frontend-dist --emptyOutDir
```

Actual result: exit code `0`.

The build used the existing local `node_modules` directory. No dependency installation was performed.

```text
vite v8.3.5 building client environment for production...
✓ 19 modules transformed.
✓ built in 150ms
```

### Ports and backend health

Ports `8080` and `5173` were already occupied before any checks were attempted. No services were started.

The running backend returned:

```json
{"service":"shipshape-backend","status":"UP"}
```

HTTP status: `200`.

## Browser Screenshots

Both requested screenshot files already existed and were not renamed, moved, or edited.

- [succes-image.png](succes-image.png): PNG image, 1562 x 881 pixels.
- [fail-image.png](fail-image.png): PNG image, 1545 x 707 pixels.

The filenames and PNG formats were verified. The screenshot contents were not independently inspected in this environment. The screenshots are included as existing Week 4 UI evidence.

## API Scenario Evidence

### Successful website check

Captured in [api-success.txt](api-success.txt):

- Request URL: `http://localhost:8080/api/checks/website`
- Request body: `{"url":"https://github.com/ibrahim-cn/ShipShape/"}`
- Actual HTTP status: `200`
- Actual curl exit code: `0`
- Actual response value for `reachable`: `true`
- Actual response value for `overallStatus`: `PASS`
- Actual target `httpStatusCode`: `200`

### Blocked private address

Captured in [api-blocked-url.txt](api-blocked-url.txt):

- Request URL: `http://localhost:8080/api/checks/website`
- Request body: `{"url":"http://127.0.0.1/"}`
- Actual HTTP status: `400`
- Actual curl exit code: `0`
- Actual response body: `{"error":"Host not allowed"}`

The backend rejected the private address and did not perform the requested check successfully.

## Completed Documentation

- [Development Stage 1 (Week 4)](../../docs/development-stage-1.md)
- [Week 4 Progress Record](../../docs/week-04-progress.md)

## Remaining Missing Evidence

- No screen recording or video demonstration is present.
- No Week 4 report PDF is present in `report/`.
- No additional screenshots beyond `succes-image.png` and `fail-image.png` are present.

The two required screenshots and both required API scenario captures are present and are not marked as missing.

## Notes

- No application source, root `README.md`, or `api/api-design.md` was edited during this verification.
- No dependencies were installed.
- No commit or push was performed.
- Maven and Vite may have refreshed ignored local build artifacts while producing the outputs above.
