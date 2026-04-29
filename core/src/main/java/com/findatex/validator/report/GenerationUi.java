package com.findatex.validator.report;

public enum GenerationUi {
    DESKTOP("Desktop"),
    WEB("Web");

    private final String label;

    GenerationUi(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
