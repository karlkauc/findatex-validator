# GitHub Governance Docs — Resumable Plan

## Context

Five files were deliberately skipped during the GitHub-readiness sweep
(commits `baace77`, `c96fe39`, `05f508b`). This plan finishes them.

Each step is **one file, one commit, independently completable**. If an
API error interrupts the session mid-step, restart from the next
unticked checkbox — no step depends on a half-finished previous one
except where explicitly noted (Step 3 needs Steps 1 + 2 done first so
the CONTRIBUTING.md links resolve; Step 5 needs Step 2).

User-confirmed decisions (carry over from the previous plan):
- Security contact: **GitHub Private Vulnerability Reporting** (no email).
- Container publish: already wired in `release.yml` — no change here.

Every step is a docs-only change. **No `mvn verify` between steps**;
run it once at the end.

---

## Step 1 — `CODE_OF_CONDUCT.md`

**Goal:** Add Contributor Covenant 2.1 verbatim, with the enforcement
contact pointing at GitHub PVR + the maintainer's GitHub handle.

**File:** `/home/karl/webdav/tpt_test/CODE_OF_CONDUCT.md`

**Source:** Contributor Covenant 2.1 from
<https://www.contributor-covenant.org/version/2/1/code_of_conduct/>
(plain Markdown link near the top of the page → "Markdown (.md)").

**Project-specific edits to the canonical text:**

In the *Enforcement* section, replace the placeholder:

> Instances of abusive, harassing, or otherwise unacceptable behavior
> may be reported to the community leaders responsible for enforcement
> at **[INSERT CONTACT METHOD]**.

with:

