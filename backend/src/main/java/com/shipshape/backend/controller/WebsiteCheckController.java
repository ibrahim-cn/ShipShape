package com.shipshape.backend.controller;

import com.shipshape.backend.dto.WebsiteCheckRequest;
import com.shipshape.backend.dto.WebsiteCheckResponse;
import com.shipshape.backend.service.WebsiteCheckService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebsiteCheckController {

    private final WebsiteCheckService websiteCheckService;

    public WebsiteCheckController(WebsiteCheckService websiteCheckService) {
        this.websiteCheckService = websiteCheckService;
    }

    @PostMapping("/api/checks/website")
    public WebsiteCheckResponse checkWebsite(@RequestBody WebsiteCheckRequest request) {
        return websiteCheckService.check(request.getUrl());
    }
}
