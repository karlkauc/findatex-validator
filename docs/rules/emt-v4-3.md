# FinDatEx EMT Validation Reference (V4.3)

Spec: `/spec/emt/EMT_V4_3_20251217.xlsx`
Manifest: `/spec/emt/emt-v43-info.json`
Released: 2025-12-17  ·  Sheet: `EMT V4.3`
Profiles: EMT (Mandatory) (`EMT_BASE`)

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
| `EMT_BASE` | EMT (Mandatory) | 40 | 33 |

## 3. General rules

The engines below run on every applicable field/row independently of the template-specific cross-field block in §4.

### Version rule

- **Rule ID:** `EMT-XF-VERSION`
- **Severity:** ERROR (INFO if the version cell is absent)
- **Expected token:** `V4.3`
- **Trigger:** the version cell of the file does not contain the expected token.
- **Score impact:** Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1).

### Presence engine (`PRESENCE/{numKey}/{profile}`)

- **What it checks:** for every `FieldSpec` flagged M for an active profile and applicable to the row's CIC, the cell value must be non-empty.
- **Severity:** ERROR.
- **Score impact:** Each missing cell lowers MANDATORY_COMPLETENESS (40 %) by 1 / total mandatory slots, and lowers the per-profile PROFILE_COMPLETENESS leg (10 %, M-weighted 0.7).
- **Active rule instances:** 40 (one per field × profile).

### Conditional-presence engine (`COND_PRESENCE/{numKey}/{profile}`)

- **What it checks:** for every `FieldSpec` flagged C for an active profile and whose CIC applicability matches the row's CIC, the cell value must be non-empty.
- **Severity:** WARNING.
- **Score impact:** Each missing cell lowers PROFILE_COMPLETENESS (10 %, C-weighted 0.3) by 1 / total conditional slots. Severity = WARNING — does not affect MANDATORY_COMPLETENESS or FORMAT_CONFORMANCE.
- **Active rule instances:** 33 (one per CIC-restricted field × profile).

### Format engine (`FORMAT/{numKey}`)

- **What it checks:** every populated cell is validated against its codification kind: ISO 4217 currency, ISO 3166-A2 country, ISO 8601 date, NACE, CIC 4-char, alphanumeric length, numeric, closed-list membership.
- **Severity:** ERROR.
- **Score impact:** Each ERROR lowers FORMAT_CONFORMANCE (20 %) by 1 / non-empty cells. Closed-list mismatches (message contains "closed list") instead lower CLOSED_LIST_CONFORMANCE (15 %) by 1 / populated closed-list cells.
- **Active rule instances:** 114 (one per field).

### External validation (opt-in)

- **Off by default.** Operators enable it via the Settings dialog (desktop) or the `FINDATEX_WEB_EXTERNAL_ENABLED` env var (web).
- **ISIN lookup (OpenFIGI):** field `9` (when type field `10` = `1`)
- **LEI lookup (GLEIF):** field `9` (when type field `10` = `10`); field `20` (single-purpose, no type flag); field `3` (single-purpose, no type flag)
- **Score impact:** External-validation findings are advisory and do not affect any score dimension.

## 4. Cross-field rules

No cross-field rules are wired for this template/version. Regulatory cross-field logic for EMT is deferred — see `docs/SME_QUESTIONS/emt-cross-field-rules.md` for the open SME briefs.

## 5. Per-field catalog

One entry per `FieldSpec` in spec order. Each entry lists every check that can fire on the field, with the profile scope, severity, trigger condition, and quantified score impact.

### Field 1 — 1

Path: `00001_EMT_Version`
Codification: FREE_TEXT
Applicability: all rows
Definition: This field specifies the output version of the template and is used by the recipient to understand the number of fields expected, their labeling and order.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/1/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/1` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 2 — 2

Path: `00002_EMT_Producer_Name`
Codification: UNKNOWN
Applicability: all rows
Definition: If the Manufacturer/Issuer have chosen to outsource the production of an EMT posting to another party responsible for the production and publication of the EMT data set, such party name should be entered in this field.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/2` | (all) | ERROR | Populated cell does not match the codification (UNKNOWN) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 3 — 3

