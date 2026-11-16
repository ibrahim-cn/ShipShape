package com.shipshape.backend.controller;

import com.shipshape.backend.dto.DockerfileAnalysisRequest;
import com.shipshape.backend.dto.DockerfileAnalysisResponse;
import com.shipshape.backend.service.DockerfileAnalysisService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DockerfileAnalysisController {

    private final DockerfileAnalysisService dockerfileAnalysisService;

    public DockerfileAnalysisController(DockerfileAnalysisService dockerfileAnalysisService) {
        this.dockerfileAnalysisService = dockerfileAnalysisService;
    }

    @PostMapping("/api/checks/dockerfile")
    public DockerfileAnalysisResponse analyzeDockerfile(@RequestBody DockerfileAnalysisRequest request) {
        return dockerfileAnalysisService.analyze(request == null ? null : request.getContent());
    }
}
