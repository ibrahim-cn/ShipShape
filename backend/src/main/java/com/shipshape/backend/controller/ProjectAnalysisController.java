package com.shipshape.backend.controller;

import com.shipshape.backend.dto.ProjectAnalysisResponse;
import com.shipshape.backend.service.ProjectAnalysisService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
public class ProjectAnalysisController {

    private final ProjectAnalysisService projectAnalysisService;

    public ProjectAnalysisController(ProjectAnalysisService projectAnalysisService) {
        this.projectAnalysisService = projectAnalysisService;
    }

    @PostMapping(value = "/api/projects/analyze/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProjectAnalysisResponse analyzeProjectFiles(
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam(value = "projectName", required = false) String projectName
    ) {
        return projectAnalysisService.analyzeFiles(files, projectName);
    }
}
