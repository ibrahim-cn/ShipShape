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
public class ComposeProjectAnalyzer implements ProjectAnalyzer {
    private static final String CATEGORY = "COMPOSE";
    private static final List<String> LIMITATION = List.of(
            "Compose files are analyzed as YAML data and are never executed with Docker Compose."
    );

    private final ComposeAnalysisService composeAnalysisService;

    public ComposeProjectAnalyzer(ComposeAnalysisService composeAnalysisService) {
        this.composeAnalysisService = composeAnalysisService;
    }

    @Override
    public String module() {
        return CATEGORY;
    }

    @Override
    public List<Finding> analyze(ProjectContext context) {
        List<ProjectFile> composeFiles = composeFiles(context);
        if (composeFiles.isEmpty()) {
            return List.of(new Finding(
                    "COMPOSE.NOT_CHECKED",
                    CATEGORY,
                    "Compose Analysis",
                    FindingStatus.NOT_CHECKED,
                    FindingSeverity.LOW,
                    "No recognized Compose file was found in the uploaded project files.",
                    "Upload a compose.yaml, compose.yml, docker-compose.yaml, or docker-compose.yml file.",
                    "Upload a compose.yaml, compose.yml, docker-compose.yaml, or docker-compose.yml file.",
                    null,
                    null,
                    null,
                    LIMITATION
            ));
        }

        List<Finding> findings = new ArrayList<>();
        for (ProjectFile file : composeFiles) {
            try {
                findings.addAll(composeAnalysisService
                        .analyze(file.path(), file.content())
                        .getFindings());
            } catch (RuntimeException ex) {
                findings.add(errorFinding(file.path()));
            }
        }
        return findings;
    }

    @Override
    public List<ProjectAnalyzerFileResult> analyzedFiles(ProjectContext context, List<Finding> findings) {
        return composeFiles(context).stream()
                .map(file -> new ProjectAnalyzerFileResult(
                        file.path(),
                        hasParseError(findings, file.path())
                                ? FileAnalysisStatus.PARSE_ERROR
                                : FileAnalysisStatus.ANALYZED
                ))
                .toList();
    }

    private List<ProjectFile> composeFiles(ProjectContext context) {
        return context.files().stream()
                .filter(file -> file.type() == ProjectFileType.COMPOSE && file.textContent())
                .toList();
    }

    private boolean hasParseError(List<Finding> findings, String filePath) {
        if (findings == null) {
            return false;
        }
        return findings.stream().anyMatch(finding ->
                ("COMPOSE.PARSE_ERROR".equals(finding.getRuleId())
                        || "COMPOSE.ERROR".equals(finding.getRuleId()))
                        && filePath.equals(finding.getFilePath()));
    }

    private Finding errorFinding(String filePath) {
        return new Finding(
                "COMPOSE.ERROR",
                CATEGORY,
                "Compose Analysis",
                FindingStatus.ERROR,
                FindingSeverity.HIGH,
                "The Compose file could not be analyzed safely.",
                "Provide a smaller, valid UTF-8 Compose file and retry.",
                "Provide a smaller, valid UTF-8 Compose file and retry.",
                filePath,
                null,
                null,
                LIMITATION
        );
    }
}
