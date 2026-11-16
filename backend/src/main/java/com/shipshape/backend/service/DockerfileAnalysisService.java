package com.shipshape.backend.service;

import com.shipshape.backend.dto.DockerfileAnalysisResponse;
import com.shipshape.backend.dto.Finding;
import com.shipshape.backend.exception.BadRequestException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class DockerfileAnalysisService {

    private static final int MAX_CONTENT_LENGTH = 100_000;
    private static final String PASS = "PASS";
    private static final String WARNING = "WARNING";
    private static final String FAIL = "FAIL";

    public DockerfileAnalysisResponse analyze(String content) {
        validateContent(content);

        List<Instruction> instructions = parseInstructions(content);
        List<Finding> findings = new ArrayList<>();
        findings.add(checkFromInstructions(instructions));
        findings.add(checkBaseImageTag(instructions));
        findings.add(checkFinalStageUser(instructions));
        findings.add(checkFinalStageHealthcheck(instructions));
        findings.add(checkCopyAndAdd(instructions));

        return new DockerfileAnalysisResponse(computeOverallStatus(findings), findings);
    }

    private void validateContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new BadRequestException("Dockerfile content is required");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new BadRequestException("Dockerfile content is too large");
        }
    }

    private Finding checkFromInstructions(List<Instruction> instructions) {
        List<Instruction> fromInstructions = findInstructions(instructions, "FROM");

        if (fromInstructions.isEmpty()) {
            return new Finding(
                    "FROM Instruction",
                    FAIL,
                    "No FROM instruction was found. A Dockerfile must define a base image.",
                    "Add a FROM instruction with a base image."
            );
        }

        int invalidFromCount = 0;
        for (Instruction instruction : fromInstructions) {
            if (extractBaseImage(instruction.arguments()) == null) {
                invalidFromCount++;
            }
        }

        if (invalidFromCount > 0) {
            return new Finding(
                    "FROM Instruction",
                    FAIL,
                    "One or more FROM instructions do not name a base image.",
                    "Provide a base image for every FROM instruction and check its syntax."
            );
        }

        String explanation = fromInstructions.size() == 1
                ? "The Dockerfile contains a valid FROM instruction."
                : "The Dockerfile contains " + fromInstructions.size()
                        + " valid FROM instructions, so multi-stage builds are supported.";

        return new Finding(
                "FROM Instruction",
                PASS,
                explanation,
                "No action required."
        );
    }

    private Finding checkBaseImageTag(List<Instruction> instructions) {
        List<Instruction> fromInstructions = findInstructions(instructions, "FROM");
        boolean hasValidBaseImage = false;
        boolean usesExplicitLatest = false;

        for (Instruction instruction : fromInstructions) {
            String baseImage = extractBaseImage(instruction.arguments());
            if (baseImage == null) {
                continue;
            }

            hasValidBaseImage = true;
            String tag = extractTag(baseImage);
            if (tag != null && "latest".equals(tag.toLowerCase(Locale.ROOT))) {
                usesExplicitLatest = true;
            }
        }

        if (fromInstructions.isEmpty()) {
            return new Finding(
                    "Base Image Tag",
                    WARNING,
                    "The base image tag could not be evaluated because no FROM instruction was found.",
                    "Add a FROM instruction with a pinned base image tag."
            );
        }

        if (!hasValidBaseImage) {
            return new Finding(
                    "Base Image Tag",
                    WARNING,
                    "The base image tag could not be evaluated because no valid base image was found.",
                    "Provide a valid base image before selecting a tag."
            );
        }

        if (usesExplicitLatest) {
            return new Finding(
                    "Base Image Tag",
                    WARNING,
                    "At least one FROM instruction explicitly uses the :latest tag.",
                    "Pin a specific base image version instead of using :latest for reproducible builds."
            );
        }

        return new Finding(
                "Base Image Tag",
                PASS,
                "No explicit :latest tag was found. A missing tag is not assumed to mean :latest.",
                "No action required for this rule."
        );
    }

    private Finding checkFinalStageUser(List<Instruction> instructions) {
        int lastFromIndex = findLastInstructionIndex(instructions, "FROM");
        if (lastFromIndex < 0) {
            return new Finding(
                    "Final Stage User",
                    WARNING,
                    "The final-stage user could not be determined because no FROM instruction was found.",
                    "Add a FROM instruction and set a non-root USER in the final stage."
            );
        }

        Instruction lastUser = null;
        for (int i = lastFromIndex + 1; i < instructions.size(); i++) {
            Instruction instruction = instructions.get(i);
            if ("USER".equals(instruction.keyword())) {
                lastUser = instruction;
            }
        }

        if (lastUser == null) {
            return new Finding(
                    "Final Stage User",
                    WARNING,
                    "No USER instruction was found in the final stage. The container may run as root.",
                    "Add a non-root USER instruction to the final stage."
            );
        }

        String username = extractUsername(lastUser.arguments());
        if (username == null || username.isEmpty()) {
            return new Finding(
                    "Final Stage User",
                    WARNING,
                    "The final-stage USER instruction does not name a user.",
                    "Provide a non-root user, for example USER appuser."
            );
        }

        if ("root".equalsIgnoreCase(username) || "0".equals(username)) {
            return new Finding(
                    "Final Stage User",
                    WARNING,
                    "The final stage explicitly runs as root through USER root or USER 0.",
                    "Create and use a non-root user in the final stage."
            );
        }

        return new Finding(
                "Final Stage User",
                PASS,
                "The final stage sets a non-root user.",
                "No action required."
        );
    }

    private Finding checkFinalStageHealthcheck(List<Instruction> instructions) {
        int lastFromIndex = findLastInstructionIndex(instructions, "FROM");
        if (lastFromIndex < 0) {
            return new Finding(
                    "Health Check",
                    WARNING,
                    "The final-stage health check could not be determined because no FROM instruction was found.",
                    "Add a FROM instruction and define a HEALTHCHECK in the final stage."
            );
        }

        Instruction lastHealthcheck = null;
        for (int i = lastFromIndex + 1; i < instructions.size(); i++) {
            Instruction instruction = instructions.get(i);
            if ("HEALTHCHECK".equals(instruction.keyword())) {
                lastHealthcheck = instruction;
            }
        }

        if (lastHealthcheck == null) {
            return new Finding(
                    "Health Check",
                    WARNING,
                    "No HEALTHCHECK instruction was found in the final stage.",
                    "Add a HEALTHCHECK CMD instruction so container health can be monitored."
            );
        }

        String arguments = lastHealthcheck.arguments().trim();
        if ("NONE".equalsIgnoreCase(arguments)) {
            return new Finding(
                    "Health Check",
                    WARNING,
                    "The final stage disables health checking with HEALTHCHECK NONE.",
                    "Enable a HEALTHCHECK CMD instruction unless disabling health checks is intentional."
            );
        }

        String[] tokens = arguments.isEmpty() ? new String[0] : arguments.split("\\s+");
        int commandIndex = 0;
        while (commandIndex < tokens.length && tokens[commandIndex].startsWith("--")) {
            commandIndex++;
        }

        if (commandIndex < tokens.length && "CMD".equalsIgnoreCase(tokens[commandIndex])) {
            return new Finding(
                    "Health Check",
                    PASS,
                    "The final stage defines a HEALTHCHECK CMD instruction.",
                    "No action required."
            );
        }

        return new Finding(
                "Health Check",
                WARNING,
                "The final-stage HEALTHCHECK instruction has an unrecognized form.",
                "Use HEALTHCHECK CMD ... or HEALTHCHECK NONE."
        );
    }

    private Finding checkCopyAndAdd(List<Instruction> instructions) {
        int copyCount = 0;
        int addCount = 0;

        for (Instruction instruction : instructions) {
            if ("COPY".equals(instruction.keyword())) {
                copyCount++;
            } else if ("ADD".equals(instruction.keyword())) {
                addCount++;
            }
        }

        if (addCount > 0) {
            return new Finding(
                    "COPY and ADD Usage",
                    WARNING,
                    "The Dockerfile contains " + addCount + " ADD instruction"
                            + (addCount == 1 ? "" : "s") + ". ADD can fetch remote URLs and extract "
                            + "archives automatically, while COPY is more predictable.",
                    "Prefer COPY unless ADD's remote URL or automatic archive extraction behavior is required."
            );
        }

        if (copyCount > 0) {
            return new Finding(
                    "COPY and ADD Usage",
                    PASS,
                    "The Dockerfile uses COPY for file and directory transfers.",
                    "No action required."
            );
        }

        return new Finding(
                "COPY and ADD Usage",
                PASS,
                "No COPY or ADD instructions were found, so there is no file-transfer instruction to review.",
                "No action required for this rule."
        );
    }

    private List<Instruction> findInstructions(List<Instruction> instructions, String keyword) {
        List<Instruction> matches = new ArrayList<>();
        for (Instruction instruction : instructions) {
            if (keyword.equals(instruction.keyword())) {
                matches.add(instruction);
            }
        }
        return matches;
    }

    private int findLastInstructionIndex(List<Instruction> instructions, String keyword) {
        for (int i = instructions.size() - 1; i >= 0; i--) {
            if (keyword.equals(instructions.get(i).keyword())) {
                return i;
            }
        }
        return -1;
    }

    private String extractBaseImage(String arguments) {
        String[] tokens = arguments.trim().isEmpty() ? new String[0] : arguments.trim().split("\\s+");

        for (String token : tokens) {
            if (token.startsWith("--")) {
                continue;
            }
            if ("AS".equalsIgnoreCase(token) || token.isEmpty()) {
                return null;
            }
            return token;
        }

        return null;
    }

    private String extractTag(String baseImage) {
        String imageWithoutDigest = baseImage;
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

    private String extractUsername(String arguments) {
        String[] tokens = arguments.trim().isEmpty() ? new String[0] : arguments.trim().split("\\s+");
        if (tokens.length == 0) {
            return null;
        }

        String user = tokens[0];
        int groupSeparatorIndex = user.indexOf(':');
        if (groupSeparatorIndex >= 0) {
            user = user.substring(0, groupSeparatorIndex);
        }
        return user;
    }

    private List<Instruction> parseInstructions(String content) {
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        List<Instruction> instructions = new ArrayList<>();

        int lineIndex = 0;
        while (lineIndex < lines.length) {
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

            addInstruction(logicalLine.toString(), instructions);
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

    private void addInstruction(String logicalLine, List<Instruction> instructions) {
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
        instructions.add(new Instruction(keyword, arguments));
    }

    private String computeOverallStatus(List<Finding> findings) {
        boolean hasFailure = false;
        boolean hasWarning = false;

        for (Finding finding : findings) {
            if (FAIL.equalsIgnoreCase(finding.getStatus())) {
                hasFailure = true;
            } else if (WARNING.equalsIgnoreCase(finding.getStatus())) {
                hasWarning = true;
            }
        }

        if (hasFailure) {
            return FAIL;
        }
        if (hasWarning) {
            return WARNING;
        }
        return PASS;
    }

    private record Instruction(String keyword, String arguments) {
    }
}
