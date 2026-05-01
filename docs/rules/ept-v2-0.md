# FinDatEx EPT Validation Reference (V2.0)

Spec: `/spec/ept/EPT_V2_0_20220215.xlsx`
Manifest: `/spec/ept/ept-v20-info.json`
Released: 2022-02-15  ·  Sheet: `EPT 2.0 `
Profiles: PRIIPs Sync (`PRIIPS_SYNC`), PRIIPs KID (`PRIIPS_KID`), UCITS KIID (`UCITS_KIID`)

> Generated file — do not edit by hand. Regenerate via `mvn -pl core -Pdocs exec:java -Dexec.args="docs/rules"`.

---

## 1. How this validator scores your file

The overall quality score is a weighted blend of five sub-scores. Only `Severity.ERROR` findings lower the score; `WARNING` and `INFO` are reported but ignored by the scorer.

| Dimension | Weight | Computation |
|---|---|---|
| MANDATORY_COMPLETENESS | 40 % | 1 − (missing M-cells / total M-cells) across active profiles |
| FORMAT_CONFORMANCE | 20 % | 1 − (format errors / non-empty cells) |
| CLOSED_LIST_CONFORMANCE | 15 % | 1 − (closed-list errors / non-empty closed-list cells) |
| CROSS_FIELD_CONSISTENCY | 15 % | 1 − (XF errors / max(distinct XF rules × rows, 1)) |
| PROFILE_COMPLETENESS | 10 % | mean over profiles of (0.7 × M-completeness + 0.3 × C-completeness) |

Findings are routed to dimensions by their rule-id prefix:

| Rule prefix | Dimension |
|---|---|
| `PRESENCE/` | MANDATORY_COMPLETENESS + PROFILE (M leg) |
| `COND_PRESENCE/` | PROFILE_COMPLETENESS (C leg) |
| `FORMAT/` (closed-list message) | CLOSED_LIST_CONFORMANCE |
| `FORMAT/` (other) | FORMAT_CONFORMANCE |
| `ISIN/`, `LEI/` | FORMAT_CONFORMANCE |
| `XF-…`, template `*-XF-*` | CROSS_FIELD_CONSISTENCY |
| `*-ONLINE` (GLEIF / OpenFIGI) | not scored (advisory) |

## 2. Profiles

Selecting a profile in the UI tells the validator to enforce the M (mandatory) and C (conditional) flags from that profile's column in the spec. Multiple profiles can be selected simultaneously — a field is mandatory if any selected profile says so.

| Code | Display name | Mandatory fields | Conditional fields |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | 0 | 0 |
| `PRIIPS_KID` | PRIIPs KID | 55 | 34 |
| `UCITS_KIID` | UCITS KIID | 6 | 1 |

## 3. General rules

The engines below run on every applicable field/row independently of the template-specific cross-field block in §4.

### Version rule

- **Rule ID:** `EPT-XF-VERSION`
- **Severity:** ERROR (INFO if the version cell is absent)
- **Expected token:** `V20`
- **Trigger:** the version cell of the file does not contain the expected token.
- **Score impact:** Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1).

### Presence engine (`PRESENCE/{numKey}/{profile}`)

- **What it checks:** for every `FieldSpec` flagged M for an active profile and applicable to the row's CIC, the cell value must be non-empty.
- **Severity:** ERROR.
- **Score impact:** Each missing cell lowers MANDATORY_COMPLETENESS (40 %) by 1 / total mandatory slots, and lowers the per-profile PROFILE_COMPLETENESS leg (10 %, M-weighted 0.7).
- **Active rule instances:** 61 (one per field × profile).

### Conditional-presence engine (`COND_PRESENCE/{numKey}/{profile}`)

- **What it checks:** for every `FieldSpec` flagged C for an active profile and whose CIC applicability matches the row's CIC, the cell value must be non-empty.
- **Severity:** WARNING.
- **Score impact:** Each missing cell lowers PROFILE_COMPLETENESS (10 %, C-weighted 0.3) by 1 / total conditional slots. Severity = WARNING — does not affect MANDATORY_COMPLETENESS or FORMAT_CONFORMANCE.
- **Active rule instances:** 35 (one per CIC-restricted field × profile).

### Format engine (`FORMAT/{numKey}`)

- **What it checks:** every populated cell is validated against its codification kind: ISO 4217 currency, ISO 3166-A2 country, ISO 8601 date, NACE, CIC 4-char, alphanumeric length, numeric, closed-list membership.
- **Severity:** ERROR.
- **Score impact:** Each ERROR lowers FORMAT_CONFORMANCE (20 %) by 1 / non-empty cells. Closed-list mismatches (message contains "closed list") instead lower CLOSED_LIST_CONFORMANCE (15 %) by 1 / populated closed-list cells.
- **Active rule instances:** 123 (one per field).

### External validation (opt-in)

