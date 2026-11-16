package com.shipshape.backend.controller;

import com.shipshape.backend.dto.DockerfileAnalysisRequest;
import com.shipshape.backend.dto.DockerfileAnalysisResponse;
import com.shipshape.backend.dto.Finding;
import com.shipshape.backend.exception.BadRequestException;
import com.shipshape.backend.exception.GlobalExceptionHandler;
import com.shipshape.backend.service.DockerfileAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DockerfileAnalysisController.class)
@Import(GlobalExceptionHandler.class)
class DockerfileAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DockerfileAnalysisService dockerfileAnalysisService;

    @Test
    void validRequestReturnsAnalysisResponse() throws Exception {
        DockerfileAnalysisResponse response = new DockerfileAnalysisResponse(
                "WARNING",
                List.of(new Finding(
                        "Health Check",
                        "WARNING",
                        "No HEALTHCHECK instruction was found in the final stage.",
                        "Add a HEALTHCHECK CMD instruction."
                ))
        );

        when(dockerfileAnalysisService.analyze(anyString())).thenReturn(response);

        DockerfileAnalysisRequest request = new DockerfileAnalysisRequest(
                "FROM node:20\nUSER node\nCOPY . .\n"
        );

        mockMvc.perform(post("/api/checks/dockerfile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallStatus").value("WARNING"))
                .andExpect(jsonPath("$.findings[0].name").value("Health Check"))
                .andExpect(jsonPath("$.findings[0].status").value("WARNING"));
    }

    @Test
    void serviceValidationErrorReturnsBadRequest() throws Exception {
        when(dockerfileAnalysisService.analyze(anyString()))
                .thenThrow(new BadRequestException("Dockerfile content is required"));

        DockerfileAnalysisRequest request = new DockerfileAnalysisRequest("   ");

        mockMvc.perform(post("/api/checks/dockerfile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Dockerfile content is required"));
    }
}
