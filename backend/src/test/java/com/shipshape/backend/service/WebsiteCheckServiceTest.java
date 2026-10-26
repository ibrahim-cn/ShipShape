package com.shipshape.backend.service;

import com.shipshape.backend.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WebsiteCheckServiceTest {

    private final WebsiteCheckService service = new WebsiteCheckService();

    @Test
    void rejectMalformedUrl() {
        assertThrows(BadRequestException.class, () -> service.check("not-a-url"));
    }

    @Test
    void rejectHttpWithoutSchemeIfMalformed() {
        assertThrows(BadRequestException.class, () -> service.check("example.com"));
    }

    @Test
    void rejectUnsupportedScheme() {
        assertThrows(BadRequestException.class, () -> service.check("ftp://example.com"));
    }

    @Test
    void rejectUrlWithCredentials() {
        assertThrows(BadRequestException.class, () -> service.check("https://user:pass@example.com"));
    }

    @Test
    void rejectLocalhost() {
        assertThrows(BadRequestException.class, () -> service.check("http://localhost/"));
    }

    @Test
    void rejectLoopbackIp() {
        assertThrows(BadRequestException.class, () -> service.check("http://127.0.0.1/"));
    }

    @Test
    void rejectPrivateIp() {
        assertThrows(BadRequestException.class, () -> service.check("http://192.168.1.1/"));
        assertThrows(BadRequestException.class, () -> service.check("http://10.0.0.1/"));
        assertThrows(BadRequestException.class, () -> service.check("http://172.16.0.1/"));
    }

    @Test
    void rejectLinkLocal() {
        assertThrows(BadRequestException.class, () -> service.check("http://169.254.169.254/"));
    }

    @Test
    void rejectMarkdownFormattedUrl() {
        assertThrows(BadRequestException.class, () -> service.check("[https://example.com](https://example.com)"));
    }

    @Test
    void rejectNullUrl() {
        assertThrows(BadRequestException.class, () -> service.check(null));
    }

    @Test
    void rejectEmptyUrl() {
        assertThrows(BadRequestException.class, () -> service.check("   "));
    }

    @Test
    void rejectMulticastIp() {
        assertThrows(BadRequestException.class, () -> service.check("http://224.0.0.1/"));
    }

    @Test
    void rejectUnspecifiedIp() {
        assertThrows(BadRequestException.class, () -> service.check("http://0.0.0.0/"));
    }
}
