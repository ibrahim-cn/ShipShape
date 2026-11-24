import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
API = ROOT / "api"


def meta(name):
    lines = (API / (name + ".meta.txt")).read_text().splitlines()
    return dict(line.split("=", 1) for line in lines if "=" in line)


def body(name):
    return json.loads((API / (name + ".json")).read_text())


def health_body(name):
    return (API / (name + ".txt")).read_text().strip()


def findings(data):
    return {item.get("name"): item.get("status") for item in data.get("findings", [])}


def status_counts(data):
    counts = {}
    for item in data.get("findings", []):
        key = item.get("status")
        counts[key] = counts.get(key, 0) + 1
    return ",".join("{}={}".format(key, counts[key]) for key in sorted(counts))


def module_summary(data):
    return ",".join("{}:{}".format(item["module"], item["status"]) for item in data.get("modules", []))


def actual_text(name):
    data = body(name)
    if "error" in data:
        return data["error"]
    if name.startswith("website"):
        return "overall={}, reachable={}, https={}, submitted={}, final={}, httpStatus={}, responseTimeMs={}".format(
            data.get("overallStatus"), data.get("reachable"), data.get("httpsEnabled"),
            data.get("submittedUrlScheme"), data.get("finalUrlScheme"),
            data.get("httpStatusCode"), data.get("responseTimeMs"))
    if name.startswith("dockerfile"):
        return "overall={}; findings={}".format(data.get("overallStatus"), status_counts(data))
    if "overallStatus" in data:
        return "overall={}, coverageComplete={}; {}".format(
            data.get("overallStatus"), data.get("coverageComplete"), module_summary(data))
    return str(data)


def ok_http(name, expected):
    return meta(name)["HTTP_STATUS"] == expected


def website_ok(name, status_code=None, reachable=None, https=None, submitted=None, final=None, http_status_finding=None):
    data = body(name)
    if not ok_http(name, "200"):
        return False
    if reachable is not None and data.get("reachable") is not reachable:
        return False
    if https is not None and data.get("httpsEnabled") is not https:
        return False
    if submitted is not None and data.get("submittedUrlScheme") != submitted:
        return False
    if final is not None and data.get("finalUrlScheme") != final:
        return False
    if status_code is not None and data.get("httpStatusCode") != status_code:
        return False
    if http_status_finding is not None and findings(data).get("HTTP Status") != http_status_finding:
        return False
    return True


def error_ok(name, expected_error):
    return ok_http(name, "400") and body(name).get("error") == expected_error


def overall_ok(name, expected):
    return ok_http(name, "200") and body(name).get("overallStatus") == expected


