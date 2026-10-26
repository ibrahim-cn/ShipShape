package com.shipshape.backend.dto;

import java.util.List;

public class WebsiteCheckRequest {
    private String url;

    public WebsiteCheckRequest() {
    }

    public WebsiteCheckRequest(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
