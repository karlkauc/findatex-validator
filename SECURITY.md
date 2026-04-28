# Security Policy

## Reporting a vulnerability

Please **do not open a public GitHub issue** for security problems.

Use GitHub's private vulnerability reporting:

> [**Report a vulnerability**](https://github.com/karlkauc/findatex-validator/security/advisories/new)
> (Repository → *Security* → *Advisories* → *Report a vulnerability*)

This sends the report only to the maintainers and lets us work with you
on a coordinated disclosure.

### What to include

- A description of the issue and its impact.
- Steps to reproduce, or a minimal proof of concept.
- Affected version(s) and delivery mode (Desktop / Web / Docker).
- Any suggested mitigation, if you have one.

Please **do not attach real fund data**. Use synthetic samples from
`samples/<template>/` or a redacted snippet that demonstrates the issue.

### What to expect

- **Initial acknowledgement:** within 72 hours.
- **Triage and severity assessment:** within 7 days.
- **Fix or mitigation target:** 30 days for high/critical issues; longer
  for low-severity findings, communicated case by case.
- **Coordinated disclosure:** we publish a GitHub Security Advisory and
  credit the reporter (unless you ask to remain anonymous) once a fix
  is released.

## Supported versions

| Version | Status                |
|---------|-----------------------|
| 1.0.x   | ✅ Active support     |
| < 1.0   | ❌ No security fixes  |

Older versions: please upgrade to the latest 1.0.x release before
reporting. Reports against unsupported versions will be triaged on a
best-effort basis.

## Scope

In scope:
- The validator code in this repository (`core/`, `javafx-app/`,
  `web-app/`).
- The official Docker image published to
  `ghcr.io/karlkauc/findatex-validator`.
- The bundled spec parsing and rule engine.

Out of scope:
- Vulnerabilities in third-party dependencies that are already tracked
  by Dependabot — please open a normal issue or wait for the next
  Dependabot PR.
- The FinDatEx specification documents themselves.
- Self-hosted deployments running modified or unsupported versions.