cases = [
    ("TC-API-01", "health", "GET /api/health", "200 UP", "health", lambda n: "HTTP_STATUS=200" in (API / "health.txt").read_text() and '"status":"UP"' in (API / "health.txt").read_text()),
    ("TC-API-02", "website success", "POST /api/checks/website", "200; reachable, HTTPS PASS", "website-success", lambda n: website_ok(n, 200, True, True, "https", "https") and findings(body(n)).get("Reachability") == "PASS"),
    ("TC-API-03", "direct HTTP URL", "POST /api/checks/website", "200; final HTTP reported insecure", "website-http", lambda n: website_ok(n, 200, True, False, "http", "http") and findings(body(n)).get("HTTPS") == "FAIL"),
    ("TC-API-04", "HTTP to HTTPS redirect", "POST /api/checks/website", "200; final HTTPS reported secure", "website-redirect", lambda n: website_ok(n, 200, True, True, "http", "https") and findings(body(n)).get("HTTPS") == "PASS"),
    ("TC-API-05", "404 website", "POST /api/checks/website", "200; reachable true, HTTP Status FAIL, code preserved", "website-not-found", lambda n: website_ok(n, 404, True, True, "https", "https", "FAIL")),
    ("TC-API-19", "401 website", "POST /api/checks/website", "200; reachable true, HTTP Status FAIL, code preserved", "website-status-401", lambda n: website_ok(n, 401, True, True, "https", "https", "FAIL")),
    ("TC-API-20", "403 website", "POST /api/checks/website", "200; reachable true, HTTP Status FAIL, code preserved", "website-status-403", lambda n: website_ok(n, 403, True, True, "https", "https", "FAIL")),
    ("TC-API-21", "500 website", "POST /api/checks/website", "200; reachable true, HTTP Status FAIL, code preserved", "website-status-500", lambda n: website_ok(n, 500, True, True, "https", "https", "FAIL")),
    ("TC-API-22", "HTTPS to HTTP redirect", "POST /api/checks/website", "200; final HTTP reported insecure", "website-https-to-http", lambda n: website_ok(n, 200, True, False, "https", "http") and findings(body(n)).get("HTTPS") == "FAIL"),
    ("TC-API-15", "Wikipedia URL", "POST /api/checks/website", "200; reachable and HTTPS checked", "website-wikipedia", lambda n: website_ok(n, 200, True, True, "https", "https")),
    ("TC-API-16", "Cloudflare URL", "POST /api/checks/website", "200; reachable and HTTPS checked", "website-cloudflare", lambda n: website_ok(n, 200, True, True, "https", "https")),
    ("TC-API-17", "Mozilla URL", "POST /api/checks/website", "200; reachable and HTTPS checked", "website-mozilla", lambda n: website_ok(n, 200, True, True, "https", "https")),
    ("TC-API-18", "non-resolving domain", "POST /api/checks/website", "400 Host not allowed", "website-dns-failure", lambda n: error_ok(n, "Host not allowed")),
    ("TC-API-06", "invalid URL", "POST /api/checks/website", "400 Unsupported scheme", "website-invalid", lambda n: error_ok(n, "Unsupported scheme")),
    ("TC-API-07", "empty URL", "POST /api/checks/website", "400 URL is required", "website-empty", lambda n: error_ok(n, "URL is required")),
    ("TC-API-08", "missing url field", "POST /api/checks/website", "400 URL is required", "website-missing-url", lambda n: error_ok(n, "URL is required")),
    ("TC-API-09", "unsupported FTP scheme", "POST /api/checks/website", "400 Unsupported scheme", "website-unsupported-scheme", lambda n: error_ok(n, "Unsupported scheme")),
    ("TC-API-10", "localhost block", "POST /api/checks/website", "400 Host not allowed", "website-localhost", lambda n: error_ok(n, "Host not allowed")),
    ("TC-API-11", "IPv6 loopback block", "POST /api/checks/website", "400 Host not allowed", "website-loopback-ipv6", lambda n: error_ok(n, "Host not allowed")),
    ("TC-API-12", "private IPv4 block", "POST /api/checks/website", "400 Host not allowed", "website-private-ip", lambda n: error_ok(n, "Host not allowed")),
    ("TC-API-13", "metadata IP block", "POST /api/checks/website", "400 Host not allowed", "website-metadata-ip", lambda n: error_ok(n, "Host not allowed")),
    ("TC-API-14", "whitespace URL", "POST /api/checks/website", "200; accepted and checked", "website-whitespace", lambda n: website_ok(n, 200, True, True, "https", "https")),
    ("TC-DF-01", "correct Dockerfile", "POST /api/checks/dockerfile", "200 overall PASS", "dockerfile-correct", lambda n: overall_ok(n, "PASS")),
    ("TC-DF-02", "risky Dockerfile", "POST /api/checks/dockerfile", "200 overall WARNING", "dockerfile-risky", lambda n: overall_ok(n, "WARNING")),
    ("TC-DF-03", "empty Dockerfile", "POST /api/checks/dockerfile", "400 content required", "dockerfile-empty", lambda n: error_ok(n, "Dockerfile content is required")),
    ("TC-DF-04", "null Dockerfile", "POST /api/checks/dockerfile", "400 content required", "dockerfile-null", lambda n: error_ok(n, "Dockerfile content is required")),
    ("TC-DF-05", "missing content field", "POST /api/checks/dockerfile", "400 content required", "dockerfile-missing-content", lambda n: error_ok(n, "Dockerfile content is required")),
    ("TC-DF-06", "100000-char Dockerfile", "POST /api/checks/dockerfile", "200 accepted at boundary", "dockerfile-boundary", lambda n: ok_http(n, "200")),
    ("TC-DF-07", "100001-char Dockerfile", "POST /api/checks/dockerfile", "400 too large", "dockerfile-oversized", lambda n: error_ok(n, "Dockerfile content is too large")),
    ("TC-PROJ-01", "correct project files", "POST /api/projects/analyze/files", "200 COMPOSE/DOCKERFILE PASS, overall NOT_CHECKED", "project-correct", lambda n: overall_ok(n, "NOT_CHECKED") and module_summary(body(n)).startswith("COMPOSE:PASS,DOCKERFILE:PASS") and body(n).get("coverageComplete") is False),
    ("TC-PROJ-02", "risky project files", "POST /api/projects/analyze/files", "200 overall FAIL", "project-risky", lambda n: overall_ok(n, "FAIL") and body(n).get("modules", [{}])[0].get("status") == "FAIL"),
    ("TC-PROJ-03", "unsupported file only", "POST /api/projects/analyze/files", "200 overall NOT_CHECKED", "project-unsupported-only", lambda n: overall_ok(n, "NOT_CHECKED")),
    ("TC-PROJ-04", "unsafe YAML tag", "POST /api/projects/analyze/files", "200 ERROR parser finding", "project-unsafe-yaml-tag", lambda n: overall_ok(n, "ERROR") and body(n).get("modules", [{}])[0].get("status") == "ERROR"),
    ("TC-PROJ-05", "malformed Compose YAML", "POST /api/projects/analyze/files", "200 ERROR parser finding", "project-malformed-compose", lambda n: overall_ok(n, "ERROR") and body(n).get("modules", [{}])[0].get("status") == "ERROR"),
    ("TC-PROJ-06", "binary upload", "POST /api/projects/analyze/files", "200 NOT_CHECKED", "project-binary-file", lambda n: overall_ok(n, "NOT_CHECKED")),
    ("TC-PROJ-07", "invalid UTF-8 upload", "POST /api/projects/analyze/files", "200 NOT_CHECKED", "project-invalid-utf8", lambda n: overall_ok(n, "NOT_CHECKED")),
    ("TC-PROJ-08", "600000-byte upload", "POST /api/projects/analyze/files", "400 too large", "boundary-oversized-upload", lambda n: error_ok(n, "Uploaded file is too large")),
    ("TC-PROJ-09", "missing files part", "POST /api/projects/analyze/files", "400 Request failed", "negative-missing-files", lambda n: error_ok(n, "Request failed")),
    ("TC-SEC-01", "path traversal filename", "POST /api/projects/analyze/files", "400 Unsafe file path", "security-path-traversal", lambda n: error_ok(n, "Unsafe file path")),
    ("TC-SEC-02", "absolute filename", "POST /api/projects/analyze/files", "400 Unsafe file path", "security-absolute-path", lambda n: error_ok(n, "Unsafe file path")),
    ("TC-SEC-03", "duplicate normalized paths", "POST /api/projects/analyze/files", "400 Unsafe file path", "security-duplicate-normalized-paths", lambda n: error_ok(n, "Unsafe file path")),
    ("TC-REL-01", "health after errors #1", "GET /api/health", "200 UP", "reliability-health-1", lambda n: meta(n)["HTTP_STATUS"] == "200" and '"status":"UP"' in health_body(n)),
    ("TC-REL-02", "health after errors #2", "GET /api/health", "200 UP", "reliability-health-2", lambda n: meta(n)["HTTP_STATUS"] == "200" and '"status":"UP"' in health_body(n)),
    ("TC-REL-03", "health after errors #3", "GET /api/health", "200 UP", "reliability-health-3", lambda n: meta(n)["HTTP_STATUS"] == "200" and '"status":"UP"' in health_body(n)),
]

