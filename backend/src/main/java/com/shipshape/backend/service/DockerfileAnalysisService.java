package com.shipshape.backend.service;

import com.shipshape.backend.dto.DockerfileAnalysisResponse;
import com.shipshape.backend.dto.Finding;
import com.shipshape.backend.dto.FindingSeverity;
import com.shipshape.backend.dto.FindingStatus;
import com.shipshape.backend.exception.BadRequestException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class DockerfileAnalysisService {

    private static final int MAX_CONTENT_LENGTH = 100_000;
    private static final String CATEGORY = "DOCKERFILE";
    private static final String BASE_LIMITATION =
            "Dockerfile instructions are parsed as text; no image build, container execution, shell command, or network lookup is performed.";

    private static final Set<String> KNOWN_KEYWORDS = Set.of(
            "ADD", "ARG", "CMD", "COPY", "ENTRYPOINT", "ENV", "EXPOSE", "FROM", "HEALTHCHECK",
            "LABEL", "MAINTAINER", "ONBUILD", "RUN", "SHELL", "STOPSIGNAL", "USER", "VOLUME", "WORKDIR"
    );

    private static final Pattern REMOTE_SOURCE = Pattern.compile("https?://|git@", Pattern.CASE_INSENSITIVE);
    private static final Pattern ARCHIVE_SOURCE = Pattern.compile(
            "\\.(tar|tar\\.gz|tgz|tar\\.bz2|tbz2|zip)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DANGEROUS_PERMISSION = Pattern.compile(
            "(?:^|\\s)(?:chmod\\s+(?:-\\w+\\s+)*(?:0?777|a\\+rwx)|--chmod\\s*=\\s*(?:0?777|a\\+rwx))",
            Pattern.CASE_INSENSITIVE);

    public DockerfileAnalysisResponse analyze(String content) {
        return analyze(null, content, false);
    }

    public DockerfileAnalysisResponse analyze(String filePath, String content) {
        return analyze(filePath, content, false);
    }

    public DockerfileAnalysisResponse analyze(String filePath, String content, boolean dockerIgnoreAvailable) {
        validateContent(content);

        List<Instruction> instructions = parseInstructions(content);
        List<Stage> stages = parseStages(instructions);
        List<Finding> findings = new ArrayList<>();

        findings.add(checkFromInstruction(stages, filePath));
        findings.add(checkBaseImageVersion(stages, filePath));
        findings.add(checkContainerUser(stages, filePath));
        findings.add(checkWorkingDirectory(stages, filePath));
        findings.add(checkStartupCommand(stages, filePath));
        findings.add(checkCopyAndAdd(instructions, dockerIgnoreAvailable, filePath));
        findings.add(checkDangerousPermissions(instructions, filePath));
        findings.add(checkHealthcheck(stages, filePath));

        Finding unsupportedSyntaxFinding = checkUnsupportedSyntax(instructions, filePath);
        if (unsupportedSyntaxFinding != null) {
            findings.add(unsupportedSyntaxFinding);
        }

        return new DockerfileAnalysisResponse(FindingStatus.aggregate(findings), findings);
    }

    private void validateContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new BadRequestException("Dockerfile content is required");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new BadRequestException("Dockerfile content is too large");
        }
    }

    private Finding checkFromInstruction(List<Stage> stages, String filePath) {
        if (stages.isEmpty()) {
            return finding(
                    "DOCKER-001", "FROM Instruction", FindingStatus.FAIL, FindingSeverity.HIGH,
                    "No FROM instruction was found. A Dockerfile must define a base image.",
                    "Add a FROM instruction with a base image.",
                    filePath, null, null,
                    "This is a limited structural check, not complete Dockerfile syntax validation."
            );
        }

        Instruction malformedFrom = null;
        for (Stage stage : stages) {
            if (stage.base().image() == null || stage.base().malformed()) {
                malformedFrom = stage.from();
                break;
            }
        }

        if (malformedFrom != null) {
            return finding(
                    "DOCKER-001", "FROM Instruction", FindingStatus.FAIL, FindingSeverity.HIGH,
                    "A FROM instruction does not name a statically readable base image.",
                    "Provide a base image for every FROM instruction.",
                    filePath, malformedFrom, null,
                    "This check does not validate every Dockerfile syntax rule."
            );
        }

        String explanation = stages.size() == 1
                ? "A FROM instruction with a readable base reference was found."
                : "Multiple FROM instructions with readable base references were found, so multi-stage builds are supported.";

        return finding(
                "DOCKER-001", "FROM Instruction", FindingStatus.PASS, FindingSeverity.INFO,
                explanation,
                "No action required for this structural rule.",
                filePath, stages.get(0).from(), null,
                "This is a limited structural check, not complete Dockerfile syntax validation."
        );
    }

    private Finding checkBaseImageVersion(List<Stage> stages, String filePath) {
        if (stages.isEmpty()) {
            return finding(
                    "DOCKER-002", "Base Image Tag", FindingStatus.NOT_CHECKED, FindingSeverity.LOW,
                    "The base image version could not be evaluated because no FROM instruction was found.",
                    "Add a FROM instruction with a pinned base image reference.",
                    filePath, null, null
            );
        }

        Set<String> allAliases = new HashSet<>();
        for (Stage stage : stages) {
            if (stage.alias() != null) {
                allAliases.add(stage.alias().toLowerCase(Locale.ROOT));
            }
        }
        Set<String> seenAliases = new HashSet<>();

        Instruction latest = null;
        Instruction missingTag = null;
        Instruction variable = null;
        Instruction aliasReference = null;
        Instruction forwardAliasReference = null;
        Instruction digest = null;
        Instruction versionTag = null;
        Instruction scratch = null;

        for (Stage stage : stages) {
            BaseReference base = stage.base();
            if (base.image() == null) {
                continue;
            }

            String image = base.image();
            String normalizedImage = image.toLowerCase(Locale.ROOT);

            if (base.variable()) {
                variable = firstNonNull(variable, stage.from());
            } else if (seenAliases.contains(normalizedImage)) {
                aliasReference = firstNonNull(aliasReference, stage.from());
            } else if (allAliases.contains(normalizedImage)) {
                forwardAliasReference = firstNonNull(forwardAliasReference, stage.from());
            } else if ("scratch".equals(normalizedImage)) {
                scratch = firstNonNull(scratch, stage.from());
            } else if (image.contains("@")) {
                digest = firstNonNull(digest, stage.from());
            } else {
                String tag = extractTag(image);
                if (tag == null) {
                    missingTag = firstNonNull(missingTag, stage.from());
                } else if ("latest".equals(tag.toLowerCase(Locale.ROOT))) {
                    latest = firstNonNull(latest, stage.from());
                } else {
                    versionTag = firstNonNull(versionTag, stage.from());
                }
            }
            if (stage.alias() != null) {
                seenAliases.add(stage.alias().toLowerCase(Locale.ROOT));
            }
        }

        if (latest != null) {
            return finding(
                    "DOCKER-002", "Base Image Tag", FindingStatus.WARNING, FindingSeverity.MEDIUM,
                    "At least one FROM instruction explicitly uses the :latest tag.",
                    "Pin a specific version or digest instead of :latest for reproducible builds.",
                    filePath, latest, null,
                    "No registry lookup was performed; a tag does not guarantee an immutable image."
            );
        }

        if (missingTag != null) {
            return finding(
                    "DOCKER-002", "Base Image Tag", FindingStatus.WARNING, FindingSeverity.MEDIUM,
                    "At least one base image has no explicit tag.",
                    "Specify an explicit version tag or digest for the base image.",
                    filePath, missingTag, null,
                    "A missing tag may resolve differently over time; no registry metadata was checked.",
                    "A version tag is mutable unless the image is pinned by digest."
            );
        }

        if (variable != null) {
            return finding(
                    "DOCKER-002", "Base Image Tag", FindingStatus.NOT_CHECKED, FindingSeverity.LOW,
                    "At least one base image reference contains a build variable and could not be resolved statically.",
                    "Resolve the variable before relying on this rule for production readiness.",
                    filePath, variable, null,
                    "Variable-based image references are not resolved from ARG or ENV values."
            );
        }

        if (forwardAliasReference != null) {
            return finding(
                    "DOCKER-002", "Base Image Tag", FindingStatus.NOT_CHECKED, FindingSeverity.LOW,
                    "A FROM instruction references a build-stage alias defined later in the file.",
                    "Move the referenced stage before the referring stage or use a concrete base image.",
                    filePath, forwardAliasReference, null,
                    "Forward build-stage references are not treated as resolvable inheritance."
            );
        }

        if (digest != null) {
            return finding(
                    "DOCKER-002", "Base Image Tag", FindingStatus.PASS, FindingSeverity.INFO,
                    "At least one base image is pinned by digest.",
                    "No action required for this rule.",
                    filePath, digest, null,
                    "Digest pinning improves reproducibility, but registry integrity and signature state were not verified."
            );
        }

        if (versionTag != null) {
            return finding(
                    "DOCKER-002", "Base Image Tag", FindingStatus.PASS, FindingSeverity.INFO,
                    "An explicit non-latest base image tag was found.",
                    "Consider digest pinning for stronger immutability.",
                    filePath, versionTag, null,
                    "An explicit tag does not guarantee that the image is immutable."
            );
        }

        if (scratch != null) {
            return finding(
                    "DOCKER-002", "Base Image Tag", FindingStatus.PASS, FindingSeverity.INFO,
                    "The base image is scratch, which has no tag to pin.",
                    "No action required for this rule.",
                    filePath, scratch, null,
                    "scratch contains no runtime libraries or user definitions."
            );
        }

        return finding(
                "DOCKER-002", "Base Image Tag", FindingStatus.PASS, FindingSeverity.INFO,
                "The base image references an earlier build stage whose version is handled by that stage.",
                "No action required for this rule.",
                filePath, aliasReference, null,
                "The referenced stage is evaluated separately in the same Dockerfile."
        );
    }

    private Finding checkContainerUser(List<Stage> stages, String filePath) {
        List<Stage> chain = stageChain(stages);
        if (chain.isEmpty()) {
            return finding(
                    "DOCKER-003", "Final Stage User", FindingStatus.NOT_CHECKED, FindingSeverity.MEDIUM,
                    "The effective container user could not be determined because no FROM instruction was found.",
                    "Add a FROM instruction and explicitly set a non-root user where appropriate.",
                    filePath, null, null
            );
        }

        Instruction userInstruction = findLastInstruction(chain, "USER");
        Instruction anchor = chain.get(0).from();

        if (userInstruction == null) {
            return finding(
                    "DOCKER-003", "Final Stage User", FindingStatus.NOT_CHECKED, FindingSeverity.MEDIUM,
                    "No explicit USER instruction was found in the final stage chain, so the effective runtime user is unknown.",
                    "Set an explicit non-root USER when the base image user is not guaranteed.",
                    filePath, null, anchor.lineNumber(),
                    "The base image user cannot be determined from the submitted Dockerfile.",
                    "Missing USER is not reported as a definite root execution."
            );
        }

        String usernameToken = extractFirstToken(userInstruction.arguments());
        if (usernameToken == null || usernameToken.contains("${")) {
            return finding(
                    "DOCKER-003", "Final Stage User", FindingStatus.NOT_CHECKED, FindingSeverity.LOW,
                    "A USER instruction was found, but its value could not be resolved statically.",
                    "Use a literal non-root user when possible.",
                    filePath, userInstruction, null,
                    "Variable-based USER values are not resolved."
            );
        }

        String username = usernameToken.split(":", 2)[0];
        if ("root".equalsIgnoreCase(username) || "0".equals(username)) {
            return finding(
                    "DOCKER-003", "Final Stage User", FindingStatus.WARNING, FindingSeverity.HIGH,
                    "The final stage chain explicitly configures root as the container user.",
                    "Create and use a non-root user where the application allows it.",
                    filePath, userInstruction, null,
                    "This rule does not claim that every root container is exploitable."
            );
        }

        return finding(
                "DOCKER-003", "Final Stage User", FindingStatus.PASS, FindingSeverity.INFO,
                "The final stage chain explicitly configures a non-root user.",
                "No action required for this rule.",
                filePath, userInstruction, null
        );
    }

    private Finding checkWorkingDirectory(List<Stage> stages, String filePath) {
        List<Stage> chain = stageChain(stages);
        if (chain.isEmpty()) {
            return finding(
                    "DOCKER-004", "Working Directory", FindingStatus.NOT_CHECKED, FindingSeverity.LOW,
                    "The working directory could not be evaluated because no FROM instruction was found.",
                    "Add a FROM instruction and an explicit WORKDIR if relative paths matter.",
                    filePath, null, null
            );
        }

        Instruction workdir = findLastInstruction(chain, "WORKDIR");
        if (workdir != null && workdir.arguments().isBlank()) {
            return finding(
                    "DOCKER-004", "Working Directory", FindingStatus.WARNING, FindingSeverity.LOW,
                    "A WORKDIR instruction was found without a path.",
                    "Provide a literal working directory path.",
                    filePath, workdir, null,
                    "An empty WORKDIR is not treated as a valid explicit working directory."
            );
        }
        if (workdir != null) {
            return finding(
                    "DOCKER-004", "Working Directory", FindingStatus.PASS, FindingSeverity.INFO,
                    "An explicit WORKDIR was found in the final stage chain.",
                    "No action required for this rule.",
                    filePath, workdir, null
            );
        }

        return finding(
                "DOCKER-004", "Working Directory", FindingStatus.WARNING, FindingSeverity.LOW,
                "No explicit WORKDIR was found in the final stage chain.",
                "Set an explicit WORKDIR so relative paths are predictable.",
                filePath, null, chain.get(0).from().lineNumber(),
                "The working directory inherited from the base image cannot be determined from the submitted Dockerfile."
        );
    }

    private Finding checkStartupCommand(List<Stage> stages, String filePath) {
        List<Stage> chain = stageChain(stages);
        if (chain.isEmpty()) {
            return finding(
                    "DOCKER-005", "Startup Command", FindingStatus.NOT_CHECKED, FindingSeverity.LOW,
                    "The startup command could not be evaluated because no FROM instruction was found.",
                    "Add a FROM instruction and an explicit CMD or ENTRYPOINT when appropriate.",
                    filePath, null, null
            );
        }

        Instruction startupInstruction = findLastInstruction(chain, "CMD", "ENTRYPOINT");
        if (startupInstruction != null && startupInstruction.arguments().isBlank()) {
            return finding(
                    "DOCKER-005", "Startup Command", FindingStatus.WARNING, FindingSeverity.LOW,
                    "A CMD or ENTRYPOINT instruction was found without arguments.",
                    "Provide an explicit startup command or remove the empty instruction.",
                    filePath, startupInstruction, null,
                    "An empty startup instruction is not treated as a valid explicit command."
            );
        }
        if (startupInstruction != null) {
            return finding(
                    "DOCKER-005", "Startup Command", FindingStatus.PASS, FindingSeverity.INFO,
                    "An explicit CMD or ENTRYPOINT was found in the final stage chain.",
                    "No action required for this rule.",
                    filePath, startupInstruction, null
            );
        }

        return finding(
                "DOCKER-005", "Startup Command", FindingStatus.WARNING, FindingSeverity.LOW,
                "No explicit CMD or ENTRYPOINT was found in the final stage chain.",
                "Define a startup command when the base image does not provide a suitable one.",
                filePath, null, chain.get(0).from().lineNumber(),
                "A missing explicit command does not prove that the container cannot start; the base image may define CMD or ENTRYPOINT."
        );
    }

    private Finding checkCopyAndAdd(List<Instruction> instructions, boolean dockerIgnoreAvailable, String filePath) {
        Instruction remoteAdd = null;
        Instruction archiveAdd = null;
        Instruction anyAdd = null;
        Instruction broadCopy = null;
        Instruction anyCopy = null;

        for (Instruction instruction : instructions) {
            String arguments = instruction.arguments();
            if ("ADD".equals(instruction.keyword())) {
                anyAdd = firstNonNull(anyAdd, instruction);
                if (REMOTE_SOURCE.matcher(arguments).find()) {
                    remoteAdd = firstNonNull(remoteAdd, instruction);
                }
                if (ARCHIVE_SOURCE.matcher(arguments.trim()).find()) {
                    archiveAdd = firstNonNull(archiveAdd, instruction);
                }
            } else if ("COPY".equals(instruction.keyword())) {
                anyCopy = firstNonNull(anyCopy, instruction);
                if (isBroadCopy(arguments)) {
                    broadCopy = firstNonNull(broadCopy, instruction);
                }
            }
        }

        if (remoteAdd != null) {
            return finding(
                    "DOCKER-006", "COPY and ADD Usage", FindingStatus.WARNING, FindingSeverity.MEDIUM,
                    "ADD with a remote source was found.",
                    "Download build inputs explicitly only when required, or use COPY for local files.",
                    filePath, remoteAdd, null,
                    "Remote source values are never included in evidence.",
                    "Remote fetches were not performed."
            );
        }

        if (broadCopy != null && !dockerIgnoreAvailable) {
            return finding(
                    "DOCKER-006", "COPY and ADD Usage", FindingStatus.WARNING, FindingSeverity.LOW,
                    "A broad build-context copy was found and no .dockerignore file was supplied.",
                    "Consider adding a .dockerignore file or copying only the required paths.",
                    filePath, broadCopy, null,
                    "Build-context exclusions could not be verified because .dockerignore was not available.",
                    "COPY . . is not treated as insecure by itself."
            );
        }

        if (archiveAdd != null || anyAdd != null) {
            return finding(
                    "DOCKER-006", "COPY and ADD Usage", FindingStatus.WARNING, FindingSeverity.LOW,
                    "ADD was found and may fetch remote sources or extract archives automatically.",
                    "Prefer COPY unless ADD behavior is required.",
                    filePath, firstNonNull(archiveAdd, anyAdd), null,
                    "Source and destination values are never included in evidence."
            );
        }

        if (broadCopy != null) {
            return finding(
                    "DOCKER-006", "COPY and ADD Usage", FindingStatus.PASS, FindingSeverity.INFO,
                    "A broad build-context copy was found and a .dockerignore file was supplied.",
                    "Review .dockerignore rules as part of a future dedicated exclusion check.",
                    filePath, broadCopy, null,
                    "The presence of .dockerignore was detected, but its rules were not evaluated."
            );
        }

        if (anyCopy != null) {
            return finding(
                    "DOCKER-006", "COPY and ADD Usage", FindingStatus.PASS, FindingSeverity.INFO,
                    "COPY is used for file transfer and no risky ADD pattern was found.",
                    "No action required for this rule.",
                    filePath, anyCopy, null
            );
        }

        return finding(
                "DOCKER-006", "COPY and ADD Usage", FindingStatus.PASS, FindingSeverity.INFO,
                "No COPY or ADD instruction was found to review.",
                "No action required for this rule.",
                filePath, null, null
        );
    }

    private Finding checkDangerousPermissions(List<Instruction> instructions, String filePath) {
        Instruction match = null;
        for (Instruction instruction : instructions) {
            if (DANGEROUS_PERMISSION.matcher(instruction.arguments()).find()) {
                match = firstNonNull(match, instruction);
            }
        }

        if (match != null) {
            return finding(
                    "DOCKER-007", "Dangerous Permissions", FindingStatus.WARNING, FindingSeverity.HIGH,
                    "An explicit broad permission pattern was found.",
                    "Use the narrowest permissions that allow the application to run.",
                    filePath, match, null,
                    "Only explicit chmod and COPY --chmod patterns are detected; arbitrary shell scripts are not interpreted.",
                    "A broad permission setting is not claimed to be exploitable in every context."
            );
        }

        return finding(
                "DOCKER-007", "Dangerous Permissions", FindingStatus.PASS, FindingSeverity.INFO,
                "No explicit broad permission pattern was detected.",
                "No action required for this rule.",
                filePath, null, null,
                "Arbitrary shell scripts and external scripts are not interpreted."
        );
    }

    private Finding checkHealthcheck(List<Stage> stages, String filePath) {
        List<Stage> chain = stageChain(stages);
        if (chain.isEmpty()) {
            return finding(
                    "DOCKER-008", "Health Check", FindingStatus.NOT_CHECKED, FindingSeverity.LOW,
                    "The health check could not be evaluated because no FROM instruction was found.",
                    "Add a FROM instruction and define a HEALTHCHECK where appropriate.",
                    filePath, null, null
            );
        }

        Instruction healthcheck = findLastInstruction(chain, "HEALTHCHECK");
        if (healthcheck == null) {
            return finding(
                    "DOCKER-008", "Health Check", FindingStatus.WARNING, FindingSeverity.LOW,
                    "No explicit HEALTHCHECK was found in the final stage chain.",
                    "Define a HEALTHCHECK CMD if the deployment platform should evaluate container health.",
                    filePath, null, chain.get(0).from().lineNumber(),
                    "An effective runtime health check may be inherited from the base image and was not verified."
            );
        }

        String arguments = healthcheck.arguments().trim();
        if (arguments.isEmpty()) {
            return finding(
                    "DOCKER-008", "Health Check", FindingStatus.WARNING, FindingSeverity.LOW,
                    "A HEALTHCHECK instruction was found without arguments.",
                    "Use HEALTHCHECK CMD ... or HEALTHCHECK NONE with valid arguments.",
                    filePath, healthcheck, null,
                    "An empty HEALTHCHECK is not treated as an effective health check."
            );
        }
        if ("NONE".equalsIgnoreCase(arguments)) {
            return finding(
                    "DOCKER-008", "Health Check", FindingStatus.WARNING, FindingSeverity.MEDIUM,
                    "The final stage chain explicitly disables health checking with HEALTHCHECK NONE.",
                    "Enable a HEALTHCHECK CMD unless disabling health checks is intentional.",
                    filePath, healthcheck, null,
                    "Platform-level health probes are not evaluated."
            );
        }

        String[] tokens = arguments.isEmpty() ? new String[0] : arguments.split("\\s+");
        int commandIndex = 0;
        while (commandIndex < tokens.length && tokens[commandIndex].startsWith("--")) {
            commandIndex++;
        }

        if (commandIndex < tokens.length && "CMD".equalsIgnoreCase(tokens[commandIndex])) {
            return finding(
                    "DOCKER-008", "Health Check", FindingStatus.PASS, FindingSeverity.INFO,
                    "An explicit HEALTHCHECK CMD was found in the final stage chain.",
                    "No action required for this rule.",
                    filePath, healthcheck, null,
                    "An explicitly defined health check is not proof of an effective runtime health check."
            );
        }

        return finding(
                "DOCKER-008", "Health Check", FindingStatus.WARNING, FindingSeverity.LOW,
                "The HEALTHCHECK instruction has an unrecognized form.",
                "Use HEALTHCHECK CMD ... or HEALTHCHECK NONE.",
                filePath, healthcheck, null,
                "Health command arguments are not returned in evidence."
        );
    }

    private Finding checkUnsupportedSyntax(List<Instruction> instructions, String filePath) {
        for (Instruction instruction : instructions) {
            if (!KNOWN_KEYWORDS.contains(instruction.keyword())) {
                return finding(
                        "DOCKER.SYNTAX",
                        "Unsupported Syntax",
                        FindingStatus.NOT_CHECKED,
                        FindingSeverity.LOW,
                        "An instruction keyword outside the supported static subset was found.",
                        "Review this instruction manually because the analyzer does not interpret it.",
                        filePath, instruction, null,
                        "Unsupported Dockerfile syntax is reported rather than silently treated as valid."
                );
            }
        }
        return null;
    }

    private Finding finding(String ruleId, String name, String status, String severity, String explanation,
                            String recommendation, String filePath, Instruction instruction, Integer anchorLine,
                            String... extraLimitations) {
        Integer lineNumber = instruction != null ? Integer.valueOf(instruction.lineNumber()) : anchorLine;
        String evidence = instruction == null ? null : safeEvidence(instruction);

        List<String> limitations = new ArrayList<>();
        limitations.add(BASE_LIMITATION);
        if (extraLimitations != null) {
            for (String limitation : extraLimitations) {
                if (limitation != null && !limitations.contains(limitation)) {
                    limitations.add(limitation);
                }
            }
        }

        return new Finding(
                ruleId,
                CATEGORY,
                name,
                status,
                severity,
                explanation,
                recommendation,
                recommendation,
                filePath,
                lineNumber,
                evidence,
                limitations
        );
    }

    private String safeEvidence(Instruction instruction) {
        return instruction.keyword() + " [line " + instruction.lineNumber() + "]";
    }

    private List<Stage> parseStages(List<Instruction> instructions) {
        List<Stage> stages = new ArrayList<>();
        Stage current = null;

        for (Instruction instruction : instructions) {
            if ("FROM".equals(instruction.keyword())) {
                if (current != null) {
                    stages.add(current);
                }
                BaseReference base = parseFrom(instruction.arguments());
                current = new Stage(instruction, base.alias(), base, new ArrayList<>());
            } else if (current != null) {
                current.instructions().add(instruction);
            }
        }

        if (current != null) {
            stages.add(current);
        }
        return stages;
    }

    private BaseReference parseFrom(String arguments) {
        String[] tokens = arguments.trim().isEmpty() ? new String[0] : arguments.trim().split("\\s+");
        int index = 0;
        while (index < tokens.length && tokens[index].startsWith("--")) {
            index++;
        }

        String image = index < tokens.length ? tokens[index++] : null;
        if (image == null || "AS".equalsIgnoreCase(image)) {
            return new BaseReference(null, null, false, true);
        }

        String alias = null;
        boolean malformedAlias = false;
        if (index < tokens.length && "AS".equalsIgnoreCase(tokens[index])) {
            if (index + 1 < tokens.length) {
                alias = tokens[index + 1];
            } else {
                malformedAlias = true;
            }
        }

        return new BaseReference(image, alias, image.contains("${"), malformedAlias);
    }

    private List<Stage> stageChain(List<Stage> stages) {
        List<Stage> chain = new ArrayList<>();
        if (stages.isEmpty()) {
            return chain;
        }

        int currentIndex = stages.size() - 1;
        while (currentIndex >= 0) {
            Stage current = stages.get(currentIndex);
            chain.add(current);
            String base = current.base().image();
            if (base == null) {
                break;
            }

            int parentIndex = -1;
            for (int index = currentIndex - 1; index >= 0; index--) {
                Stage candidate = stages.get(index);
                if (candidate.alias() != null && candidate.alias().equalsIgnoreCase(base)) {
                    parentIndex = index;
                    break;
                }
            }
            if (parentIndex < 0) {
                break;
            }
            currentIndex = parentIndex;
        }
        return chain;
    }

    private Instruction findLastInstruction(List<Stage> chain, String... keywords) {
        Set<String> wanted = Set.of(keywords);
        for (Stage stage : chain) {
            for (int i = stage.instructions().size() - 1; i >= 0; i--) {
                Instruction instruction = stage.instructions().get(i);
                if (wanted.contains(instruction.keyword())) {
                    return instruction;
                }
            }
        }
        return null;
    }

    private String extractTag(String image) {
        String imageWithoutDigest = image;
        int digestIndex = imageWithoutDigest.indexOf('@');
        if (digestIndex >= 0) {
            imageWithoutDigest = imageWithoutDigest.substring(0, digestIndex);
        }

        int lastSlashIndex = imageWithoutDigest.lastIndexOf('/');
        String lastSegment = imageWithoutDigest.substring(lastSlashIndex + 1);
        int tagSeparatorIndex = lastSegment.lastIndexOf(':');

        if (tagSeparatorIndex >= 0 && tagSeparatorIndex < lastSegment.length() - 1) {
            return lastSegment.substring(tagSeparatorIndex + 1);
        }
        return null;
    }

    private String extractFirstToken(String arguments) {
        String trimmed = arguments == null ? "" : arguments.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.split("\\s+")[0];
    }

    private boolean isBroadCopy(String arguments) {
        String[] tokens = arguments == null || arguments.trim().isEmpty()
                ? new String[0] : arguments.trim().split("\\s+");
        if (tokens.length < 2) {
            return false;
        }
        String source = tokens[0];
        String destination = tokens[1];
        boolean broadSource = ".".equals(source) || "./".equals(source);
        boolean broadDestination = ".".equals(destination) || "./".equals(destination) || destination.startsWith("/");
        return broadSource && broadDestination;
    }

    private Instruction firstNonNull(Instruction current, Instruction candidate) {
        return current == null ? candidate : current;
    }

    private List<Instruction> parseInstructions(String content) {
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        List<Instruction> instructions = new ArrayList<>();

        int lineIndex = 0;
        while (lineIndex < lines.length) {
            int physicalLineNumber = lineIndex + 1;
            String trimmedLine = lines[lineIndex++].trim();
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                continue;
            }

            StringBuilder logicalLine = new StringBuilder(trimmedLine);
            while (hasContinuation(logicalLine)) {
                removeContinuation(logicalLine);
                if (lineIndex >= lines.length) {
                    break;
                }

                String nextPart = lines[lineIndex++].trim();
                if (!nextPart.isEmpty()) {
                    if (logicalLine.length() > 0) {
                        logicalLine.append(' ');
                    }
                    logicalLine.append(nextPart);
                }
            }

            addInstruction(logicalLine.toString(), physicalLineNumber, instructions);
        }

        return instructions;
    }

    private boolean hasContinuation(CharSequence line) {
        int index = line.length() - 1;
        while (index >= 0 && Character.isWhitespace(line.charAt(index))) {
            index--;
        }
        return index >= 0 && line.charAt(index) == '\\';
    }

    private void removeContinuation(StringBuilder line) {
        int index = line.length() - 1;
        while (index >= 0 && Character.isWhitespace(line.charAt(index))) {
            index--;
        }
        if (index >= 0 && line.charAt(index) == '\\') {
            line.setLength(index);
        }
    }

    private void addInstruction(String logicalLine, int lineNumber, List<Instruction> instructions) {
        String trimmed = logicalLine.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        int splitIndex = 0;
        while (splitIndex < trimmed.length() && !Character.isWhitespace(trimmed.charAt(splitIndex))) {
            splitIndex++;
        }

        String keyword = trimmed.substring(0, splitIndex).toUpperCase(Locale.ROOT);
        String arguments = splitIndex < trimmed.length() ? trimmed.substring(splitIndex + 1).trim() : "";
        instructions.add(new Instruction(keyword, arguments, lineNumber));
    }

    private record Instruction(String keyword, String arguments, int lineNumber) {
    }

    private record BaseReference(String image, String alias, boolean variable, boolean malformed) {
    }

    private record Stage(Instruction from, String alias, BaseReference base, List<Instruction> instructions) {
    }
}
