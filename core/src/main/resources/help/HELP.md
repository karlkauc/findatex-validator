# FinDatEx Validator — Help

Welcome. This document explains what the FinDatEx Validator does, what it
does not do, and how to interpret its results. It is written for the people
who own data quality at an asset manager: the analyst preparing a TPT,
EET, EMT, or EPT file before sending it to an insurer, distributor, or
custodian — and the operations engineer making sure those files keep
flowing.

The same content is shown inside the desktop app (Help button), the web app
(Help button), and on GitHub (`core/src/main/resources/help/HELP.md`).

---

## 1. What this tool does

The FinDatEx Validator reads a data-template file (`.xlsx`, `.xlsm`, or
`.csv`) and reports how well it conforms to the official
[FinDatEx](https://findatex.eu) specification for that template and version.
The output has two parts:

- **Findings** — every issue the validator detected, classified by
  severity, with row, field, instrument, and rule reference.
- **A quality score** — five sub-scores plus an overall percentage,
  weighted to reflect how badly each issue category breaks downstream
  consumption.

You get the same engine in two delivery modes:

- **Desktop app (JavaFX)** — files never leave your machine. Use this for
  daily validations on confidential fund data.
- **Web app (Quarkus + React, Docker)** — useful when the desktop install
  is not an option, or for occasional use. Uploads are processed in
  memory and discarded immediately; reports are available for 5 minutes
  via a single-use URL.

Both modes share the same validation core, the same rules, the same
reports.

---

## 2. Templates supported

| Template | Bundled versions          | Spec owner   |
|----------|---------------------------|--------------|
| **TPT** — Tripartite Template     | V7.0 (2024-11-25), V6.0 (2022-03-14) | FinDatEx     |
| **EET** — European ESG Template   | V1.1.3 (2024-10-04), V1.1.2 (2023-12-05) | FinDatEx     |
| **EMT** — European MiFID Template | V4.3 (2025-12-17), V4.2 (2024-04-22) | FinDatEx     |
| **EPT** — European PRIIPs Template| V2.1 (2022-10-12), V2.0 (2022-02-15) | FinDatEx     |

Specs are obtained from the FinDatEx working-group portal (login-walled,
no stable URLs). New versions can be added by an operator without code
changes — see `docs/SPEC_DOWNLOADS.md` and the "Adding a new template
version" section in `README.md`.

---

## 3. Profiles explained

A "profile" is a regulatory dimension that decides which fields are
mandatory in a file. Selecting a profile in the UI tells the validator
to enforce the mandatory flags from that profile's column in the spec.
You can select multiple profiles at once — the validator applies the
union (a field is mandatory if any selected profile says so).

### TPT profiles

| Profile | What it covers |
|--------|----------------|
| **Solvency II**           | EU prudential regime for insurers (Directive 2009/138/EC). Drives the SCR calculation in pillar 1, including look-through to underlying instruments. The widest mandatory set in TPT. |
| **IORP / EIOPA / ECB**    | Occupational pensions (IORP II Directive) and the EIOPA / ECB statistical reporting columns embedded in TPT. |
| **NW 675**                | Netherlands DNB regime for pension funds (NW 675 reporting). Enforces a Dutch-specific subset of TPT fields. |
| **SST (FINMA)**           | Swiss Solvency Test (FINMA). Enforces a Swiss-specific subset. |

### EET profiles

| Profile | What it covers |
|--------|----------------|
| **SFDR Periodic**        | Sustainable Finance Disclosure Regulation periodic disclosures (Annex IV/V). |
| **SFDR Pre-contract**    | SFDR pre-contractual disclosures (Annex II/III). |
| **SFDR Entity**          | SFDR entity-level disclosures. |
| **MiFID Products**       | MiFID II product governance fields (manufacturer side). |
| **IDD Products**         | Insurance Distribution Directive equivalent of MiFID Products. |
| **MiFID Distributors**   | MiFID II target-market fields needed by distributors. |
| **IDD Insurers**         | IDD equivalent for insurance distributors. |
| **Look-through (FoF)**   | Fund-of-funds look-through fields used to roll up underlying ESG metrics. |

### EMT profiles

| Profile | What it covers |
|--------|----------------|
| **EMT (Mandatory)**      | The EMT spec carries no per-jurisdiction profile partition — it has a single mandatory column. The validator models this as one profile so the engine treatment stays uniform across templates. |

### EPT profiles

The EPT profile set is version-dependent.

| Profile | V2.0 | V2.1 | What it covers |
|--------|:---:|:---:|----------------|
| **PRIIPs Sync**           | ✓ | ✓ | Synchronisation fields for PRIIPs reporting between manufacturers and distributors. |
| **PRIIPs KID**            | ✓ | ✓ | Key Information Document fields under PRIIPs RTS. |
| **UCITS KIID**            | ✓ |   | UCITS Key Investor Information Document (replaced in V2.1 by UK). |
| **UK**                    |   | ✓ | UK FCA equivalent of the UCITS KIID profile. |

### "No selection" semantics

In the web UI, leaving the profile selector empty evaluates **all**
profiles for the chosen template — useful for an exploratory pass when
you don't yet know which regulatory regime your file is meant for.

---

## 4. What gets validated

The validator categorises every check into one of five buckets.

### 4.1 Presence (mandatory completeness)

For each field marked **M** in the selected profile's column, the
validator checks the cell is non-blank. Missing mandatory values are
reported as **ERROR**.

### 4.2 Format

Every field is validated against the format declared in the spec:

- **Currency** — ISO 4217 (e.g. `EUR`, `USD`, `JPY`).
- **Country** — ISO 3166-1 alpha-2 (e.g. `DE`, `LU`, `US`).
- **Date** — ISO 8601 `YYYY-MM-DD`.
- **NACE** — economic-sector codes (1, 2, or 4-digit forms accepted as
  per the spec).
- **Numeric** — decimal-comma or decimal-point accepted; ranges enforced
  where the spec defines them (e.g. weights `[0..1]`).

Format violations are reported as **ERROR**.

### 4.3 Codification (closed lists & checksums)

- **ISIN** — 12-character format + Luhn checksum. The spec's
  type-of-code companion field controls when the ISIN check applies
  (`type = "1"` → ISIN). The check runs on instrument identifier (TPT
  field 14), underlying identifier (TPT field 68), and the equivalent
  EET/EMT/EPT polymorphic identifier columns.
- **LEI** — 20-character format + ISO 17442 mod-97 checksum. Multiple
  LEI columns per template (issuer, group, underlying, custodian, fund
  manufacturer, EET/EMT/EPT producer LEI).
- **CIC** — closed list from the spec (`CodeListLicense`,
  `CodeListSecurityClassification`, etc.). For TPT the CIC drives many
  conditional rules.

Closed-list violations are reported as **ERROR**.

### 4.4 Cross-field consistency (TPT only)

TPT carries ~25 hand-written cross-field rules. The most important ones:

- **XF-04 — Position weight sum** — sum of position weights per fund
  matches 100% within tolerance.
- **XF-05 — NAV consistency** — `NAV = SharePrice × NumberOfShares`
  within tolerance.
- **XF-06 — Cash percentage** — declared cash percentage matches the
  positions tagged as cash.
- **XF-07 — SCR delivery** — when SCR market-risk delivery is
  requested, all SCR sub-modules must be present.
- **XF-08 — Coupon frequency** — payment frequency consistent with
  coupon-period fields.
- **XF-09 — Custodian pair** — custodian name and LEI must both be
  present or both be absent.
- **XF-10 — Interest-rate type** — fixed/float flag consistent with the
  presence of the index reference.
- **XF-11 — Date order** — issue date ≤ settlement date ≤ maturity
  date.
- **XF-12 — Maturity after reporting** — maturity strictly after the
  reporting date for non-matured positions.
- **XF-13 — PIK** — payment-in-kind flag consistent with coupon
  presence.
- **XF-14 — Underlying CIC** — derivative CIC requires an underlying
  identifier and matching underlying CIC.
- **XF-15 — TPT version** — file's declared version matches the
  template version selected in the UI.
- **XF-16 to XF-25** — declarative "if field X = trigger then field Y
  must be present" rules, sourced verbatim from the per-CIC qualifier
  text in the spec (e.g. "if item 48 set to '1' then issuer LEI is
  required"). These supersede the generic Presence rule for their
  target fields so you only see one finding per missing value, not two.

Cross-field violations are reported as **ERROR** unless the spec
designates the rule as a warning.

### 4.5 Conditional presence (all templates)

Spec-driven "if field X has value Y then field Z is mandatory". The
trigger is read from the spec's qualifier text on the field (or, for
TPT, from the per-CIC columns in the V7 spec sheet). Conditional
violations are reported as **ERROR**.

EET and EMT carry conditional presence rules extracted directly from the
spec rows; EPT carries the same shape.

---

## 5. What is *not* validated

The validator deliberately stops short of enforcing regulatory logic
that requires a subject-matter expert to maintain. The following are
**not** checked, even when the file looks "wrong" against a regulator's
own guidance:

### 5.1 EET regulatory cross-checks (deferred)

- SFDR Article 6 / 8 / 9 product-classification consistency across
  fields.
- SFDR PAI (Principal Adverse Impact) cross-field consistency.
- SFDR Taxonomy alignment / eligibility cross-checks.
- SFDR look-through aggregation (FoF) — *the look-through profile only
  enforces presence of the look-through fields, not the arithmetic*.

### 5.2 EMT regulatory cross-checks (deferred)

- MiFID II target-market consistency (manufacturer-target-market vs.
  distributor-target-market vs. negative-target-market combinations).
- IDD-specific target-market overrides.

### 5.3 EPT regulatory cross-checks (deferred)

- PRIIPs RTS scenarios (favourable / moderate / unfavourable / stress)
  arithmetic and consistency.
- PRIIPs KID stress-test scenario arithmetic.
- UCITS KIID synthetic risk-and-reward indicator (SRRI) check.

These are flagged in the source as
`// DEFERRED: requires regulatory SME — <which regulation, which
fields>`. They will be added when a regulatory SME has reviewed the
trigger logic. **Do not infer that a clean validator report means a file
is regulator-ready** — for non-TPT templates, the validator only
guarantees mechanical conformance.

### 5.4 Out of scope (will not be added)

- **PDF rendering** — the validator does not parse PRIIPs KID PDFs
  produced from the EPT.
- **FunDataXML** — the validator does not parse the XML edition of
  TPT/EET/EMT/EPT. Only the published `.xlsx` / `.csv` shape is
  supported.
- **Schema validation against XSD** — see above.

---

## 6. Severity levels and quality scoring

### 6.1 Severity

| Level   | When you'll see it |
|---------|--------------------|
| **ERROR**   | Mandatory field missing, format violation, checksum failure, cross-field inconsistency that the spec marks as required. **A single ERROR usually breaks the receiving system.** |
| **WARNING** | Spec violation that is recoverable on the receiving side, or a soft-fail rule (e.g. weight sum just outside tolerance). |
| **INFO**    | Diagnostic information — typically from external validation (GLEIF/OpenFIGI service unreachable) or an unusual-but-legal value. |

### 6.2 Quality score

The overall percentage is a weighted average of five sub-scores:

| Sub-score                    | Weight |
|------------------------------|-------:|
| Mandatory completeness       |   40%  |
| Format conformance           |   20%  |
| Closed-list conformance      |   15%  |
| Cross-field consistency      |   15%  |
| Profile completeness (avg)   |   10%  |
| **Overall**                  | **100%** |

Each sub-score is `1 – (failures / opportunities)`, clamped to `[0, 1]`,
then multiplied by 100. The overall is the weighted sum.

A perfect file scores 100%. A file with one missing mandatory field
typically scores in the high 90s — most of the score reflects the
*proportion* of fields that are correct, not the count of issues.

---

## 7. External validation: GLEIF and OpenFIGI

This is an **optional** second pass that runs after local validation.
It cross-checks identifiers in the file against authoritative external
registries:

- **GLEIF** — Global Legal Entity Identifier Foundation. Authoritative
  source for LEI metadata.
- **OpenFIGI** — Bloomberg's open identifier service. Used here to
  confirm ISIN existence and (optionally) cross-check currency and CIC
  consistency.

### 7.1 What gets checked

Per LEI in the file:

- **Existence** — GLEIF returns a record. Failure → **ERROR** finding
  (`LEI-LIVE-NOTFOUND`).
- **Lapsed / retired status** *(opt-in)* — GLEIF marks the record as
  `LAPSED` or `RETIRED` → **WARNING** (`LEI-LIVE-STATUS`).
- **Issuer name match** *(opt-in)* — fund's declared issuer name vs.
  GLEIF legal name, after lenient normalisation (Unicode NFKD,
  diacritics stripped, suffix words like `Inc / Ltd / SA` removed,
  case-insensitive). Mismatch → **WARNING** (`LEI-LIVE-NAME`).
- **Issuer country match** *(opt-in)* — fund's declared issuer country
  vs. GLEIF legal-address country. Mismatch → **WARNING**
  (`LEI-LIVE-COUNTRY`).

Per ISIN in the file:

- **Existence** — OpenFIGI returns a record. Failure → **ERROR**
  (`ISIN-LIVE-NOTFOUND`).
- **Currency match** *(opt-in)* — TPT quotation currency (field 21) vs.
  OpenFIGI currency. Mismatch → **WARNING** (`ISIN-LIVE-CCY`).
- **CIC consistency** *(opt-in)* — semantic alignment of TPT CIC code
  vs. OpenFIGI security type. Mismatch → **WARNING**
  (`ISIN-LIVE-CIC`).

### 7.2 When it runs

External validation runs only if you (a) enable it for the session in
the UI, and (b) — in the web UI — the operator has flipped on the
server-wide switch (`FINDATEX_WEB_EXTERNAL_ENABLED=true`). Local
validation always runs first; external validation runs as a second
phase, can be cancelled, and emits a single **INFO** finding if the
service is unreachable rather than flooding the report with per-row
failures.

### 7.3 Identifier sources

The validator reads identifiers from the columns the spec assigns to
them, per template and version:

- **TPT V7** — ISIN: fields 14, 68 (when type-flag = `"1"`). LEI:
  fields 47/48, 50/51, 81/82, 84/85, 115/116, 119/120, 140/141.
- **TPT V6** — same as V7 minus the custodian LEI columns 140/141.
- **EET** — polymorphic identifier field 23 with type-flag 24 (`"1"` =
  ISIN, `"10"` = LEI). Manufacturer LEI in field 13 with flag in 12.
  EET producer LEI in field 3.
- **EMT** — polymorphic identifier field 9 with type-flag 10. Field 20
  (manufacturer LEI) and field 3 (EMT producer LEI) are alphanum-only
  LEIs.
- **EPT** — polymorphic identifier field 14 with type-flag 15
  (`"1"` = ISIN, `"9"` = LEI in practice). Field 11 (manufacturer LEI)
  is alphanum-only.

### 7.4 Caching

Results are cached per identifier on disk:

- **Default TTL** — 7 days (operator-tunable).
- **Cache key** — the identifier value plus the registry kind (GLEIF /
  OpenFIGI).
- **Persistence** — JSON files in the configured cache directory; the
  cache survives restarts.
- **Visibility** — the JavaFX progress dialog shows live cache hits and
  misses.

This means re-running a validation on the same file the next morning
is essentially free for previously-seen identifiers.

### 7.5 Failure modes

- **Service unreachable / all retries exhausted** → one **INFO** finding
  (`EXTERNAL/SERVICE_UNREACHABLE`) and a status banner. The local
  findings are still authoritative.
- **Per-identifier 404** → handled as a normal "not found" check
  (`LEI-LIVE-NOTFOUND` / `ISIN-LIVE-NOTFOUND`).
- **Per-identifier timeout** → request dropped silently; no finding.
  This is a deliberate trade-off — flooding the report with timeout
  noise is worse than missing a single data point.

### 7.6 Credentials

- **GLEIF** — no key required; rate-limited at the public API tier.
- **OpenFIGI** — works key-less at 4 requests/second; with an
  OpenFIGI API key the rate goes up to 100 requests/second. The key
  can be supplied:
  - **Desktop** — via Settings → External validation → OpenFIGI.
    Stored in `settings.json`.
  - **Web** — operator-default via `FINDATEX_WEB_EXTERNAL_OPENFIGI_KEY`,
    or per-request via the UI form (the per-request key is used only
    for that validation and never stored).

### 7.7 Proxy modes

Many corporate networks force outbound HTTPS through an authenticated
proxy. The desktop app supports three modes:

| Mode      | What it does |
|-----------|--------------|
| **SYSTEM** | Auto-detect from Windows `netsh`, the Registry, `HTTP_PROXY` / `HTTPS_PROXY` env vars, and WPAD. NTLM authentication is transparent. |
| **MANUAL** | You enter host, port, optional non-proxy hosts, username, password. The password is encrypted at rest with a machine-bound AES key. |
| **NONE**   | Direct connection. |

The web app exposes the same modes via the
`FINDATEX_WEB_EXTERNAL_PROXY_*` env vars.

### 7.8 Web-mode default

In the web UI the external pipeline is **off by default**. The operator
must opt in by setting `FINDATEX_WEB_EXTERNAL_ENABLED=true` (and likely
provide proxy creds + an OpenFIGI key) before the per-request toggle in
the UI does anything. This stops a public-facing instance from
accidentally hammering GLEIF / OpenFIGI on every upload.

---

## 8. Reading the Excel report

The exported `.xlsx` has six sheets:

| Sheet | What's on it |
|-------|--------------|
| **Summary**          | Header section: filename, template, version, profile selection, validation timestamp. Followed by a "Findings by severity" breakdown. |
| **Scores**           | The five sub-scores plus the overall, as percentages. |
| **Findings**         | Every finding, one row each. Columns: Severity, Profile, Rule, Fund ID, Fund name, Valuation date, Row, Instrument code, Instrument, Weight, Field#, Field name, Message. Sortable / filterable in Excel. |
| **Field Coverage**   | Per-field stats: Field#, NUM_DATA name, FunDataXML path, count present, count missing, count invalid. Useful for spotting systemic gaps (e.g. "field 47 issuer LEI is missing on every row"). |
| **Per Position**     | Per-row roll-up: Errors / Warnings / Status (PASS / WARN / FAIL). Easy way to find the worst rows in a large file. |
| **Annotated Source** | The original file content, mirrored cell-for-cell. Cells with findings are tinted by severity (red / amber / blue) and carry an Excel comment with the matching findings. The leftmost helper column is labelled **Row**. |

If the source file can't be re-read (deleted, moved, or never persisted —
e.g. a streaming web upload), the Annotated Source sheet contains the
note "Original file no longer available — see the Findings tab for
details."

