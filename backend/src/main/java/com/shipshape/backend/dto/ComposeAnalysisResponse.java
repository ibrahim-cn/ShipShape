package com.shipshape.backend.dto;

import java.util.List;

public class ComposeAnalysisResponse {
    private String overallStatus;
    private List<Finding> findings;

    public ComposeAnalysisResponse() {
    }

    public ComposeAnalysisResponse(String overallStatus, List<Finding> findings) {
        this.overallStatus = overallStatus;
        this.findings = findings;
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