- **Off by default.** Operators enable it via the Settings dialog (desktop) or the `FINDATEX_WEB_EXTERNAL_ENABLED` env var (web).
- **ISIN lookup (OpenFIGI):** field `14` (when type field `15` = `1`)
- **LEI lookup (GLEIF):** field `14` (when type field `15` = `9`); field `11` (single-purpose, no type flag)
- **Score impact:** External-validation findings are advisory and do not affect any score dimension.

## 4. Cross-field rules

No cross-field rules are wired for this template/version. Regulatory cross-field logic for EPT is deferred — see `docs/SME_QUESTIONS/ept-cross-field-rules.md` for the open SME briefs.

## 5. Per-field catalog

One entry per `FieldSpec` in spec order. Each entry lists every check that can fire on the field, with the profile scope, severity, trigger condition, and quantified score impact.

### Field 1 — 1

Path: `00001_EPT_Version`
Codification: FREE_TEXT
Applicability: all rows
Definition: This field specifies the version of the template and is used by the recipient to understand the number of fields expected, their labelling and order.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/1/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/1` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 2 — 2

Path: `00002_EPT_Producer_Name`
Codification: ALPHANUMERIC (max 255)
Applicability: all rows
Definition: If the manufacturer has outsourced the production of the EPT to another party responsible for the production and publication of the EPT data set, the party should be identified in this field.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/2` | (all) | ERROR | Populated cell does not match the codification (ALPHANUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 3 — 3

Path: `00004_EPT_Producer_Email`
Codification: ALPHANUMERIC (max 255)
Applicability: all rows
Definition: Contact point for distributors regarding EPT.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/3` | (all) | ERROR | Populated cell does not match the codification (ALPHANUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 4 — 4

Path: `00005_File_Generation_Date_And_Time`
Codification: DATETIME
Applicability: all rows
Definition: Date and time of the creation of the EPT file.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/4/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/4` | (all) | ERROR | Populated cell does not match the codification (DATETIME) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 5 — 5

Path: `00006_EPT_Data_Reporting_Narratives`
Codification: FREE_TEXT
Applicability: all rows
Definition: Specifies if the Narratives section has been completed in the current EPT.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/5/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/5` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 6 — 6

Path: `00007_EPT_Data_Reporting_Costs`
Codification: FREE_TEXT
Applicability: all rows
Definition: Specifies if the Costs section has been completed in the current EPT.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/6/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/6` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 7 — 7

Path: `00008_EPT_Data_Reporting_Additional_Requirements_German_MOPs`
Codification: FREE_TEXT
Applicability: all rows
Definition: Specifies if the section "Additional information required for German MOPs" has been completed in the current EPT.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/7/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/7` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 8 — 8

Path: `00009_EPT_Additional_Information_Structured_Products`
Codification: FREE_TEXT
Applicability: all rows
Definition: Specifies if the section "Additional information required for structured PRIIPs" has been completed in the current EPT posting.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/8/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/8` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 9 — 9

Path: `00010_Portfolio_Manufacturer_Name`
Codification: ALPHANUMERIC (max 255)
Applicability: all rows
Definition: Name of the management company of the UCITS or AIF or the manufacturer of the structured product.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/9/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `COND_PRESENCE/9/UCITS_KIID` | UCITS KIID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/9` | (all) | ERROR | Populated cell does not match the codification (ALPHANUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 10 — 10

Path: `00015_Portfolio_Manufacturer_Group_Name`
Codification: ALPHANUMERIC (max 255)
Applicability: all rows
Definition: Name of the group the PRIIPs manufacturer belongs to

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/10/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/10` | (all) | ERROR | Populated cell does not match the codification (ALPHANUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 11 — 11

Path: `00016_Portfolio_Manufacturer_LEI`
Codification: ALPHANUMERIC (max 20)
Applicability: all rows
Definition: Legal Entity Identifier, LEI of the manufacturer of the portfolio

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/11` | (all) | ERROR | Populated cell does not match the codification (ALPHANUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |

**External validation:** GLEIF LEI lookup

---

### Field 12 — 12

Path: `00017_Portfolio_Manufacturer_Email`
Codification: ALPHANUMERIC (max 255)
Applicability: all rows
Definition: Contact point for communication with the manufacturer to either provide feedback reporting or to retrieve details on how to provide feedback reporting.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/12` | (all) | ERROR | Populated cell does not match the codification (ALPHANUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 13 — 13

Path: `00020_Portfolio_Guarantor_Name`
Codification: ALPHANUMERIC (max 255)
Applicability: all rows
Definition: Name of guarantor of the financial instrument. i.e. the entity to which the end investor has counterparty risk

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/13` | (all) | ERROR | Populated cell does not match the codification (ALPHANUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 14 — 14

Path: `00030_Portfolio_Identifying_Data`
Codification: FREE_TEXT
Applicability: all rows
Definition: Identification of the fund or share class or segregated account

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/14/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/14/UCITS_KIID` | UCITS KIID | ERROR | Cell is empty for an active row of profile UCITS KIID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/14` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |

**External validation:** GLEIF LEI lookup (active when field `15` = `9`), OpenFIGI ISIN lookup (active when field `15` = `1`)

---

### Field 15 — 15

Path: `00040_Type_Of_Identification_Code_For_The_Fund_Share_Or_Portfolio`
Codification: CLOSED_LIST, closed list of 10 entries
Applicability: all rows
Definition: Codification chosen to identify the share of the CIS

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/15/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/15/UCITS_KIID` | UCITS KIID | ERROR | Cell is empty for an active row of profile UCITS KIID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/15` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 16 — 16

Path: `00050_Portfolio_Name`
Codification: ALPHANUMERIC (max 255)
Applicability: all rows
Definition: Name of the portfolio or name of the CIS

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/16/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/16/UCITS_KIID` | UCITS KIID | ERROR | Cell is empty for an active row of profile UCITS KIID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/16` | (all) | ERROR | Populated cell does not match the codification (ALPHANUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 17 — 17

Path: `00060_Portfolio_Or_Share_Class_Currency`
Codification: ISO_4217
Applicability: all rows
Definition: Denomination currency of the share class in case the product has multiple share classes. Denomination currency of the product or portfolio in the other cases.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/17/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/17/UCITS_KIID` | UCITS KIID | ERROR | Cell is empty for an active row of profile UCITS KIID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/17` | (all) | ERROR | Populated cell does not match the codification (ISO_4217) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 18 — 18

Path: `00070_PRIIPs_KID_Publication_Date`
Codification: DATE
Applicability: all rows
Definition: Date of the latest PRIIPs KID produced for the portfolio or share class.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/18/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/18/UCITS_KIID` | UCITS KIID | ERROR | Cell is empty for an active row of profile UCITS KIID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/18` | (all) | ERROR | Populated cell does not match the codification (DATE) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 18a — 18a

Path: `00075_PRIIPs_KID_Web_Address`
Codification: FREE_TEXT
Applicability: all rows
Definition: Direct link to the PRIIPs KID

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/18a` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 19 — 19

Path: `00080_Portfolio_PRIIPs_Category`
Codification: FREE_TEXT
Applicability: all rows
Definition: PRIIPs category of the portfolio

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/19/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/19` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 20 — 20

Path: `00090_Fund_CIC_Code`
Codification: FREE_TEXT
Applicability: all rows
Definition: CIC code - fund (4 digits)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/20` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 21 — 21

Path: `00110_Is_An_Autocallable_Product`
Codification: FREE_TEXT
Applicability: all rows
Definition: Indication of whether the product is an autocallable

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/21/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/21` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 22 — 22

Path: `00120_Reference_Language`
Codification: FREE_TEXT
Applicability: all rows
Definition: Language in which the linked website with past performance, the historical performance (02190_Past_Performance_Link and 02200_Previous_Performance_Scenarios_Calculation_Link) and all narratives/texts of this set of data are written

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/22/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/22` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 23 — 23

Path: `01010_Valuation_Frequency`
Codification: CLOSED_LIST, closed list of 9 entries
Applicability: all rows
Definition: Number of valuation days per year for the portfolio or fund or share class.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/23` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 24 — 24

Path: `01020_Portfolio_VEV_Reference`
Codification: NUMERIC
Applicability: all rows
Definition: VEV of the portfolio/ share class

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/24` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 25 — 25

Path: `01030_IS_Flexible`
Codification: FREE_TEXT
Applicability: all rows
Definition: Indicator to alert if the portfolio is flexible. If the annex II number 14 of the PRIIPs RTS applies

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/25` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 26 — 26

Path: `01040_Flex_VEV_Historical`
Codification: NUMERIC
Applicability: all rows
Definition: VaR equivalent volatility of the portfolio

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/26` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 27 — 27

Path: `01050_Flex_VEV_Ref_Asset_Allocation`
Codification: NUMERIC
Applicability: all rows
Definition: VaR equivalent volatility of the reference asset allocation of the portfolio

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/27` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 28 — 28

Path: `01060_IS_Risk_Limit_Relevant`
Codification: FREE_TEXT
Applicability: all rows
Definition: Indicator to alert if there is a relevant risk limit for flexible funds

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/28` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 29 — 29

Path: `01070_Flex_VEV_Risk_Limit`
Codification: NUMERIC
Applicability: all rows
Definition: VaR equivalent volatility of the risk limit of the portfolio

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/29` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 30 — 30

Path: `01080_Existing_Credit_Risk`
Codification: FREE_TEXT
Applicability: all rows
Definition: Indicator to alert if there is a credit risk

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/30/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/30/UCITS_KIID` | UCITS KIID | ERROR | Cell is empty for an active row of profile UCITS KIID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/30` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 31 — 31

Path: `01090_SRI`
Codification: NUMERIC
Applicability: all rows
Definition: Summary risk indicator of the portfolio

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/31/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/31` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 32 — 32

Path: `01095_IS_SRI_Adjusted`
Codification: FREE_TEXT
Applicability: all rows
Definition: Whether or not the manufacturer has manually increased the SRI

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/32/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/32` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 33 — 33

Path: `01100_MRM`
Codification: NUMERIC
Applicability: all rows
Definition: Market risk measure of the portfolio

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/33/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/33` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 34 — 34

Path: `01110_CRM`
Codification: NUMERIC
Applicability: all rows
Definition: Credit risk measure of the fund or the portfolio

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/34/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/34` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 35 — 35

Path: `01120_Recommended_Holding_Period`
Codification: FREE_TEXT
Applicability: all rows
Definition: Recommended holding period of the portfolio

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/35/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/35` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 36 — 36

Path: `01125_Has_A_Contractual_Maturity_Date`
Codification: FREE_TEXT
Applicability: all rows
Definition: Indicates the existence of a contractual maturity date of the portfolio

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/36/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/36` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 37 — 37

Path: `01130_Maturity_Date`
Codification: DATE
Applicability: all rows
Definition: Date of maturity

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/37/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/37` | (all) | ERROR | Populated cell does not match the codification (DATE) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 38 — 38

Path: `01140_Liquidity_Risk`
Codification: CLOSED_LIST, closed list of 3 entries
Applicability: all rows
Definition: Risk of liquidity at the level of the fund or the portfolio, also used for narrative M = material liquidity risk, I = illiquid, L = no liquidity issue.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/38/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/38` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 39 — 39

Path: `02010_Portfolio_Return_Unfavourable_Scenario_1_Year`
Codification: NUMERIC
Applicability: all rows
Definition: Average annual return of the portfolio, fund, share class corresponding to the unfavourable scenario, after one year

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/39/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/39` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 40 — 40

Path: `02020_Portfolio_Return_Unfavourable_Scenario_Half_RHP`
Codification: NUMERIC
Applicability: all rows
Definition: Average annual return of the portfolio, fund, share class corresponding to the unfavourable scenario, after half of the RHP

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/40/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/40` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 41 — 41

Path: `02030_Portfolio_Return_Unfavourable_Scenario_RHP_Or_First_Call_Date`
Codification: NUMERIC
Applicability: all rows
Definition: Average annual return of the portfolio, fund, share class corresponding to the unfavourable scenario, at the RHP

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/41/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/41` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 42 — 42

Path: `02032_Autocall_Applied_Unfavourable_Scenario`
Codification: FREE_TEXT
Applicability: all rows
Definition: Indicate if the call has been applied in the unfavourable scenario

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/42/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/42` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 43 — 43

Path: `02035_Autocall_Date_Unfavourable_Scenario`
Codification: DATE
Applicability: all rows
Definition: Call date applied in the unfavourable scenario

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/43/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/43` | (all) | ERROR | Populated cell does not match the codification (DATE) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 44 — 44

Path: `02040_Portfolio_Return_Moderate_Scenario_1_Year`
Codification: NUMERIC
Applicability: all rows
Definition: Return of the portfolio, fund, share class corresponding to the moderate scenario, after 1 year

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/44/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/44` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 45 — 45

Path: `02050_Portfolio_Return_Moderate_Scenario_Half_RHP`
Codification: NUMERIC
Applicability: all rows
Definition: Average annual return of the portfolio, fund, share class corresponding to the moderate scenario, after half of the RHP

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/45/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/45` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 46 — 46

Path: `02060_Portfolio_Return_Moderate_Scenario_RHP_Or_First_Call_Date`
Codification: NUMERIC
Applicability: all rows
Definition: Average annual return of the portfolio, fund, share class corresponding to the moderate scenario, at the RHP

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/46/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/46` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 47 — 47

Path: `02062_Autocall_Applied_Moderate_Scenario`
Codification: FREE_TEXT
Applicability: all rows
Definition: Indicate if the call has been applied in the moderate scenario

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/47/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/47` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 48 — 48

Path: `02065_Autocall_Date_Moderate_Scenario`
Codification: DATE
Applicability: all rows
Definition: Call date applied in the moderate scenario

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/48/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/48` | (all) | ERROR | Populated cell does not match the codification (DATE) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 49 — 49

Path: `02070_Portfolio_Return_Favourable_Scenario_1_Year`
Codification: NUMERIC
Applicability: all rows
Definition: Annual return of the portfolio, fund, share class corresponding to the favourable scenario, after 1 year

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/49/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/49` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 50 — 50

Path: `02080_Portfolio_Return_Favourable_Scenario_Half_RHP`
Codification: NUMERIC
Applicability: all rows
Definition: Average annual return of the portfolio, fund, share class corresponding to the favourable scenario, after half of the RHP

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/50/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/50` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 51 — 51

Path: `02090_Portfolio_Return_Favourable_Scenario_RHP_Or_First_Call_Date`
Codification: NUMERIC
Applicability: all rows
Definition: Average annual return of the portfolio, fund, share class corresponding to the favourable scenario, at the RHP

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/51/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/51` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 52 — 52

Path: `02092_Autocall_Applied_Favourable_Scenario`
Codification: FREE_TEXT
Applicability: all rows
Definition: Indicate if the call has been applied in the favourable scenario

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/52/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/52` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 53 — 53

Path: `02095_Autocall_Date_Favourable_Scenario`
Codification: DATE
Applicability: all rows
Definition: Call date applied in the favourable scenario

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/53/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/53` | (all) | ERROR | Populated cell does not match the codification (DATE) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 54 — 54

Path: `02100_Portfolio_Return_Stress_Scenario_1_Year`
Codification: NUMERIC
Applicability: all rows
Definition: Annual return of the portfolio, fund, share class corresponding to the stress scenario, after 1 year

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/54/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/54` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 55 — 55

Path: `02110_Portfolio_Return_Stress_Scenario_Half_RHP`
Codification: NUMERIC
Applicability: all rows
Definition: Average annual return of the portfolio, fund, share class corresponding to the stress scenario, after half of the RHP

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/55/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/55` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 56 — 56

Path: `02120_Portfolio_Return_Stress_Scenario_RHP_Or_First_Call_Date`
Codification: NUMERIC
Applicability: all rows
Definition: Average annual return of the portfolio, fund, share class corresponding to the stress scenario, at the RHP

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/56/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/56` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 57 — 57

Path: `02122_Autocall_Applied_Stress_Scenario`
Codification: FREE_TEXT
Applicability: all rows
Definition: Indicate if the call has been applied

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/57/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/57` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 58 — 58

Path: `02125_Autocall_Date_Stress_Scenario`
Codification: DATE
Applicability: all rows
Definition: Call date corresponding to the stress scenario of autocallables

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/58/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/58` | (all) | ERROR | Populated cell does not match the codification (DATE) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 59 — 59

Path: `02130_Portfolio_Number_Of_Observed_Return_M0`
Codification: NUMERIC
Applicability: all rows
Definition: See PRIIPs Regulation

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/59` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 60 — 60

Path: `02140_Portfolio_Mean_Observed_Returns_M1`
Codification: NUMERIC
Applicability: all rows
Definition: See PRIIPs Regulation

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/60` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 61 — 61

Path: `02150_Portfolio_Observed_Sigma`
Codification: NUMERIC
Applicability: all rows
Definition: See PRIIPs Regulation

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/61` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 62 — 62

Path: `02160_Portfolio_Observed_Skewness`
Codification: NUMERIC
Applicability: all rows
Definition: See PRIIPs Regulation

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/62` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 63 — 63

Path: `02170_Portfolio_Observed_Excess_Kurtosis`
Codification: NUMERIC
Applicability: all rows
Definition: See PRIIPs Regulation

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/63` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 64 — 64

Path: `02180_Portfolio_Observed_Stressed_Volatility`
Codification: NUMERIC
Applicability: all rows
Definition: See PRIIPs Regulation

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/64` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 65 — 65

Path: `02185_Portfolio_Past_Performance_Disclosure_Required`
Codification: FREE_TEXT
Applicability: all rows
Definition: Does this product fulfil conditions sets in Annex VIII number 1 (a) and (b) ?

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/65/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/65` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 66 — 66

Path: `02190_Past_Performance_Link`
Codification: FREE_TEXT
Applicability: all rows
Definition: Reference Art. 8(3) RTS

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/66/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/66` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 67 — 67

Path: `02200_Previous_Performance_Scenarios_Calculation_Link`
Codification: FREE_TEXT
Applicability: all rows
Definition: Link to the previous calculations

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/67/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/67` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 68 — 68

Path: `02210_Past_Performance_Number_Of_Years`
Codification: NUMERIC
Applicability: all rows
Definition: Number of years for which past performance is presented

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/68/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/68` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 69 — 69

Path: `02220_Reference_Invested_Amount`
Codification: FREE_TEXT
Applicability: all rows
Definition: Reference Invested amount used to present performance and costs

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/69/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/69` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 70 — 70

Path: `03010_One_Off_Cost_Portfolio_Entry_Cost`
Codification: NUMERIC
Applicability: all rows
Definition: Subscription fees not acquired to the fund or the share class or portfolio mandate. Expressed as a % of the amount to be invested

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/70/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/70` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 71 — 71

Path: `03015_One_Off_Cost_Portfolio_Entry_Cost_Acquired`
Codification: NUMERIC
Applicability: all rows
Definition: Subscription fees acquired to the fund or the share class or portfolio mandate. Expressed as a % of the amount to be invested

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/71/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/71` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 72 — 72

Path: `03020_One_Off_Costs_Portfolio_Exit_Cost_At_RHP`
Codification: NUMERIC
Applicability: all rows
Definition: Exit fees at the end of RHP for the portfolio or fund or share class. It is expressed as a % of net asset value.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/72/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/72` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 73 — 73

Path: `03030_One_Off_Costs_Portfolio_Exit_Cost_At_1_Year`
Codification: NUMERIC
Applicability: all rows
Definition: Exit fees after one year for the portfolio or fund or share class. It is expressed as a % of net asset value.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/73/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/73/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/73` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 73 — 73

Path: `03040_One_Off_Costs_Portfolio_Exit_Cost_At_Half_RHP`
Codification: NUMERIC
Applicability: all rows
Definition: Exit fees after half of the RHP for the portfolio or fund or share class. It is expressed as a % of net asset value.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/73/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/73/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/73` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 74 — 74

Path: `03050_One_Off_Costs_Portfolio_Sliding_Exit_Cost_Indicator`
Codification: FREE_TEXT
Applicability: all rows
Definition: Indicates whether there is a sliding exit cost or not

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/74/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/74` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 75 — 75

Path: `03060_Ongoing_Costs_Management_Fees_And_Other_Administrative_Or_Operating_Costs`
Codification: NUMERIC
Applicability: all rows
Definition: See PRIIPs definition as a % of NAV of the portfolio, the funds or the share class / per annum. Management fees and other administrative or operating costs

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/75/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/75` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 76 — 76

Path: `03080_Ongoing_Costs_Portfolio_Transaction_Costs`
Codification: NUMERIC
Applicability: all rows
Definition: See PRIIPs definition as a % of NAV of the portfolio, the funds or the share class / per annum.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/76/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/76` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 77 — 77

Path: `03090_Existing_Incidental_Costs_Portfolio`
Codification: FREE_TEXT
Applicability: all rows
Definition: Indicates whether there are existing performance fees or carried interest

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/77/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/77` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 78 — 78

Path: `03095_Incidental_Costs`
Codification: NUMERIC
Applicability: all rows
Definition: See PRIIPs definition as a % of NAV of the portfolio, the funds or the share class / per annum

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/78/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/78` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 79 — 79

Path: `04020_Comprehension_Alert_Portfolio`
Codification: FREE_TEXT
Applicability: all rows
Definition: cf Art.14a + annex 1

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/79/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/79` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 80 — 80

Path: `04030_Intended_Target_Market_Retail_Investor_Portfolio`
Codification: FREE_TEXT
Applicability: all rows
Definition: Text in reference language, as proposed by the asset manager The description of the type of retail investor to whom the PRIIP is intended to be marketed in the section entitled ‘What is this product?’ of the key information document shall include information on the target retail investors identified by the PRIIP manufacturer, in particular depending on the needs, characteristics and objectives of the type of client for whom the PRIIPs is compatible. This determination shall be based upon the ability of retail investors to bear investment loss and their investment horizon preferences, their theoretical knowledge of, and past experience with PRIIPs, the financial markets as well as the needs, characteristics and objectives of potential end clients.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/80/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/80` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 81 — 81

Path: `04040_Investment_Objective_Portfolio`
Codification: FREE_TEXT
Applicability: all rows
Definition: Text in reference language, as proposed by the asset manager Information stating the objectives of the PRIIP and the means for achieving those objectives in the section entitled ‘What is this product?’ of the key information document shall be summarised in a brief, clear and easily understandable manner. That information shall identify the main factors upon which return depends, the underlying investment assets or reference values, and how the return is determined, as well as the relationship between the PRIIP’s return and that of the underlying investment assets or reference values.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/81/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/81` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 82 — 82

Path: `04050_Risk_Narrative_Portfolio`
Codification: FREE_TEXT
Applicability: all rows
Definition: Text in reference language, as proposed by the asset manager [insert a brief explanation of the classification of the product with a maximum of 300 characters in plain language] The field shall contain well-formulated text which can be used directly by the insurer in the KID according to article 14 of the regulation

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/82` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 83 — 83

Path: `04060_Other_Materially_Relevant_Risk_Narrative_Portfolio`
Codification: FREE_TEXT
Applicability: all rows
Definition: Text in reference language, as proposed by the asset managers : (Element E) [Where applicable: element h] [Other risks materially relevant to the PRIIP not included in the summary risk indicator to be explained with a maximum of 200 characters] The field shall contain well-formulated text which can be used directly by the insurer in the KID according to article 14 of the regulation.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/83/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/83` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 84 — 84

Path: `04070_Type_Of_Underlying_Investment_Option`
Codification: FREE_TEXT
Applicability: all rows
Definition: understandable to the customer

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/84/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/84` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 85 — 85

Path: `04080_Capital_Guarantee`
Codification: FREE_TEXT
Applicability: all rows
Definition: Boolean to identify whether the portfolio has a general capital guarantee or not.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/85/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/85` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 86 — 86

Path: `04081_Capital_Guarantee_Level`
Codification: NUMERIC
Applicability: all rows
Definition: Capital Guarantee level. Minimum amount will be paid at redemption in%. Cf annex 3, point 7, Element F

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/86/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/86` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 87 — 87

Path: `04082_Capital_Guarantee_Limitations`
Codification: FREE_TEXT
Applicability: all rows
Definition: [insert a brief explanation of the guarantee limits of the product with a maximum of 300 characters in plain language] The field shall contain well-formulated text which can be used directly by the insurer in the KID according to article 14 of the regulation.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/87/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/87` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 88 — 88

Path: `04083_Capital_Guarantee_Early_Exit_Conditions`
Codification: DATE
Applicability: all rows
Definition: Date before which the early exit conditions apply.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/88/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/88` | (all) | ERROR | Populated cell does not match the codification (DATE) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 89 — 89

Path: `04084_Capital_Guarantee_Portfolio`
Codification: FREE_TEXT
Applicability: all rows
Definition: characteristics of the guarantee: open ended or fixed maturity, daily or monthly lockin, monthly reset, constant guarantee, reference value (highest NAV, NAV of start period,…), other particularities, name of the guarantor

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/89/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/89` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 90 — 90

Path: `04085_Possible_Maximum_Loss_Portfolio`
Codification: NUMERIC
Applicability: all rows

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/90` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 91 — 91

Path: `04086_Description_Past_Interval_Unfavourable_Scenario`
Codification: FREE_TEXT
Applicability: all rows
Definition: Describes the historical time period the unfavourable scenario corresponds to

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/91/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/91` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 92 — 92

Path: `04087_Description_Past_Interval_Moderate_Scenario`
Codification: FREE_TEXT
Applicability: all rows
Definition: Describes the historical time period the moderate scenario corresponds to

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/92/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/92` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 93 — 93

Path: `04088_Description_Past_Interval_Favourable_Scenario`
Codification: FREE_TEXT
Applicability: all rows
Definition: Describes the historical time period the favourable scenario corresponds to

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/93/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/93` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 94 — 94

Path: `04089_Was_Benchmark_Used_Performance_Calculation`
Codification: FREE_TEXT
Applicability: all rows
Definition: Boolean to identify whether a benchmark or proxy was used for performance calculation in the unfavourable, moderate and favourable scenarios.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/94/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/94` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 95 — 95

Path: `04090_Portfolio_Performance_Fees_Carried_Interest_Narrative`
Codification: FREE_TEXT
Applicability: all rows
Definition: Describes the incidental costs taken under specific conditions.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/95/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/95` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 96 — 96

Path: `04120_One_Off_Cost_Portfolio_Entry_Cost_Description`
Codification: FREE_TEXT
Applicability: all rows
Definition: Description of the entry cost, not more than 300 characters.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/96` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 97 — 97

Path: `04130_One_Off_Cost_Portfolio_Exit_Cost_Description`
Codification: FREE_TEXT
Applicability: all rows
Definition: Description of exit cost, not more than 300 characters.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/97/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/97` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 98 — 98

Path: `04140_Ongoing_Costs_Portfolio_Management_Costs_Description`
Codification: FREE_TEXT
Applicability: all rows
Definition: Description of the ongoing cost, not more than 150 characters.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/98/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/98` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 99 — 99

Path: `04150_Do_Costs_Depend_On_Invested_Amount`
Codification: FREE_TEXT
Applicability: all rows
Definition: Indicates whether the costs depend on the invested amount.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/99/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/99` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 100 — 100

Path: `04160_Cost_Dependence_Explanation`
Codification: FREE_TEXT
Applicability: all rows
Definition: Describes the dependence of costs on the invested amount, not more than 300 characters.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/100/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/100` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 101 — 101

Path: `06005_German_MOPs_Reference_Date`
Codification: DATE
Applicability: all rows
Definition: Last calculation date of the additional information required in Germany.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/101/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/101` | (all) | ERROR | Populated cell does not match the codification (DATE) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 102 — 102

Path: `06010_Bonds_Weight`
Codification: NUMERIC
Applicability: all rows
Definition: Proportion (weight) of bonds and bonds futures within the fund/portfolio measured in percentage of market value.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/102/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/102` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 103 — 103

Path: `06020_Annualized_Return_Volatility`
Codification: NUMERIC
Applicability: all rows
Definition: Average annualised daily volatility of the fund / portfolio over the last 5 years.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/103/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/103` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 104 — 104

Path: `06030_Duration_Bonds`
Codification: NUMERIC
Applicability: all rows
Definition: Valuation Weighted Macaulay-Duration in years of the fund / portfolio

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/104/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/104` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 105 — 105

Path: `06040_Existing_Capital_Preservation`
Codification: FREE_TEXT
Applicability: all rows
Definition: Identifies if a capital preservation method is used (Y) or not (N).

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/105/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/105` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 106 — 106

Path: `06050_Capital_Preservation_Level`
Codification: NUMERIC
Applicability: all rows
Definition: 100% minus the maximum possible loss in percentage of its market value.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/106/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/106` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 107 — 107

Path: `06060_Time_Interval_Maximum_Loss`
Codification: DATE, closed list of 9 entries
Applicability: all rows
Definition: The time period in which a possible loss of a capital preservation funds is measured.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/107/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/107` | (all) | ERROR | Populated cell does not match the codification (DATE) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 108 — 108

Path: `06070_Uses_PI`
Codification: FREE_TEXT
Applicability: all rows
Definition: Identifies if PI (Portfolio Insurance including CPPI Constant Proportion Portfolio Insurance) is used (Y) or not (N) for capital preservation.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | M | Mandatory — must always be present. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/108/PRIIPS_KID` | PRIIPs KID | ERROR | Cell is empty for an active row of profile PRIIPs KID | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/108` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 109 — 109

Path: `06080_Multiplier_PI`
Codification: NUMERIC
Applicability: all rows
Definition: Gives the maximum multiplier value if PI algorithm is used.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/109/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/109` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 110 — 110

Path: `07005_First_Possible_Call_Date`
Codification: DATE
Applicability: all rows
Definition: Date of the first possible call for autocallable products

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | C | Conditional — required when the spec's applicability/condition holds. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/110/PRIIPS_KID` | PRIIPs KID | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/110` | (all) | ERROR | Populated cell does not match the codification (DATE) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 111 — 111

Path: `07010_Total_Cost_1_Year_Or_First_Call`
Codification: NUMERIC
Applicability: all rows
Definition: Total cost in 00060_Portfolio_Or_Share_Class_Currency terms in case the investor cashes in after one year, as requested in the "Costs over time" table. Rebased to 1.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/111` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 112 — 112

Path: `07020_RIY_1_Year_Or_First_Call`
Codification: NUMERIC
Applicability: all rows
Definition: RIY in case the investor cashes in after one year, as requested in the "Costs over time" table.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/112` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 113 — 113

Path: `07030_Total_Cost_Half_RHP`
Codification: NUMERIC
Applicability: all rows
Definition: Total cost in 00060_Portfolio_Or_Share_Class_Currency terms in case the investor cashes in at the middle of the RHP, as requested in the "Costs over time" table.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/113` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 114 — 114

Path: `07040_RIY_Half_RHP`
Codification: NUMERIC
Applicability: all rows
Definition: RIY in case the investor cashes in at the middle of the RHP, as requested in the "Costs over time" table.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/114` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 115 — 115

Path: `07050_Total_Cost_RHP`
Codification: NUMERIC
Applicability: all rows
Definition: Total cost in 00060_Portfolio_Or_Share_Class_Currency terms in case the investor cashes in at the RHP, as requested in the "Costs over time" table.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/115` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 116 — 116

Path: `07060_RIY_RHP`
Codification: NUMERIC
Applicability: all rows
Definition: RIY in case the investor cashes in at the RHP, as requested in the "Costs over time" table.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/116` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 117 — 117

Path: `07070_One_Off_Costs_Portfolio_Entry_Cost`
Codification: NUMERIC
Applicability: all rows
Definition: The entry cost at one year (or at RHP if RHP<1y), as requested in the narrative part of the "Composition of costs" table.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/117` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 118 — 118

Path: `07080_One_Off_Costs_Portfolio_Exit_Cost`
Codification: UNKNOWN
Applicability: all rows
Definition: The exit cost at one year (or at RHP if RHP<1y), as requested in the narrative part of the "Composition of costs" table.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/118` | (all) | ERROR | Populated cell does not match the codification (UNKNOWN) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 119 — 119

Path: `07090_Ongoing_Costs_Portfolio_Transaction_Costs`
Codification: NUMERIC
Applicability: all rows
Definition: The portfolio transaction costs at one year (or at RHP if RHP<1y), as requested in the narrative part of the "Composition of costs" table.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/119` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 120 — 120

Path: `07100_Ongoing_Costs_Management_Fees_And_Other_Administrative_Or_Operating_Costs`
Codification: UNKNOWN
Applicability: all rows
Definition: The other ongoing costs term at one year (or at RHP if RHP<1y), as requested in the narrative part of the "Composition of costs" table

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/120` | (all) | ERROR | Populated cell does not match the codification (UNKNOWN) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 121 — 121

Path: `07110_Incidental_Costs_Portfolio_Performance_Fees_Carried _Interest`
Codification: NUMERIC
Applicability: all rows
Definition: The performance fees, as requested in the "Composition of costs" table.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `PRIIPS_SYNC` | PRIIPs Sync | — | Profile column not present in this version. |
| `PRIIPS_KID` | PRIIPs KID | O | Optional — populate when applicable. |
| `UCITS_KIID` | UCITS KIID | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/121` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

