package com.findatex.validator.ingest;

/** The file was read, but its header row does not belong to the chosen template — see {@link HeaderMatch}. */
public final class TemplateMismatchException extends RuntimeException {

    private final transient HeaderMatch match;

    public TemplateMismatchException(String message) {
        this(message, null);
    }

    /** @param match the header statistics behind the rejection (for the usage stats' fail reason) */
    public TemplateMismatchException(String message, HeaderMatch match) {
        super(message);
        this.match = match;
    }

    /** The header statistics behind the rejection, or {@code null} if unknown. */
    public HeaderMatch match() {
        return match;
    }
}
