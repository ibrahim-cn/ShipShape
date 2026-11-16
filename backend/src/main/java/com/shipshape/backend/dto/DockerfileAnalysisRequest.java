package com.shipshape.backend.dto;

public class DockerfileAnalysisRequest {
    private String content;

    public DockerfileAnalysisRequest() {
    }

    public DockerfileAnalysisRequest(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
