package com.findatex.validator.web.service;

import com.findatex.validator.newsletter.NewsletterStatus;

/**
 * Seam over an external newsletter provider (MailerLite by default;
 * EmailOctopus documented as a same-shaped alternative). Implementations make
 * one outbound HTTPS call per sign-up and map the provider's response onto the
 * shared {@link NewsletterStatus} vocabulary.
 *
 * <p>Implementations must be fully self-contained on failure: any timeout,
 * I/O error, auth error or unexpected status maps to
 * {@link NewsletterStatus#UNAVAILABLE} and is logged <b>without</b> the
 * e-mail address. They never throw.
 */
public interface NewsletterProvider {

    /**
     * Subscribes {@code email} (already syntactically validated by the caller).
     * Returns the mapped status; never throws.
     */
    NewsletterStatus subscribe(String email);
}
