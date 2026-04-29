package com.findatex.validator.batch;

/** Outcome of a single file in a {@link BatchValidationService} run. */
public enum BatchFileStatus {
    /** Loaded, validated and scored cleanly. */
    OK,
    /** TptFileLoader rejected the file (unsupported format / parse failure). */
    LOAD_ERROR,
    /** ValidationEngine threw an unexpected exception. Currently rare — engine swallows per-rule. */
    VALIDATION_ERROR,
    /** Pre-filtered by {@link FolderScanner} (hidden file, lock file, prior report). */
    SKIPPED
}
