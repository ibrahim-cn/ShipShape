package com.shipshape.backend.service;

import com.shipshape.backend.dto.Finding;
import com.shipshape.backend.dto.FindingSeverity;
import com.shipshape.backend.dto.FindingStatus;
import com.shipshape.backend.dto.FileAnalysisStatus;
import com.shipshape.backend.dto.ModuleSummary;
import com.shipshape.backend.dto.ProjectAnalysisResponse;
import com.shipshape.backend.dto.ProjectAnalyzerFileResult;
import com.shipshape.backend.dto.ProjectContext;
import com.shipshape.backend.dto.ProjectFile;
import com.shipshape.backend.dto.ProjectFileSummary;
import com.shipshape.backend.dto.ProjectFileType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ProjectAnalysisService {
    private static final List<String> ALL_MODULES = List.of(
            "DOCKERFILE",
            "COMPOSE",
            "SECRETS",
            "ENVIRONMENT",
            "IGNORE",
            "SPRING_CONFIG",
            "DEPENDENCIES",
            "HEALTH_RELIABILITY",
            "CI_CD",
            "VULNERABILITIES"
    );

    private static final List<String> LIMITATIONS = List.of(
            "Servlet multipart may spool uploaded parts to temporary storage; the application reads bytes only for the request and does not persist uploaded files.",
            "Only Dockerfile and Docker Compose text rules are currently implemented in the shared project pipeline.",
            "ZIP input and GitHub repository input are not implemented.",
            "Uploaded files are not executed, built, or sent to external services."
    );

    private final ProjectInputService projectInputService;
    private final List<ProjectAnalyzer> analyzers;

    public ProjectAnalysisService(ProjectInputService projectInputService, List<ProjectAnalyzer> analyzers) {
        this.projectInputService = projectInputService;
        this.analyzers = analyzers == null ? List.of() : List.copyOf(analyzers);
    }

    public ProjectAnalysisResponse analyzeFiles(List<MultipartFile> files, String projectName) {
        ProjectContext context = projectInputService.buildContext(files, projectName);

        List<Finding> findings = new ArrayList<>();
        Map<String, ModuleResult> moduleResults = new LinkedHashMap<>();
        Set<String> implementedModules = new LinkedHashSet<>();
        Map<String, FileAnalysisStatus> fileStatuses = new LinkedHashMap<>();

        for (ProjectAnalyzer analyzer : analyzers) {
            String module = analyzer.module();
            implementedModules.add(module);

            List<Finding> analyzerFindings;
            try {
                analyzerFindings = analyzer.analyze(context);
            } catch (RuntimeException ex) {
                analyzerFindings = List.of(analyzerErrorFinding(module));
            }
            if (analyzerFindings == null) {
                analyzerFindings = List.of();
            }

            findings.addAll(analyzerFindings);
            moduleResults.put(module, new ModuleResult(analyzerFindings));
            mergeFileStatuses(fileStatuses, analyzer.analyzedFiles(context, analyzerFindings));
        }

        for (String module : ALL_MODULES) {
            if (!implementedModules.contains(module)) {
                Finding notCheckedFinding = notCheckedModuleFinding(module);
                findings.add(notCheckedFinding);
                moduleResults.put(module, new ModuleResult(List.of(notCheckedFinding)));
            }
        }

        for (ProjectFile file : context.files()) {
            if (!fileStatuses.containsKey(file.path())) {
                fileStatuses.put(file.path(), defaultStatus(file));
            }
        }

        findings.addAll(inputFindings(context, fileStatuses));

        List<ModuleSummary> modules = moduleResults.entrySet().stream()
                .map(entry -> new ModuleSummary(
                        entry.getKey(),
                        FindingStatus.aggregate(entry.getValue().findings()),
                        entry.getValue().findings().size()
                ))
                .toList();

        List<String> implementedModuleList = List.copyOf(implementedModules);
        List<String> incompleteModules = moduleResults.entrySet().stream()
                .filter(entry -> entry.getValue().findings().stream().anyMatch(this::isUnresolvedFinding))
                .map(Map.Entry::getKey)
                .toList();
        List<String> notCheckedModules = moduleResults.entrySet().stream()
                .filter(entry -> FindingStatus.NOT_CHECKED.equals(
                        FindingStatus.aggregate(entry.getValue().findings())))
                .map(Map.Entry::getKey)
                .toList();

        boolean coverageComplete = moduleResults.keySet().containsAll(ALL_MODULES)
                && incompleteModules.isEmpty();

        List<ProjectFileSummary> inventory = context.files().stream()
                .map(file -> {
                    FileAnalysisStatus status = fileStatuses.getOrDefault(file.path(), defaultStatus(file));
                    return new ProjectFileSummary(
                            file.path(),
                            file.type(),
                            file.sizeBytes(),
                            file.textContent(),
                            status == FileAnalysisStatus.ANALYZED,
                            status
                    );
                })
                .toList();

        return new ProjectAnalysisResponse(
                context.source(),
                context.projectName(),
                FindingStatus.aggregate(findings),
                coverageComplete,
                implementedModuleList,
                incompleteModules,
                notCheckedModules,
                findings,
                modules,
                inventory,
                LIMITATIONS
        );
    }

    private FileAnalysisStatus defaultStatus(ProjectFile file) {
        if (file.type() == ProjectFileType.UNSUPPORTED) {
            return FileAnalysisStatus.UNSUPPORTED;
        }
        return FileAnalysisStatus.NOT_ANALYZED;
    }

    private void mergeFileStatuses(Map<String, FileAnalysisStatus> statuses,
                                   List<ProjectAnalyzerFileResult> results) {
        if (results == null) {
            return;
        }
        for (ProjectAnalyzerFileResult result : results) {
            if (result == null || result.path() == null || result.status() == null) {
                continue;
            }
            FileAnalysisStatus current = statuses.get(result.path());
            if (current == null || statusRank(result.status()) > statusRank(current)) {
                statuses.put(result.path(), result.status());
            }
        }
    }

    private int statusRank(FileAnalysisStatus status) {
        return switch (status) {
            case ANALYZED -> 1;
            case NOT_ANALYZED -> 2;
            case UNSUPPORTED -> 3;
            case PARSE_ERROR -> 4;
        };
    }

    private boolean isUnresolvedFinding(Finding finding) {
        String status = finding.getStatus();
        return FindingStatus.ERROR.equals(status)
                || FindingStatus.NOT_CHECKED.equals(status);
    }

    private Finding notCheckedModuleFinding(String module) {
        String message = "The " + module + " analysis category is not implemented in this task.";
        return new Finding(
                "COVERAGE.NOT_CHECKED",
                module,
                module + " Analysis",
                FindingStatus.NOT_CHECKED,
                FindingSeverity.LOW,
                message,
                "Do not treat this category as production-ready.",
                "Do not treat this category as production-ready.",
                null,
                null,
                null,
                List.of("Incomplete analysis coverage is reported explicitly instead of assuming PASS.")
        );
    }

    private Finding analyzerErrorFinding(String module) {
        return new Finding(
                "ANALYZER.ERROR",
                module,
                module + " Analyzer",
                FindingStatus.ERROR,
                FindingSeverity.HIGH,
                "The analyzer failed before producing complete results.",
                "Review the submitted input and retry with a smaller, text-based project.",
                "Review the submitted input and retry with a smaller, text-based project.",
                null,
                null,
                null,
                List.of("Internal exception details are not exposed in API responses.")
        );
    }

    private List<Finding> inputFindings(ProjectContext context, Map<String, FileAnalysisStatus> fileStatuses) {
        List<Finding> findings = new ArrayList<>();

        for (ProjectFile file : context.files()) {
            if (file.type() == ProjectFileType.UNSUPPORTED) {
                findings.add(new Finding(
                        "INPUT.UNSUPPORTED_FILE",
                        "INPUT",
                        "Unsupported File",
                        FindingStatus.NOT_CHECKED,
                        FindingSeverity.LOW,
                        "The uploaded file could not be decoded as text or is binary, so it was not analyzed.",
                        "Upload text-based project files for static analysis.",
                        "Upload text-based project files for static analysis.",
                        file.path(),
                        null,
                        null,
                        List.of("Binary files are never executed or interpreted as source text.")
                ));
            }
        }

        boolean hasAnalyzedFile = fileStatuses.values().stream()
                .anyMatch(status -> status == FileAnalysisStatus.ANALYZED);
        if (!hasAnalyzedFile) {
            findings.add(new Finding(
                    "INPUT.NO_ANALYZABLE_FILE",
                    "INPUT",
                    "Analyzable Files",
                    FindingStatus.NOT_CHECKED,
                    FindingSeverity.LOW,
                    "No uploaded file matched a currently implemented analyzer.",
                    "Upload a Dockerfile or Compose file to use the implemented static rules.",
                    "Upload a Dockerfile or Compose file to use the implemented static rules.",
                    null,
                    null,
                    null,
                    List.of("Missing input is reported as not checked rather than PASS or NOT_APPLICABLE.")
            ));
        }

        return findings;
    }

    private record ModuleResult(List<Finding> findings) {
    }
}
