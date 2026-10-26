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

@Service
public class WebsiteCheckService {

    private static final int MAX_REDIRECTS = 5;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    private final HttpClient httpClient;
    private final DnsResolver dnsResolver;

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
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        this.dnsResolver = new DefaultDnsResolver();
    }

    public WebsiteCheckService(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.dnsResolver = new DefaultDnsResolver();
    }

    public WebsiteCheckService(HttpClient httpClient, DnsResolver dnsResolver) {
        this.httpClient = httpClient;
        this.dnsResolver = dnsResolver != null ? dnsResolver : new DefaultDnsResolver();
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

                // re-validate before request (including resolved addresses)
                validateForRequest(currentUri);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(currentUri)
                        .timeout(REQUEST_TIMEOUT)
                        .method("GET", HttpRequest.BodyPublishers.noBody())
                        .header("User-Agent", "ShipShape-PreDeployment-Checker")
                        .build();

                long reqStart = System.currentTimeMillis();
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

                long reqEnd = System.currentTimeMillis();
                if (response.getResponseTimeMs() == null) {
                    response.setResponseTimeMs(reqEnd - reqStart);
                } else {
                    response.setResponseTimeMs(Math.max(response.getResponseTimeMs(), reqEnd - reqStart));
                }

                httpStatusCode = httpResponse.statusCode();
                lastHeaders = httpResponse.headers();

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

                reachable = httpStatusCode >= 200 && httpStatusCode < 400;
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

        if (response.getResponseTimeMs() == null && startTime > 0) {
            response.setResponseTimeMs(System.currentTimeMillis() - startTime);
        }

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
                    "The website responded successfully.",
                    "No action required."));
        }

        if (response.isHttpsEnabled()) {
            findings.add(new Finding("HTTPS", "PASS",
                    "The website uses HTTPS.",
                    "No action required."));
        } else {
            findings.add(new Finding("HTTPS", "FAIL",
                    "The website does not use HTTPS.",
                    "Use HTTPS to encrypt traffic between clients and the server."));
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
            if (h.isPresent()) {
                findings.add(new Finding(h.getHeaderName(), "PASS",
                        h.getHeaderName() + " header is present.",
                        "No action required."));
            } else {
                findings.add(new Finding(h.getHeaderName(), "WARNING",
                        h.getHeaderName() + " header is missing.",
                        "Consider adding this security header for better protection."));
            }
        }

        response.setFindings(findings);

        // Compute overall status
        String overall = computeOverall(findings);
        response.setOverallStatus(overall);

        return response;
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
