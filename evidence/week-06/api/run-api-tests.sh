#!/bin/bash
set -u
BASE="http://127.0.0.1:8080"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
OUT="$SCRIPT_DIR"
TMP="${TMPDIR:-/tmp}/shipshape-week06"
FIX="$ROOT/backend/src/test/resources"
mkdir -p "$TMP"

python3 - "$TMP" "$FIX" <<'PY'
import json
import pathlib
import sys
p = pathlib.Path(sys.argv[1])
fix = pathlib.Path(sys.argv[2])
urls = {
    "website-success": "https://example.com",
    "website-http": "http://example.com",
    "website-redirect": "http://github.com",
    "website-not-found": "https://example.com/does-not-exist-week06-123456789",
    "website-status-401": "https://httpbin.org/status/401",
    "website-status-403": "https://httpbin.org/status/403",
    "website-status-500": "https://httpbin.org/status/500",
    "website-https-to-http": "https://httpbin.org/redirect-to?url=http%3A%2F%2Fexample.com",
    "website-wikipedia": "https://www.wikipedia.org",
    "website-cloudflare": "https://www.cloudflare.com",
    "website-mozilla": "https://www.mozilla.org",
    "website-dns-failure": "https://this-domain-does-not-exist-week06.invalid/",
    "website-invalid": "not-a-url",
    "website-empty": "",
    "website-missing-url": None,
    "website-unsupported-scheme": "ftp://example.com",
    "website-localhost": "http://127.0.0.1:8080/api/health",
    "website-loopback-ipv6": "http://[::1]:8080/api/health",
    "website-private-ip": "http://192.168.1.1/",
    "website-metadata-ip": "http://169.254.169.254/latest/meta-data/",
    "website-whitespace": " https://example.com ",
}
for name, url in urls.items():
    body = {} if name == "website-missing-url" else {"url": url}
    (p / (name + ".json")).write_text(json.dumps(body), encoding="utf-8")
dockerfiles = {
    "dockerfile-correct": fix / "dockerfile-analysis/fixture-a/Dockerfile",
    "dockerfile-risky": fix / "dockerfile-analysis/fixture-b/Dockerfile",
}
for name, path in dockerfiles.items():
    (p / (name + ".json")).write_text(json.dumps({"content": path.read_text(encoding="utf-8")}), encoding="utf-8")
(p / "dockerfile-empty.json").write_text(json.dumps({"content": ""}), encoding="utf-8")
(p / "dockerfile-null.json").write_text(json.dumps({"content": None}), encoding="utf-8")
(p / "dockerfile-missing-content.json").write_text(json.dumps({}), encoding="utf-8")
(p / "dockerfile-boundary.json").write_text(json.dumps({"content": "FROM alpine\n".ljust(100000, "x")}), encoding="utf-8")
(p / "dockerfile-oversized.json").write_text(json.dumps({"content": "FROM alpine\n".ljust(100001, "x")}), encoding="utf-8")
(p / "notes.txt").write_text("plain text", encoding="utf-8")
(p / "evil.txt").write_text("plain text", encoding="utf-8")
(p / "absolute.txt").write_text("plain text", encoding="utf-8")
(p / "unsafe-compose.yaml").write_text("services:\n  web:\n    image: example/image:1.0\n    labels: !!python/object/apply:os.system ['echo unsafe']\n", encoding="utf-8")
(p / "malformed-compose.yaml").write_text("services:\n  web\n", encoding="utf-8")
(p / "binary.bin").write_bytes(bytes(range(256)))
(p / "invalid-utf8.txt").write_bytes(b"FROM alpine\n\xff\xfe")
(p / "oversized.txt").write_text("x" * 600001, encoding="utf-8")
PY

post_json_file() {
  name="$1"
  file="$2"
  case "$name" in dockerfile-*) endpoint="$BASE/api/checks/dockerfile" ;; *) endpoint="$BASE/api/checks/website" ;; esac
  result=$(curl -sS --max-time 25 -o "$OUT/$name.json" -w '%{http_code}|%{time_total}' -H 'Content-Type: application/json' --data-binary "@$file" "$endpoint") || true
  printf 'HTTP_STATUS=%s\nCURL_TIME_TOTAL_SECONDS=%s\n' "${result%%|*}" "${result##*|}" > "$OUT/$name.meta.txt"
}

