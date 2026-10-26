package com.shipshape.backend.dto;

public class Finding {
    private String name;
    private String status;
    private String explanation;
    private String suggestedAction;

    public Finding() {
    }

    public Finding(String name, String status, String explanation, String suggestedAction) {
        this.name = name;
        this.status = status;
        this.explanation = explanation;
        this.suggestedAction = suggestedAction;
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
    }
}
