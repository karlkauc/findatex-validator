package com.findatex.validator.batch;

/**
 * One progress event emitted by {@link BatchValidationService} as it works through a folder of
 * files. {@code currentFileName} is the just-finished or currently-processing file's display
 * name; {@code phase} indicates which step of that file's pipeline is now active.
 */
public record BatchProgress(int filesDone, int filesTotal, String currentFileName, Phase phase) {

    public enum Phase {
        LOADING,
        VALIDATING,
        EXTERNAL,
        SCORING,
        DONE
    }
}
