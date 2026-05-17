package com.findatex.validator.web.service;

import com.findatex.validator.newsletter.NewsletterStatus;
import com.findatex.validator.web.config.WebConfig;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridges the REST layer to the configured external newsletter provider. The
 * e-mail address is forwarded straight to the provider and is <b>never</b>
 * persisted in our database or written to logs (consent proof, storage,
 * unsubscribe and deletion are the provider's responsibility — see
 * {@code docs/NEWSLETTER.md}).
 *
 * <p>The feature is inert unless an API key is configured: {@link #enabled()}
 * gates the endpoint so the app boots (and tests run) with no provider.
 */
@ApplicationScoped
public class NewsletterService {

    private static final Logger log = LoggerFactory.getLogger(NewsletterService.class);

    @Inject
    WebConfig config;

    private volatile NewsletterProvider provider;

    @PostConstruct
    void init() {
        WebConfig.Newsletter cfg = config.newsletter();
        if (cfg.apiKey().isEmpty()) {
            log.info("Newsletter: no API key configured "
                    + "(FINDATEX_WEB_NEWSLETTER_API_KEY unset) — sign-up disabled");
            return;
        }
        String name = cfg.provider();
        if ("mailerlite".equalsIgnoreCase(name)) {
            provider = new MailerLiteProvider(cfg.apiKey().get(), cfg.groupId().orElse(""));
            log.info("Newsletter: provider '{}' configured", name);
        } else {
            // Only MailerLite ships today; EmailOctopus is documented as a
            // same-shaped drop-in (see docs/NEWSLETTER.md). Unknown name =>
            // inert rather than a boot failure.
            log.warn("Newsletter: unsupported provider '{}' — sign-up disabled", name);
        }
    }

    /** {@code true} when a provider is wired and ready to take sign-ups. */
    public boolean enabled() {
        return provider != null;
    }

    /**
     * Forwards an already syntactically-validated address to the provider.
     * Returns {@link NewsletterStatus#UNAVAILABLE} when the feature is off or
     * the provider call soft-fails; never throws.
     */
    public NewsletterStatus subscribe(String email) {
        NewsletterProvider p = provider;
        if (p == null) return NewsletterStatus.UNAVAILABLE;
        return p.subscribe(email);
    }
}
