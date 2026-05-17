# Newsletter sign-up (external provider)

Users can subscribe to a newsletter with their e-mail address from the **web
app** and the **JavaFX desktop app**. Unlike the anonymous usage statistics
this is a deliberate, user-initiated action on a **personal datum (GDPR)**, so
the flow is **synchronous with a clear result message** and is *not*
fire-and-forget.

## Privacy / GDPR — the core guarantee

- The e-mail address is **never stored in our database and never written to
  our logs**. It is forwarded straight to the external newsletter provider.
- Consent proof (**double opt-in**), storage, unsubscribe and deletion are the
  **provider's** responsibility. Conclude a **data processing agreement (DPA)**
  with the provider before going live.
- The desktop app **never holds the provider API key** — it POSTs to the web
  app, which is the only component that knows the key (same trust model as
  usage-stats).
- The sign-up form/dialog states what happens to the address and that
  unsubscribe is available via the link in every mail.

## Architecture

```
Web:     React footer form → POST /api/newsletter/subscribe {email}
                            → NewsletterResource (validate, rate-limit)
                            → NewsletterService → MailerLiteProvider → MailerLite
                            → structured {status} → UI message
Desktop: Settings ▸ Newsletter → NewsletterClient (core, proxy/NTLM-aware)
                            → POST same web endpoint → {status} → dialog message
Gating:  GET /api/newsletter-config {enabled}  drives whether the SPA shows
         the form. No API key ⇒ feature inert (POST → 503), form hidden.
```

## Status vocabulary

`com.findatex.validator.newsletter.NewsletterStatus` is the single source of
truth; the JSON wire token is the lowercase name. The React frontend mirrors
the same vocabulary.

| status | meaning | HTTP |
|---|---|---|
| `pending` | accepted; provider sent the double-opt-in confirmation mail | 200 |
| `subscribed` | address is an active (confirmed) subscriber | 200 |
| `already_subscribed` | was already confirmed before this attempt | 200 |
| `already_pending` | was already pending confirmation | 200 |
| `invalid_email` | syntactically invalid / rejected by provider | 400 |
| `unavailable` | feature off, provider down, or unexpected error | 503 |

## REST endpoints

- `POST /api/newsletter/subscribe` — body `{"email":"…"}` → `{"status":"…"}`.
  Per-IP rate limited (strict, anti e-mail-bombing). Returns 503 `unavailable`
  when the feature is unconfigured.
- `GET /api/newsletter-config` — `{"enabled":bool}` (read-only, no rate limit).

## Configuration (web app; all env-overridable, feature off until key set)

| property | env | default |
|---|---|---|
| `findatex.web.newsletter.provider` | `FINDATEX_WEB_NEWSLETTER_PROVIDER` | `mailerlite` |
| `findatex.web.newsletter.api-key` | `FINDATEX_WEB_NEWSLETTER_API_KEY` | *(empty ⇒ inert)* |
| `findatex.web.newsletter.group-id` | `FINDATEX_WEB_NEWSLETTER_GROUP_ID` | *(optional)* |
| `findatex.web.newsletter.rate-per-ip-per-hour` | `FINDATEX_WEB_NEWSLETTER_RATE` | `5` |

Desktop: `AppSettings.Newsletter.endpointUrl` (Settings ▸ Newsletter) — the
web-app base URL the desktop posts to; blank disables the desktop action.

## Provider setup (MailerLite — default)

1. Create a MailerLite account; create an API token.
2. **Enable double opt-in** in the MailerLite account settings (this is an
   account setting, not a code flag — MailerLite then sends the confirmation
   mail and new subscribers come back as `unconfirmed`).
3. Optionally create a group and set its id as `…group-id`.
4. Set `FINDATEX_WEB_NEWSLETTER_API_KEY` (and group id) in the web deployment.
5. Conclude the DPA with MailerLite.

API used: `POST https://connect.mailerlite.com/api/subscribers` with
`Authorization: Bearer <key>`, body `{"email":…,"groups":[…]}`.

## Switching provider: EmailOctopus

`NewsletterProvider` is a one-method seam; `MailerLiteProvider` is the only
shipped implementation. EmailOctopus is the same shape — add an
`EmailOctopusProvider` calling
`POST https://api.emailoctopus.com/api/1.6/lists/{listId}/contacts` with
`{"api_key":"…","email_address":"…","status":"PENDING"}` and select it in
`NewsletterService.init()` by the `provider` config value. Map the response
onto the same `NewsletterStatus` vocabulary.

## Notes

- The address is validated cheaply (`EmailAddress.isValid`) before any
  outbound call; the provider performs authoritative validation and the
  double-opt-in mail is the real proof of a working address.
- All provider failures (timeout, auth, 5xx) collapse to `unavailable` and are
  logged **without** the address (status code / exception class only).
