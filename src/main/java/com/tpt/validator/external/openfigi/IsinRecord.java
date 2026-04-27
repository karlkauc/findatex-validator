package com.tpt.validator.external.openfigi;

public record IsinRecord(String isin, String figi, String name, String ticker,
                         String exchCode, String marketSector, String securityType,
                         String currency) {}
