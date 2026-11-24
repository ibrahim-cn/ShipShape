package com.shipshape.backend.controller;

import com.shipshape.backend.dto.FileAnalysisStatus;
import com.shipshape.backend.dto.Finding;
import com.shipshape.backend.dto.ModuleSummary;
import com.shipshape.backend.dto.ProjectAnalysisResponse;
import com.shipshape.backend.dto.ProjectFileSummary;
import com.shipshape.backend.dto.ProjectFileType;
import com.shipshape.backend.exception.BadRequestException;
import com.shipshape.backend.exception.GlobalExceptionHandler;
import com.shipshape.backend.service.ProjectAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectAnalysisController.class)
@Import(GlobalExceptionHandler.class)
class ProjectAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectAnalysisService projectAnalysisService;

    @Test
    void validMultipartRequestReturnsProjectAnalysis() throws Exception {
        ProjectAnalysisResponse response = new ProjectAnalysisResponse(
                "MULTIPART_FILES",
                "demo",
                "WARNING",
                false,
                List.of("DOCKERFILE", "COMPOSE"),
                List.of("COMPOSE"),
                List.of("COMPOSE"),
                List.of(
                        new Finding(
                                "DOCKER-001",
                                "DOCKERFILE",
                                "FROM Instruction",
                                "PASS",
                                "INFO",
                                "The Dockerfile contains a valid FROM instruction.",
                                "No action required.",
                                "No action required.",
                                "Dockerfile",
                                1,
                                "FROM [line 1]",
                                List.of("Dockerfile instructions are parsed as text.")
                        ),
                        new Finding(
                                "COMPOSE.NOT_CHECKED",
                                "COMPOSE",
                                "Compose Analysis",
                                "NOT_CHECKED",
                                "LOW",
                                "No recognized Compose file was found in the uploaded project files.",
                                "Upload a recognized Compose file.",
                                "Upload a recognized Compose file.",
                                null,
                                null,
                                null,
                                List.of("Compose files are analyzed as YAML data and are never executed.")
                        )
                ),
                List.of(
                        new ModuleSummary("DOCKERFILE", "WARNING", 1),
                        new ModuleSummary("COMPOSE", "NOT_CHECKED", 1)
                ),
                List.of(new ProjectFileSummary(
                        "Dockerfile", ProjectFileType.DOCKERFILE, 12, true, true, FileAnalysisStatus.ANALYZED)),
                List.of("Only Dockerfile and Compose text rules are implemented.")
        );

        when(projectAnalysisService.analyzeFiles(anyList(), eq("demo"))).thenReturn(response);

        MockMultipartFile dockerfile = new MockMultipartFile(
                "files",
                "Dockerfile",
                MediaType.TEXT_PLAIN_VALUE,
                "FROM node:20\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/projects/analyze/files")
                        .file(dockerfile)
                        .param("projectName", "demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("MULTIPART_FILES"))
                .andExpect(jsonPath("$.projectName").value("demo"))
                .andExpect(jsonPath("$.overallStatus").value("WARNING"))
                .andExpect(jsonPath("$.coverageComplete").value(false))
                .andExpect(jsonPath("$.implementedModules[0]").value("DOCKERFILE"))
                .andExpect(jsonPath("$.implementedModules[1]").value("COMPOSE"))
                .andExpect(jsonPath("$.incompleteModules[0]").value("COMPOSE"))
                .andExpect(jsonPath("$.notCheckedModules[0]").value("COMPOSE"))
                .andExpect(jsonPath("$.findings[0].ruleId").value("DOCKER-001"))
                .andExpect(jsonPath("$.findings[1].ruleId").value("COMPOSE.NOT_CHECKED"))
                .andExpect(jsonPath("$.modules[0].module").value("DOCKERFILE"))
                .andExpect(jsonPath("$.modules[1].module").value("COMPOSE"))
                .andExpect(jsonPath("$.inventory[0].path").value("Dockerfile"))
                .andExpect(jsonPath("$.inventory[0].analysisStatus").value("ANALYZED"));
    }

    @Test
    void validationErrorReturnsBadRequest() throws Exception {
        when(projectAnalysisService.analyzeFiles(anyList(), any()))
                .thenThrow(new BadRequestException("At least one file is required"));

        MockMultipartFile dockerfile = new MockMultipartFile(
                "files",
                "Dockerfile",
                MediaType.TEXT_PLAIN_VALUE,
                "FROM node:20\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/projects/analyze/files").file(dockerfile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("At least one file is required"));
    }
}
