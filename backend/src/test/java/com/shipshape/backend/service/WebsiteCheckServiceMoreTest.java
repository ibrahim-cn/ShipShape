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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
        // reachable means 2xx-3xx normally; 4xx is not reachable by our definition
        assertFalse(resp.isReachable());
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
}
