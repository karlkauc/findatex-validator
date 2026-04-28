# Project rename: `tpt_test` / `tpt-validator` → `findatex-validator`

**Status:** approved (2026-04-28)
**Scope:** in-repo references, git remote URL, GHCR image path. Local
directory rename and GitHub repo rename are out of scope (operator does
those manually).

## Motivation

Java packages, Maven coordinates, README title, and JavaFX window title
already say "FinDatEx Validator" / `com.findatex.validator` /
`findatex-validator-*`. The directory name `tpt_test` and the GitHub repo
slug `tpt-validator` are leftovers from before the multi-template rewrite
(when the project only validated TPT). Aligning the remaining names
removes the cognitive split between "old TPT-only project" and "current
multi-template validator".

## Changes (single commit)

### Build output

- `javafx-app/pom.xml` line 61 — **delete** the explicit
  `<finalName>tpt-validator-${project.version}</finalName>`. Maven then
  defaults to `${artifactId}-${version}`, which produces
  `findatex-validator-javafx-1.0.0.jar` and
  `findatex-validator-javafx-1.0.0-shaded.jar`. This is exactly what
  `package/jpackage.sh` already expects (line 15:
  `SHADED_JAR=".../findatex-validator-javafx-${APP_VERSION}-shaded.jar"`),
  so the script — currently broken because the build produces
  `tpt-validator-1.0.0-shaded.jar` instead — becomes functional again.
- `package/jpackage.sh` line 14 — `APP_NAME="TPT Validator"` →
  `APP_NAME="FinDatEx Validator"`. Affects the human-readable name on
  the native installer (.deb/.rpm/.dmg/.pkg).

### GitHub URLs (`karlkauc/tpt-validator` → `karlkauc/findatex-validator`)

- `pom.xml` — `<url>` and `<scm>` block (3 URLs).
- `README.md` — CI badge, container badge, clone URL, `cd tpt-validator`
  hint, `docker pull ghcr.io/...` line.
- `CHANGELOG.md` — `[Unreleased]` and `[1.0.0]` compare/release links.
- `CONTRIBUTING.md` — issues link, clone URL, `cd ...`, discussions link.
- `SECURITY.md` — security advisories URL.
- `.github/ISSUE_TEMPLATE/config.yml` — discussions + advisories URLs.
- `web-app/src/main/frontend/package.json` — `repository.url`.

### CI / Container

- `.github/workflows/ci.yml` — local image tag `tpt-validator:ci` →
  `findatex-validator:ci`.
- README docker-pull line — `ghcr.io/karlkauc/tpt-validator` →
  `ghcr.io/karlkauc/findatex-validator`. Existing GHCR images under the
  old path are not deleted; new pushes after the GitHub repo rename will
  publish under the new path.

### Documentation cosmetics

- `javafx-app/src/main/java/com/findatex/validator/App.java` line 28 —
  comment text `"TPT Validator"` → `"FinDatEx Validator"` (the runtime
  property `apple.awt.application.name` is already correct, only the
  Javadoc is stale).
- `docs/ROADMAP.md` — `~/.config/tpt-validator/recent.json` →
  `~/.config/findatex-validator/recent.json` (planned-feature path).
- `CLAUDE.md` — drop the parenthetical "the directory name `tpt_test` is
  historical — do **not** rename packages back to `com.tpt`". Keep the
  package-name guard in a shorter form.
- `CONTRIBUTING.md` — same: drop the historical-directory note.

### Git remote

- `git remote set-url origin git@github.com:karlkauc/findatex-validator.git`.
  Safe even before the GitHub Settings rename — GitHub auto-redirects in
  both directions once renamed.

### Verify, commit, push

- `mvn test` — full reactor regression (~520+ tests). URL/name changes
  should be test-neutral.
- One commit with all changes; message describes the rename and notes the
  two operator follow-ups (directory + GitHub Settings).
- Push to `origin/main` (per standing authorization in user memory).

## Out of scope (operator does manually)

1. **Rename GitHub repo** in Settings → `findatex-validator`. GitHub
   creates a redirect; old links continue to work.
2. **Rename local directory:** `cd ~/webdav && mv tpt_test findatex-validator`,
   then restart Claude Code in the new path. Cannot be done from inside
   the running session because the directory is the current working
   directory.

## Non-goals

- No changes to Java packages (`com.findatex.validator.*` already correct).
- No changes to Maven `groupId` or `artifactId`s (already
  `com.findatex` / `findatex-validator-*`).
- No changes to spec XLSX files, sample fixtures, or test resources.
- No changes to `Dockerfile` or `docker-compose.yml` (neither pins an
  image name that uses the old slug).

## Risk

Low. The JAR filename changes from `tpt-validator-1.0.0-shaded.jar` to
`findatex-validator-javafx-1.0.0-shaded.jar`. Any external script that
hardcoded the old name will break. The CI workflow consumes the shaded
JAR via Maven coordinates (not by filename); `package/jpackage.sh`
already expects the new filename and is currently broken — this rename
fixes it as a side effect.
