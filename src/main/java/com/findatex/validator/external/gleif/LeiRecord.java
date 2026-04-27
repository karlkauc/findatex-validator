package com.findatex.validator.external.gleif;

public record LeiRecord(String lei, String legalName, String country,
                        String entityStatus, String registrationStatus) {

    public boolean isLapsed() {
        return "LAPSED".equalsIgnoreCase(registrationStatus)
                || "RETIRED".equalsIgnoreCase(registrationStatus);
    }
}