---

## 9. Sample files

The repo ships per-template scenario fixtures under `samples/`:

```
samples/
├── tpt/   1 clean baseline + ~10 broken variants (presence, format, ISIN, LEI, ~25 cross-field)
├── eet/   clean + broken variants
├── emt/   clean + broken variants
└── ept/   clean + broken variants
```

The `01_clean_*` fixtures should validate to 100% with zero findings.
The numbered broken variants exercise specific failure paths and are
documented in the per-template `samples/<t>/README.md`.

To regenerate them after a spec or rule change:

```bash
python3 tools/build_samples.py        # core test samples
python3 tools/build_examples.py       # samples/tpt/*
python3 tools/build_eet_samples.py    # also _emt_, _ept_
```

---

## 10. FAQ

**Why is field X reported as missing when I provided a value?**
A blank-looking cell often contains whitespace, a stray apostrophe-only
prefix, or an Excel `#N/A`. The validator treats those as missing.
Inspect the Annotated Source sheet — the offending cell is highlighted
with the exact finding message in the comment.

**The same file scores differently under two different profiles. Why?**
Each profile decides which fields are mandatory. A field that's optional
under MiFID Products may be mandatory under SFDR Periodic. The
mandatory-completeness sub-score reflects the union of all selected
profiles.

**Does my data leave my machine?**
- **Desktop app** — no. Files are read locally and the report is written
  locally. The only outbound traffic is the optional GLEIF / OpenFIGI
  lookup, and only for the identifiers in your file (no full-file upload).
