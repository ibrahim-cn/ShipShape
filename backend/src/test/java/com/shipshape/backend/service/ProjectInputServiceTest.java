package com.shipshape.backend.service;

import com.shipshape.backend.dto.ProjectContext;
import com.shipshape.backend.dto.ProjectFileType;
import com.shipshape.backend.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectInputServiceTest {

    private final ProjectInputService service = new ProjectInputService();

    @Test
    void acceptsAndClassifiesProjectFiles() {
        ProjectContext context = service.buildContext(List.of(
                file("Dockerfile", "FROM node:20\n"),
                file("docker-compose.yml", "services: {}\n"),
                file("package.json", "{}\n"),
                file(".github/workflows/ci.yml", "name: ci\n"),
                file("application.properties", "server.port=8080\n"),
                file("src/Main.java", "class Main {}\n"),
                file("notes.txt", "hello\n")
        ), " demo ");

        assertEquals("MULTIPART_FILES", context.source());
        assertEquals("demo", context.projectName());
        assertEquals(7, context.files().size());
        assertEquals(ProjectFileType.DOCKERFILE, context.files().get(0).type());
        assertEquals(ProjectFileType.COMPOSE, context.files().get(1).type());
        assertEquals(ProjectFileType.PACKAGE_MANIFEST, context.files().get(2).type());
        assertEquals(ProjectFileType.CI_CONFIG, context.files().get(3).type());
        assertEquals(ProjectFileType.CONFIG, context.files().get(4).type());
        assertEquals(ProjectFileType.SOURCE, context.files().get(5).type());
        assertEquals(ProjectFileType.OTHER, context.files().get(6).type());
    }

    @Test
    void rejectsNoFiles() {
        assertThrows(BadRequestException.class, () -> service.buildContext(List.of(), "demo"));
    }

    @Test
    void rejectsDuplicatePaths() {
        assertThrows(BadRequestException.class, () -> service.buildContext(List.of(
                file("Dockerfile", "FROM node:20\n"),
                file("Dockerfile", "FROM node:20\n")
        ), "demo"));
    }

    @Test
    void rejectsUnsafePaths() {
        assertThrows(BadRequestException.class,
                () -> service.buildContext(List.of(file("../Dockerfile", "FROM node:20\n")), "demo"));
        assertThrows(BadRequestException.class,
                () -> service.buildContext(List.of(file("/tmp/Dockerfile", "FROM node:20\n")), "demo"));
        assertThrows(BadRequestException.class,
                () -> service.buildContext(List.of(file("C:\\project\\Dockerfile", "FROM node:20\n")), "demo"));
    }

    @Test
    void rejectsOversizedFile() {
        MockMultipartFile oversized = new MockMultipartFile(
                "files",
                "Dockerfile",
                MediaType.TEXT_PLAIN_VALUE,
                new byte[ProjectInputService.MAX_FILE_BYTES + 1]
        );

        assertThrows(BadRequestException.class, () -> service.buildContext(List.of(oversized), "demo"));
    }

    @Test
    void rejectsTooManyFiles() {
        List<MultipartFile> files = new ArrayList<>();
        for (int i = 0; i <= ProjectInputService.MAX_FILE_COUNT; i++) {
            files.add(file("file-" + i + ".txt", "content\n"));
        }

        assertThrows(BadRequestException.class, () -> service.buildContext(files, "demo"));
    }

    @Test
    void classifiesBinaryContentAsUnsupported() {
        MockMultipartFile binary = new MockMultipartFile(
                "files",
                "image.bin",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                new byte[]{0, 1, 2, 3}
        );

        ProjectContext context = service.buildContext(List.of(binary), "demo");

        assertEquals(ProjectFileType.UNSUPPORTED, context.files().get(0).type());
    }

    @Test
    void rejectsNullFileEntry() {
        List<MultipartFile> files = new ArrayList<>();
        files.add(null);

        assertThrows(BadRequestException.class, () -> service.buildContext(files, "demo"));
    }

    @Test
    void rejectsMissingFileName() {
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "   ",
                MediaType.TEXT_PLAIN_VALUE,
                "FROM node:20\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        assertThrows(BadRequestException.class, () -> service.buildContext(List.of(file), "demo"));
    }

    @Test
    void rejectsTotalBytesOverLimit() {
        MockMultipartFile oversized = new MockMultipartFile(
                "files",
                "large.txt",
                MediaType.TEXT_PLAIN_VALUE,
                new byte[(int) ProjectInputService.MAX_TOTAL_BYTES + 1]
        );

        assertThrows(BadRequestException.class, () -> service.buildContext(List.of(oversized), "demo"));
    }

    @Test
    void rejectsInvalidProjectName() {
        assertThrows(BadRequestException.class, () -> service.buildContext(
                List.of(file("Dockerfile", "FROM node:20\n")), "bad\nname"));
        assertThrows(BadRequestException.class, () -> service.buildContext(
                List.of(file("Dockerfile", "FROM node:20\n")), "x".repeat(121)));
    }

    @Test
    void acceptsNestedPathsAndUsesDefaultProjectName() {
        ProjectContext context = service.buildContext(List.of(
                file("src/app/Dockerfile", "FROM node:20\n")
        ), null);

        assertEquals("uploaded-project", context.projectName());
        assertEquals("src/app/Dockerfile", context.files().get(0).path());
        assertEquals(ProjectFileType.DOCKERFILE, context.files().get(0).type());
    }

    @Test
    void classifiesInvalidUtf8AsUnsupported() {
        MockMultipartFile binary = new MockMultipartFile(
                "files",
                "Dockerfile",
                MediaType.TEXT_PLAIN_VALUE,
                new byte[]{(byte) 0xC3, (byte) 0x28}
        );

        ProjectContext context = service.buildContext(List.of(binary), "demo");

        assertEquals(ProjectFileType.UNSUPPORTED, context.files().get(0).type());
        assertFalse(context.files().get(0).textContent());
    }

    private MockMultipartFile file(String path, String content) {
        return new MockMultipartFile(
                "files",
                path,
                MediaType.TEXT_PLAIN_VALUE,
                content.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }
}
