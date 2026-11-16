package com.shipshape.backend.service;

import com.shipshape.backend.dto.DockerfileAnalysisResponse;
import com.shipshape.backend.dto.Finding;
import com.shipshape.backend.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DockerfileAnalysisServiceTest {

    private final DockerfileAnalysisService service = new DockerfileAnalysisService();

    @Test
    void rejectsNullContent() {
        assertThrows(BadRequestException.class, () -> service.analyze(null));
    }

    @Test
    void rejectsEmptyContent() {
        assertThrows(BadRequestException.class, () -> service.analyze(""));
    }

    @Test
    void rejectsWhitespaceOnlyContent() {
        assertThrows(BadRequestException.class, () -> service.analyze("  \n\t  \n"));
    }

    @Test
    void rejectsOversizedContent() {
        assertThrows(BadRequestException.class, () -> service.analyze("A".repeat(100_001)));
    }

    @Test
    void missingFromFailsAnalysis() {
        DockerfileAnalysisResponse response = service.analyze("USER node\nCOPY . .\n");

        assertEquals("FAIL", response.getOverallStatus());
        assertEquals("FAIL", finding(response, "FROM Instruction").getStatus());
    }

    @Test
    void commentsOnlyFailsBecauseFromIsMissing() {
        DockerfileAnalysisResponse response = service.analyze("# syntax=docker/dockerfile:1\n# no instructions\n");

        assertEquals("FAIL", response.getOverallStatus());
        assertEquals("FAIL", finding(response, "FROM Instruction").getStatus());
    }

    @Test
    void invalidFromFailsAnalysis() {
        DockerfileAnalysisResponse response = service.analyze(
                "FROM --platform=linux/amd64\nUSER node\nHEALTHCHECK CMD true\nCOPY . .\n"
        );

        assertEquals("FAIL", response.getOverallStatus());
        assertEquals("FAIL", finding(response, "FROM Instruction").getStatus());
    }

    @Test
    void validDockerfilePassesAllRules() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20-alpine
                WORKDIR /app
                COPY . .
                USER node
                HEALTHCHECK --interval=30s CMD node -e "process.exit(0)"
                CMD ["node", "server.js"]
                """);

        assertEquals("PASS", response.getOverallStatus());
        assertEquals("PASS", finding(response, "FROM Instruction").getStatus());
        assertEquals("PASS", finding(response, "Base Image Tag").getStatus());
        assertEquals("PASS", finding(response, "Final Stage User").getStatus());
        assertEquals("PASS", finding(response, "Health Check").getStatus());
        assertEquals("PASS", finding(response, "COPY and ADD Usage").getStatus());
    }

    @Test
    void explicitLatestTagWarns() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:latest
                USER node
                HEALTHCHECK CMD true
                COPY . .
                """);

        assertEquals("WARNING", response.getOverallStatus());
        assertEquals("WARNING", finding(response, "Base Image Tag").getStatus());
    }

    @Test
    void missingTagIsNotTreatedAsLatest() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node
                USER node
                HEALTHCHECK CMD true
                COPY . .
                """);

        Finding tagFinding = finding(response, "Base Image Tag");
        assertEquals("PASS", tagFinding.getStatus());
        assertEquals("PASS", response.getOverallStatus());
    }

    @Test
    void nonRootFinalStageUserPasses() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20
                USER appuser
                HEALTHCHECK CMD true
                COPY . .
                """);

        assertEquals("PASS", finding(response, "Final Stage User").getStatus());
    }

    @Test
    void userRootWarns() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20
                USER root
                HEALTHCHECK CMD true
                COPY . .
                """);

        assertEquals("WARNING", response.getOverallStatus());
        assertEquals("WARNING", finding(response, "Final Stage User").getStatus());
    }

    @Test
    void numericRootUserWarns() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20
                USER 0:0
                HEALTHCHECK CMD true
                COPY . .
                """);

        assertEquals("WARNING", response.getOverallStatus());
        assertEquals("WARNING", finding(response, "Final Stage User").getStatus());
    }

    @Test
    void userInBuilderStageDoesNotSatisfyFinalStage() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20 AS build
                USER node
                RUN npm run build
                FROM nginx:1.27
                HEALTHCHECK CMD true
                COPY --from=build /app/dist /usr/share/nginx/html
                """);

        assertEquals("WARNING", response.getOverallStatus());
        assertEquals("WARNING", finding(response, "Final Stage User").getStatus());
    }

    @Test
    void missingUserWarns() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20
                HEALTHCHECK CMD true
                COPY . .
                """);

        assertEquals("WARNING", response.getOverallStatus());
        assertEquals("WARNING", finding(response, "Final Stage User").getStatus());
    }

    @Test
    void missingHealthcheckWarns() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20
                USER node
                COPY . .
                """);

        assertEquals("WARNING", response.getOverallStatus());
        assertEquals("WARNING", finding(response, "Health Check").getStatus());
    }

    @Test
    void healthcheckNoneWarns() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20
                USER node
                HEALTHCHECK NONE
                COPY . .
                """);

        assertEquals("WARNING", response.getOverallStatus());
        assertEquals("WARNING", finding(response, "Health Check").getStatus());
    }

    @Test
    void healthcheckCmdWithOptionsPasses() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20
                USER node
                HEALTHCHECK --interval=30s --timeout=5s CMD true
                COPY . .
                """);

        assertEquals("PASS", finding(response, "Health Check").getStatus());
    }

    @Test
    void healthcheckInBuilderStageDoesNotSatisfyFinalStage() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20 AS build
                HEALTHCHECK CMD true
                RUN npm run build
                FROM nginx:1.27
                USER nginx
                COPY --from=build /app/dist /usr/share/nginx/html
                """);

        assertEquals("WARNING", response.getOverallStatus());
        assertEquals("WARNING", finding(response, "Health Check").getStatus());
    }

    @Test
    void copyOnlyPasses() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20
                USER node
                HEALTHCHECK CMD true
                COPY package.json .
                """);

        assertEquals("PASS", finding(response, "COPY and ADD Usage").getStatus());
    }

    @Test
    void addWarnsButDoesNotFail() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM nginx:1.27
                USER nginx
                HEALTHCHECK CMD true
                ADD app.tar.gz /usr/share/nginx/html
                """);

        assertEquals("WARNING", response.getOverallStatus());
        assertEquals("WARNING", finding(response, "COPY and ADD Usage").getStatus());
    }

    @Test
    void noCopyOrAddPassesTransferRule() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM nginx:1.27
                USER nginx
                HEALTHCHECK CMD true
                """);

        assertEquals("PASS", finding(response, "COPY and ADD Usage").getStatus());
    }

    @Test
    void commentsBlankLinesAndCaseInsensitiveInstructionsAreSupported() {
        DockerfileAnalysisResponse response = service.analyze("""
                # Base image
                from node:20

                  # Run as a non-root user
                  user node
                HEALTHCHECK CMD true

                copy . .
                """);

        assertEquals("PASS", response.getOverallStatus());
    }

    @Test
    void continuationLinesAreParsed() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20
                USER node
                HEALTHCHECK CMD true
                COPY \\
                  package.json \\
                  .
                """);

        assertEquals("PASS", response.getOverallStatus());
        assertEquals("PASS", finding(response, "COPY and ADD Usage").getStatus());
    }

    @Test
    void crlfLineEndingsAreSupported() {
        DockerfileAnalysisResponse response = service.analyze(
                "FROM node:20\r\nUSER node\r\nHEALTHCHECK CMD true\r\nCOPY . .\r\n"
        );

        assertEquals("PASS", response.getOverallStatus());
    }

    @Test
    void explicitLatestInAnyStageWarns() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:latest AS build
                RUN npm run build
                FROM nginx:1.27
                USER nginx
                HEALTHCHECK CMD true
                COPY --from=build /app/dist /usr/share/nginx/html
                """);

        assertEquals("WARNING", response.getOverallStatus());
        assertEquals("WARNING", finding(response, "Base Image Tag").getStatus());
    }

    private Finding finding(DockerfileAnalysisResponse response, String name) {
        assertNotNull(response.getFindings());
        return response.getFindings().stream()
                .filter(finding -> name.equals(finding.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing finding: " + name));
    }
}
