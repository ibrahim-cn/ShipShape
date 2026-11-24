import csv
import json
import statistics
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
API = ROOT / "api"
PERF = ROOT / "performance"
TARGET_MS = 10000


def meta(name):
    lines = (API / (name + ".meta.txt")).read_text().splitlines()
    return dict(line.split("=", 1) for line in lines if "=" in line)


def body(name):
    return json.loads((API / (name + ".json")).read_text())


all_website = [
    "website-success",
    "website-http",
    "website-redirect",
    "website-not-found",
    "website-status-401",
    "website-status-403",
    "website-status-500",
    "website-https-to-http",
    "website-wikipedia",
    "website-cloudflare",
    "website-mozilla",
    "website-dns-failure",
    "website-whitespace",
]
five_urls = [
    "website-success",
    "website-redirect",
    "website-wikipedia",
    "website-cloudflare",
    "website-mozilla",
]
local = ["dockerfile-correct", "dockerfile-risky", "project-correct", "project-risky", "project-unsupported-only"]

with (PERF / "website-performance.csv").open("w", newline="") as handle:
    writer = csv.writer(handle)
    writer.writerow(["scenario", "http_status", "curl_total_seconds", "app_total_response_time_ms", "overall_status"])
    for name in all_website:
        data = body(name)
        writer.writerow([name, meta(name)["HTTP_STATUS"], meta(name)["CURL_TIME_TOTAL_SECONDS"], data.get("responseTimeMs", ""), data.get("overallStatus", "")])

with (PERF / "local-analysis-performance.csv").open("w", newline="") as handle:
    writer = csv.writer(handle)
    writer.writerow(["scenario", "http_status", "curl_total_seconds", "overall_status"])
    for name in local:
        writer.writerow([name, meta(name)["HTTP_STATUS"], meta(name)["CURL_TIME_TOTAL_SECONDS"], body(name).get("overallStatus", "")])

values = [body(name)["responseTimeMs"] for name in five_urls]
totals = [float(meta(name)["CURL_TIME_TOTAL_SECONDS"]) for name in five_urls]
failed = [name for name in five_urls if body(name)["responseTimeMs"] > TARGET_MS]
lines = [
    "Performance summary",
    "====================",
    "",
    "NFR-01 verification used five different public URLs:",
    "- https://example.com",
    "- http://github.com",
    "- https://www.wikipedia.org",
    "- https://www.cloudflare.com",
    "- https://www.mozilla.org",
    "",
    "responseTimeMs now measures the total website check duration, including redirects.",
    "App-reported total durations (ms): " + ", ".join(str(value) for value in values),
    "min={}, max={}, mean={:.2f}, median={:.1f}".format(min(values), max(values), statistics.mean(values), statistics.median(values)),
    "Full curl total API latency (seconds): " + ", ".join("{:.3f}".format(value) for value in totals),
    "min={:.3f}, max={:.3f}, mean={:.3f}".format(min(totals), max(totals), statistics.mean(totals)),
    "Target: {} ms. Failures: {}.".format(TARGET_MS, failed or "none"),
    "Result: " + ("PASS" if not failed else "FAIL"),
    "",
    "Low-volume observations only; no load test was performed.",
]
(PERF / "performance-summary.txt").write_text("\n".join(lines) + "\n")
print("performance_result=" + ("PASS" if not failed else "FAIL") + " max_ms=" + str(max(values)))
if failed:
    raise SystemExit(1)