- **Hosted web app** — yes, you upload to the operator's server. Files
  are processed in memory and discarded the moment the response is sent.
  Reports are kept in memory for 5 minutes (single-use download URL),
  then deleted. No login, no audit log of file content.
- **Self-hosted Docker** — your operator controls all data. The defaults
  match the hosted-web behaviour above.

**Can I add a new template version?**
Yes — and without writing Java. Drop the spec XLSX into
`core/src/main/resources/spec/<t>/`, author a sibling `*-info.json`
manifest (use `tpt-v7-info.json` as a worked example), and add one
constant to the per-template `*Template.java`. The UI auto-discovers
the new version on next launch. Detailed walk-through in `README.md`.

**What if a GLEIF / OpenFIGI lookup is wrong?**
Both registries have authoritative records but occasional stale data —
particularly around very recent corporate actions or newly-issued
instruments. The external-validation findings are advisory; they do
not block a clean local validation.

**Why doesn't the web app validate SFDR cross-field logic?**
Because that logic is a moving target maintained by regulators, not
FinDatEx. We refused to invent it without an SME on the hook. See
section 5 for the explicit deferred list.

**Where do I get help?**
- Issues with the data file → check the Annotated Source sheet first;
  the comment on each highlighted cell quotes the failed rule.
- Issues with the validator → see `README.md` for the developer guide
  and the issue tracker linked there.
- Issues with the spec itself → contact FinDatEx.
