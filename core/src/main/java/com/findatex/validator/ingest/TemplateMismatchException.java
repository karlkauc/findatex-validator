package com.findatex.validator.ingest;

/** The file was read, but its header row does not belong to the chosen template — see {@link HeaderMatch}. */
public final class TemplateMismatchException extends RuntimeException {

    public TemplateMismatchException(String message) {
        super(message);
    }
}
