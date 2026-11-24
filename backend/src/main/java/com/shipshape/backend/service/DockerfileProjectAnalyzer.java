package com.shipshape.backend.service;

import com.shipshape.backend.dto.Finding;
import com.shipshape.backend.dto.FindingSeverity;
import com.shipshape.backend.dto.FindingStatus;
import com.shipshape.backend.dto.FileAnalysisStatus;
import com.shipshape.backend.dto.ProjectAnalyzerFileResult;
import com.shipshape.backend.dto.ProjectContext;
import com.shipshape.backend.dto.ProjectFile;
import com.shipshape.backend.dto.ProjectFileType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DockerfileProjectAnalyzer implements ProjectAnalyzer {
    private static final String CATEGORY = "DOCKERFILE";
    private static final List<String> LIMITATION = List.of(
            "Dockerfile files are analyzed as text and are never executed."
    );

    private final DockerfileAnalysisService dockerfileAnalysisService;

    public DockerfileProjectAnalyzer(DockerfileAnalysisService dockerfileAnalysisService) {
        this.dockerfileAnalysisService = dockerfileAnalysisService;
    }

    @Override
    public String module() {
        return CATEGORY;
    }

    @Override
    public List<Finding> analyze(ProjectContext context) {
        List<ProjectFile> dockerfiles = dockerfiles(context);
        if (dockerfiles.isEmpty()) {
            return List.of(new Finding(
                    "DOCKERFILE.NOT_CHECKED",
                    CATEGORY,
                    "Dockerfile Analysis",
                    FindingStatus.NOT_CHECKED,
                    FindingSeverity.LOW,
                    "No Dockerfile was found in the uploaded project files.",
                    "Upload a Dockerfile to run the Dockerfile rules.",
                    "Upload a Dockerfile to run the Dockerfile rules.",
                    null,
                    null,
                    null,
                    LIMITATION
            ));
        }

        List<Finding> findings = new ArrayList<>();
        for (ProjectFile file : dockerfiles) {
            try {
                findings.addAll(dockerfileAnalysisService.analyze(
                        file.path(),
                        file.content(),
                        hasEffectiveDockerignore(context, file)
                ).getFindings());
            } catch (RuntimeException ex) {
                findings.add(errorFinding(file.path()));
            }
        }
        return findings;
    }

    @Override
    public List<ProjectAnalyzerFileResult> analyzedFiles(ProjectContext context, List<Finding> findings) {
        return dockerfiles(context).stream()
                .map(file -> new ProjectAnalyzerFileResult(
                        file.path(),
                        hasParseError(findings, file.path())
                                ? FileAnalysisStatus.PARSE_ERROR
                                : FileAnalysisStatus.ANALYZED
                ))
                .toList();
    }

    private List<ProjectFile> dockerfiles(ProjectContext context) {
        return context.files().stream()
                .filter(file -> file.type() == ProjectFileType.DOCKERFILE && file.textContent())
                .toList();
    }

    private boolean hasEffectiveDockerignore(ProjectContext context, ProjectFile dockerfile) {
        int separator = dockerfile.path().lastIndexOf('/');
        String directory = separator >= 0 ? dockerfile.path().substring(0, separator + 1) : "";
        String expectedPath = directory + ".dockerignore";
        return context.files().stream()
                .anyMatch(file -> expectedPath.equals(file.path())
                        && file.textContent()
                        && !file.content().isBlank());
    }

    private boolean hasParseError(List<Finding> findings, String filePath) {
        if (findings == null) {
            return false;
        }
        return findings.stream().anyMatch(finding ->
                "DOCKERFILE.ERROR".equals(finding.getRuleId())
                        && filePath.equals(finding.getFilePath()));
    }

    private Finding errorFinding(String filePath) {
        return new Finding(
                "DOCKERFILE.ERROR",
                CATEGORY,
                "Dockerfile Analysis",
                FindingStatus.ERROR,
                FindingSeverity.HIGH,
                "The Dockerfile could not be analyzed safely.",
                "Provide a smaller, valid UTF-8 Dockerfile and retry.",
                "Provide a smaller, valid UTF-8 Dockerfile and retry.",
                filePath,
                null,
                null,
                LIMITATION
        );
    }
}
