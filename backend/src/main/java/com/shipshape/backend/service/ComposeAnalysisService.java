package com.shipshape.backend.service;

import com.shipshape.backend.dto.ComposeAnalysisResponse;
import com.shipshape.backend.dto.Finding;
import com.shipshape.backend.dto.FindingSeverity;
import com.shipshape.backend.dto.FindingStatus;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ComposeAnalysisService {
    private static final int MAX_CONTENT_LENGTH = 200_000;
    private static final int MAX_NESTING_DEPTH = 20;
    private static final int MAX_ALIASES = 16;
    private static final String CATEGORY = "COMPOSE";
    private static final String FILE_NAME_ERROR = "The Compose YAML could not be parsed safely.";

    private static final Set<String> RECOGNIZED_FILE_NAMES = Set.of(
            "compose.yaml", "compose.yml", "docker-compose.yaml", "docker-compose.yml"
    );

    private static final Set<String> ALLOWED_TAGS = Set.of(
            Tag.MAP.getValue(), Tag.SEQ.getValue(), Tag.STR.getValue(), Tag.INT.getValue(),
            Tag.FLOAT.getValue(), Tag.BOOL.getValue(), Tag.NULL.getValue(), Tag.TIMESTAMP.getValue(),
            Tag.MERGE.getValue(), Tag.SET.getValue(), Tag.BINARY.getValue()
    );

    private static final Pattern SENSITIVE_ENV_KEY = Pattern.compile(
            "(?i)(^|[^A-Za-z0-9])(PASSWORD|PASSWD|SECRET|TOKEN|API_?KEY|ACCESS_?KEY|PRIVATE_?KEY|CREDENTIALS?|KEY)([^A-Za-z0-9]|$)"
    );
    private static final Pattern DIGEST_REFERENCE = Pattern.compile(
            "@(sha256:[0-9a-fA-F]{64}|sha512:[0-9a-fA-F]{128})$"
    );
    private static final Pattern ON_FAILURE_WITH_LIMIT = Pattern.compile("(?i)^on-failure:[0-9]+$");
    private static final Pattern WINDOWS_DRIVE = Pattern.compile("(?i)^[A-Za-z]:[\\\\/].*");

    private static final Set<String> DATABASE_PORTS = Set.of(
            "5432", "3306", "6379", "27017", "2375", "2376"
    );
    private static final Set<String> TRIVIAL_SUCCESS_COMMANDS = Set.of(
            "true", "/bin/true", "/usr/bin/true", ":", "exit 0"
    );
    private static final Set<String> VOLUME_TYPES = Set.of("bind", "volume", "tmpfs", "npipe", "cluster");
    private static final Set<String> SENSITIVE_HOST_PREFIXES = Set.of(
            "/etc", "/proc", "/sys", "/dev", "/var/run", "/run", "/boot", "/usr", "/bin", "/sbin", "/lib"
    );

    private static final List<String> BASE_LIMITATIONS = List.of(
            "Compose YAML is parsed as data only; no Compose command, container runtime, network lookup, or host file resolution is performed.",
            "Each Compose file is analyzed independently; override files are not merged into an effective configuration.",
            "Unsafe YAML tags are rejected, and alias, nesting-depth, and document-size limits are applied."
    );

    public ComposeAnalysisResponse analyze(String filePath, String content) {
        List<Finding> findings = new ArrayList<>();
        String fileName = fileName(filePath);

        findings.add(finding(
                "COMPOSE.SUPPORT.FILE_NAME", "Compose File Name",
                RECOGNIZED_FILE_NAMES.contains(fileName) ? FindingStatus.PASS : FindingStatus.NOT_CHECKED,
                RECOGNIZED_FILE_NAMES.contains(fileName) ? FindingSeverity.INFO : FindingSeverity.LOW,
                RECOGNIZED_FILE_NAMES.contains(fileName)
                        ? "The uploaded file uses a recognized Compose file name."
                        : "The uploaded file does not use a recognized Compose file name.",
                RECOGNIZED_FILE_NAMES.contains(fileName)
                        ? "No action required for this rule."
                        : "Use a conventional Compose file name.",
                filePath, null, null, null,
                "Recognized names are compose.yaml, compose.yml, docker-compose.yaml, and docker-compose.yml."
        ));
        if (!RECOGNIZED_FILE_NAMES.contains(fileName)) {
            return response(findings);
        }

        if (content == null || content.isBlank() || content.length() > MAX_CONTENT_LENGTH) {
            findings.add(parseErrorFinding(filePath));
            return response(findings);
        }

        ParsedCompose parsed;
        try {
            parsed = parse(content);
        } catch (RuntimeException ex) {
            findings.add(parseErrorFinding(filePath));
            return response(findings);
        }

        findings.add(finding(
                "COMPOSE.SUPPORT.SAFE_YAML", "Safe YAML Parsing", FindingStatus.PASS, FindingSeverity.INFO,
                "The Compose YAML was parsed with a restricted, data-only parser.",
                "No action required for this rule.",
                filePath, null, null, null
        ));

        if (parsed.documentCount() != 1) {
            findings.add(finding(
                    "COMPOSE.SUPPORT.DOCUMENT_COUNT", "Single YAML Document",
                    FindingStatus.FAIL, FindingSeverity.MEDIUM,
                    "The Compose file does not contain exactly one YAML document.",
                    "Keep one Compose document per file because override files are not merged.",
                    filePath, null, null, null,
                    "Production rules were not evaluated because the document structure is ambiguous."
            ));
            return response(findings);
        }

        findings.add(finding(
                "COMPOSE.SUPPORT.DOCUMENT_COUNT", "Single YAML Document",
                FindingStatus.PASS, FindingSeverity.INFO,
                "The Compose file contains one YAML document.",
                "No action required for this rule.",
                filePath, null, null, null
        ));

        Map<String, Object> services = extractServices(parsed.root());
        if (services == null) {
            findings.add(structureFailure(filePath, parsed.lines()));
            return response(findings);
        }

        findings.add(finding(
                "COMPOSE-001", "Compose Structure Validation", FindingStatus.PASS, FindingSeverity.INFO,
                "A non-empty services mapping with string-keyed service definitions was found.",
                "No action required for this rule.",
                filePath, parsed.lines().get("services"), "services", null
        ));

        for (Map.Entry<String, Object> entry : services.entrySet()) {
            String rawService = entry.getKey();
            String serviceIdentifier = serviceIdentifier(rawService);
            String servicePath = servicePath(rawService);
            Map<String, Object> service = stringKeyedMap(entry.getValue());
            if (service == null) {
                findings.add(finding(
                        "COMPOSE-001", "Compose Structure Validation", FindingStatus.FAIL, FindingSeverity.HIGH,
                        "Service '" + serviceIdentifier + "' is not a string-keyed mapping.",
                        "Define each service as a mapping with string keys.",
                        filePath, line(parsed.lines(), servicePath), "services", serviceIdentifier,
                        "Production rules were not evaluated for this service."
                ));
                continue;
            }

            findings.add(checkServiceImageOrBuild(filePath, rawService, service, parsed.lines()));
            findings.addAll(checkImageVersionPolicy(filePath, rawService, service, parsed.lines()));
            findings.addAll(checkPrivileged(filePath, rawService, service, parsed.lines()));
            findings.addAll(checkContainerUser(filePath, rawService, service, parsed.lines()));
            findings.addAll(checkDockerSocketAccess(filePath, rawService, service, parsed.lines()));
            findings.addAll(checkHostNetworkIsolation(filePath, rawService, service, parsed.lines()));
            findings.addAll(checkPublishedPorts(filePath, rawService, service, parsed.lines()));
            findings.addAll(checkHealthcheck(filePath, rawService, service, parsed.lines()));
            findings.addAll(checkRestartPolicy(filePath, rawService, service, parsed.lines()));
            findings.addAll(checkRiskyVolumeMounts(filePath, rawService, service, parsed.lines()));
            findings.addAll(checkEnvironmentSecrets(filePath, rawService, service, parsed.lines()));
        }

        return response(findings);
    }

    private Finding structureFailure(String filePath, Map<String, Integer> lines) {
        return finding(
                "COMPOSE-001", "Compose Structure Validation", FindingStatus.FAIL, FindingSeverity.HIGH,
                "The Compose document does not contain a non-empty string-keyed services mapping.",
                "Provide a top-level services mapping with at least one named service.",
                filePath, lines.get("services"), "services", null,
                "Production rules were not evaluated because the document structure is invalid."
        );
    }

    private Finding checkServiceImageOrBuild(String filePath, String rawService,
                                             Map<String, Object> service, Map<String, Integer> lines) {
        String serviceIdentifier = serviceIdentifier(rawService);
        String image = text(service.get("image"));
        String buildContext = buildContext(service.get("build"));
        String servicePath = servicePath(rawService);
        String imagePath = servicePath + ".image";
        String buildPath = servicePath + ".build";

        if (containsVariable(image)) {
            return finding(
                    "COMPOSE.SUPPORT.IMAGE_OR_BUILD", "Service Image or Build",
                    FindingStatus.NOT_CHECKED, FindingSeverity.LOW,
                    "Service '" + serviceIdentifier + "' uses a variable image reference that cannot be resolved statically.",
                    "Use a literal image reference when evaluating this service.",
                    filePath, line(lines, imagePath), "image", serviceIdentifier
            );
        }
        if (containsVariable(buildContext)) {
            return finding(
                    "COMPOSE.SUPPORT.IMAGE_OR_BUILD", "Service Image or Build",
                    FindingStatus.NOT_CHECKED, FindingSeverity.LOW,
                    "Service '" + serviceIdentifier + "' uses a variable build context that cannot be resolved statically.",
                    "Use a literal build context when evaluating this service.",
                    filePath, line(lines, buildPath), "build", serviceIdentifier
            );
        }
        if (image != null || buildContext != null) {
            return finding(
                    "COMPOSE.SUPPORT.IMAGE_OR_BUILD", "Service Image or Build",
                    FindingStatus.PASS, FindingSeverity.INFO,
                    "Service '" + serviceIdentifier + "' declares a statically readable image or build configuration.",
                    "No action required for this rule.",
                    filePath, line(lines, image != null ? imagePath : buildPath),
                    image != null ? "image" : "build", serviceIdentifier
            );
        }
        return finding(
                "COMPOSE.SUPPORT.IMAGE_OR_BUILD", "Service Image or Build",
                FindingStatus.FAIL, FindingSeverity.HIGH,
                "Service '" + serviceIdentifier + "' declares neither an image nor a build context.",
                "Declare an image or build context for this service.",
                filePath, line(lines, servicePath), "image/build", serviceIdentifier
        );
    }

    private List<Finding> checkImageVersionPolicy(String filePath, String rawService,
                                                  Map<String, Object> service, Map<String, Integer> lines) {
        String serviceIdentifier = serviceIdentifier(rawService);
        String image = text(service.get("image"));
        String buildContext = buildContext(service.get("build"));
        String imagePath = servicePath(rawService) + ".image";
        String buildPath = servicePath(rawService) + ".build";

        if (image == null) {
            if (buildContext != null) {
                return List.of(finding(
                        "COMPOSE-002", "Image Version Policy", FindingStatus.NOT_CHECKED, FindingSeverity.LOW,
                        "Service '" + serviceIdentifier + "' is build-only, so no Compose image tag can be evaluated.",
                        "Inspect the built image tag outside this static endpoint.",
                        filePath, line(lines, buildPath), "build", serviceIdentifier,
                        "The built image tag is not evaluated by this rule."
                ));
            }
            return List.of(finding(
                    "COMPOSE-002", "Image Version Policy", FindingStatus.FAIL, FindingSeverity.HIGH,
                    "Service '" + serviceIdentifier + "' has no image reference to evaluate.",
                    "Declare a versioned image or a build context with an externally verified tag.",
                    filePath, line(lines, servicePath(rawService)), "image", serviceIdentifier
            ));
        }

        if (containsVariable(image)) {
            return List.of(finding(
                    "COMPOSE-002", "Image Version Policy", FindingStatus.NOT_CHECKED, FindingSeverity.LOW,
                    "Service '" + serviceIdentifier + "' uses a variable image reference; its version policy is unresolved.",
                    "Use a statically readable image reference or resolve the variable before relying on this check.",
                    filePath, line(lines, imagePath), "image", serviceIdentifier
            ));
        }

        if (DIGEST_REFERENCE.matcher(image).find()) {
            return List.of(finding(
                    "COMPOSE-002", "Image Version Policy", FindingStatus.PASS, FindingSeverity.INFO,
                    "Service '" + serviceIdentifier + "' uses a recognized digest reference.",
                    "No action required for this rule.",
                    filePath, line(lines, imagePath), "image", serviceIdentifier,
                    "No registry lookup or digest verification is performed."
            ));
        }

        if (image.contains("@")) {
            return List.of(finding(
                    "COMPOSE-002", "Image Version Policy", FindingStatus.NOT_CHECKED, FindingSeverity.LOW,
                    "Service '" + serviceIdentifier + "' contains an @ reference that is not a recognized digest.",
                    "Use a valid sha256 or sha512 digest reference or an explicit version tag.",
                    filePath, line(lines, imagePath), "image", serviceIdentifier
            ));
        }

        String tag = imageTag(image);
        if ("latest".equalsIgnoreCase(tag)) {
            return List.of(finding(
                    "COMPOSE-002", "Image Version Policy", FindingStatus.WARNING, FindingSeverity.MEDIUM,
                    "Service '" + serviceIdentifier + "' explicitly uses the latest image tag.",
                    "Pin an explicit version tag or valid digest instead of latest.",
                    filePath, line(lines, imagePath), "image", serviceIdentifier
            ));
        }
        if (tag == null) {
            return List.of(finding(
                    "COMPOSE-002", "Image Version Policy", FindingStatus.WARNING, FindingSeverity.MEDIUM,
                    "Service '" + serviceIdentifier + "' has no explicit image tag, which generally resolves to latest by default.",
                    "Specify an explicit version tag or valid digest.",
                    filePath, line(lines, imagePath), "image", serviceIdentifier
            ));
        }
        return List.of(finding(
                "COMPOSE-002", "Image Version Policy", FindingStatus.PASS, FindingSeverity.INFO,
                "Service '" + serviceIdentifier + "' uses an explicit non-latest image tag.",
                "No action required for this rule.",
                filePath, line(lines, imagePath), "image", serviceIdentifier,
                "No registry lookup is performed; a tag does not prove immutability."
        ));
    }

    private List<Finding> checkPrivileged(String filePath, String rawService,
                                          Map<String, Object> service, Map<String, Integer> lines) {
        String serviceIdentifier = serviceIdentifier(rawService);
        String privilegedPath = servicePath(rawService) + ".privileged";
        if (!service.containsKey("privileged")) {
            return List.of(finding(
                    "COMPOSE-003", "Privileged Containers", FindingStatus.PASS, FindingSeverity.INFO,
                    "Service '" + serviceIdentifier + "' does not enable privileged mode.",
                    "No action required for this rule.",
                    filePath, line(lines, servicePath(rawService)), "privileged", serviceIdentifier
            ));
        }

        Boolean value = booleanValue(service.get("privileged"));
        if (Boolean.TRUE.equals(value)) {
            return List.of(finding(
                    "COMPOSE-003", "Privileged Containers", FindingStatus.FAIL, FindingSeverity.HIGH,
                    "Service '" + serviceIdentifier + "' enables privileged mode.",
                    "Remove privileged mode and grant only the capabilities required by the service.",
                    filePath, line(lines, privilegedPath), "privileged", serviceIdentifier
            ));
        }
        if (Boolean.FALSE.equals(value)) {
            return List.of(finding(
                    "COMPOSE-003", "Privileged Containers", FindingStatus.PASS, FindingSeverity.INFO,
                    "Service '" + serviceIdentifier + "' explicitly disables privileged mode.",
                    "No action required for this rule.",
                    filePath, line(lines, privilegedPath), "privileged", serviceIdentifier
            ));
        }
        return List.of(finding(
                "COMPOSE-003", "Privileged Containers", FindingStatus.NOT_CHECKED, FindingSeverity.LOW,
                "Service '" + serviceIdentifier + "' has a privileged value that is not a static boolean.",
                "Use a literal true or false value when evaluating this rule.",
                filePath, line(lines, privilegedPath), "privileged", serviceIdentifier
        ));
    }

    private List<Finding> checkContainerUser(String filePath, String rawService,
                                             Map<String, Object> service, Map<String, Integer> lines) {
        String serviceIdentifier = serviceIdentifier(rawService);
        String userPath = servicePath(rawService) + ".user";
        if (!service.containsKey("user")) {
            return List.of(finding(
                    "COMPOSE-004", "Container User", FindingStatus.NOT_CHECKED, FindingSeverity.LOW,
                    "Service '" + serviceIdentifier + "' has no explicit user; this is not treated as definite root execution because the image may define a non-root user.",
                    "Set an explicit non-root user when the service does not require a root identity.",
                    filePath, line(lines, servicePath(rawService)), "user", serviceIdentifier,
                    "Image-level USER configuration is not inspected."
            ));
        }

        Object rawUser = service.get("user");
        String user = text(rawUser);
        if (containsVariable(user)) {
            return List.of(finding(
                    "COMPOSE-004", "Container User", FindingStatus.NOT_CHECKED, FindingSeverity.LOW,
                    "Service '" + serviceIdentifier + "' uses an unresolved user reference.",
                    "Resolve the user variable or declare an explicit non-root user.",
                    filePath, line(lines, userPath), "user", serviceIdentifier
            ));
        }
        if (user == null || user.isBlank()) {
            return List.of(finding(
                    "COMPOSE-004", "Container User", FindingStatus.NOT_CHECKED, FindingSeverity.LOW,
                    "Service '" + serviceIdentifier + "' has a user declaration that cannot be read statically.",
                    "Use a literal user value such as an explicit numeric or named non-root user.",
                    filePath, line(lines, userPath), "user", serviceIdentifier
            ));
        }

        String userPart = user.split(":", -1)[0].trim();
        if ("root".equalsIgnoreCase(userPart) || "0".equals(userPart)) {
            return List.of(finding(
                    "COMPOSE-004", "Container User", FindingStatus.FAIL, FindingSeverity.HIGH,
                    "Service '" + serviceIdentifier + "' explicitly runs as the root user, including forms such as root:root, 0, 0:0, or root with a non-root group.",
                    "Use an explicit non-root user and group that have only the permissions the service requires.",
                    filePath, line(lines, userPath), "user", serviceIdentifier
            ));
        }
        return List.of(finding(
                "COMPOSE-004", "Container User", FindingStatus.PASS, FindingSeverity.INFO,
                "Service '" + serviceIdentifier + "' declares an explicit non-root user.",
                "No action required for this rule.",
                filePath, line(lines, userPath), "user", serviceIdentifier
        ));
    }

    private List<Finding> checkDockerSocketAccess(String filePath, String rawService,
                                                  Map<String, Object> service, Map<String, Integer> lines) {
        String serviceIdentifier = serviceIdentifier(rawService);
        List<Volume> volumes = volumes(service, rawService);
        List<Finding> findings = new ArrayList<>();
        boolean unresolved = false;

        for (Volume volume : volumes) {
            if (!isSocketRelated(volume) && !isPotentiallySocketRelated(volume)) {
                continue;
            }
            if (volume.variable() || !isSocketRelated(volume)) {
                unresolved = true;
                continue;
            }
            findings.add(finding(
                    "COMPOSE-005", "Docker Socket Access", FindingStatus.FAIL, FindingSeverity.HIGH,
                    "Service '" + serviceIdentifier + "' mounts a Docker socket path, which may allow access to the Docker daemon from the container.",
                    "Remove the Docker socket mount or use a narrowly scoped, authenticated alternative.",
                    filePath, line(lines, volume.path()), "volumes", serviceIdentifier,
                    "A read-only filesystem mount does not necessarily prevent Docker API operations through the socket. No daemon access or exploitation is demonstrated."
            ));
        }

        if (unresolved) {
            findings.add(finding(
                    "COMPOSE-005", "Docker Socket Access", FindingStatus.NOT_CHECKED, FindingSeverity.LOW,
                    "Service '" + serviceIdentifier + "' has a variable-related mount that could resolve to a Docker socket path and cannot be evaluated statically.",
                    "Resolve the mount source and target before relying on this check.",
                    filePath, line(lines, servicePath(rawService) + ".volumes"), "volumes", serviceIdentifier
            ));
        } else if (findings.isEmpty()) {
            findings.add(finding(
                    "COMPOSE-005", "Docker Socket Access", FindingStatus.PASS, FindingSeverity.INFO,
                    "Service '" + serviceIdentifier + "' does not mount a recognized Docker socket path.",
                    "No action required for this rule.",
                    filePath, line(lines, servicePath(rawService)), "volumes", serviceIdentifier
            ));
        }
        return findings;
    }

    private List<Finding> checkHostNetworkIsolation(String filePath, String rawService,
                                                    Map<String, Object> service, Map<String, Integer> lines) {
        String serviceIdentifier = serviceIdentifier(rawService);
        List<Finding> findings = new ArrayList<>();
        List<String> keys = List.of("network_mode", "pid", "ipc");

        for (String key : keys) {
            if (!service.containsKey(key)) {
                continue;
            }
            Object rawValue = service.get(key);
            String value = text(rawValue);
            String keyPath = servicePath(rawService) + "." + key;
            String name = switch (key) {
                case "network_mode" -> "Host Network Mode";
                case "pid" -> "Host PID Namespace";
                default -> "Host IPC Namespace";
            };

            if (containsVariable(value) || value == null) {
                findings.add(finding(
                        "COMPOSE-006", name, FindingStatus.NOT_CHECKED, FindingSeverity.LOW,
                        "Service '" + serviceIdentifier + "' has a " + key + " value that cannot be resolved statically.",
                        "Use a literal namespace setting when evaluating this rule.",
                        filePath, line(lines, keyPath), key, serviceIdentifier
                ));
            } else if ("host".equalsIgnoreCase(value)) {
                String explanation = switch (key) {
                    case "network_mode" -> "Service '" + serviceIdentifier + "' uses the host network, reducing network isolation; this may be intentional for services that require host-level networking.";
                    case "pid" -> "Service '" + serviceIdentifier + "' shares the host PID namespace, reducing process isolation.";
                    default -> "Service '" + serviceIdentifier + "' shares the host IPC namespace, reducing interprocess isolation.";
                };
                findings.add(finding(
                        "COMPOSE-006", name, FindingStatus.WARNING, FindingSeverity.MEDIUM,
                        explanation,
                        "Use an isolated network or namespace unless host-level access is explicitly required and reviewed.",
                        filePath, line(lines, keyPath), key, serviceIdentifier,
                        "This is a contextual warning, not proof that the service is unsuitable for production."
                ));
            }
        }

        if (findings.isEmpty()) {
            findings.add(finding(
                    "COMPOSE-006", "Host Network Isolation", FindingStatus.PASS, FindingSeverity.INFO,
                    "Service '" + serviceIdentifier + "' does not declare host network, PID, or IPC namespace usage.",
                    "No action required for this rule.",
                    filePath, line(lines, servicePath(rawService)), "network_mode", serviceIdentifier
            ));
        }
        return findings;
    }

    private List<Finding> checkPublishedPorts(String filePath, String rawService,
                                               Map<String, Object> service, Map<String, Integer> lines) {
        String serviceIdentifier = serviceIdentifier(rawService);
        List<Port> ports = ports(service, rawService);
        List<Finding> findings = new ArrayList<>();

        if (ports.isEmpty()) {
            return List.of(finding(
                    "COMPOSE-007", "Published Ports", FindingStatus.PASS, FindingSeverity.INFO,
                    "Service '" + serviceIdentifier + "' does not publish host ports or declare expose entries.",
                    "No action required for this rule.",
                    filePath, line(lines, servicePath(rawService)), "ports/expose", serviceIdentifier,
                    "Public reachability is not inferred without network and firewall evidence."
            ));
        }

        for (Port port : ports) {
            String token = port.exposed() ? "expose" : "ports";
            String ruleName = port.exposed() ? "Exposed Ports" : "Published Ports";
            if (port.variable()) {
                findings.add(finding(
                        "COMPOSE-007", ruleName, FindingStatus.NOT_CHECKED, FindingSeverity.LOW,
                        "Service '" + serviceIdentifier + "' has a " + token + " declaration with unresolved variables.",
                        "Resolve the binding before relying on this check.",
                        filePath, line(lines, port.path()), token, serviceIdentifier,
                        "Public reachability is not inferred without network and firewall evidence."
                ));
                continue;
            }
            if (port.malformed()) {
                findings.add(finding(
                        "COMPOSE-007", ruleName, FindingStatus.NOT_CHECKED, FindingSeverity.LOW,
                        "Service '" + serviceIdentifier + "' has a " + token + " declaration that could not be evaluated safely.",
                        "Use a Compose syntax that can be parsed as a static binding.",
                        filePath, line(lines, port.path()), token, serviceIdentifier,
                        "Public reachability is not inferred without network and firewall evidence."
                ));
                continue;
            }
            if (port.exposed()) {
                findings.add(finding(
                        "COMPOSE-007", ruleName, FindingStatus.PASS, FindingSeverity.INFO,
                        "Service '" + serviceIdentifier + "' declares a container-only expose entry, which does not publish a host port.",
                        "No action required for this rule.",
                        filePath, line(lines, port.path()), token, serviceIdentifier,
                        "Expose entries are distinguished from published ports; public reachability is not inferred."
                ));
                continue;
            }
            if (!port.published()) {
                findings.add(finding(
                        "COMPOSE-007", ruleName, FindingStatus.PASS, FindingSeverity.INFO,
                        "Service '" + serviceIdentifier + "' declares container-only port exposure without publishing a host port.",
                        "No action required for this rule.",
                        filePath, line(lines, port.path()), token, serviceIdentifier,
                        "Public reachability is not inferred without network and firewall evidence."
                ));
                continue;
            }

            Binding binding = binding(port.bind());
            boolean databasePort = isDatabasePort(port.hostPort()) || isDatabasePort(port.targetPort());
            if (port.dynamic()) {
                findings.add(finding(
                        "COMPOSE-007", ruleName, FindingStatus.WARNING,
                        databasePort ? FindingSeverity.HIGH : FindingSeverity.MEDIUM,
                        databasePort
                                ? "Service '" + serviceIdentifier + "' publishes an identifiable database or administrative port to a dynamically assigned host port on all interfaces."
                                : "Service '" + serviceIdentifier + "' publishes a container port to a dynamically assigned host port on all interfaces.",
                        "Declare an explicit loopback or specific-interface binding, or document the need for a dynamically assigned all-interface port.",
                        filePath, line(lines, port.path()), token, serviceIdentifier,
                        "The random host port was not resolved; runtime allocation, network controls, and public reachability were not inspected."
                ));
                continue;
            }
            if (binding == Binding.LOOPBACK) {
                findings.add(finding(
                        "COMPOSE-007", ruleName, FindingStatus.PASS, FindingSeverity.INFO,
                        "Service '" + serviceIdentifier + "' publishes a port with an explicit loopback binding.",
                        "No action required for this rule.",
                        filePath, line(lines, port.path()), token, serviceIdentifier,
                        "Public reachability is not inferred without network and firewall evidence."
                ));
            } else if (binding == Binding.ALL) {
                findings.add(finding(
                        "COMPOSE-007", ruleName, FindingStatus.WARNING,
                        databasePort ? FindingSeverity.HIGH : FindingSeverity.MEDIUM,
                        databasePort
                                ? "Service '" + serviceIdentifier + "' broadly publishes an identifiable database or administrative port on all interfaces."
                                : "Service '" + serviceIdentifier + "' publishes a port on all interfaces by default or by explicit wildcard binding.",
                        databasePort
                                ? "Bind the port to loopback or a specific required interface and place it behind an authenticated, network-restricted path."
                                : "Bind the port to loopback or a specific interface unless all-interface exposure is required and reviewed.",
                        filePath, line(lines, port.path()), token, serviceIdentifier,
                        "This does not prove public reachability; network and firewall controls were not inspected."
                ));
            } else {
                findings.add(finding(
                        "COMPOSE-007", ruleName, FindingStatus.PASS, FindingSeverity.INFO,
                        "Service '" + serviceIdentifier + "' publishes a port on an explicitly identified interface that is not a wildcard binding.",
                        "Confirm that the selected host interface is appropriate for the deployment.",
                        filePath, line(lines, port.path()), token, serviceIdentifier,
                        "Public reachability is not inferred without network and firewall evidence."
                ));
            }
        }
        return findings;
    }


    private List<Finding> checkHealthcheck(String filePath, String rawService,
                                           Map<String, Object> service, Map<String, Integer> lines) {
        String serviceIdentifier = serviceIdentifier(rawService);
        String healthcheckPath = servicePath(rawService) + ".healthcheck";
        if (!service.containsKey("healthcheck")) {
            return List.of(finding(
                    "COMPOSE-008", "Healthcheck Configuration", FindingStatus.WARNING, FindingSeverity.LOW,
                    "Service '" + serviceIdentifier + "' has no Compose healthcheck declaration; an image-level healthcheck may be inherited.",
                    "Declare a meaningful healthcheck when the platform needs readiness or restart decisions.",
                    filePath, line(lines, servicePath(rawService)), "healthcheck", serviceIdentifier,
                    "Image-level healthcheck inheritance is not inspected.",
                    "A configured healthcheck is not claimed to succeed at runtime.",
                    "This check is intentionally narrow to avoid duplicating a future dedicated reliability analyzer."
            ));
        }

        Object rawHealthcheck = service.get("healthcheck");
        Map<String, Object> healthcheck = stringKeyedMap(rawHealthcheck);
        if (healthcheck == null) {
            return List.of(finding(
                    "COMPOSE-008", "Healthcheck Configuration", FindingStatus.NOT_CHECKED, FindingSeverity.LOW,
                    "Service '" + serviceIdentifier + "' has a healthcheck declaration that cannot be read statically.",
                    "Use a mapping with test, interval, timeout, retries, and disable keys where applicable.",
                    filePath, line(lines, healthcheckPath), "healthcheck", serviceIdentifier
            ));
        }

        Object rawDisable = healthcheck.get("disable");
        Boolean disable = booleanValue(rawDisable);
        if (rawDisable != null && (containsVariable(text(rawDisable)) || disable == null)) {
            return List.of(finding(
                    "COMPOSE-008", "Healthcheck Configuration", FindingStatus.NOT_CHECKED, FindingSeverity.LOW,
                    "Service '" + serviceIdentifier + "' has a healthcheck disable value that cannot be resolved statically.",
                    "Use a literal true or false value before relying on this check.",
                    filePath, line(lines, healthcheckPath + ".disable"), "healthcheck.disable", serviceIdentifier,
                    "A configured healthcheck is not claimed to succeed at runtime."
            ));
        }
        if (Boolean.TRUE.equals(disable)) {
            return List.of(finding(
                    "COMPOSE-008", "Healthcheck Configuration", FindingStatus.FAIL, FindingSeverity.HIGH,
                    "Service '" + serviceIdentifier + "' explicitly disables its healthcheck.",
                    "Re-enable the healthcheck or document an external readiness mechanism.",
                    filePath, line(lines, healthcheckPath + ".disable"), "healthcheck.disable", serviceIdentifier,
                    "A configured healthcheck is not claimed to succeed at runtime."
            ));
        }

        Object rawTest = healthcheck.get("test");
        String testPath = healthcheckPath + ".test";
        if (rawTest == null) {
            return List.of(finding(
                    "COMPOSE-008", "Healthcheck Configuration", FindingStatus.WARNING, FindingSeverity.LOW,
                    "Service '" + serviceIdentifier + "' has a healthcheck block without an explicit test command.",
                    "Declare a supported CMD or CMD-SHELL test command.",
                    filePath, line(lines, healthcheckPath), "healthcheck", serviceIdentifier
            ));
        }

        String scalarTest = text(rawTest);
        if (isNoneTest(rawTest, scalarTest)) {
            return List.of(finding(
                    "COMPOSE-008", "Healthcheck Configuration", FindingStatus.FAIL, FindingSeverity.HIGH,
                    "Service '" + serviceIdentifier + "' sets the healthcheck test to NONE.",
                    "Use a supported CMD or CMD-SHELL test command.",
                    filePath, line(lines, testPath), "healthcheck.test", serviceIdentifier,
                    "A configured healthcheck is not claimed to succeed at runtime."
            ));
        }
        if (containsVariable(scalarTest) || (rawTest instanceof List<?> values && containsVariable(values))) {
            return List.of(finding(
                    "COMPOSE-008", "Healthcheck Configuration", FindingStatus.NOT_CHECKED, FindingSeverity.LOW,
                    "Service '" + serviceIdentifier + "' uses an unresolved variable in its healthcheck test.",
                    "Resolve the variable before relying on this check.",
                    filePath, line(lines, testPath), "healthcheck.test", serviceIdentifier
            ));
        }

        if (rawTest instanceof List<?> testList) {
            if (!validCommandTest(testList)) {
                return List.of(finding(
                        "COMPOSE-008", "Healthcheck Configuration", FindingStatus.FAIL, FindingSeverity.MEDIUM,
                        "Service '" + serviceIdentifier + "' has an empty or malformed healthcheck test command.",
                        "Use a non-empty CMD or CMD-SHELL command list that checks service readiness.",
                        filePath, line(lines, testPath), "healthcheck.test", serviceIdentifier,
                        "A configured healthcheck is not claimed to succeed at runtime."
                ));
            }
            if (isTriviallySuccessfulTest(testList, scalarTest)) {
                return List.of(triviallySuccessfulHealthcheckFinding(filePath, testPath, serviceIdentifier, lines));
            }
            return List.of(finding(
                    "COMPOSE-008", "Healthcheck Configuration", FindingStatus.PASS, FindingSeverity.INFO,
                    "Service '" + serviceIdentifier + "' declares a non-empty CMD or CMD-SHELL healthcheck test.",
                    "No action required for this rule.",
                    filePath, line(lines, testPath), "healthcheck.test", serviceIdentifier,
                    "A configured healthcheck is not claimed to succeed at runtime."
            ));
        }

        if (scalarTest != null && isSupportedStringTest(scalarTest)) {
            if (isTriviallySuccessfulTest(rawTest, scalarTest)) {
                return List.of(triviallySuccessfulHealthcheckFinding(filePath, testPath, serviceIdentifier, lines));
            }
            return List.of(finding(
                    "COMPOSE-008", "Healthcheck Configuration", FindingStatus.PASS, FindingSeverity.INFO,
                    "Service '" + serviceIdentifier + "' declares a non-empty CMD or CMD-SHELL healthcheck test.",
                    "No action required for this rule.",
                    filePath, line(lines, testPath), "healthcheck.test", serviceIdentifier,
                    "A configured healthcheck is not claimed to succeed at runtime."
            ));
        }
        if (scalarTest != null && scalarTest.isBlank()) {
            return List.of(finding(
                    "COMPOSE-008", "Healthcheck Configuration", FindingStatus.FAIL, FindingSeverity.MEDIUM,
                    "Service '" + serviceIdentifier + "' has an empty healthcheck test command.",
                    "Use a non-empty CMD or CMD-SHELL test command.",
                    filePath, line(lines, testPath), "healthcheck.test", serviceIdentifier
            ));
        }
        return List.of(finding(
                "COMPOSE-008", "Healthcheck Configuration", FindingStatus.NOT_CHECKED, FindingSeverity.LOW,
                "Service '" + serviceIdentifier + "' has a healthcheck test form that is not statically recognized.",
                "Use a supported CMD or CMD-SHELL test command.",
                filePath, line(lines, testPath), "healthcheck.test", serviceIdentifier
        ));
    }

    private List<Finding> checkRestartPolicy(String filePath, String rawService,
                                             Map<String, Object> service, Map<String, Integer> lines) {
        String serviceIdentifier = serviceIdentifier(rawService);
        String restartPath = servicePath(rawService) + ".restart";
        if (!service.containsKey("restart")) {
            return List.of(finding(
                    "COMPOSE-009", "Restart Policy", FindingStatus.WARNING, FindingSeverity.LOW,
                    "Service '" + serviceIdentifier + "' has no explicit restart policy.",
                    "Set a restart policy appropriate for whether this service is long-running.",
                    filePath, line(lines, servicePath(rawService)), "restart", serviceIdentifier,
                    "Short-lived jobs may intentionally omit a restart policy."
            ));
        }

        Object rawRestart = service.get("restart");
        String value = restartValue(rawRestart);
        if (containsVariable(value)) {
            return List.of(finding(
                    "COMPOSE-009", "Restart Policy", FindingStatus.NOT_CHECKED, FindingSeverity.LOW,
                    "Service '" + serviceIdentifier + "' uses an unresolved restart policy variable.",
                    "Resolve the variable before relying on this check.",
                    filePath, line(lines, restartPath), "restart", serviceIdentifier
            ));
        }
        if (value == null) {
            return List.of(finding(
                    "COMPOSE-009", "Restart Policy", FindingStatus.NOT_CHECKED, FindingSeverity.LOW,
                    "Service '" + serviceIdentifier + "' has a restart policy that cannot be read statically.",
                    "Use a literal supported restart policy.",
                    filePath, line(lines, restartPath), "restart", serviceIdentifier
            ));
        }

        if ("no".equalsIgnoreCase(value)
                || "always".equalsIgnoreCase(value)
                || "unless-stopped".equalsIgnoreCase(value)
                || "on-failure".equalsIgnoreCase(value)
                || ON_FAILURE_WITH_LIMIT.matcher(value).matches()) {
            return List.of(finding(
                    "COMPOSE-009", "Restart Policy", FindingStatus.PASS, FindingSeverity.INFO,
                    "Service '" + serviceIdentifier + "' declares a supported restart policy.",
                    "No action required for this rule.",
                    filePath, line(lines, restartPath), "restart", serviceIdentifier,
                    "Platform-level restart behavior and retry outcomes are not evaluated."
            ));
        }
        return List.of(finding(
                "COMPOSE-009", "Restart Policy", FindingStatus.WARNING, FindingSeverity.LOW,
                "Service '" + serviceIdentifier + "' uses a restart policy outside the statically recognized forms.",
                "Use no, always, unless-stopped, on-failure, or on-failure with an explicit retry limit.",
                filePath, line(lines, restartPath), "restart", serviceIdentifier
        ));
    }

    private List<Finding> checkRiskyVolumeMounts(String filePath, String rawService,
                                                 Map<String, Object> service, Map<String, Integer> lines) {
        String serviceIdentifier = serviceIdentifier(rawService);
        List<Finding> findings = new ArrayList<>();
        List<Volume> volumes = volumes(service, rawService);

        if (volumes.isEmpty()) {
            return List.of(finding(
                    "COMPOSE-010", "Risky Volume Mounts", FindingStatus.PASS, FindingSeverity.INFO,
                    "Service '" + serviceIdentifier + "' declares no volume mounts.",
                    "No action required for this rule.",
                    filePath, line(lines, servicePath(rawService)), "volumes", serviceIdentifier
            ));
        }

        boolean evaluated = false;
        for (Volume volume : volumes) {
            if (isSocketRelated(volume) || isPotentiallySocketRelated(volume)) {
                continue;
            }
            evaluated = true;
            VolumeRisk risk = volumeRisk(volume);
            if (risk.status().equals(FindingStatus.PASS)) {
                continue;
            }
            findings.add(finding(
                    "COMPOSE-010", "Risky Volume Mounts", risk.status(), risk.severity(),
                    risk.explanation().replace("{service}", serviceIdentifier),
                    risk.action(),
                    filePath, line(lines, volume.path()), "volumes", serviceIdentifier,
                    "Named volumes, anonymous volumes, host bind mounts, and read-only mounts are distinguished from one another."
            ));
        }

        if (findings.isEmpty() && evaluated) {
            findings.add(finding(
                    "COMPOSE-010", "Risky Volume Mounts", FindingStatus.PASS, FindingSeverity.INFO,
                    "Service '" + serviceIdentifier + "' declares volume mounts that do not require escalation.",
                    "No action required for this rule.",
                    filePath, line(lines, servicePath(rawService)), "volumes", serviceIdentifier,
                    "Named volumes, anonymous volumes, host bind mounts, and read-only mounts are distinguished from one another."
            ));
        }
        if (findings.isEmpty()) {
            findings.add(finding(
                    "COMPOSE-010", "Risky Volume Mounts", FindingStatus.PASS, FindingSeverity.INFO,
                    "Service '" + serviceIdentifier + "' has no volume mounts requiring separate host bind-mount review.",
                    "No action required for this rule.",
                    filePath, line(lines, servicePath(rawService)), "volumes", serviceIdentifier
            ));
        }
        return findings;
    }

    private List<Finding> checkEnvironmentSecrets(String filePath, String rawService,
                                                  Map<String, Object> service, Map<String, Integer> lines) {
        String serviceIdentifier = serviceIdentifier(rawService);
        String environmentPath = servicePath(rawService) + ".environment";
        List<Finding> findings = new ArrayList<>();

        for (EnvEntry entry : environmentEntries(service, rawService)) {
            if (!SENSITIVE_ENV_KEY.matcher(entry.key()).find()) {
                continue;
            }
            if (entry.fallbackLiteral()) {
                findings.add(finding(
                        "COMPOSE.SUPPORT.ENVIRONMENT_SECRETS", "Environment Secret Handling",
                        FindingStatus.FAIL, FindingSeverity.HIGH,
                        "Service '" + serviceIdentifier + "' has a sensitive-looking environment key with a literal fallback default.",
                        "Remove the literal default and use a secret mechanism or an empty fallback instead.",
                        filePath, line(lines, entry.path()), "environment", serviceIdentifier,
                        "Raw environment values are never returned.",
                        "This narrow check does not replace the dedicated secret detection module."
                ));
            } else if (entry.variable() || entry.inherited() || entry.empty()) {
                findings.add(finding(
                        "COMPOSE.SUPPORT.ENVIRONMENT_SECRETS", "Environment Secret Handling",
                        FindingStatus.PASS, FindingSeverity.INFO,
                        "Service '" + serviceIdentifier + "' has a sensitive-looking environment key that uses a reference without a literal fallback, inheritance, or an empty value rather than a literal credential.",
                        "Prefer Compose secrets or runtime injection for sensitive values.",
                        filePath, line(lines, entry.path()), "environment", serviceIdentifier,
                        "Raw environment values are never returned.",
                        "This narrow check does not replace the dedicated secret detection module."
                ));
            } else if (entry.hardcoded()) {
                findings.add(finding(
                        "COMPOSE.SUPPORT.ENVIRONMENT_SECRETS", "Environment Secret Handling",
                        FindingStatus.FAIL, FindingSeverity.HIGH,
                        "Service '" + serviceIdentifier + "' has a sensitive-looking environment key with a literal hardcoded value.",
                        "Remove the literal value and use a secret mechanism or runtime reference instead.",
                        filePath, line(lines, entry.path()), "environment", serviceIdentifier,
                        "Raw environment values are never returned.",
                        "This narrow check does not replace the dedicated secret detection module."
                ));
            } else {
                findings.add(finding(
                        "COMPOSE.SUPPORT.ENVIRONMENT_SECRETS", "Environment Secret Handling",
                        FindingStatus.NOT_CHECKED, FindingSeverity.LOW,
                        "Service '" + serviceIdentifier + "' has a sensitive-looking environment value that cannot be read statically.",
                        "Use a literal empty value, a runtime reference, or a supported secret declaration.",
                        filePath, line(lines, entry.path()), "environment", serviceIdentifier,
                        "Raw environment values are never returned."
                ));
            }
        }

        if (service.containsKey("env_file")) {
            findings.add(finding(
                    "COMPOSE.SUPPORT.ENVIRONMENT_SECRETS", "Environment Secret Handling",
                    FindingStatus.NOT_CHECKED, FindingSeverity.LOW,
                    "Service '" + serviceIdentifier + "' references an env_file whose contents are not available for analysis.",
                    "Review env_file contents outside this static endpoint before relying on this check.",
                    filePath, line(lines, servicePath(rawService) + ".env_file"), "env_file", serviceIdentifier,
                    "Raw environment values are never returned."
            ));
        }

        if (findings.isEmpty()) {
            findings.add(finding(
                    "COMPOSE.SUPPORT.ENVIRONMENT_SECRETS", "Environment Secret Handling",
                    FindingStatus.PASS, FindingSeverity.INFO,
                    "Service '" + serviceIdentifier + "' has no literal hardcoded credential indicated by a sensitive-looking environment key.",
                    "No action required for this rule.",
                    filePath, line(lines, environmentPath), "environment", serviceIdentifier,
                    "Raw environment values are never returned."
            ));
        }
        return findings;
    }

    private ParsedCompose parse(String content) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setAllowRecursiveKeys(false);
        options.setMaxAliasesForCollections(MAX_ALIASES);
        options.setNestingDepthLimit(MAX_NESTING_DEPTH);
        options.setCodePointLimit(MAX_CONTENT_LENGTH);
        options.setTagInspector(tag -> ALLOWED_TAGS.contains(tag.getValue()));

        Yaml nodeYaml = new Yaml(options);
        Iterable<Node> nodesIterable = nodeYaml.composeAll(new StringReader(content));
        List<Node> nodes = new ArrayList<>();
        for (Node node : nodesIterable) {
            nodes.add(node);
        }
        if (nodes.isEmpty()) {
            throw new ComposeParseException();
        }

        Map<String, Integer> lines = new HashMap<>();
        int[] aliasUses = new int[]{0};
        IdentityHashMap<Node, Boolean> visited = new IdentityHashMap<>();
        for (Node node : nodes) {
            walk(node, "", lines, visited, aliasUses, 0);
        }
        if (aliasUses[0] > MAX_ALIASES) {
            throw new ComposeParseException();
        }

        Object root = nodes.size() == 1
                ? new Yaml(new SafeConstructor(options)).load(content)
                : null;
        return new ParsedCompose(nodes.size(), root, lines);
    }

    private void walk(Node node, String path, Map<String, Integer> lines,
                      IdentityHashMap<Node, Boolean> visited, int[] aliasUses, int depth) {
        if (node == null) {
            return;
        }
        if (depth > MAX_NESTING_DEPTH || !ALLOWED_TAGS.contains(node.getTag().getValue())) {
            throw new ComposeParseException();
        }
        if (visited.put(node, Boolean.TRUE) != null) {
            aliasUses[0]++;
            if (aliasUses[0] > MAX_ALIASES) {
                throw new ComposeParseException();
            }
            return;
        }
        int line = node.getStartMark() == null ? 0 : node.getStartMark().getLine() + 1;
        lines.putIfAbsent(path, line);

        if (node instanceof MappingNode mappingNode) {
            int index = 0;
            for (org.yaml.snakeyaml.nodes.NodeTuple tuple : mappingNode.getValue()) {
                String key = tuple.getKeyNode() instanceof ScalarNode scalar ? scalar.getValue() : "#" + index;
                String childPath = path.isEmpty() ? key : path + "." + key;
                int keyLine = tuple.getKeyNode().getStartMark() == null
                        ? line : tuple.getKeyNode().getStartMark().getLine() + 1;
                lines.putIfAbsent(childPath, keyLine);
                walk(tuple.getKeyNode(), childPath + ".key", lines, visited, aliasUses, depth + 1);
                walk(tuple.getValueNode(), childPath, lines, visited, aliasUses, depth + 1);
                index++;
            }
        } else if (node instanceof SequenceNode sequenceNode) {
            for (int index = 0; index < sequenceNode.getValue().size(); index++) {
                String childPath = path + "[" + index + "]";
                Node child = sequenceNode.getValue().get(index);
                int childLine = child.getStartMark() == null ? line : child.getStartMark().getLine() + 1;
                lines.putIfAbsent(childPath, childLine);
                walk(child, childPath, lines, visited, aliasUses, depth + 1);
            }
        }
    }

    private Map<String, Object> extractServices(Object root) {
        if (!(root instanceof Map<?, ?> rootMap)) {
            return null;
        }
        Map<String, Object> normalizedRoot = stringKeyedMap(rootMap);
        if (normalizedRoot == null) {
            return null;
        }
        Map<String, Object> services = stringKeyedMap(normalizedRoot.get("services"));
        if (services == null || services.isEmpty()) {
            return null;
        }
        return services;
    }

    private Map<String, Object> stringKeyedMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                return null;
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private String buildContext(Object build) {
        if (build instanceof String value) {
            return value.trim();
        }
        if (build instanceof Map<?, ?> map) {
            return text(map.get("context"));
        }
        return null;
    }

    private String imageTag(String image) {
        int slash = image.lastIndexOf('/');
        int colon = image.lastIndexOf(':');
        if (colon <= slash || colon >= image.length() - 1) {
            return null;
        }
        return image.substring(colon + 1).trim();
    }

    private String text(Object value) {
        if (value instanceof String string) {
            return string.trim();
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value).trim();
        }
        return null;
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = text(value);
        if ("true".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private String restartValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool ? "yes" : "no";
        }
        return text(value);
    }

    private boolean containsVariable(String value) {
        return value != null && value.contains("${");
    }

    private boolean containsVariable(List<?> values) {
        for (Object value : values) {
            if (containsVariable(text(value))) {
                return true;
            }
        }
        return false;
    }

    private String fileName(String path) {
        if (path == null) {
            return "";
        }
        int separator = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return (separator >= 0 ? path.substring(separator + 1) : path).toLowerCase(Locale.ROOT);
    }

    private String serviceIdentifier(String name) {
        String sanitized = name == null ? "unknown" : name.replaceAll("[^A-Za-z0-9_.-]", "_");
        if (sanitized.length() > 80) {
            sanitized = sanitized.substring(0, 80);
        }
        return sanitized.isEmpty() ? "service" : sanitized;
    }

    private String servicePath(String rawService) {
        return "services." + rawService;
    }

    private Integer line(Map<String, Integer> lines, String path) {
        return lines.get(path);
    }

    private List<Volume> volumes(Map<String, Object> service, String rawService) {
        List<Volume> result = new ArrayList<>();
        Object rawVolumes = service.get("volumes");
        if (rawVolumes == null) {
            return result;
        }
        String basePath = servicePath(rawService) + ".volumes";
        if (rawVolumes instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) {
                result.add(volume(list.get(index), basePath + "[" + index + "]"));
            }
        } else if (rawVolumes instanceof Map<?, ?> || text(rawVolumes) != null) {
            result.add(volume(rawVolumes, basePath + "[0]"));
        } else {
            result.add(new Volume(null, null, null, false, false, false, false, true, basePath));
        }
        return result;
    }

    private Volume volume(Object raw, String path) {
        String shortValue = text(raw);
        if (shortValue != null) {
            return shortVolume(shortValue, path);
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> normalized = stringKeyedMap(map);
            if (normalized == null) {
                return new Volume(null, null, null, false, false, false, false, true, path);
            }
            String source = text(normalized.get("source"));
            String target = text(normalized.get("target"));
            String type = text(normalized.get("type"));
            boolean readOnly = Boolean.TRUE.equals(booleanValue(normalized.get("read_only")));
            boolean named = "volume".equalsIgnoreCase(type)
                    || (type == null && source != null && !isHostPath(source));
            boolean malformed = target == null || target.isBlank()
                    || ("bind".equalsIgnoreCase(type) && (source == null || source.isBlank()))
                    || (type != null && !VOLUME_TYPES.contains(type.toLowerCase(Locale.ROOT)));
            return new Volume(
                    source, target, readOnly ? "ro" : null, readOnly, named,
                    source == null, containsVariable(source) || containsVariable(target), malformed, path
            );
        }
        return new Volume(null, null, null, false, false, false, false, true, path);
    }

    private Volume shortVolume(String raw, String path) {
        String value = raw.trim();
        String[] parts = value.split(":", -1);
        if (parts.length == 1) {
            String target = parts[0];
            return new Volume(
                    null, target, null, false, false, target.startsWith("/"),
                    containsVariable(target), target.isBlank(), path
            );
        }
        String source = parts[0];
        String target = parts.length > 1 ? parts[1] : null;
        String mode = parts.length > 2 ? parts[2] : null;
        boolean readOnly = mode != null && (mode.contains("ro") || mode.contains("readonly"));
        boolean named = !source.isEmpty() && !isHostPath(source);
        boolean malformed = parts.length > 3 || source.isBlank() || target == null || target.isBlank()
                || (mode != null && mode.isBlank());
        return new Volume(
                source, target, mode, readOnly, named, source.isEmpty(),
                containsVariable(source) || containsVariable(target), malformed, path
        );
    }

    private boolean isHostPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        return path.startsWith("/") || path.startsWith(".") || path.startsWith("~")
                || WINDOWS_DRIVE.matcher(path).matches();
    }

    private boolean isSocketRelated(Volume volume) {
        return isDockerSocketPath(volume.source()) || isDockerSocketPath(volume.target());
    }

    private boolean isPotentiallySocketRelated(Volume volume) {
        return isPotentiallySocketPath(volume.source()) || isPotentiallySocketPath(volume.target());
    }

    private boolean isPotentiallySocketPath(String value) {
        if (value == null || !containsVariable(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("docker") || normalized.contains("sock")) {
            return true;
        }
        return normalized.startsWith("/var/run/") || normalized.startsWith("/run/")
                || normalized.matches("\\$\\{[a-z0-9_.-]*(socket|sock|docker)[a-z0-9_.-]*\\}");
    }

    private boolean isDockerSocketPath(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return "/var/run/docker.sock".equals(normalized) || "/run/docker.sock".equals(normalized);
    }

    private VolumeRisk volumeRisk(Volume volume) {
        if (volume.malformed()) {
            return new VolumeRisk(
                    FindingStatus.NOT_CHECKED, FindingSeverity.LOW,
                    "Service '{service}' has a volume declaration that could not be read statically.",
                    "Use a supported short or long volume form with a readable source and target."
            );
        }
        if (volume.variable()) {
            return new VolumeRisk(
                    FindingStatus.NOT_CHECKED, FindingSeverity.LOW,
                    "Service '{service}' has a variable-based mount that cannot be resolved statically.",
                    "Resolve the mount source before relying on this check."
            );
        }
        if (volume.named() || volume.anonymous()) {
            return new VolumeRisk(
                    FindingStatus.PASS, FindingSeverity.INFO,
                    "Service '{service}' uses a named or anonymous volume without a host bind mount.",
                    "No action required for this rule."
            );
        }
        if (!isHostPath(volume.source())) {
            return new VolumeRisk(
                    FindingStatus.PASS, FindingSeverity.INFO,
                    "Service '{service}' uses a volume form that is not identified as a host bind mount.",
                    "No action required for this rule."
            );
        }

        String source = volume.source() == null ? "" : volume.source().trim();
        String normalized = source.toLowerCase(Locale.ROOT);
        boolean sensitive = "/".equals(normalized)
                || SENSITIVE_HOST_PREFIXES.stream().anyMatch(prefix ->
                normalized.equals(prefix) || normalized.startsWith(prefix + "/"));
        boolean broadProjectRoot = ".".equals(normalized) || "./".equals(normalized)
                || "..".equals(normalized) || "../".equals(normalized);
        String explanation;
        String action;
        String status;
        String severity;
        if (sensitive && !volume.readOnly()) {
            status = FindingStatus.FAIL;
            severity = FindingSeverity.HIGH;
            explanation = "Service '{service}' mounts a sensitive host system path with write access.";
            action = "Replace the host bind mount with a named volume or a narrowly scoped read-only mount.";
        } else if (sensitive) {
            status = FindingStatus.WARNING;
            severity = FindingSeverity.MEDIUM;
            explanation = "Service '{service}' mounts a sensitive host system path read-only.";
            action = "Confirm that read-only access to this host path is required and narrowly scoped.";
        } else if (broadProjectRoot && !volume.readOnly()) {
            status = FindingStatus.WARNING;
            severity = FindingSeverity.MEDIUM;
            explanation = "Service '{service}' mounts a broad host project path with write access.";
            action = "Mount only the specific files or directories the service needs, preferably read-only.";
        } else {
            return new VolumeRisk(
                    FindingStatus.PASS, FindingSeverity.INFO,
                    "Service '{service}' uses a host bind mount that is not identified as broadly risky.",
                    "No action required for this rule."
            );
        }
        return new VolumeRisk(status, severity, explanation, action);
    }

    private List<Port> ports(Map<String, Object> service, String rawService) {
        List<Port> result = new ArrayList<>();
        String basePath = servicePath(rawService) + ".ports";
        addPortValues(result, service.get("ports"), basePath);

        Object expose = service.get("expose");
        if (expose instanceof List<?> exposeList) {
            for (int index = 0; index < exposeList.size(); index++) {
                String path = servicePath(rawService) + ".expose[" + index + "]";
                String value = text(exposeList.get(index));
                result.add(value == null
                        ? malformedPort(true, false, path)
                        : exposedPort(value, containsVariable(value), path));
            }
        } else if (text(expose) != null) {
            String value = text(expose);
            result.add(exposedPort(value, containsVariable(value), servicePath(rawService) + ".expose"));
        }
        return result;
    }

    private Port publishedPort(String bind, String hostPort, String targetPort,
                               boolean variable, String path) {
        return new Port(true, false, false, bind, hostPort, targetPort, variable, false, path);
    }

    private Port dynamicPort(String targetPort, boolean variable, String path) {
        return dynamicPort(null, targetPort, variable, path);
    }

    private Port dynamicPort(String bind, String targetPort, boolean variable, String path) {
        return new Port(true, true, false, bind, null, targetPort, variable, false, path);
    }

    private Port containerOnlyPort(String targetPort, boolean variable, String path) {
        return new Port(false, false, false, null, null, targetPort, variable, false, path);
    }

    private Port exposedPort(String targetPort, boolean variable, String path) {
        return new Port(false, false, true, null, null, targetPort, variable, false, path);
    }

    private Port malformedPort(boolean exposed, boolean variable, String path) {
        return new Port(false, false, exposed, null, null, null, variable, true, path);
    }

    private void addPortValues(List<Port> result, Object rawPorts, String basePath) {
        if (rawPorts == null) {
            return;
        }
        if (rawPorts instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) {
                result.add(port(list.get(index), basePath + "[" + index + "]"));
            }
            return;
        }
        if (rawPorts instanceof Map<?, ?> || text(rawPorts) != null) {
            result.add(port(rawPorts, basePath + "[0]"));
            return;
        }
        result.add(malformedPort(false, false, basePath));
    }

    private Port port(Object raw, String path) {
        String value = text(raw);
        if (value != null) {
            return shortPort(value, path);
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> normalized = stringKeyedMap(map);
            if (normalized == null || normalized.get("target") == null) {
                return malformedPort(false, false, path);
            }
            String target = text(normalized.get("target"));
            Object publishedObject = normalized.get("published");
            String bind = text(normalized.get("host_ip"));
            boolean variable = containsVariable(target) || containsVariable(text(publishedObject))
                    || containsVariable(bind);
            if (target == null || target.isBlank()) {
                return malformedPort(false, variable, path);
            }
            if (Boolean.FALSE.equals(publishedObject)) {
                return containerOnlyPort(target, variable, path);
            }
            if (publishedObject == null) {
                return dynamicPort(bind, target, variable, path);
            }
            String publishedPort = text(publishedObject);
            if (publishedPort == null || publishedPort.isBlank()) {
                return malformedPort(false, variable, path);
            }
            return publishedPort(bind, publishedPort, target, variable, path);
        }
        return malformedPort(false, false, path);
    }

    private Port shortPort(String raw, String path) {
        String value = raw.trim();
        int protocol = value.indexOf('/');
        if (protocol >= 0) {
            value = value.substring(0, protocol);
        }
        boolean variable = containsVariable(value);
        if (value.startsWith("[")) {
            int close = value.indexOf(']');
            if (close < 0) {
                return malformedPort(false, variable, path);
            }
            String bind = value.substring(0, close + 1);
            String rest = value.substring(close + 1);
            if (rest.startsWith(":")) {
                rest = rest.substring(1);
            }
            String[] tokens = rest.split(":", -1);
            if (tokens.length == 2 && !tokens[0].isBlank() && !tokens[1].isBlank()) {
                return publishedPort(bind, tokens[0], tokens[1], variable, path);
            }
            return malformedPort(false, variable, path);
        }

        String[] tokens = value.split(":", -1);
        if (tokens.length == 1) {
            if (tokens[0].isBlank()) {
                return malformedPort(false, variable, path);
            }
            return dynamicPort(tokens[0], variable, path);
        }
        if (tokens.length == 2 && !tokens[0].isBlank() && !tokens[1].isBlank()) {
            return publishedPort(null, tokens[0], tokens[1], variable, path);
        }
        if (tokens.length == 3 && !tokens[0].isBlank() && !tokens[1].isBlank() && !tokens[2].isBlank()) {
            return publishedPort(tokens[0], tokens[1], tokens[2], variable, path);
        }
        return malformedPort(false, variable, path);
    }

    private Binding binding(String bind) {
        if (bind == null || bind.isBlank()) {
            return Binding.ALL;
        }
        String normalized = bind.trim().toLowerCase(Locale.ROOT);
        if ("0.0.0.0".equals(normalized) || "::".equals(normalized)
                || "[::]".equals(normalized) || "*".equals(normalized)) {
            return Binding.ALL;
        }
        if ("localhost".equals(normalized) || "[::1]".equals(normalized)
                || "::1".equals(normalized) || normalized.startsWith("127.")) {
            return Binding.LOOPBACK;
        }
        return Binding.SPECIFIC;
    }

    private boolean isDatabasePort(String value) {
        if (value == null) {
            return false;
        }
        String port = value.split("/", 2)[0].trim();
        return DATABASE_PORTS.contains(port);
    }

    private boolean validCommandTest(List<?> test) {
        if (test.isEmpty()) {
            return false;
        }
        String first = text(test.get(0));
        if (!"CMD".equalsIgnoreCase(first) && !"CMD-SHELL".equalsIgnoreCase(first)) {
            return false;
        }
        if (test.size() < 2) {
            return false;
        }
        for (int index = 1; index < test.size(); index++) {
            String token = text(test.get(index));
            if (token == null || token.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private boolean isNoneTest(Object rawTest, String scalarTest) {
        if ("NONE".equalsIgnoreCase(scalarTest)) {
            return true;
        }
        if (rawTest instanceof List<?> testList && testList.size() == 1) {
            return "NONE".equalsIgnoreCase(text(testList.get(0)));
        }
        return false;
    }

    private boolean isTriviallySuccessfulTest(Object rawTest, String scalarTest) {
        if (rawTest instanceof List<?> testList) {
            String first = text(testList.get(0));
            if (!"CMD".equalsIgnoreCase(first) && !"CMD-SHELL".equalsIgnoreCase(first)) {
                return false;
            }
            StringBuilder command = new StringBuilder();
            for (int index = 1; index < testList.size(); index++) {
                if (index > 1) {
                    command.append(' ');
                }
                command.append(text(testList.get(index)));
            }
            return isTrivialCommand(command.toString());
        }
        if (scalarTest == null) {
            return false;
        }
        String upper = scalarTest.toUpperCase(Locale.ROOT);
        if (upper.startsWith("CMD-SHELL ")) {
            return isTrivialCommand(scalarTest.substring("CMD-SHELL ".length()));
        }
        if (upper.startsWith("CMD ")) {
            return isTrivialCommand(scalarTest.substring("CMD ".length()));
        }
        return false;
    }

    private boolean isTrivialCommand(String command) {
        if (command == null) {
            return false;
        }
        return TRIVIAL_SUCCESS_COMMANDS.contains(command.trim().toLowerCase(Locale.ROOT));
    }

    private Finding triviallySuccessfulHealthcheckFinding(String filePath, String testPath,
                                                          String serviceIdentifier, Map<String, Integer> lines) {
        return finding(
                "COMPOSE-008", "Healthcheck Configuration", FindingStatus.WARNING, FindingSeverity.MEDIUM,
                "Service '" + serviceIdentifier + "' declares a supported but trivially successful healthcheck test.",
                "Use a command that verifies actual service readiness instead of an always-successful command.",
                filePath, line(lines, testPath), "healthcheck.test", serviceIdentifier,
                "A configured healthcheck is not claimed to succeed at runtime.",
                "This static check does not execute the healthcheck command."
        );
    }

    private boolean isSupportedStringTest(String test) {
        String upper = test.trim().toUpperCase(Locale.ROOT);
        return upper.startsWith("CMD-SHELL ") || upper.startsWith("CMD ");
    }

    private List<EnvEntry> environmentEntries(Map<String, Object> service, String rawService) {
        List<EnvEntry> result = new ArrayList<>();
        Object environment = service.get("environment");
        String basePath = servicePath(rawService) + ".environment";
        if (environment instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = text(entry.getKey());
                if (key == null) {
                    continue;
                }
                Object value = entry.getValue();
                result.add(classifyEnvEntry(
                        key, value, basePath + "." + key, value == null, text(value), value
                ));
            }
        } else if (environment instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) {
                String item = text(list.get(index));
                if (item == null) {
                    continue;
                }
                int separator = item.indexOf('=');
                String key = separator < 0 ? item : item.substring(0, separator);
                Object value = separator < 0 ? null : item.substring(separator + 1);
                result.add(classifyEnvEntry(
                        key, value, basePath + "[" + index + "]", separator < 0, text(value), value
                ));
            }
        }
        return result;
    }

    private EnvEntry classifyEnvEntry(String key, Object rawValue, String path,
                                      boolean inherited, String valueText, Object value) {
        boolean variable = containsVariable(valueText);
        boolean fallbackLiteral = hasLiteralFallback(valueText);
        boolean empty = valueText != null && valueText.isBlank();
        boolean unsupported = valueText == null && rawValue != null && !inherited;
        boolean hardcoded = valueText != null && !empty && !variable && !inherited;
        return new EnvEntry(key, path, inherited, variable, fallbackLiteral, empty, unsupported, hardcoded);
    }

    private boolean hasLiteralFallback(String value) {
        if (value == null) {
            return false;
        }
        int index = 0;
        while (true) {
            int start = value.indexOf("${", index);
            if (start < 0) {
                return false;
            }
            int end = value.indexOf('}', start + 2);
            if (end < 0) {
                return false;
            }
            String expression = value.substring(start + 2, end);
            int separator = expression.indexOf(":-");
            int fallbackStart = separator >= 0 ? separator + 2 : -1;
            if (separator < 0) {
                separator = expression.indexOf('-');
                fallbackStart = separator + 1;
            }
            if (separator >= 0) {
                String fallback = expression.substring(fallbackStart).trim();
                if (!fallback.isEmpty() && !containsVariable(fallback)) {
                    return true;
                }
            }
            index = end + 1;
        }
    }

    private Finding finding(String ruleId, String name, String status, String severity, String explanation,
                            String action, String filePath, Integer line, String evidence,
                            String serviceIdentifier, String... extraLimitations) {
        List<String> limitations = new ArrayList<>(BASE_LIMITATIONS);
        if (extraLimitations != null) {
            for (String limitation : extraLimitations) {
                if (limitation != null && !limitations.contains(limitation)) {
                    limitations.add(limitation);
                }
            }
        }
        Finding finding = new Finding(
                ruleId,
                CATEGORY,
                name,
                status,
                severity,
                explanation,
                action,
                action,
                filePath,
                line,
                evidence,
                limitations
        );
        finding.setServiceIdentifier(serviceIdentifier);
        return finding;
    }

    private Finding parseErrorFinding(String filePath) {
        return finding(
                "COMPOSE.PARSE_ERROR", "Compose YAML Parsing", FindingStatus.ERROR, FindingSeverity.HIGH,
                FILE_NAME_ERROR,
                "Upload a smaller Compose file that uses supported YAML data structures without unsafe tags.",
                filePath, null, null, null,
                "Parser exception messages are not exposed in API responses."
        );
    }

    private ComposeAnalysisResponse response(List<Finding> findings) {
        return new ComposeAnalysisResponse(FindingStatus.aggregate(findings), findings);
    }

    private enum Binding {
        LOOPBACK,
        ALL,
        SPECIFIC
    }

    private record Volume(String source, String target, String mode, boolean readOnly, boolean named,
                          boolean anonymous, boolean variable, boolean malformed, String path) {
    }

    private record VolumeRisk(String status, String severity, String explanation, String action) {
    }

    private record Port(boolean published, boolean dynamic, boolean exposed, String bind, String hostPort,
                        String targetPort, boolean variable, boolean malformed, String path) {
    }

    private record EnvEntry(String key, String path, boolean inherited, boolean variable, boolean fallbackLiteral,
                            boolean empty, boolean unsupported, boolean hardcoded) {
    }

    private record ParsedCompose(int documentCount, Object root, Map<String, Integer> lines) {
    }

    private static final class ComposeParseException extends RuntimeException {
        private ComposeParseException() {
            super(FILE_NAME_ERROR);
        }
    }
}
