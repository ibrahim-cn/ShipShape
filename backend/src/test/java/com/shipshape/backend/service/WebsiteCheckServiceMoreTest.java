package com.shipshape.backend.service;

import com.shipshape.backend.dto.WebsiteCheckResponse;
import com.shipshape.backend.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebsiteCheckServiceMoreTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private WebsiteCheckService.DnsResolver dnsResolver;

    @Mock
    private HttpResponse<Void> httpResponse;

    @Mock
    private HttpHeaders httpHeaders;

    private WebsiteCheckService service() {
        return new WebsiteCheckService(httpClient, dnsResolver);
    }

    @Test
    void dnsResolutionFailure() throws Exception {
        WebsiteCheckService svc = service();
        when(dnsResolver.resolve("bad.example.com")).thenThrow(new java.net.UnknownHostException("fail"));
        assertThrows(BadRequestException.class, () -> svc.check("https://bad.example.com"));
    }

    @Test
    void connectionTimeout() throws Exception {
        WebsiteCheckService svc = service();
        when(dnsResolver.resolve("example.com")).thenReturn(new InetAddress[]{InetAddress.getByName("93.184.215.14")});
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new java.net.http.HttpConnectTimeoutException("timeout"));
        assertThrows(BadRequestException.class, () -> svc.check("https://example.com"));
    }

    @Test
    void redirectToForbiddenDestination() throws Exception {
        WebsiteCheckService svc = service();
        // First call to example.com - public
        when(dnsResolver.resolve("example.com")).thenReturn(new InetAddress[]{InetAddress.getByName("93.184.215.14")});
        // Redirect response
        when(httpResponse.statusCode()).thenReturn(302);
        when(httpResponse.headers()).thenReturn(httpHeaders);
        when(httpHeaders.firstValue("Location")).thenReturn(Optional.of("http://127.0.0.1/"));
        // For redirect target validation, we'll resolve 127.0.0.1 - our validator resolves; but easier to have resolver return loopback? No - validator will reject resolved loopback
        when(dnsResolver.resolve("127.0.0.1")).thenReturn(new InetAddress[]{InetAddress.getByName("127.0.0.1")});
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        assertThrows(BadRequestException.class, () -> svc.check("https://example.com"));
    }

    @Test
    void httpErrorResponseReachable() throws Exception {
        WebsiteCheckService svc = service();
        when(dnsResolver.resolve("example.com")).thenReturn(new InetAddress[]{InetAddress.getByName("93.184.215.14")});
        when(httpResponse.statusCode()).thenReturn(404);
        when(httpResponse.headers()).thenReturn(httpHeaders);
        when(httpHeaders.firstValue(any())).thenReturn(Optional.empty());
        when(httpHeaders.allValues(any())).thenReturn(List.of());
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        WebsiteCheckResponse resp = svc.check("https://example.com");
        assertNotNull(resp);
        assertEquals(404, resp.getHttpStatusCode());
        assertTrue(resp.isReachable());
        assertTrue(resp.getFindings().stream().anyMatch(f -> "HTTP Status".equals(f.getName()) && "FAIL".equals(f.getStatus())));
        assertEquals("FAIL", resp.getOverallStatus());
    }

    @Test
    void headersMissingAndPresentClassification() throws Exception {
        WebsiteCheckService svc = service();
        when(dnsResolver.resolve("example.com")).thenReturn(new InetAddress[]{InetAddress.getByName("93.184.215.14")});
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.headers()).thenReturn(httpHeaders);
        when(httpHeaders.firstValue(any())).thenReturn(Optional.empty());
        when(httpHeaders.allValues(any())).thenReturn(List.of());
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        WebsiteCheckResponse resp = svc.check("https://example.com");
        assertNotNull(resp.getSecurityHeaders());
        assertEquals(3, resp.getSecurityHeaders().size());
        assertTrue(resp.getFindings().stream().anyMatch(f -> f.getStatus().equals("WARNING") || f.getStatus().equals("PASS")));
        assertEquals("WARNING", resp.getOverallStatus());
    }

    private HttpHeaders headers(String name, String value) {
        return HttpHeaders.of(Map.of(name, List.of(value)), (key, headerValue) -> true);
    }

    private HttpResponse<Void> response(int statusCode, HttpHeaders headers) {
        HttpResponse<Void> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.headers()).thenReturn(headers);
        return response;
    }

    private void publicDns() throws Exception {
        when(dnsResolver.resolve("example.com")).thenReturn(new InetAddress[]{InetAddress.getByName("93.184.215.14")});
    }

    @Test
    void httpErrorResponseIsReachableAndReportsHttpStatusFailure() throws Exception {
        WebsiteCheckService svc = service();
        publicDns();
        HttpResponse<Void> error = response(404, headers("Content-Security-Policy", "default-src 'self'"));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(error);
        WebsiteCheckResponse resp = svc.check("https://example.com");
        assertTrue(resp.isReachable());
        assertEquals(404, resp.getHttpStatusCode());
        assertTrue(resp.getFindings().stream().anyMatch(f -> "HTTP Status".equals(f.getName()) && "FAIL".equals(f.getStatus())));
    }

    @Test
    void httpToHttpsRedirectReportsSecureFinalConnection() throws Exception {
        WebsiteCheckService svc = service();
        publicDns();
        HttpResponse<Void> redirect = response(302, headers("Location", "https://example.com/final"));
        HttpResponse<Void> ok = response(200, headers("Strict-Transport-Security", "max-age=31536000"));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(redirect, ok);
        WebsiteCheckResponse resp = svc.check("http://example.com");
        assertTrue(resp.isHttpsEnabled());
        assertEquals("http", resp.getSubmittedUrlScheme());
        assertEquals("https", resp.getFinalUrlScheme());
    }

    @Test
    void httpsToHttpRedirectReportsInsecureFinalConnection() throws Exception {
        WebsiteCheckService svc = service();
        publicDns();
        HttpResponse<Void> redirect = response(302, headers("Location", "http://example.com/final"));
        HttpResponse<Void> ok = response(200, headers("Content-Security-Policy", "default-src 'self'"));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(redirect, ok);
        WebsiteCheckResponse resp = svc.check("https://example.com");
        assertFalse(resp.isHttpsEnabled());
        assertEquals("https", resp.getSubmittedUrlScheme());
        assertEquals("http", resp.getFinalUrlScheme());
    }

    @Test
    void hstsMaxAgeZeroIsReportedAsMisconfigured() throws Exception {
        WebsiteCheckService svc = service();
        publicDns();
        HttpResponse<Void> ok = response(200, headers("Strict-Transport-Security", "max-age=0"));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(ok);
        WebsiteCheckResponse resp = svc.check("https://example.com");
        assertTrue(resp.getFindings().stream().anyMatch(f -> "Strict-Transport-Security".equals(f.getName()) && "WARNING".equals(f.getStatus())));
    }

    @Test
    void invalidXContentTypeOptionsIsReportedAsMisconfigured() throws Exception {
        WebsiteCheckService svc = service();
        publicDns();
        HttpResponse<Void> ok = response(200, headers("X-Content-Type-Options", "invalid"));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(ok);
        WebsiteCheckResponse resp = svc.check("https://example.com");
        assertTrue(resp.getFindings().stream().anyMatch(f -> "X-Content-Type-Options".equals(f.getName()) && "WARNING".equals(f.getStatus())));
    }

    @Test
    void emptyCspIsReportedAsMisconfigured() throws Exception {
        WebsiteCheckService svc = service();
        publicDns();
        HttpResponse<Void> ok = response(200, headers("Content-Security-Policy", "   "));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(ok);
        WebsiteCheckResponse resp = svc.check("https://example.com");
        assertTrue(resp.getFindings().stream().anyMatch(f -> "Content-Security-Policy".equals(f.getName()) && "WARNING".equals(f.getStatus())));
    }

    @Test
    void responseTimeMsMeasuresWholeRedirectChain() throws Exception {
        WebsiteCheckService svc = service();
        publicDns();
        AtomicInteger calls = new AtomicInteger();
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            Thread.sleep(60);
            if (calls.getAndIncrement() == 0) {
                return response(302, headers("Location", "https://example.com/final"));
            }
            return response(200, headers("Content-Security-Policy", "default-src 'self'"));
        });
        WebsiteCheckResponse resp = svc.check("http://example.com");
        assertNotNull(resp.getResponseTimeMs());
        assertTrue(resp.getResponseTimeMs() >= 100, "responseTimeMs=" + resp.getResponseTimeMs());
    }

    @Test
    void responseTimeFindingUsesMeasuredDuration() throws Exception {
        WebsiteCheckService svc = service();
        publicDns();
        HttpResponse<Void> ok = response(200, headers("Strict-Transport-Security", "max-age=31536000"));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(ok);
        WebsiteCheckResponse resp = svc.check("https://example.com");
        assertTrue(resp.getFindings().stream().anyMatch(f -> "Response Time".equals(f.getName()) && "PASS".equals(f.getStatus())));
    }

    @Test
    void totalDeadlineIsEnforcedAcrossRedirects() throws Exception {
        WebsiteCheckService svc = new WebsiteCheckService(httpClient, dnsResolver, Duration.ofSeconds(8), Duration.ofMillis(30));
        publicDns();
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            Thread.sleep(60);
            return response(302, headers("Location", "https://example.com/next"));
        });
        BadRequestException ex = assertThrows(BadRequestException.class, () -> svc.check("https://example.com"));
        assertTrue(ex.getMessage().contains("deadline"), ex.getMessage());
    }
}
