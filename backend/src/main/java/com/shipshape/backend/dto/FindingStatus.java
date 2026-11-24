package com.shipshape.backend.dto;

import java.util.List;

public final class FindingStatus {
    public static final String PASS = "PASS";
    public static final String WARNING = "WARNING";
    public static final String FAIL = "FAIL";
    public static final String NOT_CHECKED = "NOT_CHECKED";
    public static final String NOT_APPLICABLE = "NOT_APPLICABLE";
    public static final String ERROR = "ERROR";

    private FindingStatus() {
    }

    public static String aggregate(List<Finding> findings) {
        if (findings == null || findings.isEmpty()) {
            return NOT_CHECKED;
        }

        boolean hasError = false;
        boolean hasFailure = false;
        boolean hasWarning = false;
        boolean hasNotChecked = false;
        boolean hasPass = false;
        boolean hasNotApplicable = false;

        for (Finding finding : findings) {
            String status = finding == null ? null : finding.getStatus();
            if (status == null) {
                continue;
            }
            if (ERROR.equalsIgnoreCase(status)) {
                hasError = true;
            } else if (FAIL.equalsIgnoreCase(status)) {
                hasFailure = true;
            } else if (WARNING.equalsIgnoreCase(status)) {
                hasWarning = true;
            } else if (NOT_CHECKED.equalsIgnoreCase(status)) {
                hasNotChecked = true;
            } else if (PASS.equalsIgnoreCase(status)) {
                hasPass = true;
            } else if (NOT_APPLICABLE.equalsIgnoreCase(status)) {
                hasNotApplicable = true;
            }
        }

        if (hasError) {
            return ERROR;
        }
        if (hasFailure) {
            return FAIL;
        }
        if (hasWarning) {
            return WARNING;
        }
        if (hasNotChecked) {
            return NOT_CHECKED;
        }
        if (hasPass) {
            return PASS;
        }
        if (hasNotApplicable) {
            return NOT_APPLICABLE;
        }
        return NOT_CHECKED;
    }
}
