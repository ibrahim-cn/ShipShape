package com.shipshape.backend.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectAnalysisIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void riskyComposeFixtureReturnsRealComposeFindingsWithoutSensitiveValues() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/projects/analyze/files")
                        .file(uiFile("docker-compose.yml", fixture("fixture-b/docker-compose.yml")))
                        .param("projectName", "task-3-integration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("MULTIPART_FILES"))
                .andExpect(jsonPath("$.overallStatus").value("FAIL"))
                .andExpect(jsonPath("$.findings[?(@.ruleId=='COMPOSE-004')].serviceIdentifier")
                        .value(org.hamcrest.Matchers.hasItem("db")))
                .andExpect(jsonPath("$.findings[?(@.ruleId=='COMPOSE-005')].status")
                        .value(org.hamcrest.Matchers.hasItem("FAIL")))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"ruleId\":\"COMPOSE-003\""));
        assertTrue(body.contains("\"ruleId\":\"COMPOSE-010\""));
        assertFalse(body.contains("hunter2"));
    }

    @Test
    void correctedComposeFixtureReturnsPassingComposeAnalysis() throws Exception {
        mockMvc.perform(multipart("/api/projects/analyze/files")
                        .file(uiFile("compose.yaml", fixture("fixture-a/compose.yaml")))
                        .param("projectName", "task-3-corrected"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules[?(@.module=='COMPOSE')].status")
                        .value(org.hamcrest.Matchers.hasItem("PASS")))
                .andExpect(jsonPath("$.findings[?(@.ruleId=='COMPOSE-004')].status")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("PASS"))))
                .andExpect(jsonPath("$.findings[?(@.ruleId=='COMPOSE-008')].status")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("PASS"))));
    }

    @Test
    void combinedDockerfileAndComposeFilesAreAnalyzedThroughRealServices() throws Exception {
        String dockerfile = """
                FROM node:20-alpine
                USER node
                COPY package.json .
                """;

        MvcResult result = mockMvc.perform(multipart("/api/projects/analyze/files")
                        .file(uiFile("Dockerfile", dockerfile))
                        .file(uiFile("compose.yaml", fixture("fixture-a/compose.yaml"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.implementedModules")
                        .value(org.hamcrest.Matchers.hasItems("DOCKERFILE", "COMPOSE")))
                .andExpect(jsonPath("$.inventory.length()").value(2))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"ruleId\":\"DOCKER-001\""));
        assertTrue(body.contains("\"ruleId\":\"COMPOSE-001\""));
    }

    @Test
    void unsafeComposeFixtureReturnsParserErrorThroughRealPipeline() throws Exception {
        String content = """
                services:
                  api:
                    image: !!python/object/apply:os.system ["echo leaked_secret"]
                """;

        MvcResult result = mockMvc.perform(multipart("/api/projects/analyze/files")
                        .file(uiFile("compose.yaml", content)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallStatus").value("ERROR"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("COMPOSE.PARSE_ERROR"));
        assertFalse(body.contains("leaked_secret"));
        assertFalse(body.contains("os.system"));
    }

    @Test
    void missingComposeFileIsReportedAsNotChecked() throws Exception {
        mockMvc.perform(multipart("/api/projects/analyze/files")
                        .file(uiFile("README.txt", "No Compose configuration here.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallStatus").value("NOT_CHECKED"))
                .andExpect(jsonPath("$.findings[?(@.ruleId=='COMPOSE.NOT_CHECKED')]").exists());
    }

    @Test
    void standaloneDockerfileEndpointStillWorks() throws Exception {
        mockMvc.perform(post("/api/checks/dockerfile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"FROM node:20-alpine\\nUSER node\\n\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[?(@.ruleId=='DOCKER-001')]").exists());
    }

    private MockMultipartFile uiFile(String originalFilename, String content) {
        return new MockMultipartFile(
                "files",
                originalFilename,
                MediaType.TEXT_PLAIN_VALUE,
                content.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String fixture(String relativePath) throws Exception {
        return Files.readString(Path.of("src/test/resources/compose-analysis", relativePath));
    }
}
