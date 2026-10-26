package com.shipshape.backend.dto;

import java.util.List;

public class WebsiteCheckResponse {
    private String url;
    private boolean reachable;
    private Integer httpStatusCode;
    private Long responseTimeMs;
    private boolean httpsEnabled;
    private List<HeaderCheckResult> securityHeaders;
    private String overallStatus;
    private List<Finding> findings;

    public WebsiteCheckResponse() {
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isReachable() {
        return reachable;
    }

    public void setReachable(boolean reachable) {
        this.reachable = reachable;
    }

    public Integer getHttpStatusCode() {
        return httpStatusCode;
    }

    public void setHttpStatusCode(Integer httpStatusCode) {
        this.httpStatusCode = httpStatusCode;
    }

    public Long getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(Long responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }

    public boolean isHttpsEnabled() {
        return httpsEnabled;
    }

    public void setHttpsEnabled(boolean httpsEnabled) {
        this.httpsEnabled = httpsEnabled;
    }

    public List<HeaderCheckResult> getSecurityHeaders() {
        return securityHeaders;
    }

    public void setSecurityHeaders(List<HeaderCheckResult> securityHeaders) {
        this.securityHeaders = securityHeaders;
    }

    public String getOverallStatus() {
        return overallStatus;
    }

    public void setOverallStatus(String overallStatus) {
        this.overallStatus = overallStatus;
    }

    public List<Finding> getFindings() {
        return findings;
    }

    public void setFindings(List<Finding> findings) {
        this.findings = findings;
    }
}
