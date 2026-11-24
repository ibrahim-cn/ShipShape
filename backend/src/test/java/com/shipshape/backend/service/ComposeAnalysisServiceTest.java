package com.shipshape.backend.service;

import com.shipshape.backend.dto.ComposeAnalysisResponse;
import com.shipshape.backend.dto.Finding;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComposeAnalysisServiceTest {

    private final ComposeAnalysisService service = new ComposeAnalysisService();

    @Test
    void recognizesAllConventionalComposeFileNames() {
        String content = """
                services:
                  api:
                    image: alpine:3.20
                    user: "1000:1000"
                    restart: no
                    healthcheck:
                      test: ["CMD", "/usr/local/bin/healthcheck"]
                """;

        for (String path : List.of(
                "compose.yaml", "nested/compose.yml", "deploy/docker-compose.yaml", "docker-compose.yml"
        )) {
            ComposeAnalysisResponse response = service.analyze(path, content);
            assertEquals("PASS", status(response, "COMPOSE.SUPPORT.FILE_NAME"));
        }
    }

    @Test
    void fixtureARepresentsCorrectedComposeFile() throws Exception {
        String content = Files.readString(Path.of("src/test/resources/compose-analysis/fixture-a/compose.yaml"));

        ComposeAnalysisResponse response = service.analyze("fixture-a/compose.yaml", content);

        assertEquals("PASS", response.getOverallStatus());
        assertEquals("PASS", status(response, "COMPOSE-001"));
        for (String ruleId : List.of(
                "COMPOSE-002", "COMPOSE-003", "COMPOSE-004", "COMPOSE-005", "COMPOSE-006",
                "COMPOSE-007", "COMPOSE-008", "COMPOSE-009", "COMPOSE-010"
        )) {
            assertTrue(rules(response, ruleId).stream().allMatch(finding -> "PASS".equals(finding.getStatus())),
                    ruleId + " should pass for the corrected fixture");
        }
    }

    @Test
    void fixtureBReportsComparableRisksWithoutSecretValues() throws Exception {
        String content = Files.readString(Path.of(
                "src/test/resources/compose-analysis/fixture-b/docker-compose.yml"));

        ComposeAnalysisResponse response = service.analyze("fixture-b/docker-compose.yml", content);

        assertEquals("FAIL", response.getOverallStatus());
        assertEquals("WARNING", serviceRule(response, "COMPOSE-002", "db").getStatus());
        assertEquals("FAIL", serviceRule(response, "COMPOSE-004", "db").getStatus());
        assertEquals("FAIL", serviceRule(response, "COMPOSE-003", "api").getStatus());
        assertEquals("FAIL", serviceRule(response, "COMPOSE-005", "api").getStatus());
        assertEquals("WARNING", serviceRule(response, "COMPOSE-006", "api").getStatus());
        assertEquals("WARNING", serviceRule(response, "COMPOSE-007", "db").getStatus());
        assertEquals("WARNING", serviceRule(response, "COMPOSE-008", "db").getStatus());
        assertEquals("FAIL", serviceRule(response, "COMPOSE-008", "worker").getStatus());
        assertEquals("WARNING", serviceRule(response, "COMPOSE-009", "worker").getStatus());
        assertEquals("FAIL", serviceRule(response, "COMPOSE-010", "api").getStatus());
        assertEquals("FAIL", serviceRule(response, "COMPOSE.SUPPORT.ENVIRONMENT_SECRETS", "db").getStatus());

        assertFalse(serialize(response).contains("hunter2"));
    }

    @Test
    void missingServicesFailsInsteadOfPassing() {
        ComposeAnalysisResponse response = service.analyze("compose.yaml", "version: \"3.9\"\n");

        assertEquals("FAIL", status(response, "COMPOSE-001"));
        assertTrue(rules(response, "COMPOSE-002").isEmpty());
        assertTrue(rules(response, "COMPOSE-010").isEmpty());
    }

    @Test
    void multipleDocumentsFailInsteadOfClaimingPass() {
        ComposeAnalysisResponse response = service.analyze("compose.yaml", """
                services:
                  api:
                    image: alpine:3.20
                ---
                services:
                  api:
                    image: alpine:3.21
                """);

        assertEquals("FAIL", status(response, "COMPOSE.SUPPORT.DOCUMENT_COUNT"));
        assertTrue(rules(response, "COMPOSE-002").isEmpty());
    }

    @Test
    void buildOnlyServiceDoesNotClaimImageTagPass() {
        ComposeAnalysisResponse response = service.analyze("compose.yaml", """
                services:
                  worker:
                    build:
                      context: ./worker
                """);

        assertEquals("PASS", status(response, "COMPOSE.SUPPORT.IMAGE_OR_BUILD"));
        assertEquals("NOT_CHECKED", serviceRule(response, "COMPOSE-002", "worker").getStatus());
    }

    @Test
    void envFileWithoutContentsIsNotChecked() {
        ComposeAnalysisResponse response = service.analyze("compose.yaml", """
                services:
                  api:
                    image: alpine:3.20
                    env_file: .env
                """);

        assertEquals("NOT_CHECKED", serviceRule(response, "COMPOSE.SUPPORT.ENVIRONMENT_SECRETS", "api").getStatus());
    }

    @Test
    void unsafeYamlTagProducesParseErrorWithoutLeakingParserDetails() {
        ComposeAnalysisResponse response = service.analyze("compose.yaml", """
                services:
                  api:
                    image: !!python/object/apply:os.system ["echo leaked_secret"]
                """);

        Finding parseFinding = rule(response, "COMPOSE.PARSE_ERROR");
        assertEquals("ERROR", parseFinding.getStatus());
        String serialized = parseFinding.getExplanation() + parseFinding.getMaskedEvidence();
        assertFalse(serialized.contains("python"));
        assertFalse(serialized.contains("os.system"));
        assertFalse(serialized.contains("leaked_secret"));
    }

    @Test
    void excessiveAliasesProduceParseError() {
        StringBuilder content = new StringBuilder("""
                services:
                  base: &base
                    image: alpine:3.20
                """);
        for (int index = 0; index < 20; index++) {
            content.append("  service").append(index).append(":\n    <<: *base\n");
        }

        ComposeAnalysisResponse response = service.analyze("compose.yaml", content.toString());

        assertEquals("ERROR", status(response, "COMPOSE.PARSE_ERROR"));
    }

    @Test
    void excessiveNestingProducesParseError() {
        StringBuilder content = new StringBuilder("""
                services:
                  api:
                    image: alpine:3.20
                metadata:
                """);
        for (int index = 0; index < 25; index++) {
            content.append("  ".repeat(index + 1)).append("level").append(index).append(":\n");
        }
        content.append("  ".repeat(26)).append("value: end\n");

        ComposeAnalysisResponse response = service.analyze("compose.yaml", content.toString());

        assertEquals("ERROR", status(response, "COMPOSE.PARSE_ERROR"));
    }

    @Test
    void oversizedComposeFileProducesParseError() {
        ComposeAnalysisResponse response = service.analyze("compose.yaml", "a".repeat(200_001));

        assertEquals("ERROR", status(response, "COMPOSE.PARSE_ERROR"));
    }

    @Test
    void findingsIncludeRuleMetadataAndSafeEvidence() {
        ComposeAnalysisResponse response = service.analyze("nested/compose.yaml", """
                services:
                  api:
                    image: nginx:latest
                    privileged: true
                    restart: always
                    healthcheck:
                      test: ["CMD", "/usr/local/bin/healthcheck"]
                """);

        Finding privileged = serviceRule(response, "COMPOSE-003", "api");
        assertEquals("COMPOSE-003", privileged.getRuleId());
        assertEquals("COMPOSE", privileged.getCategory());
        assertEquals("nested/compose.yaml", privileged.getFilePath());
        assertEquals("api", privileged.getServiceIdentifier());
        assertEquals(Integer.valueOf(4), privileged.getLineNumber());
        assertNotNull(privileged.getMaskedEvidence());
        assertTrue(privileged.getMaskedEvidence().contains("privileged"));
        assertFalse(privileged.getMaskedEvidence().contains("true"));
        assertFalse(privileged.getLimitations().isEmpty());
    }

    @Test
    void rootUserVariantsProduceFindings() {
        ComposeAnalysisResponse response = service.analyze("compose.yaml", """
                services:
                  named:
                    image: alpine:3.20
                    user: root
                  numeric:
                    image: alpine:3.20
                    user: 0
                  quoted:
                    image: alpine:3.20
                    user: "0"
                  pair:
                    image: alpine:3.20
                    user: root:root
                  numericpair:
                    image: alpine:3.20
                    user: "0:0"
                  mixedgroup:
                    image: alpine:3.20
                    user: root:app
                """);

        for (String serviceName : List.of("named", "numeric", "quoted", "pair", "numericpair", "mixedgroup")) {
            Finding finding = serviceRule(response, "COMPOSE-004", serviceName);
            assertEquals("FAIL", finding.getStatus(), serviceName);
            assertEquals("HIGH", finding.getSeverity(), serviceName);
        }
    }

    @Test
    void nonRootUserPassesAndMissingUserIsNotChecked() {
        ComposeAnalysisResponse response = service.analyze("compose.yaml", """
                services:
                  explicit:
                    image: alpine:3.20
                    user: "10001:10001"
                  inherited:
                    image: alpine:3.20
                """);

        assertEquals("PASS", serviceRule(response, "COMPOSE-004", "explicit").getStatus());
        Finding missing = serviceRule(response, "COMPOSE-004", "inherited");
        assertEquals("NOT_CHECKED", missing.getStatus());
        assertTrue(missing.getExplanation().contains("image may define a non-root user"));
    }

    @Test
    void unresolvedUserIsNotChecked() {
        ComposeAnalysisResponse response = service.analyze("compose.yaml", """
                services:
                  api:
                    image: alpine:3.20
                    user: ${APP_USER}
                """);

        assertEquals("NOT_CHECKED", serviceRule(response, "COMPOSE-004", "api").getStatus());
    }

    @Test
    void dockerSocketMountsInShortAndLongSyntaxAreDetected() {
        ComposeAnalysisResponse response = service.analyze("compose.yaml", """
                services:
                  short:
                    image: alpine:3.20
                    volumes:
                      - /var/run/docker.sock:/var/run/docker.sock
                  long:
                    image: alpine:3.20
                    volumes:
                      - type: bind
                        source: /run/docker.sock
                        target: /var/run/docker.sock
                        read_only: true
                  alternate:
                    image: alpine:3.20
                    volumes:
                      - /run/docker.sock:/var/run/docker.sock:ro
                """);

        for (String serviceName : List.of("short", "long", "alternate")) {
            Finding finding = serviceRule(response, "COMPOSE-005", serviceName);
            assertEquals("FAIL", finding.getStatus(), serviceName);
            assertEquals("HIGH", finding.getSeverity(), serviceName);
            assertTrue(rules(response, "COMPOSE-010").stream()
                    .noneMatch(candidate -> serviceName.equals(candidate.getServiceIdentifier())
                            && "FAIL".equals(candidate.getStatus())));
        }
    }

    @Test
    void readOnlyDockerSocketMountStillFails() {
        ComposeAnalysisResponse response = service.analyze("compose.yaml", """
                services:
                  api:
                    image: alpine:3.20
                    volumes:
                      - /var/run/docker.sock:/var/run/docker.sock:ro
                """);

        Finding finding = serviceRule(response, "COMPOSE-005", "api");
        assertEquals("FAIL", finding.getStatus());
        assertTrue(finding.getLimitations().stream().anyMatch(value -> value.contains("read-only filesystem")));
    }

    @Test
    void variableSocketLikeMountsAreNotCheckedAndConfirmedSocketsStillFail() {
        ComposeAnalysisResponse response = service.analyze("compose.yaml", """
                services:
                  bare:
                    image: alpine:3.20
                    volumes:
                      - ${DOCKER_SOCKET}:/socket
                  rundirvar:
                    image: alpine:3.20
                    volumes:
                      - /run/${SOCKET_NAME}:/socket
                  sockdirvar:
                    image: alpine:3.20
                    volumes:
                      - /var/run/${SOCKET_NAME}:/socket
                  generic:
                    image: alpine:3.20
                    volumes:
                      - ${DATA_DIR}:/data
                  confirmed:
                    image: alpine:3.20
                    volumes:
                      - /run/docker.sock:/var/run/docker.sock
                """);

        for (String serviceName : List.of("bare", "rundirvar", "sockdirvar")) {
            assertEquals("NOT_CHECKED", serviceRule(response, "COMPOSE-005", serviceName).getStatus(), serviceName);
        }
        assertEquals("PASS", serviceRule(response, "COMPOSE-005", "generic").getStatus());
        assertEquals("FAIL", serviceRule(response, "COMPOSE-005", "confirmed").getStatus());
    }

    @Test
    void publishedPortsDistinguishBindingsDynamicPortsAndExpose() {
        ComposeAnalysisResponse response = service.analyze("compose.yaml", """
                services:
                  dynamicport:
                    image: alpine:3.20
                    ports:
                      - "3000"
                  exposeonly:
                    image: alpine:3.20
                    expose:
                      - "3000"
                  containeronly:
                    image: alpine:3.20
                    ports:
                      - target: 8080
                        published: false
                  loopback:
                    image: alpine:3.20
                    ports:
                      - "127.0.0.1:8080:80"
                  all:
                    image: alpine:3.20
                    ports:
                      - "8080:80"
                  ipv6loopback:
                    image: alpine:3.20
                    ports:
                      - "[::1]:8080:80"
                  ipv6all:
                    image: alpine:3.20
                    ports:
                      - "[::]:8080:80"
                  specific:
                    image: alpine:3.20
                    ports:
                      - "192.168.1.10:8080:80"
                """);

        Finding dynamicPort = serviceRule(response, "COMPOSE-007", "dynamicport");
        assertEquals("WARNING", dynamicPort.getStatus());
        assertEquals("MEDIUM", dynamicPort.getSeverity());
        assertTrue(dynamicPort.getExplanation().contains("dynamically assigned"));

        Finding exposedPort = serviceRule(response, "COMPOSE-007", "exposeonly");
        assertEquals("PASS", exposedPort.getStatus());
        assertEquals("expose", exposedPort.getMaskedEvidence());
        assertTrue(exposedPort.getExplanation().contains("expose"));
        assertEquals("PASS", serviceRule(response, "COMPOSE-007", "containeronly").getStatus());
        assertEquals("PASS", serviceRule(response, "COMPOSE-007", "loopback").getStatus());
        assertEquals("WARNING", serviceRule(response, "COMPOSE-007", "all").getStatus());
        assertEquals("PASS", serviceRule(response, "COMPOSE-007", "ipv6loopback").getStatus());
        assertEquals("WARNING", serviceRule(response, "COMPOSE-007", "ipv6all").getStatus());
        assertEquals("PASS", serviceRule(response, "COMPOSE-007", "specific").getStatus());
    }

    @Test
    void broadlyPublishedDatabasePortWarnsButLoopbackDoesNot() {
        ComposeAnalysisResponse response = service.analyze("compose.yaml", """
                services:
                  broad:
                    image: postgres:16.4
                    ports:
                      - "5432:5432"
                  local:
                    image: postgres:16.4
                    ports:
                      - "127.0.0.1:5432:5432"
                """);

        Finding broad = serviceRule(response, "COMPOSE-007", "broad");
        assertEquals("WARNING", broad.getStatus());
        assertEquals("HIGH", broad.getSeverity());
        assertTrue(broad.getExplanation().contains("database or administrative port"));
        assertEquals("PASS", serviceRule(response, "COMPOSE-007", "local").getStatus());
    }

    @Test
    void longPortSyntaxIsEvaluated() {
        ComposeAnalysisResponse response = service.analyze("compose.yaml", """
                services:
                  longdb:
                    image: postgres:16.4
                    ports:
                      - target: 5432
                        published: 5432
                        host_ip: 0.0.0.0
                  longcontainer:
                    image: alpine:3.20
                    ports:
                      - target: 8080
                  longpublishedfalse:
                    image: alpine:3.20
                    ports:
                      - target: 8081
                        published: false
                """);

        Finding longDatabase = serviceRule(response, "COMPOSE-007", "longdb");
        assertEquals("WARNING", longDatabase.getStatus());
        assertEquals("HIGH", longDatabase.getSeverity());
        Finding longContainer = serviceRule(response, "COMPOSE-007", "longcontainer");
        assertEquals("WARNING", longContainer.getStatus());
        assertTrue(longContainer.getExplanation().contains("dynamically assigned"));
        assertEquals("PASS", serviceRule(response, "COMPOSE-007", "longpublishedfalse").getStatus());
    }

    @Test
    void singleTokenDatabasePortIsDynamicPublicationNotContainerOnly() {
        ComposeAnalysisResponse response = service.analyze("compose.yaml", """
                services:
                  db:
                    image: postgres:16.4
                    ports:
                      - "5432"
                """);

        Finding finding = serviceRule(response, "COMPOSE-007", "db");
        assertEquals("WARNING", finding.getStatus());
        assertEquals("HIGH", finding.getSeverity());
        assertTrue(finding.getExplanation().contains("dynamically assigned"));
        assertTrue(finding.getExplanation().contains("database or administrative port"));
    }

    @Test
    void malformedPortDeclarationsAreNotChecked() {
        ComposeAnalysisResponse response = service.analyze("compose.yaml", """
                services:
                  emptymap:
                    image: alpine:3.20
                    ports:
                      - {}
                  extraTokens:
                    image: alpine:3.20
                    ports:
                      - "host:port:target:extra"
                  emptyexpose:
                    image: alpine:3.20
                    expose:
                      - null
                """);

        for (String serviceName : List.of("emptymap", "extraTokens", "emptyexpose")) {
            assertEquals("NOT_CHECKED", serviceRule(response, "COMPOSE-007", serviceName).getStatus(), serviceName);
        }
    }

    @Test
    void hostNetworkModeIsContextualWarningAndNamespacesAreSeparate() {
        ComposeAnalysisResponse response = service.analyze("compose.yaml", """
                services:
                  api:
                    image: alpine:3.20
                    network_mode: host
                  worker:
                    image: alpine:3.20
                    pid: host
                  scheduler:
                    image: alpine:3.20
                    ipc: host
                """);

        assertEquals("WARNING", serviceRule(response, "COMPOSE-006", "api").getStatus());
        assertEquals("WARNING", serviceRule(response, "COMPOSE-006", "worker").getStatus());
        assertEquals("WARNING", serviceRule(response, "COMPOSE-006", "scheduler").getStatus());
        assertEquals("Host Network Mode", serviceRule(response, "COMPOSE-006", "api").getName());
        assertEquals("Host PID Namespace", serviceRule(response, "COMPOSE-006", "worker").getName());
        assertEquals("Host IPC Namespace", serviceRule(response, "COMPOSE-006", "scheduler").getName());
    }

    @Test
    void missingAndDisabledHealthchecksAreDistinguished() {
        ComposeAnalysisResponse response = service.analyze("compose.yaml", """
                services:
                  missing:
                    image: alpine:3.20
                  disabled:
                    image: alpine:3.20
                    healthcheck:
                      disable: true
                  none:
                    image: alpine:3.20
                    healthcheck:
                      test: NONE
                  nonelist:
                    image: alpine:3.20
                    healthcheck:
                      test: ["NONE"]
                  disablevariable:
                    image: alpine:3.20
                    healthcheck:
                      disable: ${HEALTHCHECK_DISABLED}
                      test: ["CMD", "/usr/local/bin/healthcheck"]
                  validcmd:
                    image: alpine:3.20
                    healthcheck:
                      test: ["CMD", "/usr/local/bin/healthcheck"]
                  validshell:
                    image: alpine:3.20
                    healthcheck:
                      test: ["CMD-SHELL", "curl -fsS localhost:8080/health"]
                """);

        Finding missing = serviceRule(response, "COMPOSE-008", "missing");
        assertEquals("WARNING", missing.getStatus());
        assertTrue(missing.getLimitations().stream().anyMatch(value -> value.contains("Image-level healthcheck")));
        assertEquals("FAIL", serviceRule(response, "COMPOSE-008", "disabled").getStatus());
        assertEquals("FAIL", serviceRule(response, "COMPOSE-008", "none").getStatus());
        assertEquals("FAIL", serviceRule(response, "COMPOSE-008", "nonelist").getStatus());
        assertEquals("NOT_CHECKED", serviceRule(response, "COMPOSE-008", "disablevariable").getStatus());
        assertEquals("PASS", serviceRule(response, "COMPOSE-008", "validcmd").getStatus());
        assertEquals("PASS", serviceRule(response, "COMPOSE-008", "validshell").getStatus());
    }

    @Test
    void malformedHealthcheckCommandsAreReported() {
        ComposeAnalysisResponse response = service.analyze("compose.yaml", """
                services:
                  empty:
                    image: alpine:3.20
                    healthcheck:
                      test: []
                  blank:
                    image: alpine:3.20
                    healthcheck:
                      test: ""
                  unsupported:
                    image: alpine:3.20
                    healthcheck:
                      test: curl localhost
                  variable:
                    image: alpine:3.20
                    healthcheck:
                      test: ["CMD", "${CHECK}"]
                """);

        assertEquals("FAIL", serviceRule(response, "COMPOSE-008", "empty").getStatus());
        assertEquals("FAIL", serviceRule(response, "COMPOSE-008", "blank").getStatus());
        assertEquals("NOT_CHECKED", serviceRule(response, "COMPOSE-008", "unsupported").getStatus());
        assertEquals("NOT_CHECKED", serviceRule(response, "COMPOSE-008", "variable").getStatus());
    }

    @Test
    void triviallySuccessfulHealthcheckCommandsAreWarned() {
        ComposeAnalysisResponse response = service.analyze("compose.yaml", """
                services:
                  listtrue:
                    image: alpine:3.20
                    healthcheck:
                      test: ["CMD", "true"]
                  shelltrue:
                    image: alpine:3.20
                    healthcheck:
                      test: ["CMD-SHELL", "true"]
                  scalartrue:
                    image: alpine:3.20
                    healthcheck:
                      test: CMD true
                  meaningful:
                    image: alpine:3.20
                    healthcheck:
                      test: ["CMD", "/usr/local/bin/healthcheck"]
                """);

        for (String serviceName : List.of("listtrue", "shelltrue", "scalartrue")) {
            Finding finding = serviceRule(response, "COMPOSE-008", serviceName);
            assertEquals("WARNING", finding.getStatus(), serviceName);
            assertEquals("MEDIUM", finding.getSeverity(), serviceName);
            assertTrue(finding.getExplanation().contains("trivially successful"), serviceName);
        }
        assertEquals("PASS", serviceRule(response, "COMPOSE-008", "meaningful").getStatus());
    }

    @Test
    void riskyVolumeMountsDistinguishBindsVolumesAndVariables() {
        ComposeAnalysisResponse response = service.analyze("compose.yaml", """
                services:
                  root:
                    image: alpine:3.20
                    volumes:
                      - /:/host:rw
                  etcreadonly:
                    image: alpine:3.20
                    volumes:
                      - /etc:/etc:ro
                  named:
                    image: alpine:3.20
                    volumes:
                      - data:/data
                  anonymous:
                    image: alpine:3.20
                    volumes:
                      - /data
                  projectroot:
                    image: alpine:3.20
                    volumes:
                      - ./:/app:rw
                  variable:
                    image: alpine:3.20
                    volumes:
                      - "${DATA_DIR}:/data"
                  normalbind:
                    image: alpine:3.20
                    volumes:
                      - ./config:/app/config:ro
                """);

        assertEquals("FAIL", serviceRule(response, "COMPOSE-010", "root").getStatus());
        assertEquals("WARNING", serviceRule(response, "COMPOSE-010", "etcreadonly").getStatus());
        assertEquals("PASS", serviceRule(response, "COMPOSE-010", "named").getStatus());
        assertEquals("PASS", serviceRule(response, "COMPOSE-010", "anonymous").getStatus());
        assertEquals("WARNING", serviceRule(response, "COMPOSE-010", "projectroot").getStatus());
        assertEquals("NOT_CHECKED", serviceRule(response, "COMPOSE-010", "variable").getStatus());
        assertEquals("PASS", serviceRule(response, "COMPOSE-010", "normalbind").getStatus());
    }

    @Test
    void allSafeVolumeMountsStillEmitPassFinding() {
        ComposeAnalysisResponse response = service.analyze("compose.yaml", """
                services:
                  safe:
                    image: alpine:3.20
                    volumes:
                      - data:/data
                      - ./config:/app/config:ro
                """);

        Finding finding = serviceRule(response, "COMPOSE-010", "safe");
        assertEquals("PASS", finding.getStatus());
        assertTrue(finding.getExplanation().contains("do not require escalation"));
    }

    @Test
    void malformedVolumeDeclarationsAreNotChecked() {
        ComposeAnalysisResponse response = service.analyze("compose.yaml", """
                services:
                  emptymap:
                    image: alpine:3.20
                    volumes:
                      - {}
                  extraTokens:
                    image: alpine:3.20
                    volumes:
                      - "source:/target:ro:extra"
                  missingTarget:
                    image: alpine:3.20
                    volumes:
                      - type: bind
                        source: /etc
                """);

        for (String serviceName : List.of("emptymap", "extraTokens", "missingTarget")) {
            assertEquals("NOT_CHECKED", serviceRule(response, "COMPOSE-010", serviceName).getStatus(), serviceName);
        }
    }

    @Test
    void imageTagsDistinguishDefaultLatestExplicitLatestDigestAndBuilds() {
        String digest = "a".repeat(64);
        String content = """
                services:
                  notag:
                    image: alpine
                  latest:
                    image: nginx:latest
                  version:
                    image: alpine:3.20
                  digest:
                    image: alpine@sha256:%s
                  baddigest:
                    image: alpine@sha256:abc
                  buildonly:
                    build:
                      context: ./worker
                  variable:
                    image: ${IMAGE}
                """.formatted(digest);
        ComposeAnalysisResponse response = service.analyze("compose.yaml", content);

        assertEquals("WARNING", serviceRule(response, "COMPOSE-002", "notag").getStatus());
        assertTrue(serviceRule(response, "COMPOSE-002", "notag").getExplanation().contains("default"));
        assertEquals("WARNING", serviceRule(response, "COMPOSE-002", "latest").getStatus());
        assertEquals("PASS", serviceRule(response, "COMPOSE-002", "version").getStatus());
        assertEquals("PASS", serviceRule(response, "COMPOSE-002", "digest").getStatus());
        assertEquals("NOT_CHECKED", serviceRule(response, "COMPOSE-002", "baddigest").getStatus());
        assertEquals("NOT_CHECKED", serviceRule(response, "COMPOSE-002", "buildonly").getStatus());
        assertEquals("NOT_CHECKED", serviceRule(response, "COMPOSE-002", "variable").getStatus());
    }

    @Test
    void validRestartPoliciesPassAndAlwaysIsNotInherentlyRejected() {
        ComposeAnalysisResponse response = service.analyze("compose.yaml", """
                services:
                  noPolicy:
                    image: alpine:3.20
                  noString:
                    image: alpine:3.20
                    restart: "no"
                  noBoolean:
                    image: alpine:3.20
                    restart: no
                  always:
                    image: alpine:3.20
                    restart: always
                  unless:
                    image: alpine:3.20
                    restart: unless-stopped
                  onfailure:
                    image: alpine:3.20
                    restart: on-failure
                  limited:
                    image: alpine:3.20
                    restart: on-failure:3
                  variable:
                    image: alpine:3.20
                    restart: ${RESTART}
                  unknown:
                    image: alpine:3.20
                    restart: sometimes
                """);

        assertEquals("WARNING", serviceRule(response, "COMPOSE-009", "noPolicy").getStatus());
        for (String serviceName : List.of("noString", "noBoolean", "always", "unless", "onfailure", "limited")) {
            assertEquals("PASS", serviceRule(response, "COMPOSE-009", serviceName).getStatus(), serviceName);
        }
        assertEquals("NOT_CHECKED", serviceRule(response, "COMPOSE-009", "variable").getStatus());
        assertEquals("WARNING", serviceRule(response, "COMPOSE-009", "unknown").getStatus());
    }

    @Test
    void hardcodedAndReferencedCredentialsAreDistinguished() throws Exception {
        ComposeAnalysisResponse response = service.analyze("compose.yaml", """
                services:
                  hardcoded:
                    image: alpine:3.20
                    environment:
                      DB_PASSWORD: hunter2
                  reference:
                    image: alpine:3.20
                    environment:
                      DB_PASSWORD: ${DB_PASSWORD}
                  fallback:
                    image: alpine:3.20
                    environment:
                      DB_PASSWORD: ${DB_PASSWORD:-examplePassword}
                  emptyfallback:
                    image: alpine:3.20
                    environment:
                      DB_PASSWORD: ${DB_PASSWORD:-}
                  empty:
                    image: alpine:3.20
                    environment:
                      DB_PASSWORD: ""
                  inherited:
                    image: alpine:3.20
                    environment:
                      - DB_PASSWORD
                """);

        assertEquals("FAIL", serviceRule(response, "COMPOSE.SUPPORT.ENVIRONMENT_SECRETS", "hardcoded").getStatus());
        assertEquals("PASS", serviceRule(response, "COMPOSE.SUPPORT.ENVIRONMENT_SECRETS", "reference").getStatus());
        assertEquals("FAIL", serviceRule(response, "COMPOSE.SUPPORT.ENVIRONMENT_SECRETS", "fallback").getStatus());
        assertEquals("PASS", serviceRule(response, "COMPOSE.SUPPORT.ENVIRONMENT_SECRETS", "emptyfallback").getStatus());
        assertEquals("PASS", serviceRule(response, "COMPOSE.SUPPORT.ENVIRONMENT_SECRETS", "empty").getStatus());
        assertEquals("PASS", serviceRule(response, "COMPOSE.SUPPORT.ENVIRONMENT_SECRETS", "inherited").getStatus());
        assertFalse(serialize(response).contains("hunter2"));
        assertFalse(serialize(response).contains("examplePassword"));
    }

    @Test
    void multipleAffectedServicesPreserveMixedFailAndNotCheckedFindings() {
        ComposeAnalysisResponse response = service.analyze("compose.yaml", """
                services:
                  service_a:
                    image: alpine:3.20
                    privileged: true
                  service_b:
                    image: alpine:3.20
                    privileged: ${PRIVILEGED}
                """);

        Finding fail = serviceRule(response, "COMPOSE-003", "service_a");
        Finding unresolved = serviceRule(response, "COMPOSE-003", "service_b");
        assertEquals("FAIL", fail.getStatus());
        assertEquals("NOT_CHECKED", unresolved.getStatus());
        assertEquals("service_a", fail.getServiceIdentifier());
        assertEquals("service_b", unresolved.getServiceIdentifier());
        assertFalse(fail.getExplanation().contains("service_b"));
        assertFalse(unresolved.getExplanation().contains("service_a"));
    }

    @Test
    void multipleComposeFilesRetainIndependentFindings() {
        String risky = """
                services:
                  api:
                    image: nginx:latest
                    privileged: true
                """;
        String corrected = """
                services:
                  api:
                    image: nginx:1.27
                    privileged: false
                    user: "1000:1000"
                    healthcheck:
                      test: ["CMD", "/usr/local/bin/healthcheck"]
                    restart: no
                """;

        ComposeAnalysisResponse first = service.analyze("deploy/compose.yaml", risky);
        ComposeAnalysisResponse second = service.analyze("deploy/docker-compose.yml", corrected);

        assertEquals("deploy/compose.yaml", serviceRule(first, "COMPOSE-003", "api").getFilePath());
        assertEquals("deploy/docker-compose.yml", serviceRule(second, "COMPOSE-003", "api").getFilePath());
        assertEquals("FAIL", serviceRule(first, "COMPOSE-003", "api").getStatus());
        assertEquals("PASS", serviceRule(second, "COMPOSE-003", "api").getStatus());
    }

    @Test
    void serviceIdentifierIsSanitizedAndUsedInsteadOfRawEvidence() {
        ComposeAnalysisResponse response = service.analyze("compose.yaml", """
                services:
                  "weird name!":
                    image: nginx:latest
                    privileged: true
                """);

        Finding finding = serviceRule(response, "COMPOSE-003", "weird_name_");
        assertEquals("weird_name_", finding.getServiceIdentifier());
        assertFalse(String.valueOf(finding.getMaskedEvidence()).contains("weird name!"));
        assertFalse(finding.getExplanation().contains("weird name!"));
    }

    private String serialize(ComposeAnalysisResponse response) {
        StringBuilder result = new StringBuilder();
        for (Finding finding : response.getFindings()) {
            result.append(finding.getRuleId())
                    .append(finding.getName())
                    .append(finding.getExplanation())
                    .append(finding.getSuggestedAction())
                    .append(finding.getMaskedEvidence())
                    .append(finding.getServiceIdentifier())
                    .append(finding.getFilePath())
                    .append(String.valueOf(finding.getLimitations()));
        }
        return result.toString();
    }

    private String status(ComposeAnalysisResponse response, String ruleId) {
        return rule(response, ruleId).getStatus();
    }

    private Finding rule(ComposeAnalysisResponse response, String ruleId) {
        List<Finding> matches = rules(response, ruleId);
        assertFalse(matches.isEmpty(), "Missing finding for rule: " + ruleId);
        return matches.get(0);
    }

    private Finding serviceRule(ComposeAnalysisResponse response, String ruleId, String serviceIdentifier) {
        return rules(response, ruleId).stream()
                .filter(finding -> serviceIdentifier.equals(finding.getServiceIdentifier()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Missing finding for rule " + ruleId + " and service " + serviceIdentifier));
    }

    private List<Finding> rules(ComposeAnalysisResponse response, String ruleId) {
        assertNotNull(response.getFindings());
        return response.getFindings().stream()
                .filter(finding -> ruleId.equals(finding.getRuleId()))
                .toList();
    }
}
