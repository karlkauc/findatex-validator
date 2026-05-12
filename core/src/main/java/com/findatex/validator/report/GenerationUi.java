package com.findatex.validator.report;

public enum GenerationUi {
    DESKTOP("Desktop"),
    WEB("Web"),
    CLI("CLI");

    private final String label;

    GenerationUi(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