multipart() {
  name="$1"
  shift
  result=$(curl -sS --max-time 25 -o "$OUT/$name.json" -w '%{http_code}|%{time_total}' "$@" "$BASE/api/projects/analyze/files") || true
  printf 'HTTP_STATUS=%s\nCURL_TIME_TOTAL_SECONDS=%s\n' "${result%%|*}" "${result##*|}" > "$OUT/$name.meta.txt"
}

for name in website-success website-http website-redirect website-not-found website-status-401 website-status-403 website-status-500 website-https-to-http website-wikipedia website-cloudflare website-mozilla website-dns-failure website-invalid website-empty website-missing-url website-unsupported-scheme website-localhost website-loopback-ipv6 website-private-ip website-metadata-ip website-whitespace; do
  post_json_file "$name" "$TMP/$name.json"
done

for name in dockerfile-correct dockerfile-risky dockerfile-empty dockerfile-null dockerfile-missing-content dockerfile-boundary dockerfile-oversized; do
  post_json_file "$name" "$TMP/$name.json"
done

multipart project-correct \
  -F "files=@$FIX/dockerfile-analysis/fixture-a/Dockerfile;filename=Dockerfile" \
  -F "files=@$FIX/compose-analysis/fixture-a/compose.yaml;filename=compose.yaml" \
  -F "projectName=week06-correct"
multipart project-risky \
  -F "files=@$FIX/dockerfile-analysis/fixture-b/Dockerfile;filename=Dockerfile" \
  -F "files=@$FIX/compose-analysis/fixture-b/docker-compose.yml;filename=docker-compose.yml" \
  -F "projectName=week06-risky"
multipart project-unsupported-only \
  -F "files=@$TMP/notes.txt;filename=notes.txt" \
  -F "projectName=week06-unsupported"
multipart security-path-traversal \
  -F "files=@$TMP/evil.txt;filename=../evil.txt" \
  -F "projectName=week06-path"
multipart security-absolute-path \
  -F "files=@$TMP/absolute.txt;filename=/tmp/week06-absolute.txt" \
  -F "projectName=week06-absolute"
multipart security-duplicate-normalized-paths \
  -F "files=@$FIX/dockerfile-analysis/fixture-a/Dockerfile;filename=a/../dup.txt" \
  -F "files=@$FIX/dockerfile-analysis/fixture-a/Dockerfile;filename=dup.txt" \
  -F "projectName=week06-duplicate"
multipart project-unsafe-yaml-tag \
  -F "files=@$TMP/unsafe-compose.yaml;filename=docker-compose.yml" \
  -F "projectName=week06-unsafe-yaml"
multipart project-malformed-compose \
  -F "files=@$TMP/malformed-compose.yaml;filename=compose.yaml" \
  -F "projectName=week06-malformed-compose"
multipart project-binary-file \
  -F "files=@$TMP/binary.bin;filename=binary.bin" \
  -F "projectName=week06-binary"
multipart project-invalid-utf8 \
  -F "files=@$TMP/invalid-utf8.txt;filename=Dockerfile" \
  -F "projectName=week06-invalid-utf8"
multipart boundary-oversized-upload \
  -F "files=@$TMP/oversized.txt;filename=oversized.txt" \
  -F "projectName=week06-oversized"
multipart negative-missing-files -F "projectName=week06-missing-files"

for i in 1 2; do
  result=$(curl -sS --max-time 10 -o "$OUT/reliability-health-$i.txt" -w '%{http_code}|%{time_total}' "$BASE/api/health") || true
  printf 'HTTP_STATUS=%s\nCURL_TIME_TOTAL_SECONDS=%s\n' "${result%%|*}" "${result##*|}" > "$OUT/reliability-health-$i.meta.txt"
done
