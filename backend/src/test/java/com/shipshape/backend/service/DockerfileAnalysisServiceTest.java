package com.shipshape.backend.service;

import com.shipshape.backend.dto.DockerfileAnalysisResponse;
import com.shipshape.backend.dto.Finding;
import com.shipshape.backend.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals("FAIL", status(response, "FROM Instruction"));
    }

    @Test
    void commentsOnlyFailsBecauseFromIsMissing() {
        DockerfileAnalysisResponse response = service.analyze("# syntax=docker/dockerfile:1\n# no instructions\n");

        assertEquals("FAIL", response.getOverallStatus());
        assertEquals("FAIL", status(response, "FROM Instruction"));
    }

    @Test
    void invalidFromFailsAnalysis() {
        DockerfileAnalysisResponse response = service.analyze(
                "FROM --platform=linux/amd64\nUSER node\nHEALTHCHECK CMD true\nCOPY . .\n"
        );

        assertEquals("FAIL", response.getOverallStatus());
        assertEquals("FAIL", status(response, "FROM Instruction"));
    }

    @Test
    void validDockerfilePassesAllRules() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20-alpine
                WORKDIR /app
                COPY package.json .
                USER node
                HEALTHCHECK CMD true
                CMD ["node", "server.js"]
                """);

        assertEquals("PASS", response.getOverallStatus());
        assertEquals("PASS", status(response, "FROM Instruction"));
        assertEquals("PASS", status(response, "Base Image Tag"));
        assertEquals("PASS", status(response, "Final Stage User"));
        assertEquals("PASS", status(response, "Working Directory"));
        assertEquals("PASS", status(response, "Startup Command"));
        assertEquals("PASS", status(response, "COPY and ADD Usage"));
        assertEquals("PASS", status(response, "Dangerous Permissions"));
        assertEquals("PASS", status(response, "Health Check"));
    }

    @Test
    void explicitLatestTagWarns() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:latest
                WORKDIR /app
                USER node
                HEALTHCHECK CMD true
                COPY package.json .
                CMD ["node", "server.js"]
                """);

        assertEquals("WARNING", response.getOverallStatus());
        assertEquals("WARNING", status(response, "Base Image Tag"));
    }

    @Test
    void missingTagWarns() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node
                WORKDIR /app
                USER node
                HEALTHCHECK CMD true
                COPY package.json .
                CMD ["node", "server.js"]
                """);

        assertEquals("WARNING", status(response, "Base Image Tag"));
        assertTrue(finding(response, "Base Image Tag").getLimitations().stream()
                .anyMatch(value -> value.contains("mutable")));
    }

    @Test
    void variableBaseImageIsNotChecked() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM ${BASE_IMAGE}
                WORKDIR /app
                USER node
                HEALTHCHECK CMD true
                COPY package.json .
                CMD ["node", "server.js"]
                """);

        assertEquals("NOT_CHECKED", status(response, "Base Image Tag"));
        assertEquals("NOT_CHECKED", response.getOverallStatus());
    }

    @Test
    void digestPinnedBaseImagePasses() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                WORKDIR /app
                USER node
                HEALTHCHECK CMD true
                COPY package.json .
                CMD ["node", "server.js"]
                """);

        assertEquals("PASS", status(response, "Base Image Tag"));
    }

    @Test
    void scratchBaseImagePassesTagRule() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM scratch
                USER 10001
                HEALTHCHECK CMD true
                COPY app /app
                CMD ["/app"]
                """);

        assertEquals("PASS", status(response, "Base Image Tag"));
    }

    @Test
    void registryPortIsNotMistakenForTag() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM registry.example.com:5000/node:20-alpine
                WORKDIR /app
                USER node
                HEALTHCHECK CMD true
                COPY package.json .
                CMD ["node", "server.js"]
                """);

        assertEquals("PASS", status(response, "Base Image Tag"));
    }

    @Test
    void registryPortWithoutTagWarns() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM registry.example.com:5000/node
                WORKDIR /app
                USER node
                HEALTHCHECK CMD true
                COPY package.json .
                CMD ["node", "server.js"]
                """);

        assertEquals("WARNING", status(response, "Base Image Tag"));
    }

    @Test
    void nonRootFinalStageUserPasses() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20
                WORKDIR /app
                USER node
                HEALTHCHECK CMD true
                COPY package.json .
                CMD ["node", "server.js"]
                """);

        assertEquals("PASS", status(response, "Final Stage User"));
    }

    @Test
    void userRootWarns() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20
                USER root
                WORKDIR /app
                HEALTHCHECK CMD true
                COPY package.json .
                CMD ["node", "server.js"]
                """);

        assertEquals("WARNING", status(response, "Final Stage User"));
    }

    @Test
    void numericRootUserWarns() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20
                USER 0:0
                WORKDIR /app
                HEALTHCHECK CMD true
                COPY package.json .
                CMD ["node", "server.js"]
                """);

        assertEquals("WARNING", status(response, "Final Stage User"));
    }

    @Test
    void variableUserIsNotChecked() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20
                USER ${APP_USER}
                WORKDIR /app
                HEALTHCHECK CMD true
                COPY package.json .
                CMD ["node", "server.js"]
                """);

        assertEquals("NOT_CHECKED", status(response, "Final Stage User"));
    }

    @Test
    void missingUserIsNotCheckedInsteadOfDefiniteRoot() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20
                WORKDIR /app
                HEALTHCHECK CMD true
                COPY package.json .
                CMD ["node", "server.js"]
                """);

        assertEquals("NOT_CHECKED", status(response, "Final Stage User"));
        assertTrue(finding(response, "Final Stage User").getLimitations().stream()
                .anyMatch(value -> value.contains("root")));
    }

    @Test
    void userInBuilderStageDoesNotSatisfyUnrelatedFinalStage() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20 AS build
                USER node
                RUN npm run build
                FROM nginx:1.27
                WORKDIR /app
                HEALTHCHECK CMD true
                COPY --from=build /app/dist /usr/share/nginx/html
                CMD ["nginx", "-g", "daemon off;"]
                """);

        assertEquals("NOT_CHECKED", status(response, "Final Stage User"));
    }

    @Test
    void inheritedStageUserWorkingDirectoryAndCommandAreRecognized() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20 AS build
                USER node
                WORKDIR /app
                CMD ["node", "server.js"]
                FROM build AS runtime
                HEALTHCHECK CMD true
                COPY package.json .
                """);

        assertEquals("PASS", status(response, "Final Stage User"));
        assertEquals("PASS", status(response, "Working Directory"));
        assertEquals("PASS", status(response, "Startup Command"));
        assertEquals("PASS", status(response, "Base Image Tag"));
    }

    @Test
    void missingWorkingDirectoryWarns() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20
                USER node
                HEALTHCHECK CMD true
                COPY package.json .
                CMD ["node", "server.js"]
                """);

        assertEquals("WARNING", status(response, "Working Directory"));
    }

    @Test
    void missingStartupCommandWarns() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20
                WORKDIR /app
                USER node
                HEALTHCHECK CMD true
                COPY package.json .
                """);

        assertEquals("WARNING", status(response, "Startup Command"));
    }

    @Test
    void entrypointSatisfiesStartupCommandRule() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20
                WORKDIR /app
                USER node
                HEALTHCHECK CMD true
                COPY package.json .
                ENTRYPOINT ["node", "server.js"]
                """);

        assertEquals("PASS", status(response, "Startup Command"));
    }

    @Test
    void copyOnlyPasses() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20
                WORKDIR /app
                USER node
                HEALTHCHECK CMD true
                COPY package.json .
                CMD ["node", "server.js"]
                """);

        assertEquals("PASS", status(response, "COPY and ADD Usage"));
    }

    @Test
    void broadCopyWithoutDockerignoreWarns() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20
                WORKDIR /app
                USER node
                HEALTHCHECK CMD true
                COPY . .
                CMD ["node", "server.js"]
                """);

        assertEquals("WARNING", status(response, "COPY and ADD Usage"));
        assertTrue(finding(response, "COPY and ADD Usage").getLimitations().stream()
                .anyMatch(value -> value.contains(".dockerignore")));
    }

    @Test
    void broadCopyWithDockerignorePassesButLimitsClaim() {
        DockerfileAnalysisResponse response = service.analyze("src/Dockerfile", """
                FROM node:20
                WORKDIR /app
                USER node
                HEALTHCHECK CMD true
                COPY . .
                CMD ["node", "server.js"]
                """, true);

        assertEquals("PASS", status(response, "COPY and ADD Usage"));
        assertTrue(finding(response, "COPY and ADD Usage").getLimitations().stream()
                .anyMatch(value -> value.contains("rules were not evaluated")));
    }

    @Test
    void addWarnsButDoesNotFail() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM nginx:1.27
                WORKDIR /app
                USER nginx
                HEALTHCHECK CMD true
                ADD app.tar.gz /usr/share/nginx/html
                CMD ["nginx", "-g", "daemon off;"]
                """);

        assertEquals("WARNING", status(response, "COPY and ADD Usage"));
    }

    @Test
    void remoteAddWarnsAndNeverReturnsSource() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM nginx:1.27
                WORKDIR /app
                USER nginx
                HEALTHCHECK CMD true
                ADD https://user:supersecret@example.com/app.tar.gz /usr/share/nginx/html
                CMD ["nginx", "-g", "daemon off;"]
                """);

        assertEquals("WARNING", status(response, "COPY and ADD Usage"));
        assertFalse(response.getFindings().stream()
                .anyMatch(finding -> finding.getMaskedEvidence() != null
                        && (finding.getMaskedEvidence().contains("supersecret")
                        || finding.getMaskedEvidence().contains("example.com"))));
    }

    @Test
    void noCopyOrAddPassesTransferRule() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM nginx:1.27
                WORKDIR /app
                USER nginx
                HEALTHCHECK CMD true
                CMD ["nginx", "-g", "daemon off;"]
                """);

        assertEquals("PASS", status(response, "COPY and ADD Usage"));
    }

    @Test
    void dangerousRunPermissionWarns() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20
                WORKDIR /app
                USER node
                HEALTHCHECK CMD true
                RUN chmod -R 777 /app
                COPY package.json .
                CMD ["node", "server.js"]
                """);

        assertEquals("WARNING", status(response, "Dangerous Permissions"));
    }

    @Test
    void dangerousCopyPermissionWarns() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20
                WORKDIR /app
                USER node
                HEALTHCHECK CMD true
                COPY --chmod=0777 package.json .
                CMD ["node", "server.js"]
                """);

        assertEquals("WARNING", status(response, "Dangerous Permissions"));
    }

    @Test
    void noDangerousPermissionPasses() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20
                WORKDIR /app
                USER node
                HEALTHCHECK CMD true
                COPY package.json .
                CMD ["node", "server.js"]
                """);

        assertEquals("PASS", status(response, "Dangerous Permissions"));
    }

    @Test
    void missingHealthcheckWarns() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20
                WORKDIR /app
                USER node
                COPY package.json .
                CMD ["node", "server.js"]
                """);

        assertEquals("WARNING", status(response, "Health Check"));
    }

    @Test
    void healthcheckNoneWarns() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20
                WORKDIR /app
                USER node
                HEALTHCHECK NONE
                COPY package.json .
                CMD ["node", "server.js"]
                """);

        assertEquals("WARNING", status(response, "Health Check"));
    }

    @Test
    void healthcheckCmdWithOptionsPasses() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20
                WORKDIR /app
                USER node
                HEALTHCHECK --interval=30s --timeout=5s CMD true
                COPY package.json .
                CMD ["node", "server.js"]
                """);

        assertEquals("PASS", status(response, "Health Check"));
    }

    @Test
    void unrecognizedHealthcheckFormWarns() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20
                WORKDIR /app
                USER node
                HEALTHCHECK curl localhost
                COPY package.json .
                CMD ["node", "server.js"]
                """);

        assertEquals("WARNING", status(response, "Health Check"));
    }

    @Test
    void healthcheckInBuilderStageDoesNotSatisfyUnrelatedFinalStage() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20 AS build
                HEALTHCHECK CMD true
                RUN npm run build
                FROM nginx:1.27
                WORKDIR /app
                USER nginx
                COPY --from=build /app/dist /usr/share/nginx/html
                CMD ["nginx", "-g", "daemon off;"]
                """);

        assertEquals("WARNING", status(response, "Health Check"));
    }

    @Test
    void healthcheckIsInheritedWhenFinalStageExtendsBuilder() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20 AS build
                HEALTHCHECK CMD true
                FROM build AS runtime
                WORKDIR /app
                USER node
                COPY package.json .
                CMD ["node", "server.js"]
                """);

        assertEquals("PASS", status(response, "Health Check"));
    }

    @Test
    void unsupportedSyntaxIsExplicitlyNotChecked() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROBNICATE hidden-value
                FROM node:20
                WORKDIR /app
                USER node
                HEALTHCHECK CMD true
                COPY package.json .
                CMD ["node", "server.js"]
                """);

        assertEquals("NOT_CHECKED", status(response, "Unsupported Syntax"));
        assertEquals("NOT_CHECKED", response.getOverallStatus());
        assertEquals("FROBNICATE [line 1]", finding(response, "Unsupported Syntax").getMaskedEvidence());
    }

    @Test
    void commentsBlankLinesAndCaseInsensitiveInstructionsAreSupported() {
        DockerfileAnalysisResponse response = service.analyze("""
                # Base image
                from node:20

                  # Run as a non-root user
                  user node
                workdir /app
                healthcheck CMD true

                copy package.json .
                cmd ["node", "server.js"]
                """);

        assertEquals("PASS", response.getOverallStatus());
    }

    @Test
    void continuationLinesAreParsed() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20
                USER node
                WORKDIR /app
                HEALTHCHECK CMD true
                COPY \\
                  package.json \\
                  .
                CMD ["node", "server.js"]
                """);

        assertEquals("PASS", response.getOverallStatus());
        assertEquals("PASS", status(response, "COPY and ADD Usage"));
    }

    @Test
    void crlfLineEndingsAreSupported() {
        DockerfileAnalysisResponse response = service.analyze(
                "FROM node:20\r\nWORKDIR /app\r\nUSER node\r\nHEALTHCHECK CMD true\r\nCOPY package.json .\r\nCMD [\"node\", \"server.js\"]\r\n"
        );

        assertEquals("PASS", response.getOverallStatus());
    }

    @Test
    void explicitLatestInAnyStageWarns() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:latest AS build
                RUN npm run build
                FROM nginx:1.27
                WORKDIR /app
                USER nginx
                HEALTHCHECK CMD true
                COPY --from=build /app/dist /usr/share/nginx/html
                CMD ["nginx", "-g", "daemon off;"]
                """);

        assertEquals("WARNING", response.getOverallStatus());
        assertEquals("WARNING", status(response, "Base Image Tag"));
    }

    @Test
    void enrichedFindingsIncludeRuleMetadata() {
        DockerfileAnalysisResponse response = service.analyze("src/Dockerfile", """
                FROM node:20
                COPY package.json .
                """);

        Finding fromFinding = finding(response, "FROM Instruction");
        assertEquals("DOCKER-001", fromFinding.getRuleId());
        assertEquals("DOCKER-002", finding(response, "Base Image Tag").getRuleId());
        assertEquals("DOCKERFILE", fromFinding.getCategory());
        assertEquals("src/Dockerfile", fromFinding.getFilePath());
        assertEquals(Integer.valueOf(1), fromFinding.getLineNumber());
        assertNotNull(fromFinding.getMaskedEvidence());
        assertEquals("FROM [line 1]", fromFinding.getMaskedEvidence());
        assertFalse(fromFinding.getLimitations().isEmpty());
        assertTrue(fromFinding.getSeverity() != null && !fromFinding.getSeverity().isBlank());
    }

    @Test
    void evidenceNeverIncludesInstructionArgumentsOrSecrets() {
        DockerfileAnalysisResponse response = service.analyze("Dockerfile", """
                FROM --platform=linux/amd64 node:20
                ENV API_TOKEN=supersecret-value
                ARG PASSWORD="quoted secret"
                WORKDIR /app
                COPY {"token":"json-secret"} .
                RUN echo 'shell-secret' > /app/secret.txt
                USER node
                HEALTHCHECK CMD true
                ADD https://user:embedded-secret@example.com/app.tar.gz /app
                CMD ["node", "server.js"]
                """);

        for (Finding finding : response.getFindings()) {
            String evidence = finding.getMaskedEvidence();
            if (evidence != null) {
                assertFalse(evidence.contains("supersecret-value"), evidence);
                assertFalse(evidence.contains("quoted secret"), evidence);
                assertFalse(evidence.contains("json-secret"), evidence);
                assertFalse(evidence.contains("shell-secret"), evidence);
                assertFalse(evidence.contains("embedded-secret"), evidence);
                assertFalse(evidence.contains("example.com"), evidence);
                assertFalse(evidence.contains("node:20"), evidence);
                assertFalse(evidence.contains("/app"), evidence);
            }
        }
    }

    @Test
    void fixtureARepresentsHardenedDockerfile() throws Exception {
        String dockerfile = Files.readString(Path.of("src/test/resources/dockerfile-analysis/fixture-a/Dockerfile"));
        String dockerignore = Files.readString(Path.of("src/test/resources/dockerfile-analysis/fixture-a/.dockerignore"));

        DockerfileAnalysisResponse response = service.analyze("fixture-a/Dockerfile", dockerfile, !dockerignore.isBlank());

        assertEquals("PASS", response.getOverallStatus());
    }

    @Test
    void fixtureBRepresentsLegacyDockerfileWithExplicitWarnings() throws Exception {
        String dockerfile = Files.readString(Path.of("src/test/resources/dockerfile-analysis/fixture-b/Dockerfile"));

        DockerfileAnalysisResponse response = service.analyze("fixture-b/Dockerfile", dockerfile, false);

        assertEquals("WARNING", response.getOverallStatus());
        assertEquals("WARNING", status(response, "Base Image Tag"));
        assertEquals("NOT_CHECKED", status(response, "Final Stage User"));
        assertEquals("WARNING", status(response, "Working Directory"));
        assertEquals("WARNING", status(response, "Startup Command"));
        assertEquals("WARNING", status(response, "COPY and ADD Usage"));
        assertEquals("WARNING", status(response, "Dangerous Permissions"));
        assertEquals("WARNING", status(response, "Health Check"));
    }

    @Test
    void detectsRootGroupNotationAsRootUser() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20-alpine
                USER root:root
                WORKDIR /app
                HEALTHCHECK CMD true
                COPY package.json .
                CMD ["node", "server.js"]
                """);

        assertEquals("WARNING", status(response, "Final Stage User"));
    }

    @Test
    void malformedFromAliasFailsTheFromRule() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20-alpine AS
                USER node
                WORKDIR /app
                HEALTHCHECK CMD true
                COPY package.json .
                CMD ["node", "server.js"]
                """);

        assertEquals("FAIL", status(response, "FROM Instruction"));
    }

    @Test
    void emptyStructuralInstructionsDoNotPassAsExplicitConfiguration() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM node:20-alpine
                WORKDIR
                USER node
                HEALTHCHECK
                ENTRYPOINT
                COPY package.json .
                """);

        assertEquals("WARNING", status(response, "Working Directory"));
        assertEquals("WARNING", status(response, "Startup Command"));
        assertEquals("WARNING", status(response, "Health Check"));
    }

    @Test
    void forwardBuildStageAliasIsNotTreatedAsAnInheritedParent() {
        DockerfileAnalysisResponse response = service.analyze("""
                FROM app AS runtime
                USER node
                WORKDIR /app
                HEALTHCHECK CMD true
                CMD ["node", "server.js"]
                FROM node:20-alpine AS app
                USER root
                """);

        assertEquals("PASS", status(response, "FROM Instruction"));
        assertEquals("NOT_CHECKED", status(response, "Base Image Tag"));
    }

    private String status(DockerfileAnalysisResponse response, String name) {
        return finding(response, name).getStatus();
    }

    private Finding finding(DockerfileAnalysisResponse response, String name) {
        assertNotNull(response.getFindings());
        return response.getFindings().stream()
                .filter(finding -> name.equals(finding.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing finding: " + name));
    }
}
