package com.shipshape.backend.dto;

public class HeaderCheckResult {
    private String headerName;
    private boolean present;
    private String value;

    public HeaderCheckResult() {
    }

    public HeaderCheckResult(String headerName, boolean present, String value) {
        this.headerName = headerName;
        this.present = present;
        this.value = value;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public boolean isPresent() {
        return present;
    }

    public void setPresent(boolean present) {
        this.present = present;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
