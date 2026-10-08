package com.findatex.validator.batch;

/** Outcome of a single file in a {@link BatchValidationService} run. */
public enum BatchFileStatus {
    /** Loaded, validated and scored cleanly. */
    OK,
    /** The file was rejected before validation: unsupported format, parse failure, or not of the chosen template. */
    LOAD_ERROR,
    /** ValidationEngine threw an unexpected exception. Currently rare — engine swallows per-rule. */
    VALIDATION_ERROR,
    /** Pre-filtered by {@link FolderScanner} (hidden file, lock file, prior report). */
    SKIPPED
}
