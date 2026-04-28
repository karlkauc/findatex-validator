package com.findatex.validator.web;

import java.nio.file.Path;

/** Locations of canonical test fixtures shared with the {@code core} module. */
public final class TestFixtures {

    /** maven runs each module from its own module dir, so {@code ../core/...} resolves correctly. */
    public static final Path CLEAN_V7_XLSX = Path.of("../core/src/test/resources/sample/clean_v7.xlsx");
    public static final Path MISSING_MANDATORY_CSV = Path.of("../core/src/test/resources/sample/missing_mandatory.csv");
    public static final Path BAD_FORMATS_XLSX = Path.of("../core/src/test/resources/sample/bad_formats.xlsx");

    private TestFixtures() {}
}