ids = [case[0] for case in cases]
if len(ids) != len(set(ids)):
    raise SystemExit("duplicate test IDs: {}".format(sorted({item for item in ids if ids.count(item) > 1})))

rows = []
failures = []
for test_id, scenario, endpoint, expected, name, check in cases:
    verdict = "PASS" if check(name) else "FAIL"
    if verdict == "FAIL":
        failures.append(test_id)
    observed = health_body(name).replace("\n", "; ") if name == "health" or name.startswith("reliability-health-") else actual_text(name)
    http_status = "200" if name == "health" else meta(name)["HTTP_STATUS"]
    rows.append("| {} | {} | {} | {} | {} | {} | {} |".format(test_id, scenario, endpoint, expected, observed, http_status, verdict))

summary = [
    "Week 6 API request summary",
    "=========================",
    "",
    "Results are generated by assertions in api/generate-request-summary.py. Test verdict is distinct from application finding status. This table contains executed cases only; unexecuted and partially validated cases are in test-case-matrix.md.",
    "",
    "| ID | Scenario | Endpoint | Expected | Actual | HTTP | Result |",
    "|---|---|---|---|---|---|---|",
]
summary.extend(rows)
summary.extend([
    "",
    "Executed cases: {}. Assertion failures: {}.".format(len(rows), failures or "none"),
    "Response bodies: corresponding JSON/TXT files in evidence/week-06/api/; timing and status metadata: corresponding .meta.txt files.",
])
(API / "request-summary.md").write_text("\n".join(summary) + "\n")
(API / "assertion-report.txt").write_text("generated_rows={}\nfailures={}\nunique_ids={}\n".format(len(rows), failures or "none", len(set(ids)) == len(ids)))
print("rows={} failures={}".format(len(rows), failures or "none"))
if failures:
    raise SystemExit(1)
