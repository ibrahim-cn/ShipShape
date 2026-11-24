package com.shipshape.backend.service;

import com.shipshape.backend.dto.Finding;
import com.shipshape.backend.dto.HeaderCheckResult;
import com.shipshape.backend.dto.WebsiteCheckResponse;
import com.shipshape.backend.exception.BadRequestException;
import com.shipshape.backend.util.UrlValidator;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WebsiteCheckService {

    private static final int MAX_REDIRECTS = 5;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final Pattern HSTS_MAX_AGE = Pattern.compile("(?:^|;)\\s*max-age\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private final HttpClient httpClient;
    private final DnsResolver dnsResolver;
    private final Duration requestTimeout;
    private final Duration totalDeadline;

    public interface DnsResolver {
        InetAddress[] resolve(String host) throws Exception;
    }

    public static class DefaultDnsResolver implements DnsResolver {
        @Override
        public InetAddress[] resolve(String host) throws Exception {
            return InetAddress.getAllByName(host);
        }
    }

    public WebsiteCheckService() {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(CONNECT_TIMEOUT)
                .build(), new DefaultDnsResolver(), REQUEST_TIMEOUT, Duration.ofSeconds(10));
    }

    public WebsiteCheckService(HttpClient httpClient) {
        this(httpClient, new DefaultDnsResolver(), REQUEST_TIMEOUT, Duration.ofSeconds(10));
    }

    public WebsiteCheckService(HttpClient httpClient, DnsResolver dnsResolver) {
        this(httpClient, dnsResolver, REQUEST_TIMEOUT, Duration.ofSeconds(10));
    }

    WebsiteCheckService(HttpClient httpClient, DnsResolver dnsResolver, Duration requestTimeout, Duration totalDeadline) {
        this.httpClient = httpClient;
        this.dnsResolver = dnsResolver != null ? dnsResolver : new DefaultDnsResolver();
        this.requestTimeout = requestTimeout;
        this.totalDeadline = totalDeadline;
    }

    public WebsiteCheckResponse check(String url) {
        long startTime = System.currentTimeMillis();
        WebsiteCheckResponse response = new WebsiteCheckResponse();
        response.setUrl(url);

        URI currentUri;
        try {
            currentUri = UrlValidator.validateAndParse(url);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Malformed URL");
        }

        response.setSubmittedUrlScheme(currentUri.getScheme());
        response.setFinalUrlScheme(currentUri.getScheme());
        response.setHttpsEnabled("https".equalsIgnoreCase(currentUri.getScheme()));

        boolean reachable = false;
        Integer httpStatusCode = null;
        HttpHeaders lastHeaders = null;
        Set<String> visited = new HashSet<>();

        try {
            int redirects = 0;
            while (redirects <= MAX_REDIRECTS) {
                if (visited.contains(currentUri.toString())) {
                    throw new BadRequestException("Redirect loop detected");
                }
                visited.add(currentUri.toString());

                Duration timeouts = remainingTimeout(startTime);
                validateForRequest(currentUri);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(currentUri)
                        .timeout(timeouts)
                        .method("GET", HttpRequest.BodyPublishers.noBody())
                        .header("User-Agent", "ShipShape-PreDeployment-Checker")
                        .build();

                HttpResponse<Void> httpResponse;
                try {
                    httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                } catch (HttpConnectTimeoutException ex) {
                    throw new BadRequestException("Request timed out");
                } catch (java.net.SocketTimeoutException ex) {
                    throw new BadRequestException("Request timed out");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new BadRequestException("Request failed");
                } catch (IOException ex) {
                    // Don't leak details
                    throw new BadRequestException("Request failed");
                }

                httpStatusCode = httpResponse.statusCode();
                lastHeaders = httpResponse.headers();
                reachable = true;

                // Check for redirect
                if (isRedirect(httpStatusCode)) {
                    String location = httpResponse.headers().firstValue("Location").orElse(null);
                    if (location == null || location.isEmpty()) {
                        break;
                    }
                    URI nextUri = resolveRedirect(currentUri, location);
                    // Revalidate redirect target
                    try {
                        nextUri = UrlValidator.validateAndParse(nextUri.toString());
                    } catch (BadRequestException e) {
                        throw e;
                    }
                    validateForRequest(nextUri);
                    currentUri = nextUri;
                    redirects++;
                    // continue loop to validate and request next
                    continue;
                }

                break;
            }

            if (redirects > MAX_REDIRECTS) {
                throw new BadRequestException("Too many redirects");
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Request failed");
        }

        response.setReachable(reachable);
        response.setHttpStatusCode(httpStatusCode);
        response.setFinalUrlScheme(currentUri.getScheme());
        response.setHttpsEnabled("https".equalsIgnoreCase(currentUri.getScheme()));
        response.setResponseTimeMs(System.currentTimeMillis() - startTime);

        // Check security headers
        List<HeaderCheckResult> headerResults = new ArrayList<>();
        if (lastHeaders != null) {
            headerResults.add(checkHeader(lastHeaders, "Strict-Transport-Security"));
            headerResults.add(checkHeader(lastHeaders, "Content-Security-Policy"));
            headerResults.add(checkHeader(lastHeaders, "X-Content-Type-Options"));
        } else {
            headerResults.add(new HeaderCheckResult("Strict-Transport-Security", false, null));
            headerResults.add(new HeaderCheckResult("Content-Security-Policy", false, null));
            headerResults.add(new HeaderCheckResult("X-Content-Type-Options", false, null));
        }
        response.setSecurityHeaders(headerResults);

        // Build findings and overall status
        List<Finding> findings = new ArrayList<>();
        if (!reachable) {
            findings.add(new Finding("Reachability", "FAIL",
                    "The website did not respond successfully or was unreachable.",
                    "Verify the URL is correct and the server is accessible from public networks."));
        } else {
            findings.add(new Finding("Reachability", "PASS",
                    "The website responded with an HTTP response.",
                    "No action required for network reachability."));
        }

        if (httpStatusCode != null && httpStatusCode >= 400) {
            findings.add(new Finding("HTTP Status", "FAIL",
                    "The server responded with unsuccessful HTTP status " + httpStatusCode + ".",
                    "Investigate the application or deployment configuration returning this status."));
        } else if (httpStatusCode != null && httpStatusCode >= 300) {
            findings.add(new Finding("HTTP Status", "WARNING",
                    "The check ended with redirect status " + httpStatusCode + " without a usable destination.",
                    "Verify that the redirect target is valid and reachable."));
        }

        if (response.isHttpsEnabled()) {
            findings.add(new Finding("HTTPS", "PASS",
                    "The final connection uses HTTPS.",
                    "No action required."));
        } else {
            findings.add(new Finding("HTTPS", "FAIL",
                    "The final connection does not use HTTPS.",
                    "Use HTTPS for the submitted URL and every redirect target."));
        }

        // Response time finding
        if (response.getResponseTimeMs() != null) {
            if (response.getResponseTimeMs() <= 2000) {
                findings.add(new Finding("Response Time", "PASS",
                        "Response time is within acceptable range.",
                        "No action required."));
            } else if (response.getResponseTimeMs() <= 5000) {
                findings.add(new Finding("Response Time", "WARNING",
                        "Response time is slower than expected.",
                        "Consider optimizing server performance or caching."));
            } else {
                findings.add(new Finding("Response Time", "WARNING",
                        "Response time is high.",
                        "Investigate network latency or server performance issues."));
            }
        } else {
            findings.add(new Finding("Response Time", "WARNING",
                    "Response time could not be measured reliably.",
                    "Check connectivity and timeouts."));
        }

        // Headers
        for (HeaderCheckResult h : headerResults) {
            if (!h.isPresent()) {
                findings.add(new Finding(h.getHeaderName(), "WARNING",
                        h.getHeaderName() + " header is missing.",
                        "Consider adding this security header for better protection."));
                continue;
            }
            String misconfiguration = headerMisconfiguration(h);
            if (misconfiguration != null) {
                findings.add(new Finding(h.getHeaderName(), "WARNING",
                        h.getHeaderName() + " header is present but " + misconfiguration + ".",
                        "Review the header value and update it to a safe configuration."));
            } else {
                findings.add(new Finding(h.getHeaderName(), "PASS",
                        h.getHeaderName() + " header is present with an acceptable basic value.",
                        "No action required for the checked value."));
            }
        }

        response.setFindings(findings);

        // Compute overall status
        String overall = computeOverall(findings);
        response.setOverallStatus(overall);

        return response;
    }

    private String headerMisconfiguration(HeaderCheckResult header) {
        String value = header.getValue() == null ? "" : header.getValue().trim();
        if ("Strict-Transport-Security".equalsIgnoreCase(header.getHeaderName())) {
            Matcher matcher = HSTS_MAX_AGE.matcher(value);
            if (!matcher.find()) {
                return "has no readable max-age directive";
            }
            try {
                if (Long.parseLong(matcher.group(1)) <= 0) {
                    return "has a zero max-age";
                }
            } catch (NumberFormatException ex) {
                return "has an invalid max-age";
            }
            return null;
        }
        if ("X-Content-Type-Options".equalsIgnoreCase(header.getHeaderName())) {
            return "nosniff".equalsIgnoreCase(value) ? null : "has an unrecognized value";
        }
        if ("Content-Security-Policy".equalsIgnoreCase(header.getHeaderName())) {
            return value.isEmpty() || value.replace(";", "").trim().isEmpty()
                    ? "has an empty or malformed policy" : null;
        }
        return null;
    }

    private Duration remainingTimeout(long startTime) {
        long remaining = totalDeadline.toMillis() - (System.currentTimeMillis() - startTime);
        if (remaining <= 0) {
            throw new BadRequestException("Request exceeded total deadline");
        }
        return Duration.ofMillis(Math.min(requestTimeout.toMillis(), remaining));
    }

    private void validateForRequest(URI uri) {
        UrlValidator.validateAndParse(uri.toString());
        try {
            String host = uri.getHost();
            if (host == null) {
                throw new BadRequestException("Invalid host");
            }
            InetAddress[] addresses = dnsResolver.resolve(host);
            if (addresses == null || addresses.length == 0) {
                throw new BadRequestException("Host not allowed");
            }
            for (InetAddress addr : addresses) {
                UrlValidator.validateAddress(addr);
                // Extra check - ensure not resolving to cloud metadata common IPs
                if (addr.getHostAddress().equals("169.254.169.254")) {
                    throw new BadRequestException("Host not allowed");
                }
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Host not allowed");
        }
    }

    private boolean isRedirect(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308;
    }

    private URI resolveRedirect(URI base, String location) {
        try {
            URI locUri = new URI(location);
            if (locUri.isAbsolute()) {
                return locUri;
            } else {
                return base.resolve(locUri);
            }
        } catch (URISyntaxException e) {
            throw new BadRequestException("Invalid redirect location");
        }
    }

    private HeaderCheckResult checkHeader(HttpHeaders headers, String name) {
        if (headers == null) {
            return new HeaderCheckResult(name, false, null);
        }
        List<String> values = headers.allValues(name);
        if (values == null || values.isEmpty()) {
            // try case-insensitive not directly available, but HttpHeaders is case-insensitive
            // also check firstValue
            if (headers.firstValue(name).isPresent()) {
                String v = headers.firstValue(name).get();
                return new HeaderCheckResult(name, true, v);
            }
            return new HeaderCheckResult(name, false, null);
        }
        return new HeaderCheckResult(name, true, values.get(0));
    }

    private String computeOverall(List<Finding> findings) {
        boolean hasFail = false;
        boolean hasWarning = false;
        for (Finding f : findings) {
            if ("FAIL".equalsIgnoreCase(f.getStatus())) {
                hasFail = true;
            } else if ("WARNING".equalsIgnoreCase(f.getStatus())) {
                hasWarning = true;
            }
        }
        if (hasFail) {
            return "FAIL";
        }
        if (hasWarning) {
            return "WARNING";
        }
        return "PASS";
    }
}
