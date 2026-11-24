package com.shipshape.backend.service;

import com.shipshape.backend.dto.ProjectContext;
import com.shipshape.backend.dto.ProjectFile;
import com.shipshape.backend.dto.ProjectFileType;
import com.shipshape.backend.exception.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ProjectInputService {
    public static final int MAX_FILE_COUNT = 200;
    public static final int MAX_FILE_BYTES = 512_000;
    public static final long MAX_TOTAL_BYTES = 5_000_000L;
    public static final int MAX_PROJECT_NAME_LENGTH = 120;

    private static final int MAX_PATH_LENGTH = 500;
    private static final int MAX_COMPONENT_LENGTH = 255;

    public ProjectContext buildContext(List<MultipartFile> files, String projectName) {
        if (files == null || files.isEmpty()) {
            throw new BadRequestException("At least one file is required");
        }
        if (files.size() > MAX_FILE_COUNT) {
            throw new BadRequestException("Too many uploaded files");
        }

        String safeProjectName = normalizeProjectName(projectName);
        Set<String> paths = new HashSet<>();
        List<ProjectFile> projectFiles = new ArrayList<>();
        long totalBytes = 0;

        for (MultipartFile file : files) {
            if (file == null) {
                throw new BadRequestException("Uploaded files must not be null");
            }

            totalBytes += Math.max(0L, file.getSize());
            if (totalBytes > MAX_TOTAL_BYTES) {
                throw new BadRequestException("Uploaded files are too large in total");
            }

            String path = normalizePath(file.getOriginalFilename());
            if (!paths.add(path)) {
                throw new BadRequestException("Duplicate uploaded file path");
            }

            byte[] bytes = readBytes(file);
            if (bytes.length > MAX_FILE_BYTES) {
                throw new BadRequestException("Uploaded file is too large");
            }

            DecodedContent decoded = decodeText(bytes);
            ProjectFileType type = classify(path, decoded.textContent());
            projectFiles.add(new ProjectFile(path, type, bytes.length, decoded.textContent(), decoded.content()));
        }

        return new ProjectContext("MULTIPART_FILES", safeProjectName, projectFiles);
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new BadRequestException("Unable to read uploaded file");
        }
    }

    private DecodedContent decodeText(byte[] bytes) {
        try {
            String content = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            if (content.indexOf('\0') >= 0) {
                return new DecodedContent(false, "");
            }
            return new DecodedContent(true, content);
        } catch (CharacterCodingException ex) {
            return new DecodedContent(false, "");
        }
    }

    private ProjectFileType classify(String path, boolean textContent) {
        if (!textContent) {
            return ProjectFileType.UNSUPPORTED;
        }

        String normalized = path.toLowerCase(Locale.ROOT);
        String fileName = normalized.contains("/")
                ? normalized.substring(normalized.lastIndexOf('/') + 1)
                : normalized;

        if ("dockerfile".equals(fileName) || fileName.startsWith("dockerfile.")
                || fileName.endsWith(".dockerfile")) {
            return ProjectFileType.DOCKERFILE;
        }
        if ("docker-compose.yml".equals(fileName) || "docker-compose.yaml".equals(fileName)
                || "compose.yml".equals(fileName) || "compose.yaml".equals(fileName)) {
            return ProjectFileType.COMPOSE;
        }
        if ("package.json".equals(fileName) || "package-lock.json".equals(fileName)
                || "pom.xml".equals(fileName) || "build.gradle".equals(fileName)
                || "build.gradle.kts".equals(fileName) || "requirements.txt".equals(fileName)
                || "go.mod".equals(fileName) || "cargo.toml".equals(fileName)) {
            return ProjectFileType.PACKAGE_MANIFEST;
        }
        if (normalized.contains(".github/workflows/") || "jenkinsfile".equals(fileName)
                || fileName.startsWith("azure-pipelines")) {
            return ProjectFileType.CI_CONFIG;
        }
        if (fileName.startsWith("application.") || fileName.equals(".env.example")
                || fileName.endsWith(".properties") || fileName.endsWith(".yaml")
                || fileName.endsWith(".yml")) {
            return ProjectFileType.CONFIG;
        }
        if (fileName.endsWith(".java") || fileName.endsWith(".js") || fileName.endsWith(".jsx")
                || fileName.endsWith(".ts") || fileName.endsWith(".tsx") || fileName.endsWith(".py")
                || fileName.endsWith(".go") || fileName.endsWith(".rb") || fileName.endsWith(".php")
                || fileName.endsWith(".cs") || fileName.endsWith(".kt")) {
            return ProjectFileType.SOURCE;
        }
        return ProjectFileType.OTHER;
    }

    private String normalizeProjectName(String projectName) {
        if (projectName == null || projectName.isBlank()) {
            return "uploaded-project";
        }

        String trimmed = projectName.trim();
        if (trimmed.length() > MAX_PROJECT_NAME_LENGTH) {
            throw new BadRequestException("Project name is too long");
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char character = trimmed.charAt(i);
            if (character < 32 || character == 127) {
                throw new BadRequestException("Project name contains invalid characters");
            }
        }
        return trimmed;
    }

    private String normalizePath(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BadRequestException("Uploaded file name is required");
        }

        String normalized = originalFilename.trim().replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }

        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) {
            throw new BadRequestException("Unsafe file path");
        }
        if (normalized.length() > MAX_PATH_LENGTH) {
            throw new BadRequestException("Uploaded file path is too long");
        }

        String[] parts = normalized.split("/", -1);
        List<String> safeParts = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank() || ".".equals(part) || "..".equals(part)) {
                throw new BadRequestException("Unsafe file path");
            }
            if (part.length() > MAX_COMPONENT_LENGTH) {
                throw new BadRequestException("Uploaded file path is too long");
            }
            for (int i = 0; i < part.length(); i++) {
                char character = part.charAt(i);
                if (character < 32 || character == 127) {
                    throw new BadRequestException("Uploaded file name contains invalid characters");
                }
            }
            safeParts.add(part);
        }

        return String.join("/", safeParts);
    }

    private record DecodedContent(boolean textContent, String content) {
    }
}
