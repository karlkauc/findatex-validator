# Privacy policy

## Summary

- Files you upload are used for the validation and deleted afterwards. Results are kept for 5 minutes.
- There are no cookies, no client-side analytics and no third-party scripts.
- Your IP address is processed to run the site. Our hosting provider keeps it in request logs for 30 days.
- Usage statistics are kept without your raw IP address. They are pseudonymous, not fully anonymous.

## Who is responsible

Karl Kauc, Vienna, Austria, is the controller under the GDPR.
Email: [karl.kauc@gmail.com](mailto:karl.kauc@gmail.com)

## Files you validate

An uploaded file is held temporarily for the validation and deleted when the request ends. The Excel report and the
annotated source view are kept for 5 minutes so that you can open them, and then deleted. They contain data from your
file. File content and file names do not reach the usage statistics.

If you arrive with a file from the Fund Data Converter, your browser passes it directly to this site.

## External validation

External validation is off unless you switch it on for a run. Only then, identifiers from your file (LEIs and ISINs)
are sent to GLEIF and OpenFIGI to check them. Their answers are cached on our server for up to 7 days. An OpenFIGI API
key you enter is used for that run only and is not stored.

## Request logs

Every request reaches our hosting provider, which records your IP address, the time, the requested address including
any query string, the response status and your browser's user agent in request logs. These logs are kept for 30 days.

The application itself also uses your IP address briefly in memory for rate limiting, for the visitor hash and for
the country lookup described below. It does not write the IP address to its own logs or database.

The purpose is to operate the site securely and to investigate errors and abuse. The legal basis is our legitimate
interest (Art. 6(1)(f) GDPR).

## Usage statistics

The server records events such as page views, validation runs, report downloads and sample loads. An event can contain:

- a visitor hash: a keyed hash of your IP address and user agent that changes every day, so a visitor is recognised
  within one day but not across days
- your country, looked up in a local copy of the MaxMind GeoLite2 database, so your IP address is not sent to MaxMind
- your user agent, device type and operating system family
- the page, the host of the referring site, and any campaign parameter in the link
- technical details of a run: template and version, profiles, file format, size and naming pattern class (never the
  name), row and finding counts, triggered rule IDs (never values), score, duration, outcome and lookup counts

Because the visitor hash and the user agent are stored, these records are treated as personal data. We do not try
to identify anyone from them.

The purpose is to understand how the site is used and to improve it. The legal basis is our legitimate interest
(Art. 6(1)(f) GDPR). These records are kept for as long as they are needed for that purpose, including comparisons
over longer periods. There is no fixed deletion date.

## Desktop app

By default the desktop app reports the same kind of technical details of a run to this site, plus app start and a
daily sign that it is running. These reports carry a random installation ID, the app version, the operating system
family and the Java version, and your country is derived from the request. They contain no files, file names or
values. You can switch this off in the app under Settings.

## Rating and feedback

If you rate the tool, we store the rating, your optional comment, the selected template, the app version if sent from
the desktop app, and your country. It is not linked to the usage statistics. The legal basis is our legitimate
interest in improving the tool (Art. 6(1)(f) GDPR), and ratings are kept for as long as they are needed for this
purpose.

"Report a false positive" opens a pre-filled issue on GitHub. Nothing is sent until you submit it there, and GitHub's
privacy policy then applies.

## Newsletter

If a sign-up form is shown, your email address goes directly to our newsletter provider, MailerLite, and is not
stored in our database or logs. You confirm the subscription by email. The legal basis is your consent (Art. 6(1)(a)
GDPR). You can withdraw it at any time through the link in every email.

## Stored in your browser

The site uses your browser's local storage to remember which sections you opened or closed. These settings stay on
your device and are not sent to us. You can delete them in your browser at any time.

## Recipients

These providers process data for us:

- **Google Cloud** runs the site and keeps the request logs (Cloud Run, region europe-west4, the Netherlands). Google
  may process data outside the EU. Such transfers are covered by its data processing terms.
- **Hetzner Online GmbH**, Germany, hosts the statistics database and a test copy of the site at
  validator.status20.net, whose web server also receives request data.

We do not sell data or pass it to anyone else, unless the law requires it.

## Your rights

You have the right to access, rectification, erasure, restriction of processing and data portability, and the
right to object to processing based on legitimate interest. To use these rights, email the address above.

The daily visitor hash cannot be traced back to a person, so we may need more information from you, or may be
unable to find your statistics records at all (Art. 11 GDPR).

You can also complain to the Austrian Data Protection Authority (Datenschutzbehörde, Barichgasse 40–42, 1030 Vienna,
[dsb.gv.at](https://www.dsb.gv.at)).

Providing data is neither legally nor contractually required, but the site cannot be reached without your IP address
being processed.

This product includes GeoLite2 data created by MaxMind, available from
[maxmind.com](https://www.maxmind.com).
