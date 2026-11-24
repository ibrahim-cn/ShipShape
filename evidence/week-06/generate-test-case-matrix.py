import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent


def read(path):
    return (ROOT / path).read_text()


def parse_backend():
    rows = []
    for line in read("backend/test-summary.txt").splitlines():
        match = re.match(r"(.+): tests=(\d+), failures=(\d+), errors=(\d+), skipped=(\d+), time=", line)
        if match:
            name, tests, failures, errors, skipped = match.groups()
            ok = int(failures) == 0 and int(errors) == 0 and int(skipped) == 0
            rows.append(("UNIT-{:02d}".format(len(rows) + 1), "Unit/Integration", name,
                         "failures=0, errors=0, skipped=0",
                         "tests={}, failures={}, errors={}, skipped={}".format(tests, failures, errors, skipped),
                         "PASS" if ok else "FAIL"))
    return rows


def parse_api():
    rows = []
    for line in read("api/request-summary.md").splitlines():
        if not line.startswith("|") or "---" in line or "Scenario" in line:
            continue
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if len(cells) == 7 and (cells[0].startswith("TC-") or cells[0].startswith("TC")):
            test_id, scenario, endpoint, expected, actual, http_status, result = cells
            ok = result == "PASS" and http_status in {"200", "400"}
            rows.append((test_id, "API", scenario, endpoint, expected, actual, http_status, "PASS" if ok else "FAIL"))
    return rows


def parse_browser():
    data = json.loads(read("frontend/browser-verification.json"))
    rows = []
    for index, case in enumerate(data["cases"], start=1):
        ok = case["status"] == "PASS" and not data["page_errors"]
        rows.append(("TC-UI-{:02d}".format(index), "Browser UI", case["case"], case["expected"],
                     case["actual"][:180], "PASS" if ok else "FAIL"))
    return rows


backend_rows = parse_backend()
api_rows = parse_api()
browser_rows = parse_browser()
all_ids = [row[0] for row in backend_rows + api_rows + browser_rows]

unexecuted_rows = [
    ("TC-ENV-01", "Unexecuted", "Live private-DNS result resolving to an internal address", "Requires controlled DNS", "Not executed", "UNEXECUTED"),
    ("TC-ENV-02", "Unexecuted", "Live public redirect to an internal address", "Requires controlled public redirect", "Not executed", "UNEXECUTED"),
    ("TC-ENV-03", "Unexecuted", "Pinned connection-stage address guard", "No separate guard was demonstrated", "Not executed", "UNEXECUTED"),
    ("TC-ENV-04", "Unexecuted", "Live connection timeout against a controlled slow endpoint", "No controlled slow public endpoint", "Not executed", "UNEXECUTED"),
    ("TC-FR05-01", "Unexecuted", "Frontend file picker for Dockerfile text file input", "Frontend provides a textarea, not a file picker", "Not executed", "UNEXECUTED"),
    ("TC-TASK4-01", "Unexecuted", "Task 4/Week 7 analysis modules", "Explicitly out of scope", "Not executed", "UNEXECUTED"),
]

partial_rows = [
    ("PART-NFR-02", "Partial", "NFR-02 SSRF validation", "Live literal-IP/DNS and deterministic redirect tests passed; private-DNS, public-redirect-to-internal, and pinned connection-stage cases remain unexecuted", "PARTIAL"),
    ("PART-NFR-03", "Partial", "NFR-03 timeouts and invalid responses", "Deterministic timeout and structured error responses passed; live controlled timeout not executed", "PARTIAL"),
    ("PART-FR-05", "Partial", "FR-05 Dockerfile as a text file", "JSON text input and multipart text-file upload verified at API level; frontend accepts pasted text but has no file picker; no execution path found", "PARTIAL"),
]

all_ids.extend(row[0] for row in unexecuted_rows + partial_rows)
if len(all_ids) != len(set(all_ids)):
    raise SystemExit("duplicate test IDs")

lines = [
    "# Week 6 Test Case Matrix",
    "",
    "PASS means the observed result matched the expectation. Application finding values such as WARNING, FAIL, ERROR and NOT_CHECKED are reported values, not test failures. UNEXECUTED and PARTIAL rows are not counted as PASS.",
    "",
    "This matrix is generated from `backend/test-summary.txt`, `api/request-summary.md`, and `frontend/browser-verification.json` by `generate-test-case-matrix.py`.",
    "",
    "## Automated backend tests",
    "",
    "| ID | Level | Test class / scenario | Expected | Actual | Result |",
    "|---|---|---|---|---|---|",
]
lines.extend("| {} | {} | {} | {} | {} | {} |".format(*row) for row in backend_rows)
lines.extend([
    "",
    "## Live API cases",
    "",
    "| ID | Level | Scenario | Method | Expected | Actual | HTTP | Result |",
    "|---|---|---|---|---|---|---|---|",
])
lines.extend("| {} | {} | {} | {} | {} | {} | {} | {} |".format(*row) for row in api_rows)
lines.extend([
    "",
    "## Browser UI cases",
    "",
    "| ID | Level | Scenario | Expected | Actual | Result |",
    "|---|---|---|---|---|---|",
])
lines.extend("| {} | {} | {} | {} | {} | {} |".format(*row) for row in browser_rows)
lines.extend([
    "",
    "## Unexecuted cases",
    "",
    "| ID | Level | Scenario | Reason | Actual | Result |",
    "|---|---|---|---|---|---|",
])
lines.extend("| {} | {} | {} | {} | {} | {} |".format(*row) for row in unexecuted_rows)
lines.extend([
    "",
    "## Partially validated requirements",
    "",
    "| ID | Status | Requirement | Evidence boundary | Result |",
    "|---|---|---|---|---|",
])
lines.extend("| {} | {} | {} | {} | {} |".format(*row) for row in partial_rows)
failed = [row[0] for row in backend_rows + api_rows + browser_rows if row[-1] != "PASS"]
lines.extend([
    "",
    "Counts: backend={} PASS, API={} PASS, browser={} PASS, unexecuted={}, partial={}. Assertion failures: {}.".format(
        sum(row[-1] == "PASS" for row in backend_rows),
        sum(row[-1] == "PASS" for row in api_rows),
        sum(row[-1] == "PASS" for row in browser_rows),
        len(unexecuted_rows), len(partial_rows), failed or "none"),
])
(ROOT / "test-case-matrix.md").write_text("\n".join(lines) + "\n")
(ROOT / "matrix-assertion-report.txt").write_text("unique_ids={}\nbackend={}\napi={}\nbrowser={}\nunexecuted={}\npartial={}\nassertion_failures={}\n".format(
    len(set(all_ids)) == len(all_ids), len(backend_rows), len(api_rows), len(browser_rows),
    len(unexecuted_rows), len(partial_rows), failed or "none"))
print("backend={} api={} browser={} unexecuted={} partial={} failures={}".format(
    len(backend_rows), len(api_rows), len(browser_rows), len(unexecuted_rows), len(partial_rows), failed or "none"))
if failed:
    raise SystemExit(1)
