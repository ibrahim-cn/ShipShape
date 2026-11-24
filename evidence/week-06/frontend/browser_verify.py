import json
from pathlib import Path
from playwright.sync_api import sync_playwright

OUT = Path(__file__).resolve().parent
URL = "http://127.0.0.1:5173/"
DOCKERFILE = """FROM node:20-alpine
WORKDIR /app
COPY package.json .
USER node
HEALTHCHECK CMD true
CMD ["node", "server.js"]"""
results = {"cases": [], "console_messages": [], "page_errors": []}


def record(case, status, expected, actual, screenshot=None):
    item = {"case": case, "status": status, "expected": expected, "actual": actual}
    if screenshot:
        item["screenshot"] = screenshot
    results["cases"].append(item)


with sync_playwright() as p:
    browser = p.chromium.launch(channel="chrome", headless=True)
    page = browser.new_page(viewport={"width": 1440, "height": 1000})
    page.on("console", lambda msg: results["console_messages"].append("{}: {}".format(msg.type, msg.text)))
    page.on("pageerror", lambda err: results["page_errors"].append(str(err)))
    page.goto(URL, wait_until="networkidle")
    initial = page.locator("body").inner_text()
    ok = "ShipShape" in initial and "Website Check" in initial and "Dockerfile Analysis" in initial
    record("initial_page", "PASS" if ok else "FAIL", "Brand and both check tabs", initial[:220].replace("\n", " | "), "initial-page.png")
    page.screenshot(path=str(OUT / "initial-page.png"), full_page=True)

    page.fill("#website-url", "https://example.com")
    page.get_by_role("button", name="Check Website").click()
    page.wait_for_selector(".result-card", timeout=30000)
    website_text = page.locator(".result-card").inner_text()
    lowered = website_text.lower()
    ok = (
        "readiness result" in lowered
        and "reachability" in lowered
        and "http status code" in lowered
        and "https" in lowered
        and "submitted / final scheme" in lowered
        and "https -> https" in lowered
        and "response time" in lowered
        and "findings and recommendations" in lowered
    )
    record("website_success", "PASS" if ok else "FAIL", "Reachability, HTTP status, HTTPS, submitted/final scheme, response time, headers, findings", website_text[:1000].replace("\n", " | "), "website-success.png")
    page.screenshot(path=str(OUT / "website-success.png"), full_page=True)

    page.fill("#website-url", "http://127.0.0.1:8080/api/health")
    page.get_by_role("button", name="Check Website").click()
    page.wait_for_selector(".alert-error", timeout=30000)
    error_text = page.locator(".alert-error").inner_text()
    record("website_blocked_url", "PASS" if "Host not allowed" in error_text else "FAIL", "Host not allowed alert", error_text.replace("\n", " | "), "website-blocked.png")
    page.screenshot(path=str(OUT / "website-blocked.png"), full_page=True)

    page.fill("#website-url", "")
    page.get_by_role("button", name="Check Website").click()
    page.wait_for_selector("#url-error", timeout=10000)
    field_error = page.locator("#url-error").inner_text()
    record("website_empty_validation", "PASS" if "Enter a website URL" in field_error else "FAIL", "Client-side empty URL validation", field_error)

    page.get_by_role("tab", name="Dockerfile Analysis").click()
    page.fill("#dockerfile-content", DOCKERFILE)
    page.get_by_role("button", name="Analyze Dockerfile").click()
    page.wait_for_selector(".result-card", timeout=30000)
    dockerfile_text = page.locator(".result-card").inner_text()
    ok = "Dockerfile analysis passed" in dockerfile_text and "Static text analysis only" in dockerfile_text and "Findings and Recommendations" in dockerfile_text
    record("dockerfile_success", "PASS" if ok else "FAIL", "Dockerfile PASS result and findings", dockerfile_text[:900].replace("\n", " | "), "dockerfile-success.png")
    page.screenshot(path=str(OUT / "dockerfile-success.png"), full_page=True)

    page.fill("#dockerfile-content", "")
    page.get_by_role("button", name="Analyze Dockerfile").click()
    page.wait_for_selector("#dockerfile-error", timeout=10000)
    field_error = page.locator("#dockerfile-error").inner_text()
    record("dockerfile_empty_validation", "PASS" if "Paste Dockerfile content" in field_error else "FAIL", "Client-side empty Dockerfile validation", field_error)

    page.fill("#dockerfile-content", "FROM alpine\n" + "x" * 100000)
    page.get_by_role("button", name="Analyze Dockerfile").click()
    page.wait_for_selector("#dockerfile-error", timeout=10000)
    field_error = page.locator("#dockerfile-error").inner_text()
    record("dockerfile_boundary_validation", "PASS" if "100,000 characters or fewer" in field_error else "FAIL", "Client-side 100,000-character boundary", field_error)

    page.get_by_role("tab", name="Website Check").click()
    page.set_viewport_size({"width": 390, "height": 844})
    page.reload(wait_until="networkidle")
    overflow = page.evaluate("document.documentElement.scrollWidth > window.innerWidth")
    record("mobile_layout", "FAIL" if overflow else "PASS", "No horizontal overflow at 390x844", "horizontal_overflow={}".format(overflow), "mobile-initial.png")
    page.screenshot(path=str(OUT / "mobile-initial.png"), full_page=True)
    browser.close()

(OUT / "browser-verification.json").write_text(json.dumps(results, indent=2) + "\n")
failed = [case["case"] for case in results["cases"] if case["status"] != "PASS"] + (["page_errors"] if results["page_errors"] else [])
print("cases={} console={} page_errors={} failures={}".format(len(results["cases"]), len(results["console_messages"]), len(results["page_errors"]), failed or "none"))
if failed:
    raise SystemExit(1)