> Instances of abusive, harassing, or otherwise unacceptable behavior
> may be reported to the project maintainer
> [@karlkauc](https://github.com/karlkauc) via GitHub's
> [private vulnerability reporting](https://github.com/karlkauc/tpt-validator/security/advisories/new)
> channel.

**Commit:**
```bash
git add CODE_OF_CONDUCT.md
git commit -m "$(cat <<'EOF'
docs: add Contributor Covenant 2.1 Code of Conduct

Standard 2.1 text. Enforcement contact: maintainer @karlkauc via GitHub
private vulnerability reporting (no public email exposed).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
git push
```

- [ ] Done

---

## Step 2 — `SECURITY.md`

**Goal:** Tell reporters how to file a security issue (PVR only) and set
expectations.

**File:** `/home/karl/webdav/tpt_test/SECURITY.md`

**Full content** (copy verbatim):

```markdown
# Security Policy

## Reporting a vulnerability

Please **do not open a public GitHub issue** for security problems.

Use GitHub's private vulnerability reporting:

> [**Report a vulnerability**](https://github.com/karlkauc/tpt-validator/security/advisories/new)
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
  `ghcr.io/karlkauc/findatex-validator-web`.
- The bundled spec parsing and rule engine.

Out of scope:
- Vulnerabilities in third-party dependencies that are already tracked
  by Dependabot — please open a normal issue or wait for the next
  Dependabot PR.
- The FinDatEx specification documents themselves.
- Self-hosted deployments running modified or unsupported versions.
```

**Commit:**
```bash
git add SECURITY.md
git commit -m "$(cat <<'EOF'
docs: add SECURITY.md pointing at GitHub private vulnerability reporting

Sets a 72h ack / 7d triage / 30d fix-target SLA, declares supported
versions (1.0.x), and clarifies in-scope vs out-of-scope. No public
email is exposed; all reports go through GitHub's PVR channel.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
git push
```

- [ ] Done

---

## Step 3 — Stage `CONTRIBUTING.md`

**Depends on:** Steps 1 + 2 (the file already references both).

**Goal:** The `CONTRIBUTING.md` is currently untracked. After Steps 1+2
its links to `CODE_OF_CONDUCT.md` and `SECURITY.md` resolve, so it can
be staged as-is. **No content edits**.

**Verify the references first:**
```bash
grep -n "CODE_OF_CONDUCT\|SECURITY" CONTRIBUTING.md
```
Expected: lines 9 and 10 mention both files.

**Commit:**
```bash
git add CONTRIBUTING.md
git commit -m "$(cat <<'EOF'
docs: add CONTRIBUTING.md (dev setup, PR workflow, conventions)

Was previously untracked. Now that CODE_OF_CONDUCT.md and SECURITY.md
both exist, the cross-references in this file resolve.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
git push
```

- [ ] Done

---

## Step 4 — `CHANGELOG.md`

**Goal:** Keep a Changelog format. One real entry for 1.0.0 (the current
shipped state) and an `[Unreleased]` placeholder for the next cycle.

**File:** `/home/karl/webdav/tpt_test/CHANGELOG.md`

**Full content** (copy verbatim):

```markdown
# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- _Nothing yet._

### Changed
- _Nothing yet._

### Fixed
- _Nothing yet._

## [1.0.0] — 2026-04-28

First public release.

### Added

- Validation core for four FinDatEx templates: **TPT** (V6, V7), **EET**
  (V1.1.2, V1.1.3), **EMT** (V4.2, V4.3), **EPT** (V2.0, V2.1).
- Manifest-driven spec loader so new template versions are added by
  dropping an XLSX + sibling `*-info.json` into
  `core/src/main/resources/spec/`.
- Two delivery modes from one validation core:
  - **JavaFX desktop app** — files never leave the user's machine.
  - **Quarkus + React web app** — Docker-deployable, no login,
    rate-limited (per-IP token bucket + concurrency cap), auto-deletes
    uploads and reports.
- Optional external validation against **GLEIF** (LEI) and **OpenFIGI**
  (ISIN); off by default, supports system + manual NTLM proxies.
- Excel quality report with five sheets (`Summary`, `Scores`,
  `Findings`, `Field Coverage`, `Per Position`) and an *Annotated
  Source* tab with cell-level highlights and comments.
- Profile-aware quality scoring with a four-category weighted overall
  score (mandatory 40 / format 20 / closed-list 15 / cross-field 15 /
  profile-completeness avg 10).
- ~25 cross-field rules for TPT (SCR delivery, weight sums, NAV,
  custodian pair, dates, conditional XF-16..XF-25). EET/EMT/EPT rule
  sets are mechanical-only (presence + format + codification +
  spec-explicit conditional presence) — anything regulatory is
  explicitly DEFERRED.
- End-user `HELP.md` and a technical `README.md` (English-only UIs).
- Apache-2.0 license; CI workflow with xvfb-run JavaFX tests, JaCoCo
  coverage, and a Docker smoke build.

[Unreleased]: https://github.com/karlkauc/tpt-validator/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/karlkauc/tpt-validator/releases/tag/v1.0.0
```

**Commit:**
```bash
git add CHANGELOG.md
git commit -m "$(cat <<'EOF'
docs: add CHANGELOG.md (Keep a Changelog, 1.0.0 entry + Unreleased)

Summarises the shipped feature set: four templates with two versions
each, JavaFX + web delivery, optional GLEIF/OpenFIGI lookup, profile-
aware Excel report, manifest-driven spec loader.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
git push
```

- [ ] Done

---

## Step 5 — README "Security" section

**Depends on:** Step 2.

**Goal:** A short paragraph above the existing `## License` section that
points readers at SECURITY.md / GitHub PVR. Keep it tiny — no other
README changes.

**File:** `/home/karl/webdav/tpt_test/README.md`

**How to find the insertion point:**
```bash
grep -n "^## License" README.md
```

**Edit:** Insert the following block on a new line, immediately
**before** the `## License` heading. Preserve the blank line above and
below the new heading.

```markdown
## Security

Found a vulnerability? Please **do not open a public issue**. See
[SECURITY.md](SECURITY.md) and use GitHub's private vulnerability
reporting (Repository → *Security* → *Advisories* → *Report a
vulnerability*). The maintainer will acknowledge within 72 hours.

```

**Commit:**
```bash
git add README.md
git commit -m "$(cat <<'EOF'
docs(readme): add Security section linking SECURITY.md and PVR

Short pointer above the License section so readers landing on the
README know how to report security issues responsibly.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
git push
```

- [ ] Done

---

## Final verification (run after Step 5)

```bash
# Sanity: all governance files now tracked, no stragglers.
git ls-files CODE_OF_CONDUCT.md SECURITY.md CHANGELOG.md CONTRIBUTING.md README.md
git status                                # nothing pending

# Optional: build still green (none of these touch code, but cheap to confirm).
mvn -B -ntp -DskipTests verify
```

GitHub-side:
1. **Insights → Community Standards** — every checklist item should now
   show ✅ (Description, README, Code of Conduct, Contributing, License,
   Security policy, Issue templates, PR template).
2. **Settings → Code security** — enable *Private vulnerability
   reporting* (so the link in SECURITY.md works).

---

## Recovery notes (if API errors hit)

- Each step is a single `git add` + `git commit` + `git push`. Worst
  case after an interrupted step: `git status` shows an unstaged file
  → re-run that step's commit block.
- Steps 1–2, 4 are independent. Step 3 needs Step 1 + Step 2 already
  pushed (otherwise CONTRIBUTING.md links 404 on GitHub). Step 5 needs
  Step 2.
- Standing authorisation in memory covers commit + push per feature, so
  no need to re-confirm before each push.
- If a step fails mid-edit, no partial file is in git — `git status`
  is the source of truth for what's done.
