package com.shipshape.backend.service;

import com.shipshape.backend.dto.Finding;
import com.shipshape.backend.dto.FileAnalysisStatus;
import com.shipshape.backend.dto.ProjectAnalysisResponse;
import com.shipshape.backend.dto.ProjectAnalyzerFileResult;
import com.shipshape.backend.dto.ProjectContext;
import com.shipshape.backend.dto.ProjectFile;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectAnalysisServiceTest {

    private final ProjectAnalysisService service = new ProjectAnalysisService(
            new ProjectInputService(),
            List.of(
                    new DockerfileProjectAnalyzer(new DockerfileAnalysisService()),
                    new ComposeProjectAnalyzer(new ComposeAnalysisService())
            )
    );

    @Test
    void analyzesDockerfileThroughSharedPipeline() {
        ProjectAnalysisResponse response = service.analyzeFiles(List.of(
                textFile("Dockerfile", "FROM node:20\nCOPY . .\n")
        ), "demo");

        assertEquals("MULTIPART_FILES", response.source());
        assertEquals("demo", response.projectName());
        assertEquals("WARNING", response.overallStatus());
        assertFalse(response.coverageComplete());
        assertTrue(response.notCheckedModules().contains("COMPOSE"));
        assertFalse(response.notCheckedModules().contains("DOCKERFILE"));
        assertEquals(10, response.modules().size());
        assertEquals("DOCKERFILE", response.modules().get(0).module());
        assertTrue(response.inventory().get(0).analyzed());
        assertTrue(response.limitations().stream().anyMatch(value -> value.contains("ZIP input")));
        assertTrue(response.limitations().stream().anyMatch(value -> value.contains("temporary storage")));

        assertTrue(response.findings().stream().anyMatch(finding ->
                "DOCKER-001".equals(finding.getRuleId())
                        && "Dockerfile".equals(finding.getFilePath())
                        && finding.getLineNumber() != null
                        && finding.getMaskedEvidence() != null
        ));
        assertTrue(response.implementedModules().contains("COMPOSE"));
        assertFalse(response.findings().stream().anyMatch(finding ->
                "COVERAGE.NOT_CHECKED".equals(finding.getRuleId())
                        && "COMPOSE".equals(finding.getCategory())
        ));
        assertTrue(response.findings().stream().anyMatch(finding ->
                "COMPOSE.NOT_CHECKED".equals(finding.getRuleId())
        ));
    }

    @Test
    void analyzesDockerfileAndComposeFilesIndependently() {
        ProjectAnalysisResponse response = service.analyzeFiles(List.of(
                textFile("Dockerfile", "FROM node:20-alpine\nCOPY package.json .\n"),
                textFile("compose.yaml", """
                        services:
                          api:
                            image: registry.example.com/api:1.4.0
                            user: "1000:1000"
                            restart: unless-stopped
                        """)
        ), "demo");

        assertTrue(response.findings().stream().anyMatch(finding ->
                "DOCKER-001".equals(finding.getRuleId())
        ));
        assertTrue(response.findings().stream().anyMatch(finding ->
                "COMPOSE-004".equals(finding.getRuleId())
                        && "PASS".equals(finding.getStatus())
        ));
        assertEquals(FileAnalysisStatus.ANALYZED, response.inventory().get(0).analysisStatus());
        assertEquals(FileAnalysisStatus.ANALYZED, response.inventory().get(1).analysisStatus());
        assertTrue(response.implementedModules().containsAll(List.of("DOCKERFILE", "COMPOSE")));
    }

    @Test
    void reportsUnsupportedFilesAsNotChecked() {
        MockMultipartFile binary = new MockMultipartFile(
                "files",
                "image.bin",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                new byte[]{0, 1, 2, 3}
        );

        ProjectAnalysisResponse response = service.analyzeFiles(List.of(binary), "demo");

        assertEquals("NOT_CHECKED", response.overallStatus());
        assertFalse(response.coverageComplete());
        assertFalse(response.inventory().get(0).analyzed());
        assertTrue(response.findings().stream().anyMatch(finding ->
                "INPUT.UNSUPPORTED_FILE".equals(finding.getRuleId())
        ));
        assertTrue(response.findings().stream().anyMatch(finding ->
                "INPUT.NO_ANALYZABLE_FILE".equals(finding.getRuleId())
        ));
    }

    @Test
    void turnsDockerfileValidationErrorIntoFinding() {
        ProjectAnalysisResponse response = service.analyzeFiles(List.of(
                textFile("Dockerfile", "   ")
        ), "demo");

        assertEquals("ERROR", response.overallStatus());
        assertFalse(response.coverageComplete());
        assertTrue(response.incompleteModules().contains("DOCKERFILE"));
        assertEquals(FileAnalysisStatus.PARSE_ERROR, response.inventory().get(0).analysisStatus());
        assertTrue(response.findings().stream().anyMatch(finding ->
                "DOCKERFILE.ERROR".equals(finding.getRuleId())
                        && "ERROR".equals(finding.getStatus())
        ));
    }

    @Test
    void reportsMissingDockerfileAsNotCheckedInsteadOfNotApplicable() {
        ProjectAnalysisResponse response = service.analyzeFiles(List.of(
                textFile("README.md", "# demo")
        ), "demo");

        assertTrue(response.findings().stream().anyMatch(finding ->
                "DOCKERFILE.NOT_CHECKED".equals(finding.getRuleId())
                        && "NOT_CHECKED".equals(finding.getStatus())
        ));
        assertFalse(response.findings().stream().anyMatch(finding ->
                "NOT_APPLICABLE".equals(finding.getStatus())
        ));
        assertEquals(FileAnalysisStatus.NOT_ANALYZED, response.inventory().get(0).analysisStatus());
    }

    @Test
    void broadCopyDoesNotSilentlyPassWithUnrelatedDockerignore() {
        ProjectAnalysisResponse response = service.analyzeFiles(List.of(
                textFile("app/Dockerfile", "FROM node:20\nCOPY . .\n"),
                textFile(".dockerignore", "node_modules\n")
        ), "demo");

        assertTrue(response.findings().stream().anyMatch(finding ->
                "DOCKER-006".equals(finding.getRuleId())
                        && "WARNING".equals(finding.getStatus())
                        && finding.getExplanation().contains("no .dockerignore file was supplied")
        ));
    }

    @Test
    void adjacentNonEmptyDockerignoreSupportsBroadCopyCheck() {
        ProjectAnalysisResponse response = service.analyzeFiles(List.of(
                textFile("app/Dockerfile", "FROM node:20\nCOPY . .\n"),
                textFile("app/.dockerignore", "node_modules\n")
        ), "demo");

        assertTrue(response.findings().stream().anyMatch(finding ->
                "DOCKER-006".equals(finding.getRuleId())
                        && "PASS".equals(finding.getStatus())
        ));
        assertEquals(FileAnalysisStatus.NOT_ANALYZED, response.inventory().get(1).analysisStatus());
    }

    @Test
    void isolatesAnalyzerFailuresWithoutLosingCoverageReporting() {
        ProjectAnalysisService failingService = new ProjectAnalysisService(
                new ProjectInputService(),
                List.of(new ProjectAnalyzer() {
                    @Override
                    public String module() {
                        return "DOCKERFILE";
                    }

                    @Override
                    public List<Finding> analyze(ProjectContext context) {
                        throw new IllegalStateException("sensitive internal detail");
                    }
                })
        );

        ProjectAnalysisResponse response = failingService.analyzeFiles(List.of(
                textFile("Dockerfile", "FROM node:20\n")
        ), "demo");

        assertEquals("ERROR", response.overallStatus());
        assertFalse(response.coverageComplete());
        assertTrue(response.findings().stream().anyMatch(finding ->
                "ANALYZER.ERROR".equals(finding.getRuleId())
                        && "DOCKERFILE".equals(finding.getCategory())
        ));
        assertFalse(response.findings().stream().anyMatch(finding ->
                (finding.getExplanation() != null && finding.getExplanation().contains("sensitive internal detail"))
                        || (finding.getMaskedEvidence() != null
                        && finding.getMaskedEvidence().contains("sensitive internal detail"))
        ));
    }

    @Test
    void marksCoverageCompleteWhenEveryModuleIsImplemented() {
        List<ProjectAnalyzer> analyzers = new ArrayList<>();
        for (String module : List.of(
                "DOCKERFILE", "COMPOSE", "SECRETS", "ENVIRONMENT", "IGNORE",
                "SPRING_CONFIG", "DEPENDENCIES", "HEALTH_RELIABILITY", "CI_CD", "VULNERABILITIES"
        )) {
            analyzers.add(passingAnalyzer(module));
        }

        ProjectAnalysisResponse response = new ProjectAnalysisService(
                new ProjectInputService(), analyzers
        ).analyzeFiles(List.of(textFile("Dockerfile", "FROM node:20\n")), "demo");

        assertEquals("PASS", response.overallStatus());
        assertTrue(response.coverageComplete());
        assertTrue(response.implementedModules().contains("COMPOSE"));
        assertTrue(response.incompleteModules().isEmpty());
        assertTrue(response.notCheckedModules().isEmpty());
    }

    @Test
    void preservesSuccessfulFindingsWhenAnotherAnalyzerFails() {
        ProjectAnalysisService partiallyFailingService = new ProjectAnalysisService(
                new ProjectInputService(),
                List.of(new DockerfileProjectAnalyzer(new DockerfileAnalysisService()), failingAnalyzer("SECRETS"))
        );

        ProjectAnalysisResponse response = partiallyFailingService.analyzeFiles(List.of(
                textFile("Dockerfile", "FROM node:20\nCOPY . .\n")
        ), "demo");

        assertEquals("ERROR", response.overallStatus());
        assertFalse(response.coverageComplete());
        assertTrue(response.incompleteModules().contains("SECRETS"));
        assertTrue(response.findings().stream().anyMatch(finding ->
                "DOCKER-001".equals(finding.getRuleId())
        ));
        assertTrue(response.findings().stream().anyMatch(finding ->
                "DOCKER-006".equals(finding.getRuleId())
        ));
    }

    @Test
    void multipleComposeFilesAreAnalyzedIndependently() {
        ProjectAnalysisResponse response = service.analyzeFiles(List.of(
                textFile("compose.yaml", """
                        services:
                          app:
                            image: nginx:latest
                            privileged: true
                        """),
                textFile("docker-compose.yml", """
                        services:
                          app:
                            image: nginx:1.27
                            privileged: false
                            user: "1000:1000"
                            healthcheck:
                              test: ["CMD", "/usr/local/bin/healthcheck"]
                            restart: no
                        """)
        ), "demo");

        assertTrue(response.findings().stream().anyMatch(finding ->
                "COMPOSE.SUPPORT.FILE_NAME".equals(finding.getRuleId())
                        && "compose.yaml".equals(finding.getFilePath())
        ));
        assertTrue(response.findings().stream().anyMatch(finding ->
                "COMPOSE.SUPPORT.FILE_NAME".equals(finding.getRuleId())
                        && "docker-compose.yml".equals(finding.getFilePath())
        ));
        assertTrue(response.findings().stream().anyMatch(finding ->
                "COMPOSE-003".equals(finding.getRuleId())
                        && "FAIL".equals(finding.getStatus())
                        && "compose.yaml".equals(finding.getFilePath())
                        && "app".equals(finding.getServiceIdentifier())
        ));
        assertTrue(response.findings().stream().anyMatch(finding ->
                "COMPOSE-003".equals(finding.getRuleId())
                        && "PASS".equals(finding.getStatus())
                        && "docker-compose.yml".equals(finding.getFilePath())
                        && "app".equals(finding.getServiceIdentifier())
        ));
        assertEquals(2, response.inventory().stream()
                .filter(file -> file.analysisStatus() == FileAnalysisStatus.ANALYZED)
                .count());
    }

    private ProjectAnalyzer failingAnalyzer(String module) {
        return new ProjectAnalyzer() {
            @Override
            public String module() {
                return module;
            }

            @Override
            public List<Finding> analyze(ProjectContext context) {
                throw new IllegalStateException("sensitive internal detail");
            }
        };
    }

    private ProjectAnalyzer passingAnalyzer(String module) {
        return new ProjectAnalyzer() {
            @Override
            public String module() {
                return module;
            }

            @Override
            public List<Finding> analyze(ProjectContext context) {
                return List.of(new Finding(
                        "TEST.PASS", module, "Test Module", "PASS", "INFO",
                        "Test module passed.", "No action required.", "No action required.",
                        null, null, null, List.of()
                ));
            }

            @Override
            public List<ProjectAnalyzerFileResult> analyzedFiles(ProjectContext context, List<Finding> findings) {
                return context.files().stream()
                        .filter(ProjectFile::textContent)
                        .map(file -> new ProjectAnalyzerFileResult(file.path(), FileAnalysisStatus.ANALYZED))
                        .toList();
            }
        };
    }

    private MockMultipartFile textFile(String path, String content) {
        return new MockMultipartFile(
                "files",
                path,
                MediaType.TEXT_PLAIN_VALUE,
                content.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }
}
