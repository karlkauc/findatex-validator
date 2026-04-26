package com.tpt.validator.spec;

public enum Profile {
    SOLVENCY_II("Solvency II"),
    IORP_EIOPA_ECB("IORP / EIOPA / ECB"),
    NW_675("NW 675");

    private final String displayName;

    Profile(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