Path: `00003_EMT_Producer_LEI`
Codification: UNKNOWN
Applicability: all rows
Definition: If the Manufacturer/Issuer have chosen to outsource the production of an EMT posting to another party responsible for the production and publication of the EMT data set, such party LEI should be entered in this field.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/3` | (all) | ERROR | Populated cell does not match the codification (UNKNOWN) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |

**External validation:** GLEIF LEI lookup

---

### Field 4 — 4

Path: `00004_EMT_Producer_Email`
Codification: UNKNOWN
Applicability: all rows
Definition: Contact entry point for distributors regarding EMT

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/4` | (all) | ERROR | Populated cell does not match the codification (UNKNOWN) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 5 — 5

Path: `00005_File_Generation_Date_And_Time`
Codification: DATE
Applicability: all rows
Definition: Date and Time of the creation of the EMT file

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/5/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/5` | (all) | ERROR | Populated cell does not match the codification (DATE) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 6 — 6

Path: `00006_EMT_Data_Reporting_Target_Market`
Codification: FREE_TEXT
Applicability: all rows
Definition: Specifies if the Target Market section is filled in the current EMT posting.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/6/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/6` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 7 — 7

Path: `00007_EMT_Data_Reporting_Ex_Ante`
Codification: FREE_TEXT
Applicability: all rows
Definition: Specifies if the Ex-Ante Cost & Charges section is filled in the current EMT posting.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/7/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/7` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 8 — 8

Path: `00008_EMT_Data_Reporting_Ex_Post`
Codification: FREE_TEXT
Applicability: all rows
Definition: Specifies if the Ex-Post Cost & Charges section is filled in the current EMT posting.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/8/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/8` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 9 — 9

Path: `00010_Financial_Instrument_Identifying_Data`
Codification: FREE_TEXT
Applicability: all rows
Definition: Identification of the financial instrument

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/9/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/9` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |

**External validation:** GLEIF LEI lookup (active when field `10` = `10`), OpenFIGI ISIN lookup (active when field `10` = `1`)

---

### Field 10 — 10

Path: `00020_Type_Of_Identification_Code_For_The_Financial_Instrument`
Codification: CLOSED_LIST, closed list of 11 entries
Applicability: all rows
Definition: Codification chosen to identify the financial instrument

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/10/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/10` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 11 — 11

Path: `00030_Financial_Instrument_Name`
Codification: ALPHANUMERIC (max 255)
Applicability: all rows
Definition: Name of the financial instrument

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/11/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/11` | (all) | ERROR | Populated cell does not match the codification (ALPHANUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 12 — 12

Path: `00040_Financial_Instrument_Currency`
Codification: ISO_4217
Applicability: all rows
Definition: Denomination currency of the financial instrument

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/12/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/12` | (all) | ERROR | Populated cell does not match the codification (ISO_4217) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 13 — 13

Path: `00045_Financial_Instrument_Performance_Fee`
Codification: FREE_TEXT
Applicability: all rows
Definition: Does this financial instrument have potential performance fees or carried interest?

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/13/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/13` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 14 — 14

Path: `00047_Financial_Instrument_Distribution_Of_Cash`
Codification: FREE_TEXT
Applicability: all rows
Definition: Does this financial instrument distribute Income in the form of cash to the investor?

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/14/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/14` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 15 — 15

Path: `00050_General_Reference_Date`
Codification: DATE
Applicability: all rows
Definition: Date to which the General data within the EMT refer

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/15/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/15` | (all) | ERROR | Populated cell does not match the codification (DATE) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 16 — 16

Path: `00060_Financial_Instrument_Product_Type`
Codification: FREE_TEXT
Applicability: all rows
Definition: Structured Securities or Structured Funds or UCITS or Non UCITS or UCITS Money Market Funds or Non UCITS Money Market Funds or Exchanged Traded Commodities or Bonds

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/16/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/16` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 17 — 17

Path: `00065_Maturity_Date`
Codification: DATE
Applicability: all rows
Definition: Date of Maturity

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/17/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/17` | (all) | ERROR | Populated cell does not match the codification (DATE) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 18 — 18

Path: `00067_May_Be_Terminated_Early`
Codification: FREE_TEXT
Applicability: all rows
Definition: Yes or No

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/18/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/18` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 19 — 19

