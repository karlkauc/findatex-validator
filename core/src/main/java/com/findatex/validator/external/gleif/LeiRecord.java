package com.findatex.validator.external.gleif;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record LeiRecord(String lei, String legalName, String country,
                        String entityStatus, String registrationStatus) {

    @JsonIgnore
    public boolean isLapsed() {
        return "LAPSED".equalsIgnoreCase(registrationStatus)
                || "RETIRED".equalsIgnoreCase(registrationStatus);
    }
}
