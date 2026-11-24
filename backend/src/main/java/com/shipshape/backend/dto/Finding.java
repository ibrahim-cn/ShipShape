package com.shipshape.backend.dto;

import java.util.ArrayList;
import java.util.List;

public class Finding {
    private String ruleId;
    private String category;
    private String name;
    private String status;
    private String severity;
    private String explanation;
    private String suggestedAction;
    private String recommendation;
    private String filePath;
    private Integer lineNumber;
    private String maskedEvidence;
    private String serviceIdentifier;
    private List<String> limitations;

    public Finding() {
        this.limitations = new ArrayList<>();
    }

    public Finding(String name, String status, String explanation, String suggestedAction) {
        this(null, null, name, status, FindingSeverity.UNSPECIFIED, explanation,
                suggestedAction, suggestedAction, null, null, null, List.of());
    }

    public Finding(String ruleId, String category, String name, String status, String severity,
                   String explanation, String suggestedAction, String recommendation, String filePath,
                   Integer lineNumber, String maskedEvidence, List<String> limitations) {
        this.ruleId = ruleId;
        this.category = category;
        this.name = name;
        this.status = status;
        this.severity = severity == null ? FindingSeverity.UNSPECIFIED : severity;
        this.explanation = explanation;
        this.suggestedAction = suggestedAction;
        this.recommendation = recommendation == null ? suggestedAction : recommendation;
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.maskedEvidence = maskedEvidence;
        this.serviceIdentifier = null;
        this.limitations = limitations == null ? new ArrayList<>() : new ArrayList<>(limitations);
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getSuggestedAction() {
        return suggestedAction;
    }

    public void setSuggestedAction(String suggestedAction) {
        this.suggestedAction = suggestedAction;
        if (recommendation == null) {
            recommendation = suggestedAction;
        }
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(Integer lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getMaskedEvidence() {
        return maskedEvidence;
    }

    public void setMaskedEvidence(String maskedEvidence) {
        this.maskedEvidence = maskedEvidence;
    }

    public String getServiceIdentifier() {
        return serviceIdentifier;
    }

    public void setServiceIdentifier(String serviceIdentifier) {
        this.serviceIdentifier = serviceIdentifier;
    }

    public List<String> getLimitations() {
        return limitations;
    }

    public void setLimitations(List<String> limitations) {
        this.limitations = limitations == null ? new ArrayList<>() : new ArrayList<>(limitations);
    }
}