Path: `00070_Financial_Instrument_Manufacturer_Name`
Codification: ALPHANUMERIC (max 255)
Applicability: all rows
Definition: Name of Manufacturer of the financial instrument. The one who is responsible for the financial instrument management/issuance

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/19/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/19` | (all) | ERROR | Populated cell does not match the codification (ALPHANUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 20 — 20

Path: `00073_Financial_Instrument_Manufacturer_LEI`
Codification: ALPHANUMERIC (max 255)
Applicability: all rows
Definition: Legal Entity Identifier, LEI of the Manufacturer of the financial instrument

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/20` | (all) | ERROR | Populated cell does not match the codification (ALPHANUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |

**External validation:** GLEIF LEI lookup

---

### Field 21 — 21

Path: `00074_Financial_Instrument_Manufacturer_Email`
Codification: UNKNOWN
Applicability: all rows
Definition: Contact entry point for communication with the Manufacturer to either provide feed back reporting or to retrieve details on how to provide feed back reporting.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/21` | (all) | ERROR | Populated cell does not match the codification (UNKNOWN) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 22 — 22

Path: `00075_Financial_Instrument_Manufacturer_Product_Governance_Process`
Codification: FREE_TEXT
Applicability: all rows
Definition: A = Product governance procedure pursuant to MiFID II B = Product governance procedure comparable to MiFID II C = Product governance procedure not in accordance with MiFID II D = No information is requested from the issuer

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/22` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 23 — 23

Path: `00080_Financial_Instrument_Guarantor_Name`
Codification: ALPHANUMERIC (max 255)
Applicability: all rows
Definition: Name of Guarantor of the financial instrument.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/23` | (all) | ERROR | Populated cell does not match the codification (ALPHANUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 24 — 24

Path: `00085_Financial_Instrument_Type_Notional_Or_Item_Based`
Codification: FREE_TEXT
Applicability: all rows
Definition: N for Notional based instrument, I for Item based instrument

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/24/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/24` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 25 — 25

Path: `00090_Product_Category_Or_Nature_Germany`
Codification: FREE_TEXT
Applicability: all rows
Definition: Designation of the respective product category or nature for Germany

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/25/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/25` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 26 — 26

Path: `00095_Structured_Securities_Product_Category_Or_Nature`
Codification: FREE_TEXT
Applicability: all rows
Definition: Designation of the respective product category or nature. EUSIPA Map/Codes for structured securities (https://eusipa.org/governance/#EusipaDMap)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/26` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 27 — 27

Path: `00096_Structured_Securities_Quotation`
Codification: FREE_TEXT
Applicability: all rows
Definition: Defines if the quotation type in the Ex-Ante and Ex-Post section of the EMT file is in UNITS or in PERCENTAGE related to the specific Reference as presented in field 07150,07155, 08110 and 08120 respectively.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/27/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/27` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 28 — 28

Path: `00100_Leveraged_Financial_Instrument_Or_Contingent_Liability_Instrument`
Codification: FREE_TEXT
Applicability: all rows
Definition: Yes or No

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | I | Informational — populate if available, no enforcement. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/28` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 29 — 29

Path: `00110_Fund_Share_Class_Without_Retrocession`
Codification: FREE_TEXT
Applicability: all rows
Definition: Yes or No

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | I | Informational — populate if available, no enforcement. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/29` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 30 — 30

Path: `00120_Ex_Post_Cost_Calculation_Basis_Italy`
Codification: FREE_TEXT
Applicability: all rows
Definition: Rolling based (last 12 months) or Fixed base (calendar year)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/30/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/30` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 31 — 31

Path: `01000_Target_Market_Reference_Date`
Codification: DATE
Applicability: all rows
Definition: Date to which the Target Market data within the EMT refer

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/31/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/31` | (all) | ERROR | Populated cell does not match the codification (DATE) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 32 — 32

Path: `01010_Investor_Type_Retail`
Codification: FREE_TEXT
Applicability: all rows
Definition: Yes or No or Neutral

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/32/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/32` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 33 — 33

Path: `01020_Investor_Type_Professional`
Codification: FREE_TEXT
Applicability: all rows
Definition: Yes or No or Professional Per Se or Elective Professional

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/33/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/33` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 34 — 34

Path: `01030_Investor_Type_Eligible_Counterparty`
Codification: FREE_TEXT
Applicability: all rows
Definition: Yes or No

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/34/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/34` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 35 — 35

Path: `02010_Basic_Investor`
Codification: FREE_TEXT
Applicability: all rows
Definition: Yes or No or Neutral

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/35/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/35` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 36 — 36

Path: `02020_Informed_Investor`
Codification: FREE_TEXT
Applicability: all rows
Definition: Yes or No or Neutral

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/36/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/36` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 37 — 37

Path: `02030_Advanced_Investor`
Codification: FREE_TEXT
Applicability: all rows
Definition: Yes or No or Neutral

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/37/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/37` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 38 — 38

Path: `02040_Expert_Investor_Germany`
Codification: FREE_TEXT
Applicability: all rows
Definition: Yes or No or Neutral

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/38` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 39 — 39

Path: `03010_Compatible_With_Clients_Who_Can_Not_Bear_Capital_Loss`
Codification: FREE_TEXT
Applicability: all rows
Definition: Investor can bear no loss of capital. Minor losses especially due to costs possible. Yes or No or Neutral

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/39/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/39` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 40 — 40

Path: `03020_Compatible_With_Clients_Who_Can_Bear_Limited_Capital_Loss`
Codification: FREE_TEXT
Applicability: all rows
Definition: Investor seeking to preserve capital or can bear losses limited to a level specified by the product. Assessment of loss level is based on investments in the same currency as the instrument denomination and do not take into consideration potential adverse FX market performance. To be filled only for structured securities & funds with an explicit capital protection or for Money Market funds. Yes or No or Neutral

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/40/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/40` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 41 — 41

Path: `03030_Limited_Capital_Loss_Level`
Codification: NUMERIC
Applicability: all rows
Definition: Loss up to XX%

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/41/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/41` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 42 — 42

Path: `03040_Compatible_With_Clients_Who_Do_Not_Need_Capital_Guarantee`
Codification: FREE_TEXT
Applicability: all rows
Definition: No Capital Guarantee nor protection. 100% capital at risk . Yes or No or Neutral

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/42/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/42` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 43 — 43

Path: `03050_Compatible_With_Clients_Who_Can_Bear_Loss_Beyond_Capital`
Codification: FREE_TEXT
Applicability: all rows
Definition: Loss Beyond the Capital. Yes or No or Neutral

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/43/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/43` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 44 — 44

Path: `04010_Risk_Tolerance_PRIIPS_Methodology`
Codification: CLOSED_LIST, closed list of 1 entries
Applicability: all rows
Definition: SRI

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/44/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/44` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 45 — 45

Path: `04020_Risk_Tolerance_UCITS_Methodology`
Codification: CLOSED_LIST, closed list of 1 entries
Applicability: all rows
Definition: SRRI

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/45/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/45` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 46 — 46

Path: `04030_Risk_Tolerance_Internal_Methodology_For_Non_PRIIPS_And_Non_UCITS`
Codification: FREE_TEXT
Applicability: all rows
Definition: Low/medium/high

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/46/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/46` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 47 — 47

Path: `04040_Risk_Tolerance_For_Non_PRIIPS_And_Non_UCITS_Spain`
Codification: CLOSED_LIST, closed list of 1 entries
Applicability: all rows
Definition: Spanish SRI

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/47` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 48 — 48

Path: `04050_Not_For_Investors_With_The_Lowest_Risk_Tolerance_Germany`
Codification: FREE_TEXT
Applicability: all rows
Definition: Yes or Neutral

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/48` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 49 — 49

Path: `05010_Return_Profile_Client_Looking_For_Preservation`
Codification: FREE_TEXT
Applicability: all rows
Definition: Yes or No or Neutral

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/49/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/49` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 50 — 50

Path: `05020_Return_Profile_Client_Looking_For_Capital_Growth`
Codification: FREE_TEXT
Applicability: all rows
Definition: Yes or No or Neutral

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/50/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/50` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 51 — 51

Path: `05030_Return_Profile_Client_Looking_For_Income`
Codification: FREE_TEXT
Applicability: all rows
Definition: Yes or No or Neutral

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/51/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/51` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 52 — 52

Path: `05040_Return_Profile_Hedging`
Codification: FREE_TEXT
Applicability: all rows
Definition: Yes or No or Neutral

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/52/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/52` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 53 — 53

Path: `05050_Option_Or_Leveraged_Return_Profile`
Codification: FREE_TEXT
Applicability: all rows
Definition: Yes or No or Neutral

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/53` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 54 — 54

Path: `05070_Return_Profile_Pension_Scheme_Germany`
Codification: FREE_TEXT
Applicability: all rows
Definition: Yes or No or Neutral

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/54` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 55 — 55

Path: `05080_Minimum_Recommended_Holding_Period`
Codification: NUMERIC
Applicability: all rows
Definition: Minimum recommending holding period: RHP in years or Very Short Term (<1Y)or Short term (>=1Y) or Medium term (>=3Y) or Long term (>5Y) or Hold To Maturity

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/55/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/55` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 56 — 56

Path: `05105_Does_This_Financial_Instrument_Consider_End_Client_Sustainability_Preferences`
Codification: FREE_TEXT
Applicability: all rows
Definition: Yes or Neutral

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/56/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/56` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 57 — 57

Path: `05115_Other_Specific_Investment_Need`
Codification: FREE_TEXT
Applicability: all rows
Definition: No, Islamic banking or Other

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | I | Informational — populate if available, no enforcement. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/57` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 58 — 58

Path: `06010_Execution_Only`
Codification: FREE_TEXT
Applicability: all rows
Definition: Retail or Professional or Both or Neither

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | I | Informational — populate if available, no enforcement. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/58` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 59 — 59

Path: `06020_Execution_With_Appropriateness_Test_Or_Non_Advised_Services`
Codification: FREE_TEXT
Applicability: all rows
Definition: Retail or Professional or Both or Neither

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | I | Informational — populate if available, no enforcement. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/59` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 60 — 60

Path: `06030_Investment_Advice`
Codification: FREE_TEXT
Applicability: all rows
Definition: Retail or Professional or Both or Neither

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | I | Informational — populate if available, no enforcement. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/60` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 61 — 61

Path: `06040_Portfolio_Management`
Codification: FREE_TEXT
Applicability: all rows
Definition: Retail or Professional or Both or Neither

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | I | Informational — populate if available, no enforcement. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/61` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 62 — 62

Path: `07020_Gross_One-off_Cost_Financial_Instrument_Maximum_Entry_Cost_Non_Acquired`
Codification: CLOSED_LIST, closed list of 1 entries
Applicability: all rows
Definition: Maximum not acquired to the fund. Expressed as a % of the amount to be invested.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/62/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/62` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 63 — 63

Path: `07025_Net_One-off_Cost_Structured_Products_Entry_Cost_Non_Acquired`
Codification: UNKNOWN
Applicability: all rows
Definition: Expressed as a % of the amount to be invested. Subscription NAV - Fair Value

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/63` | (all) | ERROR | Populated cell does not match the codification (UNKNOWN) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 64 — 64

Path: `07030_One-off_Cost_Financial_Instrument_Maximum_Entry_Cost_Fixed_Amount_Italy`
Codification: NUMERIC
Applicability: all rows
Definition: Maximum fixed amount per subscription, not incorporated. Flat fixed fee definied by the manufacturer (Linked to Paying Agent)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/64/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/64` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 65 — 65

Path: `07040_One-off_Cost_Financial_Instrument_Maximum_Entry_Cost_Acquired`
Codification: CLOSED_LIST, closed list of 1 entries
Applicability: all rows
Definition: Subscription fees acquired to the fund Expressed as a % of the amount to be invested

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/65/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/65` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 66 — 66

Path: `07050_One-off_Costs_Financial_Instrument_Maximum_Exit_Cost_Non_Acquired`
Codification: CLOSED_LIST, closed list of 1 entries
Applicability: all rows
Definition: Maximum not acquired to the fund Expressed as a % of the NAV.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/66/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/66` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 67 — 67

Path: `07060_One-off_Costs_Financial_Instrument_Maximum_Exit_Cost_Fixed_Amount_Italy`
Codification: NUMERIC
Applicability: all rows
Definition: Maximum fixed amount per redemption, not incorporated. Flat fee definied by the manufacturer

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/67/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/67` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 68 — 68

Path: `07070_One-off_Costs_Financial_Instrument_Maximum_Exit_Cost_Acquired`
Codification: CLOSED_LIST, closed list of 1 entries
Applicability: all rows
Definition: Maximum Exit fees acquired to the fund Expressed as a % of the NAV

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/68/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/68` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 69 — 69

Path: `07080_One-off_Costs_Financial_Instrument_Typical_Exit_Cost`
Codification: CLOSED_LIST, closed list of 1 entries
Applicability: all rows
Definition: Current exit cost linked to the RHP or Time to Maturity or 1Y (V) or 3Y(S) or 5Y (M L) (the value of 05080_Minimum_Recommended_Holding_Period)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/69` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 70 — 70

Path: `07090_One-off_Cost_Financial_Instrument_Exit_Cost_Structured_Products_Prior_RHP`
Codification: CLOSED_LIST, closed list of 1 entries
Applicability: all rows
Definition: Expressed as a % of the amount to be divested. Fair Value - Exit Value (eg Bid Price)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/70/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/70` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 71 — 71

Path: `07100_Financial_Instrument_Gross_Ongoing_Costs`
Codification: CLOSED_LIST, closed list of 1 entries
Applicability: all rows
Definition: % of NAV of the Financial Product expressed in annualized terms (rate of cost deduction to be applied)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/71/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/71` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 72 — 72

Path: `07105_Financial_Instrument_Borrowing_Costs_Ex_Ante_UK`
Codification: CLOSED_LIST, closed list of 1 entries
Applicability: all rows
Definition: Financing costs related to borrowing for the purposes of gearing expressed as a % of NAV of the Financial Product expressed in annualized terms (rate of cost deduction to be applied)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/72` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 73 — 73

Path: `07110_Financial_Instrument_Management_Fee`
Codification: CLOSED_LIST, closed list of 1 entries
Applicability: all rows
Definition: % of NAV of the Financial Product expressed in annualized terms (rate of cost deduction to be applied)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/73/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/73` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 74 — 74

Path: `07120_Financial_Instrument_Distribution_Fee`
Codification: CLOSED_LIST, closed list of 1 entries
Applicability: all rows
Definition: % of NAV of the Financial Product expressed in annualized terms (rate of cost deduction to be applied)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/74/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/74` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 75 — 75

Path: `07130_Financial_Instrument_Transaction_Costs_Ex_Ante`
Codification: CLOSED_LIST, closed list of 1 entries
Applicability: all rows
Definition: % of NAV of the Financial Product expressed in annualized terms (rate of cost deduction to be applied)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/75/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/75` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 76 — 76

Path: `07140_Financial_Instrument_Incidental_Costs_Ex_Ante`
Codification: CLOSED_LIST, closed list of 1 entries
Applicability: all rows
Definition: % of NAV of the Financial Product expressed in annualized terms (rate of cost deduction to be applied). Includes Performance Fees and other costs.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/76/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/76` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 77 — 77

Path: `07150_Structured_Securities_Reference_Price_Ex_Ante`
Codification: NUMERIC
Applicability: all rows
Definition: The Reference Price is the instrument price to which a Unit disclosed Ex-Post cost is based and to which a Percentage disclosed cost should be multiplied in order to retrieve the Unit cost. This field is conditional and only used if 07155 is not used.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/77/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/77` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 78 — 78

Path: `07155_Structured_Securities_Notional_Reference_Amount_Ex_Ante`
Codification: NUMERIC
Applicability: all rows
Definition: The Notional Reference Amount is the amount expressed in number of currency units to which a Unit disclosed Ex-Post cost is based and to which a Percentage disclosed cost should be multiplied in order to retrieve the Unit cost. This field is conditional and only used if 07150 is not used.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/78/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/78` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 79 — 79

Path: `07160_Ex_Ante_Costs_Reference_Date`
Codification: DATE
Applicability: all rows
Definition: The Reference Date to which all Ex-Ante Cost disclosures refer (i.e NOT to be misstaken for General Reference Date, field 00050 or Generation Date and Time, field 00005)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/79/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/79` | (all) | ERROR | Populated cell does not match the codification (DATE) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 80 — 80

Path: `08010_Gross_One-off_Cost_Structured_Securities_Entry_Cost_Ex_Post`
Codification: FREE_TEXT
Applicability: all rows
Definition: Fixed Amount

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/80/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/80` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 81 — 81

Path: `08015_Net_One-off_Cost_Structured_Securities_Entry_Cost_Ex_Post`
Codification: UNKNOWN
Applicability: all rows
Definition: Net One-off Entry cost = 08010 less upfront distribution fee embedded in the 08010. In practice 08015 will be the portion of the 08010 retained by the manufacturer.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/81` | (all) | ERROR | Populated cell does not match the codification (UNKNOWN) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 82 — 82

Path: `08020_One-off_Costs_Structured_Securities_Exit_Cost_Ex_Post`
Codification: FREE_TEXT
Applicability: all rows
Definition: Fixed amount

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/82/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/82` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 83 — 83

Path: `08025_One-off_Cost_Financial_Instrument_Entry_Cost_Acquired`
Codification: CLOSED_LIST, closed list of 1 entries
Applicability: all rows
Definition: % of NAV

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/83/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/83` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 84 — 84

Path: `08030_Financial_Instrument_Ongoing_Costs_Ex_Post`
Codification: CLOSED_LIST, closed list of 1 entries
Applicability: all rows
Definition: % of NAV of the Financial Product expressed in annualized terms (rate of cost deduction to be applied)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/84/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/84` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 85 — 85

Path: `08040_Structured_Securities_Ongoing_Costs_Ex_Post_Accumulated`
Codification: FREE_TEXT
Applicability: all rows
Definition: Sum of each daily Recurring Product Costs

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/85` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 86 — 86

Path: `08045_Financial_Instrument_Borrowing_Costs_Ex_Post_UK`
Codification: CLOSED_LIST, closed list of 1 entries
Applicability: all rows
Definition: Financing costs related to borrowing for the purposes of gearing expressed as a % of NAV of the Financial Product expressed in annualized terms (rate of cost deduction to be applied)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/86` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 87 — 87

Path: `08050_Financial_Instrument_Management_Fee_Ex_Post`
Codification: CLOSED_LIST, closed list of 1 entries
Applicability: all rows
Definition: % of NAV of the Financial Product expressed in annualized terms (rate of cost deduction to be applied)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/87/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/87` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 88 — 88

Path: `08060_Financial_Instrument_Distribution_Fee_Ex_Post`
Codification: CLOSED_LIST, closed list of 1 entries
Applicability: all rows
Definition: % of NAV of the Financial Product expressed in annualized terms (rate of cost deduction to be applied)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/88/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/88` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 89 — 89

Path: `08070_Financial_Instrument_Transaction_Costs_Ex_Post`
Codification: CLOSED_LIST, closed list of 1 entries
Applicability: all rows
Definition: % of NAV of the Financial Product expressed in annualized terms (rate of cost deduction to be applied)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/89/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/89` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 90 — 90

Path: `08080_Financial_Instrument_Incidental_Costs_Ex_Post`
Codification: CLOSED_LIST, closed list of 1 entries
Applicability: all rows
Definition: % of NAV of the Financial Product expressed in annualized terms (rate of cost deduction to be applied)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/90/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/90` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 91 — 91

Path: `08090_Beginning_Of_Reference_Period`
Codification: DATE
Applicability: all rows
Definition: The Date that specifies the start of the Reference Period. Defined as "From and including". All ex-post cost disclosures apart from 08040 refers to all dates in such period.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/91/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/91` | (all) | ERROR | Populated cell does not match the codification (DATE) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 92 — 92

Path: `08100_End_Of_Reference_Period`
Codification: DATE
Applicability: all rows
Definition: The Date that specifies the end of the Reference Period. Defined as "To and including". All ex-post cost disclosures apart from 08040 refers to all dates in such period. For the avoidance of doubt, this date can be specified as equal to the date specified in 08090.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/92/EMT_BASE` | EMT (Mandatory) | ERROR | Cell is empty for an active row of profile EMT (Mandatory) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/92` | (all) | ERROR | Populated cell does not match the codification (DATE) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 93 — 93

Path: `08110_Structured_Securities_Reference_Price_Ex_Post`
Codification: NUMERIC
Applicability: all rows
Definition: The Reference Price is the instrument price to which a Unit disclosed Ex-Post cost is based and to which a Percentage disclosed cost should be multiplied in order to retrieve the Unit cost. This field is conditional and only used if 08120 is not used.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/93/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/93` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 94 — 94

Path: `08120_Structured_Securities_Notional_Reference_Amount`
Codification: NUMERIC
Applicability: all rows
Definition: The Notional Reference Amount is the amount expressed in number of currency units to which a Unit disclosed Ex-Post cost is based and to which a Percentage disclosed cost should be multiplied in order to retrieve the Unit cost. This field is conditional and only used if 08110 is not used.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/94/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/94` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 95 — 95

Path: `09010_Financial_Instrument_Transaction_Costs_Ex_Ante_UK`
Codification: CLOSED_LIST, closed list of 1 entries
Applicability: all rows
Definition: % of NAV of the Financial Product expressed in annualized terms (rate of cost deduction to be applied)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/95` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 96 — 96

Path: `09020_Financial_Instrument_Transaction_Costs_Ex_Post_UK`
Codification: CLOSED_LIST, closed list of 1 entries
Applicability: all rows
Definition: % of NAV of the Financial Product expressed in annualized terms (rate of cost deduction to be applied)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/96` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 97 — 97

Path: `09030_EMT_Data_Reporting_VFM_UK`
Codification: FREE_TEXT
Applicability: all rows
Definition: Specifies if the Value for Money section is filled in the current EMT posting.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/97/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/97` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 98 — 98

Path: `09040_Is_Assessment_Of_Value_Required_Under_COLL_UK`
Codification: FREE_TEXT
Applicability: all rows
Definition: Yes or No

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/98/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/98` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 99 — 99

Path: `09050_Outcome_Of_COLL_Assessment_Of_Value_UK`
Codification: FREE_TEXT
Applicability: all rows
Definition: 1 – charges are justified based on assessment and any action identified or, where the first assessment is not yet due, based on initial product design 2 – charges are not justified, significant action is required

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/99/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/99` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 100 — 100

Path: `09060_Outcome_Of_PRIN_Value_Assessment_Or_Review_UK`
Codification: FREE_TEXT
Applicability: all rows
Definition: 1 – product expected to provide fair value for reasonably foreseeable period 2 – review indicates significant changes required in order to provide fair value

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/100/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/100` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 101 — 101

Path: `09070_Other_Review_Related_To_Value_And_Or_Charges_UK`
Codification: FREE_TEXT
Applicability: all rows
Definition: A – In line with ESMA supervisory briefing on the supervision of costs in UCITS and AIFs or relevant NCA supervisory activity O – Other local requirements or procedures

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/101` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 102 — 102

Path: `09080_Further_Information_UK`
Codification: FREE_TEXT
Applicability: all rows
Definition: Link to sources of relevant information

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/102` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 103 — 103

Path: `09090_Review_Date_UK`
Codification: DATE
Applicability: all rows
Definition: Date of value assessment review or date COLL assessment of value report published or initial launch date

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/103/EMT_BASE` | EMT (Mandatory) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/103` | (all) | ERROR | Populated cell does not match the codification (DATE) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 104 — 104

Path: `09100_Review_Next_Due_UK`
Codification: DATE
Applicability: all rows

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/104` | (all) | ERROR | Populated cell does not match the codification (DATE) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 105 — 105

Path: `10000_Financial_Instrument_Indirect_Costs_Open_Ended_Ex_Ante_UK`
Codification: CLOSED_LIST, closed list of 1 entries
Applicability: all rows
Definition: % of NAV of the Financial Product expressed in annualized terms (rate of cost deduction to be applied)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/105` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 106 — 106

Path: `10010_Financial_Instrument_Indirect_Costs_Closed_Ended_Ex_Ante_UK`
Codification: CLOSED_LIST, closed list of 1 entries
Applicability: all rows
Definition: % of NAV of the Financial Product expressed in annualized terms (rate of cost deduction to be applied)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/106` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 107 — 107

Path: `10020_Financial_Instrument_Real_Assets_Costs_Ex_Ante_UK`
Codification: CLOSED_LIST, closed list of 1 entries
Applicability: all rows
Definition: % of NAV of the Financial Product expressed in annualized terms (rate of cost deduction to be applied)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/107` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 108 — 108

Path: `10030_Financial_Instrument_Indirect_Costs_Open_Ended_Ex_Post_UK`
Codification: CLOSED_LIST, closed list of 1 entries
Applicability: all rows
Definition: % of NAV of the Financial Product expressed in annualized terms (rate of cost deduction to be applied)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/108` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 109 — 109

Path: `10040_Financial_Instrument_Indirect_Costs_Closed_Ended_Ex_Post_UK`
Codification: CLOSED_LIST, closed list of 1 entries
Applicability: all rows
Definition: % of NAV of the Financial Product expressed in annualized terms (rate of cost deduction to be applied)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/109` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 110 — 110

Path: `10050_Financial_Instrument_Real_Assets_Costs_Ex_Post_UK`
Codification: CLOSED_LIST, closed list of 1 entries
Applicability: all rows
Definition: % of NAV of the Financial Product expressed in annualized terms (rate of cost deduction to be applied)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/110` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 111 — 111

Path: `10060_Does_Financial_Instrument_Produce_Client_Facing_Disclosures_UK`
Codification: FREE_TEXT
Applicability: all rows
Definition: Y - Yes, there is a CFD I - There is an intention to produce a CFD N - No, there is no intention to produce a CFD

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/111` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 112 — 112

Path: `11000_EMT_Data_Reporting_VCA_FR`
Codification: FREE_TEXT
Applicability: all rows
Definition: Did the product pass a VCA test successfully ?

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/112` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 113 — 113

Path: `11010_Used_VCA_Methodology_FR`
Codification: FREE_TEXT
Applicability: all rows
Definition: Specifies which VCA methodology test has been performed

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/113` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 114 — 114

Path: `11020_Hyperlink_To_VCA_Methodology_FR`
Codification: FREE_TEXT
Applicability: all rows
Definition: Hyperlink to VCA methodology

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `EMT_BASE` | EMT (Mandatory) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/114` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

