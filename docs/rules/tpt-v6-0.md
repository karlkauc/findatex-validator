# FinDatEx TPT Validation Reference (V6.0)

Spec: `/spec/tpt/TPT_V6_20220314.xlsx`
Manifest: `/spec/tpt/tpt-v6-info.json`
Released: 2022-03-14  ·  Sheet: `TPT V6.0`
Profiles: Solvency II (`SOLVENCY_II`), IORP / EIOPA / ECB (`IORP_EIOPA_ECB`), NW 675 (`NW_675`), SST (FINMA) (`SST`)

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
| `SOLVENCY_II` | Solvency II | 36 | 57 |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | 31 | 0 |
| `NW_675` | NW 675 | 7 | 6 |
| `SST` | SST (FINMA) | 24 | 44 |

## 3. General rules

The engines below run on every applicable field/row independently of the template-specific cross-field block in §4.

### Version rule

- **Rule ID:** `XF-15/TPT_VERSION`
- **Severity:** ERROR (INFO if the version cell is absent)
- **Expected token:** `V6.0`
- **Trigger:** the version cell of the file does not contain the expected token.
- **Score impact:** Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1).

### Presence engine (`PRESENCE/{numKey}/{profile}`)

- **What it checks:** for every `FieldSpec` flagged M for an active profile and applicable to the row's CIC, the cell value must be non-empty.
- **Severity:** ERROR.
- **Score impact:** Each missing cell lowers MANDATORY_COMPLETENESS (40 %) by 1 / total mandatory slots, and lowers the per-profile PROFILE_COMPLETENESS leg (10 %, M-weighted 0.7).
- **Active rule instances:** 94 (one per field × profile).

### Conditional-presence engine (`COND_PRESENCE/{numKey}/{profile}`)

- **What it checks:** for every `FieldSpec` flagged C for an active profile and whose CIC applicability matches the row's CIC, the cell value must be non-empty.
- **Severity:** WARNING.
- **Score impact:** Each missing cell lowers PROFILE_COMPLETENESS (10 %, C-weighted 0.3) by 1 / total conditional slots. Severity = WARNING — does not affect MANDATORY_COMPLETENESS or FORMAT_CONFORMANCE.
- **Active rule instances:** 79 (one per CIC-restricted field × profile).

### Format engine (`FORMAT/{numKey}`)

- **What it checks:** every populated cell is validated against its codification kind: ISO 4217 currency, ISO 3166-A2 country, ISO 8601 date, NACE, CIC 4-char, alphanumeric length, numeric, closed-list membership.
- **Severity:** ERROR.
- **Score impact:** Each ERROR lowers FORMAT_CONFORMANCE (20 %) by 1 / non-empty cells. Closed-list mismatches (message contains "closed list") instead lower CLOSED_LIST_CONFORMANCE (15 %) by 1 / populated closed-list cells.
- **Active rule instances:** 143 (one per field).

### ISIN engine (`ISIN/{code}/{type}`)

- **What it checks:** when the type cell is `1` (ISIN), the code cell must be a 12-character alphanumeric value with a valid ISO 6166 Luhn checksum.
- **Severity:** ERROR.
- **Score impact:** Each ERROR lowers FORMAT_CONFORMANCE (20 %) by 1 / non-empty cells.
- `ISIN/14/15` — code field `14`, type field `15`
- `ISIN/68/69` — code field `68`, type field `69`

### LEI engine (`LEI/{code}/{type}`)

- **What it checks:** when the type cell is `1` (LEI), the code cell must be a 20-character ISO 17442 LEI with the mod-97 checksum.
- **Severity:** ERROR.
- **Score impact:** Each ERROR lowers FORMAT_CONFORMANCE (20 %) by 1 / non-empty cells.
- `LEI/47/48` — code field `47`, type field `48`
- `LEI/50/51` — code field `50`, type field `51`
- `LEI/81/82` — code field `81`, type field `82`
- `LEI/84/85` — code field `84`, type field `85`
- `LEI/115/116` — code field `115`, type field `116`
- `LEI/119/120` — code field `119`, type field `120`
- `LEI/140/141` — code field `140`, type field `141`

### External validation (opt-in)

- **Off by default.** Operators enable it via the Settings dialog (desktop) or the `FINDATEX_WEB_EXTERNAL_ENABLED` env var (web).
- **ISIN lookup (OpenFIGI):** field `14` (when type field `15` = `1`); field `68` (when type field `69` = `1`)
- **LEI lookup (GLEIF):** field `47` (when type field `48` = `1`); field `50` (when type field `51` = `1`); field `81` (when type field `82` = `1`); field `84` (when type field `85` = `1`); field `115` (when type field `116` = `1`); field `119` (when type field `120` = `1`)
- **Score impact:** External-validation findings are advisory and do not affect any score dimension.

## 4. Cross-field rules

Each rule below fires per row when its trigger condition holds and the expected target field state is violated. The score impact column applies to every individual finding the rule emits.

### XF-01/COMPLETE_SCR_DELIVERY — When field 11 (CompleteSCRDelivery) is "Y", every SCR contribution field 97..105b must be populated.

- **Severity:** ERROR
- **Trigger:** Field 11 (CompleteSCRDelivery) = Y
- **Required:** All SCR contribution fields 97..105b must be present.
- **Source field(s):** `11`
- **Target field(s):** `97`, `98`, `99`, `100`, `101`, `102`, `103`, `104`, `105`, `105a`, `105b`
- **Score impact:** Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1).

### XF-04/POSITION_WEIGHT_SUM — Σ field 26 (Position weight) across all positions must be ≈ 1.0 within ±0.02.

- **Severity:** WARNING
- **Trigger:** At least one position has a populated field 26
- **Required:** Σ field 26 (Position weight) must be ≈ 1.0 within ±0.02.
- **Source field(s):** `26`
- **Target field(s):** `26`
- **Score impact:** Severity = WARNING — surfaced in the report but not factored into the score (only ERROR severity feeds CROSS_FIELD_CONSISTENCY).

### XF-05/CASH_PERCENTAGE — Declared cash ratio (field 9) must match Σ MarketValuePC of CIC xx7x positions divided by TotalNetAssets, within ±0.05.

- **Severity:** WARNING
- **Trigger:** Σ MarketValuePC of CIC xx7x positions / TotalNetAssets
- **Required:** Field 9 must match within ±0.05.
- **Source field(s):** `5`, `24`
- **Target field(s):** `9`
- **Score impact:** Severity = WARNING — surfaced in the report but not factored into the score (only ERROR severity feeds CROSS_FIELD_CONSISTENCY).

### XF-06/NAV_PRICE_SHARES — TotalNetAssets (field 5) must match SharePrice (field 8) × Shares (field 8b) within ±0.01 relative tolerance.

- **Severity:** WARNING
- **Trigger:** Fields 5, 8, 8b are all populated
- **Required:** Field 5 must equal field 8 × field 8b within ±0.01 relative tolerance.
- **Source field(s):** `8`, `8b`
- **Target field(s):** `5`
- **Score impact:** Severity = WARNING — surfaced in the report but not factored into the score (only ERROR severity feeds CROSS_FIELD_CONSISTENCY).

### XF-08/COUPON_FREQUENCY — Field 38 (Coupon payment frequency) must be one of {0, 1, 2, 4, 12, 52}.

- **Severity:** ERROR
- **Trigger:** Field 38 (Coupon payment frequency) is populated
- **Required:** Value must be one of {0, 1, 2, 4, 12, 52}.
- **Source field(s):** `38`
- **Target field(s):** `38`
- **Score impact:** Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1).

### XF-09/CUSTODIAN_PAIR — Custodian identification code (field 140) and its type indicator (field 141) must be paired: filling one without the other is a finding.

- **Severity:** ERROR
- **Trigger:** Field 140 or field 141 is populated
- **Required:** Both fields 140 and 141 must be populated as a pair.
- **Source field(s):** `140`, `141`
- **Target field(s):** `140`, `141`
- **Score impact:** Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1).

### XF-10/INTEREST_RATE_TYPE — When field 32 (interest-rate type) is Floating/Variable, fields 34..37 are mandatory; when Fixed, field 33 (Coupon rate) is mandatory.

- **Severity:** ERROR
- **Trigger:** Field 32 is Floating/Variable, or Fixed
- **Required:** Floating/Variable → fields 34..37 mandatory; Fixed → field 33 mandatory.
- **Source field(s):** `32`
- **Target field(s):** `33`, `34`, `35`, `36`, `37`
- **Score impact:** Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1).

### XF-12/DATE_ORDER — Field 7 (Reporting date) must not precede field 6 (Valuation date).

- **Severity:** ERROR
- **Trigger:** Fields 6 and 7 are both populated
- **Required:** Field 7 (Reporting date) must not precede field 6 (Valuation date).
- **Source field(s):** `6`, `7`
- **Target field(s):** `7`
- **Score impact:** Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1).

### XF-11/MATURITY_AFTER_REPORTING — For dated/interest-rate instruments (CIC categories 1, 2, 5, 6, 8), field 39 (Maturity date) must not precede field 7 (Reporting date).

- **Severity:** WARNING
- **Trigger:** Row CIC ∈ {1, 2, 5, 6, 8} and field 39 is populated
- **Required:** Field 39 (Maturity date) must not precede field 7 (Reporting date).
- **Source field(s):** `7`
- **Target field(s):** `39`
- **Score impact:** Severity = WARNING — surfaced in the report but not factored into the score (only ERROR severity feeds CROSS_FIELD_CONSISTENCY).

### XF-13/PIK — Field 146 (PIK) must be one of {0, 1, 2, 3, 4} and is meaningful only for bonds (CIC xx2x) and loans (CIC xx8x). Each PIK case mandates a specific subset of fields 32, 33, 38, 39, 40, 41 per the PIK guidelines.

- **Severity:** ERROR
- **Trigger:** Field 146 (PIK) is populated
- **Required:** Value must be one of {0,1,2,3,4} and case-specific fields must be present.
- **Source field(s):** `146`
- **Target field(s):** `32`, `33`, `38`, `39`, `40`, `41`, `146`
- **Score impact:** Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1).

### XF-14/UNDERLYING_CIC — Field 67 (Underlying CIC) is mandatory when the main CIC category is one of {2, A, B, C, D, F} (instruments that have an economic underlying).

- **Severity:** ERROR
- **Trigger:** Row CIC ∈ {2, A, B, C, D, F}
- **Required:** Field 67 (Underlying CIC) must be populated.
- **Source field(s):** `12`
- **Target field(s):** `67`
- **Score impact:** Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1).

### XF-16/THIRD_CURRENCY_WEIGHT — Conditional presence of field 31

- **Severity:** ERROR
- **Trigger:** Field `29` is not blank
- **Required:** Field `31` must be non-empty.
- **Source field(s):** `29`
- **Target field(s):** `31`
- **Score impact:** Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1).

### XF-17/INDEX_TYPE — Conditional presence of field 35

- **Severity:** ERROR
- **Trigger:** Field `34` is not blank
- **Required:** Field `35` must be non-empty.
- **Source field(s):** `34`
- **Target field(s):** `35`
- **Score impact:** Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1).

### XF-17/INDEX_NAME — Conditional presence of field 36

- **Severity:** ERROR
- **Trigger:** Field `34` is not blank
- **Required:** Field `36` must be non-empty.
- **Source field(s):** `34`
- **Target field(s):** `36`
- **Score impact:** Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1).

### XF-17/INDEX_MARGIN — Conditional presence of field 37

- **Severity:** ERROR
- **Trigger:** Field `34` is not blank
- **Required:** Field `37` must be non-empty.
- **Source field(s):** `34`
- **Target field(s):** `37`
- **Score impact:** Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1).

### XF-18/CALL_PUT_DATE — Conditional presence of field 43

- **Severity:** ERROR
- **Trigger:** Field `42` ∈ [CAL, PUT]
- **Required:** Field `43` must be non-empty.
- **Source field(s):** `42`
- **Target field(s):** `43`
- **Score impact:** Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1).

### XF-18/OPTION_DIRECTION — Conditional presence of field 44

- **Severity:** ERROR
- **Trigger:** Field `42` ∈ [CAL, PUT]
- **Required:** Field `44` must be non-empty.
- **Source field(s):** `42`
- **Target field(s):** `44`
- **Score impact:** Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1).

### XF-19/STRIKE_PRICE — Conditional presence of field 45

- **Severity:** ERROR
- **Trigger:** Field `42` is not blank
- **Required:** Field `45` must be non-empty.
- **Source field(s):** `42`
- **Target field(s):** `45`
- **Score impact:** Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1).

### XF-20/ISSUER_LEI_PRESENT — Conditional presence of field 47

- **Severity:** ERROR
- **Trigger:** Field `48` = "1"
- **Required:** Field `47` must be non-empty.
- **Source field(s):** `48`
- **Target field(s):** `47`
- **Score impact:** Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1).

### XF-21/GROUP_LEI_PRESENT — Conditional presence of field 50

- **Severity:** ERROR
- **Trigger:** Field `51` = "1"
- **Required:** Field `50` must be non-empty.
- **Source field(s):** `51`
- **Target field(s):** `50`
- **Score impact:** Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1).

### XF-22/UNDERLYING_GROUP_LEI — Conditional presence of field 84

- **Severity:** ERROR
- **Trigger:** Field `85` = "1"
- **Required:** Field `84` must be non-empty.
- **Source field(s):** `85`
- **Target field(s):** `84`
- **Score impact:** Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1).

### XF-23/FUND_ISSUER_LEI — Conditional presence of field 115

- **Severity:** ERROR
- **Trigger:** Field `116` = "1"
- **Required:** Field `115` must be non-empty.
- **Source field(s):** `116`
- **Target field(s):** `115`
- **Score impact:** Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1).

### XF-24/FUND_GROUP_LEI — Conditional presence of field 119

- **Severity:** ERROR
- **Trigger:** Field `120` = "1"
- **Required:** Field `119` must be non-empty.
- **Source field(s):** `120`
- **Target field(s):** `119`
- **Score impact:** Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1).

### XF-25/COLLATERAL_VALUE — Conditional presence of field 139

- **Severity:** ERROR
- **Trigger:** Field `138` ∈ [1, 2, 3]
- **Required:** Field `139` must be non-empty.
- **Source field(s):** `138`
- **Target field(s):** `139`
- **Score impact:** Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1).

## 5. Per-field catalog

One entry per `FieldSpec` in spec order. Each entry lists every check that can fire on the field, with the profile scope, severity, trigger condition, and quantified score impact.

### Field 1 — 1_Portfolio_identifying_data

Path: `Portfolio / PortfolioID / Code`
Codification: FREE_TEXT
Applicability: all rows
Definition: Identification of the fund or share class

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | M | Mandatory — must always be present. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/1/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/1/IORP_EIOPA_ECB` | IORP / EIOPA / ECB | ERROR | Cell is empty for an active row of profile IORP / EIOPA / ECB | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/1/SST` | SST (FINMA) | ERROR | Cell is empty for an active row of profile SST (FINMA) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/1` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 2 — 2_Type_of_identification_code_for_the_fund_share_or_portfolio

Path: `Portfolio / PortfolioID / CodificationSystem`
Codification: CLOSED_LIST, closed list of 10 entries
Applicability: all rows
Definition: Codification chosen to identify the share of the CIS

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | M | Mandatory — must always be present. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/2/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/2/IORP_EIOPA_ECB` | IORP / EIOPA / ECB | ERROR | Cell is empty for an active row of profile IORP / EIOPA / ECB | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/2/SST` | SST (FINMA) | ERROR | Cell is empty for an active row of profile SST (FINMA) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/2` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 3 — 3_Portfolio_name

Path: `Portfolio / PorfolioName`
Codification: ALPHANUMERIC (max 255)
Applicability: all rows
Definition: Name of the Portfolio or name of the CIS

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | M | Mandatory — must always be present. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/3/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/3/IORP_EIOPA_ECB` | IORP / EIOPA / ECB | ERROR | Cell is empty for an active row of profile IORP / EIOPA / ECB | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/3/SST` | SST (FINMA) | ERROR | Cell is empty for an active row of profile SST (FINMA) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/3` | (all) | ERROR | Populated cell does not match the codification (ALPHANUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 4 — 4_Portfolio_currency_(B)

Path: `Portfolio / PortfolioCurrency`
Codification: ISO_4217
Applicability: all rows
Definition: Valuation currency of the portfolio

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | M | Mandatory — must always be present. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/4/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/4/IORP_EIOPA_ECB` | IORP / EIOPA / ECB | ERROR | Cell is empty for an active row of profile IORP / EIOPA / ECB | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/4/SST` | SST (FINMA) | ERROR | Cell is empty for an active row of profile SST (FINMA) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/4` | (all) | ERROR | Populated cell does not match the codification (ISO_4217) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 5 — 5_Net_asset_valuation_of_the_portfolio_or_the_share_class_in_portfolio_currency

Path: `Portfolio / TotalNetAssets`
Codification: NUMERIC
Applicability: all rows
Definition: Portfolio valuation

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | M | Mandatory — must always be present. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/5/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/5/IORP_EIOPA_ECB` | IORP / EIOPA / ECB | ERROR | Cell is empty for an active row of profile IORP / EIOPA / ECB | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/5/SST` | SST (FINMA) | ERROR | Cell is empty for an active row of profile SST (FINMA) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/5` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `XF-06/NAV_PRICE_SHARES` | (all) | WARNING | Fields 5, 8, 8b are all populated | Field 5 must equal field 8 × field 8b within ±0.01 relative tolerance. | Severity = WARNING — surfaced in the report but not factored into the score (only ERROR severity feeds CROSS_FIELD_CONSISTENCY). |

**Referenced as source by:** `XF-05/CASH_PERCENTAGE`

---

### Field 6 — 6_Valuation_date

Path: `Portfolio / ValuationDate`
Codification: DATE
Applicability: all rows
Definition: Date of valuation (date positions valid for)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | M | Mandatory — must always be present. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/6/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/6/IORP_EIOPA_ECB` | IORP / EIOPA / ECB | ERROR | Cell is empty for an active row of profile IORP / EIOPA / ECB | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/6/SST` | SST (FINMA) | ERROR | Cell is empty for an active row of profile SST (FINMA) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/6` | (all) | ERROR | Populated cell does not match the codification (DATE) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |

**Referenced as source by:** `XF-12/DATE_ORDER`

---

### Field 7 — 7_Reporting_date

Path: `Portfolio / ReportingDate`
Codification: DATE
Applicability: all rows
Definition: Date of reference for the reporting

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | M | Mandatory — must always be present. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/7/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/7/IORP_EIOPA_ECB` | IORP / EIOPA / ECB | ERROR | Cell is empty for an active row of profile IORP / EIOPA / ECB | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/7/SST` | SST (FINMA) | ERROR | Cell is empty for an active row of profile SST (FINMA) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/7` | (all) | ERROR | Populated cell does not match the codification (DATE) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `XF-12/DATE_ORDER` | (all) | ERROR | Fields 6 and 7 are both populated | Field 7 (Reporting date) must not precede field 6 (Valuation date). | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |

**Referenced as source by:** `XF-12/DATE_ORDER`, `XF-11/MATURITY_AFTER_REPORTING`

---

### Field 8 — 8_Share_price

Path: `Portfolio / ShareClass / SharePrice`
Codification: NUMERIC
Applicability: all rows
Definition: Share price of the fund/share class

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | M | Mandatory — must always be present. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/8/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/8/IORP_EIOPA_ECB` | IORP / EIOPA / ECB | ERROR | Cell is empty for an active row of profile IORP / EIOPA / ECB | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/8/SST` | SST (FINMA) | ERROR | Cell is empty for an active row of profile SST (FINMA) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/8` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |

**Referenced as source by:** `XF-06/NAV_PRICE_SHARES`

---

### Field 8b — 8b_Total_number_of_shares

Path: `Portfolio / ShareClass / TotalNumberOfShares`
Codification: NUMERIC
Applicability: all rows
Definition: Total number of shares (per share class, if applicable)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | M | Mandatory — must always be present. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/8b/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/8b/IORP_EIOPA_ECB` | IORP / EIOPA / ECB | ERROR | Cell is empty for an active row of profile IORP / EIOPA / ECB | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/8b/SST` | SST (FINMA) | ERROR | Cell is empty for an active row of profile SST (FINMA) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/8b` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |

**Referenced as source by:** `XF-06/NAV_PRICE_SHARES`

---

### Field 9 — 9_Cash_ratio

Path: `Portfolio / CashPercentage`
Codification: NUMERIC
Applicability: all rows
Definition: Amount of cash of the fund / total net asset value of the fund, in %

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | O | Optional — populate when applicable. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/9` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `XF-05/CASH_PERCENTAGE` | (all) | WARNING | Σ MarketValuePC of CIC xx7x positions / TotalNetAssets | Field 9 must match within ±0.05. | Severity = WARNING — surfaced in the report but not factored into the score (only ERROR severity feeds CROSS_FIELD_CONSISTENCY). |


---

### Field 10 — 10_Portfolio_modified_duration

Path: `Portfolio / PortfolioModifiedDuration`
Codification: NUMERIC
Applicability: all rows
Definition: Weighted average modified duration of portfolio positions

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | O | Optional — populate when applicable. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/10` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 11 — 11_Complete_SCR_delivery

Path: `Portfolio / CompleteSCRDelivery`
Codification: ALPHA (max 1)
Applicability: all rows
Definition: Y/N

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/11/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/11` | (all) | ERROR | Populated cell does not match the codification (ALPHA) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |

**Referenced as source by:** `XF-01/COMPLETE_SCR_DELIVERY`

---

### Field 12 — 12_CIC_code_of_the_instrument

Path: `Position / InstrumentCIC`
Codification: CIC
Applicability: all rows
Definition: CIC Code (Complementary Identification Code).

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | M | Mandatory — must always be present. |
| `NW_675` | NW 675 | M | Mandatory — must always be present. |
| `SST` | SST (FINMA) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/12/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/12/IORP_EIOPA_ECB` | IORP / EIOPA / ECB | ERROR | Cell is empty for an active row of profile IORP / EIOPA / ECB | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/12/NW_675` | NW 675 | ERROR | Cell is empty for an active row of profile NW 675 | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/12/SST` | SST (FINMA) | ERROR | Cell is empty for an active row of profile SST (FINMA) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/12` | (all) | ERROR | Populated cell does not match the codification (CIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |

**Referenced as source by:** `XF-14/UNDERLYING_CIC`

---

### Field 13 — 13_Economic_zone_of_the_quotation_place

Path: `Position / EconomicArea`
Codification: FREE_TEXT
Applicability: CIC categories CIC3
Definition: Indication of the economic zone of the quotation place

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/13/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/13` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 14 — 14_Identification_code_of_the_instrument

Path: `Position / InstrumentCode / Code`
Codification: FREE_TEXT
Applicability: all rows
Definition: Identification code of the financial instrument - including identifier for leg of instrument if required

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | M | Mandatory — must always be present. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/14/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/14/IORP_EIOPA_ECB` | IORP / EIOPA / ECB | ERROR | Cell is empty for an active row of profile IORP / EIOPA / ECB | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/14/SST` | SST (FINMA) | ERROR | Cell is empty for an active row of profile SST (FINMA) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/14` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `ISIN/14/15` | (all) | ERROR | Type field `15` = `1` and this cell is not a valid 12-char ISIN with Luhn checksum | Identifier cannot be resolved against ISO 6166. | FORMAT_CONFORMANCE −1/M |

**External validation:** OpenFIGI ISIN lookup (active when field `15` = `1`)

---

### Field 15 — 15_Type_of_identification_code_for_the_instrument

Path: `Position / InstrumentCode / CodificationSystem`
Codification: CLOSED_LIST, closed list of 10 entries
Applicability: all rows
Definition: Codification chosen to identify the instrument

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | M | Mandatory — must always be present. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/15/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/15/IORP_EIOPA_ECB` | IORP / EIOPA / ECB | ERROR | Cell is empty for an active row of profile IORP / EIOPA / ECB | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/15/SST` | SST (FINMA) | ERROR | Cell is empty for an active row of profile SST (FINMA) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/15` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 16 — 16_Grouping_code_for_multiple_leg_instruments

Path: `Position / GroupID`
Codification: ALPHANUMERIC (max 255)
Applicability: CIC categories CICA, CICB, CICC, CICD, CICE; CICE sub-categories [2]; CICC sub-categories [3]; CICB sub-categories [3]; CICA sub-categories [3]
Definition: grouping code for operations on multi leg instruments

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/16/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/16/SST` | SST (FINMA) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/16` | (all) | ERROR | Populated cell does not match the codification (ALPHANUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 17 — 17_Instrument_name

Path: `Position / InstrumentName`
Codification: ALPHANUMERIC (max 255)
Applicability: all rows
Definition: instrument name

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | M | Mandatory — must always be present. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/17/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/17/IORP_EIOPA_ECB` | IORP / EIOPA / ECB | ERROR | Cell is empty for an active row of profile IORP / EIOPA / ECB | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/17/SST` | SST (FINMA) | ERROR | Cell is empty for an active row of profile SST (FINMA) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/17` | (all) | ERROR | Populated cell does not match the codification (ALPHANUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 17b — 17b_Asset_liability

Path: `Position / Valuation / AssetOrLiability`
Codification: CLOSED_LIST, closed list of 2 entries
Applicability: all rows
Definition: Asset/Liability identification if needed

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | NA | Not applicable to this profile. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | O | Optional — populate when applicable. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/17b` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 18 — 18_Quantity

Path: `Position / Valuation / Quantity`
Codification: NUMERIC
Applicability: CIC categories CIC0, CIC2, CIC3, CIC4, CICA, CICB, CICC, CICD; CICD sub-categories [4, 5, 9]; CICC sub-categories [1, 4, 5, 9]; CIC2 sub-categories [2, 9]; CICB sub-categories [1, 4, 5, 9]; CICA sub-categories [1, 5, 9]
Definition: Number of instruments on position

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/18/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/18/SST` | SST (FINMA) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/18` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 19 — 19_Nominal_amount

Path: `Position / Valuation / TotalNominalValueQC`
Codification: NUMERIC
Applicability: CIC categories CIC0, CIC1, CIC2, CIC5, CIC6, CIC7, CIC8, CICA, CICB, CICC, CICD, CICE, CICF; CICC sub-categories [2, 3, 6, 7, 8, 9]; CICB sub-categories [2, 3, 6, 7, 8, 9]; CICA sub-categories [2, 3, 9]
Definition: Quantity * nominal unit amount

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | C | Conditional — required when the spec's applicability/condition holds. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/19/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/19/NW_675` | NW 675 | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/19/SST` | SST (FINMA) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/19` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 20 — 20_Contract_size_for_derivatives

Path: `Position / Valuation / ContractSize`
Codification: NUMERIC
Applicability: CIC categories CICA, CICB, CICC
Definition: Contract size

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/20/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/20/SST` | SST (FINMA) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/20` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 21 — 21_Quotation_currency_(A)

Path: `Position / Valuation / QuotationCurrency`
Codification: ISO_4217
Applicability: all rows
Definition: Currency of quotation for the instrument or denomination

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | M | Mandatory — must always be present. |
| `NW_675` | NW 675 | M | Mandatory — must always be present. |
| `SST` | SST (FINMA) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/21/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/21/IORP_EIOPA_ECB` | IORP / EIOPA / ECB | ERROR | Cell is empty for an active row of profile IORP / EIOPA / ECB | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/21/NW_675` | NW 675 | ERROR | Cell is empty for an active row of profile NW 675 | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/21/SST` | SST (FINMA) | ERROR | Cell is empty for an active row of profile SST (FINMA) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/21` | (all) | ERROR | Populated cell does not match the codification (ISO_4217) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 22 — 22_Market_valuation_in_quotation_currency_(A)

Path: `Position / Valuation / MarketValueQC`
Codification: NUMERIC
Applicability: all rows
Definition: Market valuation of the position accrued interest included in quotation currency

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | M | Mandatory — must always be present. |
| `SST` | SST (FINMA) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/22/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/22/NW_675` | NW 675 | ERROR | Cell is empty for an active row of profile NW 675 | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/22/SST` | SST (FINMA) | ERROR | Cell is empty for an active row of profile SST (FINMA) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/22` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 23 — 23_Clean_market_valuation_in_quotation_currency_(A)

Path: `Position / Valuation / CleanValueQC`
Codification: NUMERIC
Applicability: all rows
Definition: Market valuation of the position accrued interest excluded in quotation currency

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/23/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/23/SST` | SST (FINMA) | ERROR | Cell is empty for an active row of profile SST (FINMA) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/23` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 24 — 24_Market_valuation_in_portfolio_currency_(B)

Path: `Position / Valuation / MarketValuePC`
Codification: NUMERIC
Applicability: all rows
Definition: Market valuation of the position accrued interest included in portfolio currency

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | M | Mandatory — must always be present. |
| `NW_675` | NW 675 | M | Mandatory — must always be present. |
| `SST` | SST (FINMA) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/24/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/24/IORP_EIOPA_ECB` | IORP / EIOPA / ECB | ERROR | Cell is empty for an active row of profile IORP / EIOPA / ECB | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/24/NW_675` | NW 675 | ERROR | Cell is empty for an active row of profile NW 675 | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/24/SST` | SST (FINMA) | ERROR | Cell is empty for an active row of profile SST (FINMA) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/24` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |

**Referenced as source by:** `XF-05/CASH_PERCENTAGE`

---

### Field 25 — 25_Clean_market_valuation_in_portfolio_currency_(B)

Path: `Position / Valuation / CleanValuePC`
Codification: NUMERIC
Applicability: all rows
Definition: Market valuation of the position accrued interest excluded in portfolio currency

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/25/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/25/SST` | SST (FINMA) | ERROR | Cell is empty for an active row of profile SST (FINMA) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/25` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 26 — 26_Valuation_weight

Path: `Position / Valuation / PositionWeight`
Codification: NUMERIC
Applicability: all rows
Definition: Market valuation in portfolio currency / portfolio net asset value in %

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | M | Mandatory — must always be present. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/26/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/26/IORP_EIOPA_ECB` | IORP / EIOPA / ECB | ERROR | Cell is empty for an active row of profile IORP / EIOPA / ECB | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/26/SST` | SST (FINMA) | ERROR | Cell is empty for an active row of profile SST (FINMA) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/26` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `XF-04/POSITION_WEIGHT_SUM` | (all) | WARNING | At least one position has a populated field 26 | Σ field 26 (Position weight) must be ≈ 1.0 within ±0.02. | Severity = WARNING — surfaced in the report but not factored into the score (only ERROR severity feeds CROSS_FIELD_CONSISTENCY). |

**Referenced as source by:** `XF-04/POSITION_WEIGHT_SUM`

---

### Field 27 — 27_Market_exposure_amount_in_quotation_currency_(A)

Path: `Position / Valuation / MarketExposureQC`
Codification: NUMERIC
Applicability: all rows
Definition: Market exposure amount different from market valuation for derivatives (valuation of the equivalent position on the underlying asset)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | M | Mandatory — must always be present. |
| `SST` | SST (FINMA) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/27/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/27/NW_675` | NW 675 | ERROR | Cell is empty for an active row of profile NW 675 | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/27/SST` | SST (FINMA) | ERROR | Cell is empty for an active row of profile SST (FINMA) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/27` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 28 — 28_Market_exposure_amount_in_portfolio_currency_(B)

Path: `Position / Valuation / MarketExposurePC`
Codification: NUMERIC
Applicability: all rows
Definition: Market exposure amount different from market valuation for derivatives (valuation of the equivalent position on the underlying asset) in the quotation currency of the portfolio

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | M | Mandatory — must always be present. |
| `SST` | SST (FINMA) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/28/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/28/NW_675` | NW 675 | ERROR | Cell is empty for an active row of profile NW 675 | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/28/SST` | SST (FINMA) | ERROR | Cell is empty for an active row of profile SST (FINMA) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/28` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 29 — 29_Market_exposure_amount_for_the_3rd_quotation_currency_(C)

Path: `Position / Valuation / MarketExposureLeg2`
Codification: NUMERIC
Applicability: all rows
Definition: Market exposure amount different from market valuation for derivatives (valuation of the equivalent position on the underlying asset) in the quotation currency of the underlying asset

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/29` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |

**Referenced as source by:** `XF-16/THIRD_CURRENCY_WEIGHT`

---

### Field 30 — 30_Market_exposure_in_weight

Path: `Position / Valuation / MarketExposureWeight`
Codification: NUMERIC
Applicability: all rows
Definition: Exposure valuation in portfolio currency / total net asset value of the fund, in %

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/30/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/30/SST` | SST (FINMA) | ERROR | Cell is empty for an active row of profile SST (FINMA) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/30` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 31 — 31_Market_exposure_for_the_3rd_currency_in_weight_over_NAV

Path: `Position / Valuation / MarketExposureWeightLeg2`
Codification: NUMERIC
Applicability: CIC categories CICE
Definition: Exposure valuation for leg 2 in portfolio currency / total net asset value of the fund, in %

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/31` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `XF-16/THIRD_CURRENCY_WEIGHT` | (all) | ERROR | Field `29` is not blank | Field `31` must be non-empty. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |


---

### Field 32 — 32_Interest_rate_type

Path: `Position / BondCharacteristics / RateType`
Codification: CLOSED_LIST, closed list of 4 entries
Applicability: CIC categories CIC1, CIC2, CIC5, CIC6, CIC7, CIC8, CICD, CICE, CICF; CIC7 sub-categories [3, 4, 5]; CICE sub-categories [1]; CICD sub-categories [1, 3]
Definition: * Fixed - plain vanilla fixed coupon rate * Floating - plain vanilla floating coupon rates (for all interest rates, which refer to a reference interest rate like EONIA or Libor or Libor + margin in BP) * Variable - all other variable interest rates like step-up or step-down or fixed-to-float bonds. The variable feature is the (credit) margin or the change between fixed and float. * Infation_linked for inflation linked bonds in order to identify them.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | C | Conditional — required when the spec's applicability/condition holds. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/32/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/32/NW_675` | NW 675 | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/32/SST` | SST (FINMA) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/32` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `XF-13/PIK` | (all) | ERROR | Field 146 (PIK) is populated | Value must be one of {0,1,2,3,4} and case-specific fields must be present. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |

**Referenced as source by:** `XF-10/INTEREST_RATE_TYPE`

---

### Field 33 — 33_Coupon_rate

Path: `Position / BondCharacteristics / CouponRate`
Codification: NUMERIC
Applicability: CIC categories CIC1, CIC2, CIC5, CIC6, CIC7, CIC8, CICD, CICE, CICF; CIC7 sub-categories [3, 4, 5]; CICF sub-categories [1, 3, 4]; CICE sub-categories [1]; CICD sub-categories [1, 3]
Definition: Fixed rate: coupon rate as a percentage of nominal amount Floating rate: last fixing rate + margin as a percentage of nominal amount Variable rate: estimation of current rate over the period + margin as a percentage of nominal amount all rates are expressed on an annual basis

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/33` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `XF-10/INTEREST_RATE_TYPE` | (all) | ERROR | Field 32 is Floating/Variable, or Fixed | Floating/Variable → fields 34..37 mandatory; Fixed → field 33 mandatory. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |
| `XF-13/PIK` | (all) | ERROR | Field 146 (PIK) is populated | Value must be one of {0,1,2,3,4} and case-specific fields must be present. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |


---

### Field 34 — 34_Interest_rate_reference_identification

Path: `Position / BondCharacteristics / VariableRate / IndexID / Code`
Codification: FREE_TEXT
Applicability: CIC categories CIC1, CIC2, CIC5, CIC6, CIC7, CIC8, CICD, CICE, CICF
Definition: identification code for interest rate index

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/34` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `XF-10/INTEREST_RATE_TYPE` | (all) | ERROR | Field 32 is Floating/Variable, or Fixed | Floating/Variable → fields 34..37 mandatory; Fixed → field 33 mandatory. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |

**Referenced as source by:** `XF-17/INDEX_TYPE`, `XF-17/INDEX_NAME`, `XF-17/INDEX_MARGIN`

---

### Field 35 — 35_Identification_type_for_interest_rate_index

Path: `Position / BondCharacteristics / VariableRate / IndexID / CodificationSystem`
Codification: FREE_TEXT
Applicability: CIC categories CIC1, CIC2, CIC5, CIC6, CIC7, CIC8, CICD, CICE, CICF
Definition: Type of codification used for interest rate index

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/35` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `XF-10/INTEREST_RATE_TYPE` | (all) | ERROR | Field 32 is Floating/Variable, or Fixed | Floating/Variable → fields 34..37 mandatory; Fixed → field 33 mandatory. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |
| `XF-17/INDEX_TYPE` | (all) | ERROR | Field `34` is not blank | Field `35` must be non-empty. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |


---

### Field 36 — 36_Interest_rate_index_name

Path: `Position / BondCharacteristics / VariableRate / IndexName`
Codification: FREE_TEXT
Applicability: CIC categories CIC1, CIC2, CIC5, CIC6, CIC7, CIC8, CICD, CICE, CICF
Definition: name of interest rate index

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/36` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `XF-10/INTEREST_RATE_TYPE` | (all) | ERROR | Field 32 is Floating/Variable, or Fixed | Floating/Variable → fields 34..37 mandatory; Fixed → field 33 mandatory. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |
| `XF-17/INDEX_NAME` | (all) | ERROR | Field `34` is not blank | Field `36` must be non-empty. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |


---

### Field 37 — 37_Interest_rate_margin

Path: `Position / BondCharacteristics / VariableRate / Margin`
Codification: NUMERIC
Applicability: CIC categories CIC1, CIC2, CIC5, CIC6, CIC7, CIC8, CICD, CICE, CICF
Definition: Facial margin as a percentage of nominal amount on an annual basis

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/37` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `XF-10/INTEREST_RATE_TYPE` | (all) | ERROR | Field 32 is Floating/Variable, or Fixed | Floating/Variable → fields 34..37 mandatory; Fixed → field 33 mandatory. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |
| `XF-17/INDEX_MARGIN` | (all) | ERROR | Field `34` is not blank | Field `37` must be non-empty. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |


---

### Field 38 — 38_Coupon_payment_frequency

Path: `Position / BondCharacteristics / CouponFrequency`
Codification: CLOSED_LIST, closed list of 6 entries
Applicability: CIC categories CIC1, CIC2, CIC5, CIC6, CIC7, CIC8, CICD, CICE, CICF; CIC7 sub-categories [3, 4, 5]; CICF sub-categories [1, 3, 4]; CICE sub-categories [1]; CICD sub-categories [1, 3]
Definition: number of coupon payment per year 0 = other than below options: 1= annual 2= biannual 4= quarterly 12= monthly 52= weekly

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/38/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/38/SST` | SST (FINMA) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/38` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `XF-08/COUPON_FREQUENCY` | (all) | ERROR | Field 38 (Coupon payment frequency) is populated | Value must be one of {0, 1, 2, 4, 12, 52}. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |
| `XF-13/PIK` | (all) | ERROR | Field 146 (PIK) is populated | Value must be one of {0,1,2,3,4} and case-specific fields must be present. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |

**Referenced as source by:** `XF-08/COUPON_FREQUENCY`

---

### Field 39 — 39_Maturity_date

Path: `Position / BondCharacteristics / Redemption / MaturityDate`
Codification: DATE
Applicability: CIC categories CIC1, CIC2, CIC5, CIC6, CIC7, CIC8, CICA, CICB, CICC, CICD, CICE, CICF; CIC7 sub-categories [3, 4, 5]
Definition: Last redemption date

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/39/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/39/SST` | SST (FINMA) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/39` | (all) | ERROR | Populated cell does not match the codification (DATE) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `XF-11/MATURITY_AFTER_REPORTING` | (all) | WARNING | Row CIC ∈ {1, 2, 5, 6, 8} and field 39 is populated | Field 39 (Maturity date) must not precede field 7 (Reporting date). | Severity = WARNING — surfaced in the report but not factored into the score (only ERROR severity feeds CROSS_FIELD_CONSISTENCY). |
| `XF-13/PIK` | (all) | ERROR | Field 146 (PIK) is populated | Value must be one of {0,1,2,3,4} and case-specific fields must be present. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |


---

### Field 40 — 40_Redemption_type

Path: `Position / BondCharacteristics / Redemption /Type`
Codification: CLOSED_LIST, closed list of 3 entries
Applicability: CIC categories CIC1, CIC2, CIC5, CIC6, CIC7, CIC8, CICD, CICE; CIC7 sub-categories [3, 4, 5]; CICE sub-categories [1]; CICD sub-categories [1, 3]
Definition: Type of redemption payment schedule : bullet, constant annuity…

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/40/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/40/SST` | SST (FINMA) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/40` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `XF-13/PIK` | (all) | ERROR | Field 146 (PIK) is populated | Value must be one of {0,1,2,3,4} and case-specific fields must be present. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |


---

### Field 41 — 41_Redemption_rate

Path: `Position / BondCharacteristics / Redemption / Rate`
Codification: NUMERIC
Applicability: CIC categories CIC1, CIC2, CIC5, CIC6, CIC7, CIC8, CICD, CICE; CIC7 sub-categories [3, 4, 5]; CICE sub-categories [1]; CICD sub-categories [1, 3]
Definition: Redemption amount in % of nominal amount

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/41/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/41/SST` | SST (FINMA) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/41` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `XF-13/PIK` | (all) | ERROR | Field 146 (PIK) is populated | Value must be one of {0,1,2,3,4} and case-specific fields must be present. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |


---

### Field 42 — 42_Callable_putable

Path: `Position / BondCharacteristics / OptionalCallPut / CallPutType`
Codification: CLOSED_LIST, closed list of 4 entries
Applicability: CIC categories CIC1, CIC2, CIC5, CIC6, CIC7, CIC8
Definition: Cal = Call Put = Put Cap = Cap Flr= Floor empty if none

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/42/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/42/SST` | SST (FINMA) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/42` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |

**Referenced as source by:** `XF-18/CALL_PUT_DATE`, `XF-18/OPTION_DIRECTION`, `XF-19/STRIKE_PRICE`

---

### Field 43 — 43_Call_put_date

Path: `Position / BondCharacteristics / OptionalCallPut / CallPutDate`
Codification: DATE
Applicability: CIC categories CIC1, CIC2, CIC5, CIC6, CIC7, CIC8
Definition: Next call/put date

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/43` | (all) | ERROR | Populated cell does not match the codification (DATE) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `XF-18/CALL_PUT_DATE` | (all) | ERROR | Field `42` ∈ [CAL, PUT] | Field `43` must be non-empty. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |


---

### Field 44 — 44_Issuer_bearer_option_exercise

Path: `Position / BondCharacteristics / OptionalCallPut / OptionDirection`
Codification: CLOSED_LIST, closed list of 3 entries
Applicability: CIC categories CIC1, CIC2, CIC5, CIC6, CIC7, CIC8
Definition: I : issuer B : bearer O : Both

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/44` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `XF-18/OPTION_DIRECTION` | (all) | ERROR | Field `42` ∈ [CAL, PUT] | Field `44` must be non-empty. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |


---

### Field 45 — 45_Strike_price_for_embedded_(call_put)_options

Path: `Position / BondCharacteristics / OptionalCallPut / StrikePrice`
Codification: NUMERIC
Applicability: CIC categories CIC1, CIC2, CIC5, CIC6, CIC7, CIC8
Definition: strike price, floor or cap rate for embedded options expressed as a percentage of the nominal amount.

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/45` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `XF-19/STRIKE_PRICE` | (all) | ERROR | Field `42` is not blank | Field `45` must be non-empty. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |


---

### Field 46 — 46_Issuer_name

Path: `Position / CreditRiskData / InstrumentIssuer / Name`
Codification: FREE_TEXT
Applicability: CIC categories CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CICA, CICB, CICC, CICD, CICE, CICF
Definition: name of the issuer

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/46/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/46/SST` | SST (FINMA) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/46` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 47 — 47_Issuer_identification_code

Path: `Position / CreditRiskData / InstrumentIssuer / Code / Code`
Codification: ALPHANUMERIC (max 20)
Applicability: CIC categories CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CICA, CICB, CICC, CICD, CICE, CICF
Definition: LEI

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/47` | (all) | ERROR | Populated cell does not match the codification (ALPHANUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `LEI/47/48` | (all) | ERROR | Type field `48` = `1` and this cell is not a valid 20-char LEI with mod-97 checksum | Identifier cannot be resolved against ISO 17442 (GLEIF). | FORMAT_CONFORMANCE −1/M |
| `XF-20/ISSUER_LEI_PRESENT` | (all) | ERROR | Field `48` = "1" | Field `47` must be non-empty. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |

**External validation:** GLEIF LEI lookup (active when field `48` = `1`)

---

### Field 48 — 48_Type_of_identification_code_for_issuer

Path: `Position / CreditRiskData / InstrumentIssuer / Code / CodificationSystem`
Codification: FREE_TEXT
Applicability: CIC categories CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CICA, CICB, CICC, CICD, CICE, CICF
Definition: C0220 1- LEI 9 - None

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/48/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/48/SST` | SST (FINMA) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/48` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |

**Referenced as source by:** `XF-20/ISSUER_LEI_PRESENT`

---

### Field 49 — 49_Name_of_the_group_of_the_issuer

Path: `Position / CreditRiskData / IssuerGroup / Name`
Codification: FREE_TEXT
Applicability: CIC categories CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CICA, CICB, CICC, CICD, CICE, CICF
Definition: Name of the highest parent company

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/49/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/49/SST` | SST (FINMA) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/49` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 50 — 50_Identification_of_the_group

Path: `Position / CreditRiskData / IssuerGroup / Code / Code`
Codification: ALPHANUMERIC (max 20)
Applicability: CIC categories CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CICA, CICB, CICC, CICD, CICE, CICF
Definition: LEI

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/50` | (all) | ERROR | Populated cell does not match the codification (ALPHANUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `LEI/50/51` | (all) | ERROR | Type field `51` = `1` and this cell is not a valid 20-char LEI with mod-97 checksum | Identifier cannot be resolved against ISO 17442 (GLEIF). | FORMAT_CONFORMANCE −1/M |
| `XF-21/GROUP_LEI_PRESENT` | (all) | ERROR | Field `51` = "1" | Field `50` must be non-empty. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |

**External validation:** GLEIF LEI lookup (active when field `51` = `1`)

---

### Field 51 — 51_Type_of_identification_code_for_issuer_group

Path: `Position / CreditRiskData / IssuerGroup / Code / CodificationSystem`
Codification: FREE_TEXT
Applicability: CIC categories CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CICA, CICB, CICC, CICD, CICE, CICF
Definition: C0260 1- LEI 9 - None

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/51/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/51/SST` | SST (FINMA) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/51` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |

**Referenced as source by:** `XF-21/GROUP_LEI_PRESENT`

---

### Field 52 — 52_Issuer_country

Path: `Position / CreditRiskData / IssuerCountry`
Codification: ISO_3166_A2
Applicability: all rows
Definition: Country of the issuer company

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | M | Mandatory — must always be present. |
| `NW_675` | NW 675 | M | Mandatory — must always be present. |
| `SST` | SST (FINMA) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/52/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/52/IORP_EIOPA_ECB` | IORP / EIOPA / ECB | ERROR | Cell is empty for an active row of profile IORP / EIOPA / ECB | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/52/NW_675` | NW 675 | ERROR | Cell is empty for an active row of profile NW 675 | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/52/SST` | SST (FINMA) | ERROR | Cell is empty for an active row of profile SST (FINMA) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/52` | (all) | ERROR | Populated cell does not match the codification (ISO_3166_A2) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 53 — 53_Issuer_economic_area

Path: `Position / CreditRiskData / EconomicArea`
Codification: FREE_TEXT
Applicability: all rows
Definition: Economic area of the Issuer 1=EEA / 2=NON EEA / 3=NON OECD

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/53` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 54 — 54_Economic_sector

Path: `Position / CreditRiskData / EconomicSector`
Codification: NACE
Applicability: CIC categories CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CICA, CICB, CICC, CICD, CICE, CICF
Definition: Economic sector

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | C | Conditional — required when the spec's applicability/condition holds. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/54/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/54/NW_675` | NW 675 | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/54/SST` | SST (FINMA) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/54` | (all) | ERROR | Populated cell does not match the codification (NACE) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 55 — 55_Covered_not_covered

Path: `Position / CreditRiskData / Covered`
Codification: CLOSED_LIST, closed list of 2 entries
Applicability: CIC categories CIC1, CIC2

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/55/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/55/SST` | SST (FINMA) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/55` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 56 — 56_Securitisation

Path: `Position / Securitisation / Securitised`
Codification: CLOSED_LIST, closed list of 11 entries
Applicability: CIC categories CIC5, CIC6
Definition: Securitisation typology

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/56/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/56/SST` | SST (FINMA) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/56` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 57 — 57_Explicit_guarantee_by_the_country_of_issue

Path: `Position / CreditRiskData / StateGuarantee`
Codification: CLOSED_LIST, closed list of 2 entries
Applicability: CIC categories CIC1, CIC2, CIC5, CIC6
Definition: Y = guaranteed N = without guarantee

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/57/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/57/SST` | SST (FINMA) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/57` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 58 — 58_Subordinated_debt

Path: `Position / SubordinatedDebt`
Codification: CLOSED_LIST, closed list of 2 entries
Applicability: all rows
Definition: Subordinated or not ?

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/58` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 58b — 58b_Nature_of_the_tranche

Path: `Position / Securitisation / TrancheLevel`
Codification: FREE_TEXT
Applicability: all rows
Definition: Tranche level (seniority)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/58b` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 59 — 59_Credit_quality_step

Path: `Position / CreditRiskData / CreditQualitStep`
Codification: FREE_TEXT
Applicability: CIC categories CIC1, CIC2, CIC5, CIC6, CIC7, CIC8, CICB, CICC, CICD, CICE, CICF; CIC7 sub-categories [3, 4, 5]
Definition: Credit quality step as defined by S2 regulation

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | I | Informational — populate if available, no enforcement. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | I | Informational — populate if available, no enforcement. |
| `SST` | SST (FINMA) | I | Informational — populate if available, no enforcement. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/59` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 60 — 60_Call_Put_Cap_Floor

Path: `Position / DerivativeOrConvertible / OptionCharacteristics / CallPutType`
Codification: CLOSED_LIST, closed list of 4 entries
Applicability: CIC categories CIC2, CICB, CICC, CICE; CIC2 sub-categories [2]
Definition: Cal = Call Put = Put Cap = Cap Flr= Floor empty if none

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/60/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/60/SST` | SST (FINMA) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/60` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 61 — 61_Strike_price

Path: `Position / DerivativeOrConvertible / OptionCharacteristics / StrikePrice`
Codification: NUMERIC
Applicability: CIC categories CIC2, CICB, CICC, CICE; CIC2 sub-categories [2]
Definition: Strike price expressed as the quotation of the underlying asset

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/61/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/61/SST` | SST (FINMA) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/61` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 62 — 62_Conversion_factor_(convertibles)_concordance_factor_parity_(options)

Path: `Position / DerivativeOrConvertible / OptionCharacteristics / ConversionRatio`
Codification: NUMERIC
Applicability: CIC categories CIC2, CICA, CICB, CICC; CIC2 sub-categories [2]

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/62/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/62/SST` | SST (FINMA) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/62` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 63 — 63_Effective_date_of_instrument

Path: `Position / DerivativeOrConvertible / OptionCharacteristics / Effective Date`
Codification: DATE
Applicability: all rows
Definition: Effective Date

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/63` | (all) | ERROR | Populated cell does not match the codification (DATE) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 64 — 64_Exercise_type

Path: `Position / DerivativeOrConvertible / OptionCharacteristics / OptionStyle`
Codification: CLOSED_LIST, closed list of 4 entries
Applicability: CIC categories CICB, CICC
Definition: AMerican, EUropean, ASiatic, BErmudian

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/64/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/64/SST` | SST (FINMA) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/64` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 65 — 65_Hedging_rolling

Path: `Position / HedgingStrategy`
Codification: CLOSED_LIST, closed list of 3 entries
Applicability: CIC categories CICA, CICB, CICC, CICD, CICE, CICF
Definition: Indication of existing Risk Mitigation program ( Y = used for Risk Mitigation purpose and the position is systematically rolled before maturity, N = used for hedging purpose but no systematic roll before maturity); EPM = Efficient Portfolio Management / not used for hedging purpose .

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | C | Conditional — required when the spec's applicability/condition holds. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/65/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/65/NW_675` | NW 675 | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/65` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 67 — 67_CIC_of_the_underlying_asset

Path: `Position / DerivativeOrConvertible /  UnderlyingInstrument / InstrumentCIC`
Codification: CIC
Applicability: CIC categories CIC2, CICA, CICB, CICC, CICD, CICF; CICD sub-categories [4, 5]; CIC2 sub-categories [2]
Definition: CIC Code (Complementary Identification Code).

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/67` | (all) | ERROR | Populated cell does not match the codification (CIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `XF-14/UNDERLYING_CIC` | (all) | ERROR | Row CIC ∈ {2, A, B, C, D, F} | Field 67 (Underlying CIC) must be populated. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |


---

### Field 68 — 68_Identification_code_of_the_underlying_asset

Path: `Position / DerivativeOrConvertible /  UnderlyingInstrument / InstrumentCode / Code`
Codification: FREE_TEXT
Applicability: CIC categories CIC2, CICA, CICB, CICC, CICD, CICF; CICD sub-categories [4, 5]; CIC2 sub-categories [2]
Definition: identification code of underlying asset

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/68/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/68/SST` | SST (FINMA) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/68` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `ISIN/68/69` | (all) | ERROR | Type field `69` = `1` and this cell is not a valid 12-char ISIN with Luhn checksum | Identifier cannot be resolved against ISO 6166. | FORMAT_CONFORMANCE −1/M |

**External validation:** OpenFIGI ISIN lookup (active when field `69` = `1`)

---

### Field 69 — 69_Type_of_identification_code_for_the_underlying_asset

Path: `Position / UnderlyingInstrument / InstrumentCode / CodificationSystem`
Codification: CLOSED_LIST, closed list of 10 entries
Applicability: CIC categories CIC2, CICA, CICB, CICC, CICD, CICF; CICD sub-categories [4, 5]; CIC2 sub-categories [2]
Definition: name of the codification used for identification of the underlying asset

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/69/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/69/SST` | SST (FINMA) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/69` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 70 — 70_Name_of_the_underlying_asset

Path: `Position / DerivativeOrConvertible /  UnderlyingInstrument / InstrumentName`
Codification: FREE_TEXT
Applicability: CIC categories CIC2, CICA, CICB, CICC, CICD, CICF; CICD sub-categories [4, 5]; CIC2 sub-categories [2]
Definition: Name

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/70/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/70/SST` | SST (FINMA) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/70` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 71 — 71_Quotation_currency_of_the_underlying_asset_(C)

Path: `Position / DerivativeOrConvertible /  UnderlyingInstrument / Valuation / Currency`
Codification: ISO_4217
Applicability: CIC categories CIC2, CICA, CICB, CICC, CICD, CICF; CICD sub-categories [4, 5]; CIC2 sub-categories [2]
Definition: currency of quotation for the asset

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/71/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/71/SST` | SST (FINMA) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/71` | (all) | ERROR | Populated cell does not match the codification (ISO_4217) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 72 — 72_Last_valuation_price_of_the_underlying_asset

Path: `Position / DerivativeOrConvertible /  UnderlyingInstrument / Valuation / MarketPrice`
Codification: NUMERIC
Applicability: CIC categories CIC2, CICA, CICB, CICC, CICD, CICF; CICD sub-categories [4, 5]; CIC2 sub-categories [2]
Definition: Last valuation price of the underlying asset

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/72/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/72/SST` | SST (FINMA) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/72` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 73 — 73_Country_of_quotation_of_the_underlying_asset

Path: `Position / DerivativeOrConvertible /  UnderlyingInstrument / Valuation / Country`
Codification: ISO_3166_A2
Applicability: all rows
Definition: Country of quotation of the underlying asset

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/73` | (all) | ERROR | Populated cell does not match the codification (ISO_3166_A2) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 74 — 74_Economic_area_of_quotation_of_the_underlying_asset

Path: `Position / DerivativeOrConvertible /  UnderlyingInstrument / Valuation / EconomicArea`
Codification: FREE_TEXT
Applicability: CIC categories CIC2, CICA, CICB, CICC, CICD; CICD sub-categories [4, 5]; CICC sub-categories [1, 4]; CIC2 sub-categories [2]; CICB sub-categories [1, 4]; CICA sub-categories [1]
Definition: economic area of quotation 0= non listed, listed 1=EEA / 2=NON EEA / 3=NON OECD

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/74/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/74` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 75 — 75_Coupon_rate_of_the_underlying_asset

Path: `Position / DerivativeOrConvertible / UnderlyingInstrument / BondCharacteristics / CouponRate`
Codification: NUMERIC
Applicability: all rows
Definition: Fixed rate : coupon rate as a percentage of nominal amount all rates are expressed on an annual basis

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/75` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 76 — 76_Coupon_payment_frequency_of_the_underlying_asset

Path: `Position / DerivativeOrConvertible / UnderlyingInstrument / BondCharacteristics / CouponFrequency`
Codification: CLOSED_LIST, closed list of 6 entries
Applicability: all rows
Definition: number of coupon payment per year 0 = other than below options: 1= annual 2= biannual 4= quarterly 12= monthly 52= weekly

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/76` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 77 — 77_Maturity_date_of_the_underlying_asset

Path: `Position / DerivativeOrConvertible / UnderlyingInstrument / BondCharacteristics / Redemption / MaturityDate`
Codification: DATE
Applicability: all rows
Definition: Last redemption date

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/77` | (all) | ERROR | Populated cell does not match the codification (DATE) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 78 — 78_Redemption_profile_of_the_underlying_asset

Path: `Position / DerivativeOrConvertible / UnderlyingInstrument / BondCharacteristics / Redemption / Type`
Codification: CLOSED_LIST, closed list of 2 entries
Applicability: all rows
Definition: Type of redemption payment schedule : bullet, constant annuity…

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/78` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 79 — 79_Redemption_rate_of_the_underlying_asset

Path: `Position / DerivativeOrConvertible / UnderlyingInstrument / BondCharacteristics / Redemption / Rate`
Codification: NUMERIC
Applicability: all rows
Definition: Redemption amount in % of nominal amount

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/79` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 80 — 80_Issuer_name_of_the_underlying_asset

Path: `Position / DerivativeOrConvertible / UnderlyingInstrument / CreditRiskData / InstrumentIssuer / Name`
Codification: FREE_TEXT
Applicability: CIC categories CIC2, CICA, CICB, CICC, CICD, CICF; CICF sub-categories [1, 3, 4]; CICD sub-categories [4, 5]; CICC sub-categories [1, 4]; CIC2 sub-categories [2]; CICB sub-categories [1, 4]; CICA sub-categories [1]
Definition: name of the issuer

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/80/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/80` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 81 — 81_Issuer_identification_code_of_the_underlying_asset

Path: `Position / DerivativeOrConvertible / UnderlyingInstrument / CreditRiskData / InstrumentIssuer / Code / Code`
Codification: FREE_TEXT
Applicability: CIC categories CIC2, CICA, CICB, CICC, CICD, CICF; CICF sub-categories [1, 3, 4]; CICD sub-categories [4, 5]; CICC sub-categories [1, 4]; CIC2 sub-categories [2]; CICB sub-categories [1, 4]; CICA sub-categories [1]
Definition: identification code of the issuer

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/81/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/81` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `LEI/81/82` | (all) | ERROR | Type field `82` = `1` and this cell is not a valid 20-char LEI with mod-97 checksum | Identifier cannot be resolved against ISO 17442 (GLEIF). | FORMAT_CONFORMANCE −1/M |

**External validation:** GLEIF LEI lookup (active when field `82` = `1`)

---

### Field 82 — 82_Type_of_issuer_identification_code_of_the_underlying_asset

Path: `Position / UnderlyingInstrument / Issuer / InstrumentIssuer / Identification / Code`
Codification: FREE_TEXT
Applicability: CIC categories CIC2, CICA, CICB, CICC, CICD, CICF; CICF sub-categories [1, 3, 4]; CICD sub-categories [4, 5]; CICC sub-categories [1, 4]; CIC2 sub-categories [2]; CICB sub-categories [1, 4]; CICA sub-categories [1]
Definition: C0220 1- LEI 9 - None

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/82/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/82` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 83 — 83_Name_of_the_group_of_the_issuer_of_the_underlying_asset

Path: `Position / DerivativeOrConvertible / UnderlyingInstrument / CreditRiskData / IssuerGroup / Name`
Codification: FREE_TEXT
Applicability: CIC categories CIC2, CICA, CICB, CICC, CICD, CICF; CICF sub-categories [1, 3, 4]; CICD sub-categories [4, 5]; CICC sub-categories [1, 4]; CIC2 sub-categories [2]; CICB sub-categories [1, 4]; CICA sub-categories [1]
Definition: Name of the highest parent company

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/83/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/83` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 84 — 84_Identification_of_the_group_of_the_underlying_asset

Path: `Position / DerivativeOrConvertible / UnderlyingInstrument / CreditRiskData / IssuerGroup / Code / Code`
Codification: FREE_TEXT
Applicability: CIC categories CIC2, CICA, CICB, CICC, CICD, CICF
Definition: Identification code of the group

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/84` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `LEI/84/85` | (all) | ERROR | Type field `85` = `1` and this cell is not a valid 20-char LEI with mod-97 checksum | Identifier cannot be resolved against ISO 17442 (GLEIF). | FORMAT_CONFORMANCE −1/M |
| `XF-22/UNDERLYING_GROUP_LEI` | (all) | ERROR | Field `85` = "1" | Field `84` must be non-empty. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |

**External validation:** GLEIF LEI lookup (active when field `85` = `1`)

---

### Field 85 — 85_Type_of_the_group_identification_code_of_the_underlying_asset

Path: `Position / DerivativeOrConvertible / UnderlyingInstrument / CreditRiskData / IssuerGroup / Code / CodificationSystem`
Codification: FREE_TEXT
Applicability: CIC categories CIC2, CICA, CICB, CICC, CICD, CICF; CICF sub-categories [1, 3, 4]; CICD sub-categories [4, 5]; CICC sub-categories [1, 4]; CIC2 sub-categories [2]; CICB sub-categories [1, 4]; CICA sub-categories [1]
Definition: C0260 1- LEI 9 - None

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/85/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/85` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |

**Referenced as source by:** `XF-22/UNDERLYING_GROUP_LEI`

---

### Field 86 — 86_Issuer_country_of_the_underlying_asset

Path: `Position / DerivativeOrConvertible / UnderlyingInstrument / CreditRiskData / Country`
Codification: ISO_3166_A2
Applicability: CIC categories CIC2, CICA, CICB, CICC, CICD, CICF; CICF sub-categories [1, 3, 4]; CICD sub-categories [4, 5]; CICC sub-categories [1, 4]; CIC2 sub-categories [2]; CICB sub-categories [1, 4]; CICA sub-categories [1]
Definition: Country of the issuer company

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/86/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/86` | (all) | ERROR | Populated cell does not match the codification (ISO_3166_A2) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 87 — 87_Issuer_economic_area_of_the_underlying_asset

Path: `Position / DerivativeOrConvertible / UnderlyingInstrument / CreditRiskData / EconomicArea`
Codification: FREE_TEXT
Applicability: all rows
Definition: economic area of the Issuer 1=EEA / 2=NON EEA / 3=NON OECD

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/87` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 88 — 88_Explicit_guarantee_by_the_country_of_issue_of_the_underlying_asset

Path: `Position / DerivativeOrConvertible / UnderlyingInstrument / CreditRiskData  / StateGuarantee`
Codification: CLOSED_LIST, closed list of 2 entries
Applicability: all rows
Definition: Y = Guaranteed N = without guarantee

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/88` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 89 — 89_Credit_quality_step_of_the_underlying_asset

Path: `Position / DerivativeOrConvertible / UnderlyingInstrument / CreditRiskData  / CreditQualityStep`
Codification: FREE_TEXT
Applicability: CIC categories CIC2, CICA, CICB, CICC, CICF; CICF sub-categories [1, 2]; CICC sub-categories [2]; CIC2 sub-categories [2]; CICB sub-categories [2]; CICA sub-categories [2]
Definition: Credit quality step as defined by S2 regulation

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | I | Informational — populate if available, no enforcement. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | I | Informational — populate if available, no enforcement. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/89` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 90 — 90_Modified_duration_to_maturity_date

Path: `Position / Analytics / ModifiedDurationToMaturity`
Codification: NUMERIC
Applicability: CIC categories CIC1, CIC2, CIC5, CIC6, CIC7, CIC8, CICA, CICB, CICC, CICD; CIC7 sub-categories [3, 4, 5]; CIC6 sub-categories [2, 4]; CIC5 sub-categories [2, 4]; CICD sub-categories [1, 3]; CICC sub-categories [2]; CICB sub-categories [2]; CICA sub-categories [2]

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | O | Optional — populate when applicable. |
| `NW_675` | NW 675 | C | Conditional — required when the spec's applicability/condition holds. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/90/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/90/NW_675` | NW 675 | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/90/SST` | SST (FINMA) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/90` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 91 — 91_Modified_duration_to_next_option_exercise_date

Path: `Position / Analytics / ModifiedDurationToCall`
Codification: NUMERIC
Applicability: CIC categories CIC1, CIC2, CIC5, CIC6, CIC7, CIC8, CICB, CICC, CICD; CIC7 sub-categories [3, 4, 5]; CIC6 sub-categories [2, 4]; CIC5 sub-categories [2, 4]; CICD sub-categories [1, 3]; CICC sub-categories [2]; CICB sub-categories [2]

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | O | Optional — populate when applicable. |
| `NW_675` | NW 675 | C | Conditional — required when the spec's applicability/condition holds. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/91/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/91/NW_675` | NW 675 | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/91/SST` | SST (FINMA) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/91` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 92 — 92_Credit_sensitivity

Path: `Position / Analytics / CreditSensitivity`
Codification: NUMERIC
Applicability: CIC categories CIC1, CIC2, CIC5, CIC6, CIC7, CIC8, CICD, CICF; CIC7 sub-categories [3, 4, 5]; CIC6 sub-categories [2, 4]; CIC5 sub-categories [2, 4]; CICD sub-categories [1, 3]

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/92/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/92/SST` | SST (FINMA) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/92` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 93 — 93_Sensitivity_to_underlying_asset_price_(delta)

Path: `Position / Analytics / Delta`
Codification: NUMERIC
Applicability: CIC categories CIC2, CIC5, CIC6, CICA, CICB, CICC, CICE, CICF; CIC6 sub-categories [1, 3, 6]; CIC5 sub-categories [1, 3, 6]; CIC2 sub-categories [2]; CICA sub-categories [3, 5]
Definition: Sensitivity to the underlying asset

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/93/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `COND_PRESENCE/93/SST` | SST (FINMA) | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/93` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 94 — 94_Convexity_gamma_for_derivatives

Path: `Position / Analytics / Convexity`
Codification: NUMERIC
Applicability: all rows
Definition: Convexity for interest rates instruments; or gamma for derivatives with optional components

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/94` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 94b — 94b_Vega

Path: `Position / Analytics / Vega`
Codification: NUMERIC
Applicability: all rows

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/94b` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 95 — 95_Identification_of_the_original_portfolio_for_positions_embedded_in_a_fund

Path: `Position / LookThroughISIN`
Codification: FREE_TEXT
Applicability: all rows
Definition: identification code of the investee funds

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | C | Conditional — required when the spec's applicability/condition holds. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/95` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 97 — 97_SCR_mrkt_IR_up_weight_over_NAV

Path: `Position / ContributionToSCR / MktIntUp`
Codification: NUMERIC
Applicability: all rows
Definition: Capital requirement for interest rate risk for the "up" shock (Delta between Market value before and market value after stress)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/97` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `XF-01/COMPLETE_SCR_DELIVERY` | (all) | ERROR | Field 11 (CompleteSCRDelivery) = Y | All SCR contribution fields 97..105b must be present. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |


---

### Field 98 — 98_SCR_mrkt_IR_down_weight_over_NAV

Path: `Position / ContributionToSCR / MktintDown`
Codification: NUMERIC
Applicability: all rows
Definition: Capital requirement for interest rate risk for the "down" shock (Delta between Market value before and market value after stress)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/98` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `XF-01/COMPLETE_SCR_DELIVERY` | (all) | ERROR | Field 11 (CompleteSCRDelivery) = Y | All SCR contribution fields 97..105b must be present. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |


---

### Field 99 — 99_SCR_mrkt_eq_type1_weight_over_NAV

Path: `Position / ContributionToSCR / MktEqGlobal`
Codification: NUMERIC
Applicability: all rows
Definition: Capital requirement for equity risk - Type 1 *) (Delta between Market value before and market value after stress)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/99` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `XF-01/COMPLETE_SCR_DELIVERY` | (all) | ERROR | Field 11 (CompleteSCRDelivery) = Y | All SCR contribution fields 97..105b must be present. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |


---

### Field 100 — 100_SCR_mrkt_eq_type2_weight_over_NAV

Path: `Position / ContributionToSCR / MktEqOther`
Codification: NUMERIC
Applicability: all rows
Definition: Capital requirement for equity risk - Type 2 *) (Delta between Market value before and market value after stress)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/100` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `XF-01/COMPLETE_SCR_DELIVERY` | (all) | ERROR | Field 11 (CompleteSCRDelivery) = Y | All SCR contribution fields 97..105b must be present. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |


---

### Field 101 — 101_SCR_mrkt_prop_weight_over_NAV

Path: `Position / ContributionToSCR / MktProp`
Codification: NUMERIC
Applicability: all rows
Definition: Capital requirement for property risk (Delta between Market value before and market value after stress)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/101` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `XF-01/COMPLETE_SCR_DELIVERY` | (all) | ERROR | Field 11 (CompleteSCRDelivery) = Y | All SCR contribution fields 97..105b must be present. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |


---

### Field 102 — 102_SCR_mrkt_spread_bonds_weight_over_NAV

Path: `Position / ContributionToSCR / MktSpread / Bonds`
Codification: NUMERIC
Applicability: all rows
Definition: Capital requirement for spread risk on bonds (Delta between Market value before and market value after stress)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/102` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `XF-01/COMPLETE_SCR_DELIVERY` | (all) | ERROR | Field 11 (CompleteSCRDelivery) = Y | All SCR contribution fields 97..105b must be present. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |


---

### Field 103 — 103_SCR_mrkt_spread_structured_weight_over_NAV

Path: `Position / ContributionToSCR / MktSpread / Structured`
Codification: NUMERIC
Applicability: all rows
Definition: Capital requirement for spread risk on structured products (Delta between Market value before and market value after stress)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/103` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `XF-01/COMPLETE_SCR_DELIVERY` | (all) | ERROR | Field 11 (CompleteSCRDelivery) = Y | All SCR contribution fields 97..105b must be present. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |


---

### Field 104 — 104_SCR_mrkt_spread_derivatives_up_weight_over_NAV

Path: `Position / ContributionToSCR / MktSpread / DerivativesUp`
Codification: NUMERIC
Applicability: all rows
Definition: Capital requirement for spread risk - credit derivatives (upward shock) (Delta between Market value before and market value after stress)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/104` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `XF-01/COMPLETE_SCR_DELIVERY` | (all) | ERROR | Field 11 (CompleteSCRDelivery) = Y | All SCR contribution fields 97..105b must be present. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |


---

### Field 105 — 105_SCR_mrkt_spread_derivatives_down_weight_over_NAV

Path: `Position / ContributionToSCR / MktSpread / DerivativesDown`
Codification: NUMERIC
Applicability: all rows
Definition: Capital requirement for spread risk - credit derivatives (downward shock) (Delta between Market value before and market value after stress)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/105` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `XF-01/COMPLETE_SCR_DELIVERY` | (all) | ERROR | Field 11 (CompleteSCRDelivery) = Y | All SCR contribution fields 97..105b must be present. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |


---

### Field 105a — 105a_SCR_mrkt_FX_up_weight_over_NAV

Path: `Position / ContributionToSCR / MktFXUp`
Codification: NUMERIC
Applicability: all rows
Definition: Capital requirement for FX (upward shock) (Delta between Market value before and market value after stress)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/105a` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `XF-01/COMPLETE_SCR_DELIVERY` | (all) | ERROR | Field 11 (CompleteSCRDelivery) = Y | All SCR contribution fields 97..105b must be present. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |


---

### Field 105b — 105b_SCR_mrkt_FX_down_weight_over_NAV

Path: `Position / ContributionToSCR / MktFXDown`
Codification: NUMERIC
Applicability: all rows
Definition: Capital requirement for FX (downward shock) (Delta between Market value before and market value after stress)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/105b` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `XF-01/COMPLETE_SCR_DELIVERY` | (all) | ERROR | Field 11 (CompleteSCRDelivery) = Y | All SCR contribution fields 97..105b must be present. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |


---

### Field 106 — 106_Asset_pledged_as_collateral

Path: `Position / QRTPositionInformation / CollateralisedAsset`
Codification: CLOSED_LIST, closed list of 5 entries
Applicability: all rows
Definition: Indicator used to identify the under-written instruments (Assets D1)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/106` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 107 — 107_Place_of_deposit

Path: `Position / QRTPositionInformation / PlaceOfDeposit`
Codification: FREE_TEXT
Applicability: all rows
Definition: Instruments' place of deposit (S.06.02 - old: Assets D1)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/107` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 108 — 108_Participation

Path: `Position / QRTPositionInformation / Participation`
Codification: FREE_TEXT
Applicability: all rows
Definition: Indicator used to identify the guidelines of participation in accountancy terms

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/108` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 110 — 110_Valorisation_method

Path: `Position / QRTPositionInformation / ValorisationMethod`
Codification: CLOSED_LIST, closed list of 6 entries
Applicability: all rows
Definition: valuation method (cf specifications QRT) (S.06.02 - old: Assets D1)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | O | Optional — populate when applicable. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/110` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 111 — 111_Value_of_acquisition

Path: `Position / QRTPositionInformation / AverageBuyPrice`
Codification: FREE_TEXT
Applicability: all rows
Definition: Value of acquisition (S.06.02 - old: Assets D1)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/111` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 112 — 112_Credit_rating

Path: `Position / QRTPositionInformation / CounterpartyRating / RatingValue`
Codification: UNKNOWN
Applicability: all rows
Definition: Rating of the counterparty / issuer (cf specifications QRT) (S.06.02 - old: Assets D1)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/112` | (all) | ERROR | Populated cell does not match the codification (UNKNOWN) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 113 — 113_Rating_agency

Path: `Position / QRTPositionInformation / CounterpartyRating / RatingAgency`
Codification: UNKNOWN
Applicability: all rows
Definition: Name of the rating agency (cf specification QRT) (S.06.02 - old: Assets D1)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/113` | (all) | ERROR | Populated cell does not match the codification (UNKNOWN) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 114 — 114_Issuer_economic_area

Path: `Position / QRTPositionInformation / IssuerEconomicArea`
Codification: FREE_TEXT
Applicability: all rows
Definition: economic area of the Issuer 1=EEA / 2=NON EEA / 3=NON OECD

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | NA | Not applicable to this profile. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/114` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 115 — 115_Fund_issuer_code

Path: `Portfolio / QRTPortfolioInformation / FundIssuer / Code / Code`
Codification: FREE_TEXT
Applicability: all rows
Definition: LEI when available, otherwise not reported

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | M | Mandatory — must always be present. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/115` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `LEI/115/116` | (all) | ERROR | Type field `116` = `1` and this cell is not a valid 20-char LEI with mod-97 checksum | Identifier cannot be resolved against ISO 17442 (GLEIF). | FORMAT_CONFORMANCE −1/M |
| `XF-23/FUND_ISSUER_LEI` | (all) | ERROR | Field `116` = "1" | Field `115` must be non-empty. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |

**External validation:** GLEIF LEI lookup (active when field `116` = `1`)

---

### Field 116 — 116_Fund_issuer_code_type

Path: `Portfolio / QRTPortfolioInformation / FundIssuer / Code / CodificationSystem`
Codification: UNKNOWN
Applicability: all rows
Definition: C0220 1- LEI 9 - None

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | M | Mandatory — must always be present. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/116/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/116/IORP_EIOPA_ECB` | IORP / EIOPA / ECB | ERROR | Cell is empty for an active row of profile IORP / EIOPA / ECB | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/116` | (all) | ERROR | Populated cell does not match the codification (UNKNOWN) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |

**Referenced as source by:** `XF-23/FUND_ISSUER_LEI`

---

### Field 117 — 117_Fund_issuer_name

Path: `Portfolio / QRTPortfolioInformation / FundIssuer / Name`
Codification: FREE_TEXT
Applicability: all rows
Definition: Name of Issuer of Fund or Share Class

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/117/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/117` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 118 — 118_Fund_issuer_sector

Path: `Portfolio / QRTPortfolioInformation / FundIssuer / EconomicSector`
Codification: FREE_TEXT
Applicability: all rows
Definition: NACE code of Issuer of Fund or Share Class

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | M | Mandatory — must always be present. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/118/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/118/IORP_EIOPA_ECB` | IORP / EIOPA / ECB | ERROR | Cell is empty for an active row of profile IORP / EIOPA / ECB | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/118` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 119 — 119_Fund_issuer_group_code

Path: `Portfolio / QRTPortfolioInformation / FundIssuerGroup / Code / Code`
Codification: FREE_TEXT
Applicability: all rows
Definition: LEI of ultimate parent when available, otherwise not reported

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | M | Mandatory — must always be present. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/119` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `LEI/119/120` | (all) | ERROR | Type field `120` = `1` and this cell is not a valid 20-char LEI with mod-97 checksum | Identifier cannot be resolved against ISO 17442 (GLEIF). | FORMAT_CONFORMANCE −1/M |
| `XF-24/FUND_GROUP_LEI` | (all) | ERROR | Field `120` = "1" | Field `119` must be non-empty. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |

**External validation:** GLEIF LEI lookup (active when field `120` = `1`)

---

### Field 120 — 120_Fund_issuer_group_code_type

Path: `Portfolio / QRTPortfolioInformation / FundIssuerGroup / Code / CodificationSystem`
Codification: UNKNOWN
Applicability: all rows
Definition: C0260 1- LEI 9 - None

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | M | Mandatory — must always be present. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/120/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/120/IORP_EIOPA_ECB` | IORP / EIOPA / ECB | ERROR | Cell is empty for an active row of profile IORP / EIOPA / ECB | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/120` | (all) | ERROR | Populated cell does not match the codification (UNKNOWN) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |

**Referenced as source by:** `XF-24/FUND_GROUP_LEI`

---

### Field 121 — 121_Fund_issuer_group_name

Path: `Portfolio / QRTPortfolioInformation / FundIssuerGroup / Name`
Codification: UNKNOWN
Applicability: all rows
Definition: Name of Ultimate parent of issuer of Fund or Share Class

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | M | Mandatory — must always be present. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/121/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/121/IORP_EIOPA_ECB` | IORP / EIOPA / ECB | ERROR | Cell is empty for an active row of profile IORP / EIOPA / ECB | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/121` | (all) | ERROR | Populated cell does not match the codification (UNKNOWN) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 122 — 122_Fund_issuer_country

Path: `Portfolio / QRTPortfolioInformation / FundIssuer / Country`
Codification: ISO_3166_A2
Applicability: all rows
Definition: Country ISO of Issuer of Fund or Share Class

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | M | Mandatory — must always be present. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/122/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/122/IORP_EIOPA_ECB` | IORP / EIOPA / ECB | ERROR | Cell is empty for an active row of profile IORP / EIOPA / ECB | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/122` | (all) | ERROR | Populated cell does not match the codification (ISO_3166_A2) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 123 — 123_Fund_CIC

Path: `Portfolio / QRTPortfolioInformation / PortfolioCIC`
Codification: UNKNOWN
Applicability: all rows
Definition: CIC code - Fund or Share Class (4 digits)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | M | Mandatory — must always be present. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/123/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/123/IORP_EIOPA_ECB` | IORP / EIOPA / ECB | ERROR | Cell is empty for an active row of profile IORP / EIOPA / ECB | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/123` | (all) | ERROR | Populated cell does not match the codification (UNKNOWN) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 123a — 123a_Fund_custodian_country

Path: `Portfolio / QRTPortfolioInformation / FundCustodianCountry`
Codification: ISO_3166_A2
Applicability: all rows
Definition: First level of Custody - Fund or seggregated account Custodian

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | M | Mandatory — must always be present. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/123a/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/123a/IORP_EIOPA_ECB` | IORP / EIOPA / ECB | ERROR | Cell is empty for an active row of profile IORP / EIOPA / ECB | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/123a` | (all) | ERROR | Populated cell does not match the codification (ISO_3166_A2) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 124 — 124_Duration

Path: `Portfolio / QRTPortfolioInformation / PortfolioModifiedDuration`
Codification: UNKNOWN
Applicability: all rows
Definition: mainly invested in bonds (>50%) - Fund modified Duration (Residual modified duration)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | M | Mandatory — must always be present. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/124/IORP_EIOPA_ECB` | IORP / EIOPA / ECB | ERROR | Cell is empty for an active row of profile IORP / EIOPA / ECB | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/124` | (all) | ERROR | Populated cell does not match the codification (UNKNOWN) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 125 — 125_Accrued_income_(Security Denominated Currency)

Path: `Portfolio / QRTPortfolioInformation /  AccruedIncomeQC ????`
Codification: UNKNOWN
Applicability: all rows
Definition: Amount of accrued income in security denomination currency at report date

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | O | Optional — populate when applicable. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/125` | (all) | ERROR | Populated cell does not match the codification (UNKNOWN) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 126 — 126_Accrued_income_(Portfolio Denominated Currency)

Path: `Portfolio / QRTPortfolioInformation / AccruedIncomePC`
Codification: UNKNOWN
Applicability: all rows
Definition: Amount of accrued income in portfolio denomination currency at report date

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | M | Mandatory — must always be present. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/126/IORP_EIOPA_ECB` | IORP / EIOPA / ECB | ERROR | Cell is empty for an active row of profile IORP / EIOPA / ECB | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/126` | (all) | ERROR | Populated cell does not match the codification (UNKNOWN) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 127 — 127_Bond_floor_(convertible_instrument_only)

Path: `Position / DerivativeOrConvertible / OptionCharacteristics / Convertible / BondFloor`
Codification: NUMERIC
Applicability: all rows
Definition: Lowest value of a convertible bond expressed in quotation currency, at current issuer spread

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/127` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 128 — 128_Option_premium_(convertible_instrument_only)

Path: `Position / DerivativeOrConvertible / OptionCharacteristics / Convertible / OptionPremium`
Codification: NUMERIC
Applicability: all rows
Definition: Premium of the embedded option of a convertible bond in quotation currency

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/128` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 129 — 129_Valuation_yield

Path: `Position / BondCharacteristics / ValuationYieldCurve /  Yield`
Codification: NUMERIC
Applicability: all rows
Definition: Valuation Yield of the interest rate instrument

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/129` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 130 — 130_Valuation_z_spread

Path: `Position / BondCharacteristics / ValuationYieldCurve /  Spread`
Codification: NUMERIC
Applicability: all rows
Definition: Issuer spread calculated from Z coupon IRS curve of quotation currency

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/130` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 131 — 131_Underlying_asset_category

Path: `Position / Instrument/ UAC`
Codification: CLOSED_LIST, closed list of 18 entries
Applicability: all rows
Definition: SII definition as per QRT S.06.03

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | M | Mandatory — must always be present. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | O | Optional — populate when applicable. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/131/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/131/IORP_EIOPA_ECB` | IORP / EIOPA / ECB | ERROR | Cell is empty for an active row of profile IORP / EIOPA / ECB | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/131` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 132 — 132_Infrastructure_investment

Path: `To be defined with Fundxml`
Codification: CLOSED_LIST, closed list of 6 entries
Applicability: CIC categories CIC1, CIC2, CIC3, CIC5, CIC6, CIC8, CIC9
Definition: Type of infrastructure investment according to Type of infrastructure investment according to COMMISSION DELEGATED REGULATION (EU) 2016/467 of 30 September 2015 amending Commission Delegated Regulation (EU) 2015/35 concerning the calculation of regulatory capital requirements for several categories of assets held by insurance and reinsurance undertakings and COMMISSION DELEGATED REGULATION (EU) 2017/1542 as of 8 June 2017 amending Delegated Regulation (EU) 2015/35 concerning the calculation of regulatory capital requirements for certain categories of assets held by insurance and reinsurance undertakings (infrastructure corporates).

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | I | Informational — populate if available, no enforcement. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | I | Informational — populate if available, no enforcement. |
| `SST` | SST (FINMA) | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/132` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 133 — 133_custodian_name

Path: `To be defined with Fundxml`
Codification: FREE_TEXT
Applicability: all rows
Definition: Name of the custodian of the seggregated account

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | O | Optional — populate when applicable. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | M | Mandatory — must always be present. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | M | Mandatory — must always be present. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/133/IORP_EIOPA_ECB` | IORP / EIOPA / ECB | ERROR | Cell is empty for an active row of profile IORP / EIOPA / ECB | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `PRESENCE/133/SST` | SST (FINMA) | ERROR | Cell is empty for an active row of profile SST (FINMA) | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/133` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 134 — 134_type1_private_equity_portfolio_eligibility

Codification: CLOSED_LIST, closed list of 3 entries
Applicability: CIC categories CIC3, CIC4
Definition: Eligibility of the investment to art 168a of the regulation UE DR 2019/981

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | I | Informational — populate if available, no enforcement. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/134` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 135 — 135_type1_private_equity_issuer_beta

Codification: NUMERIC
Applicability: CIC categories CIC3, CIC4
Definition: Beta of the issuer of the private equity calculated according to art 168a of the regulation UE DR 2019/981

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | I | Informational — populate if available, no enforcement. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/135` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 137 — 137_Counterparty_sector

Codification: CLOSED_LIST, closed list of 12 entries
Applicability: CIC categories CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CICA, CICB, CICC, CICD, CICE, CICF
Definition: Classification of the issuer or counterparty according to IORP II regulation based on FINREP breakdown ( ESA 2010) and EIOPA specifications

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | M | Mandatory — must always be present. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/137/IORP_EIOPA_ECB` | IORP / EIOPA / ECB | ERROR | Cell is empty for an active row of profile IORP / EIOPA / ECB | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `COND_PRESENCE/137/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/137` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

### Field 138 — 138_Collateral_eligibility

Codification: CLOSED_LIST, closed list of 5 entries
Applicability: CIC categories CIC2, CIC5, CIC6, CIC8
Definition: Eligibility of the collateral according to solvency regulation (RD UE 2015/35 art 176.5 and related art197, art214)

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `COND_PRESENCE/138/SOLVENCY_II` | Solvency II | WARNING | Row's CIC matches the field's applicability list and the cell is empty | Conditionally-required cell missing for this profile. | PROFILE_COMPLETENESS (C leg) −1/N |
| `FORMAT/138` | (all) | ERROR | Populated cell does not match the codification (CLOSED_LIST) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |

**Referenced as source by:** `XF-25/COLLATERAL_VALUE`

---

### Field 139 — 139_Collateral_Market_valuation_in_portfolio_currency

Codification: NUMERIC
Applicability: CIC categories CIC2, CIC5, CIC8
Definition: Market valuation of the collateral in portfolio currency

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | C | Conditional — required when the spec's applicability/condition holds. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `FORMAT/139` | (all) | ERROR | Populated cell does not match the codification (NUMERIC) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |
| `XF-25/COLLATERAL_VALUE` | (all) | ERROR | Field `138` ∈ [1, 2, 3] | Field `139` must be non-empty. | Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by 1 / max(distinct cross-field rules × rows, 1). |


---

### Field 1000 — 1000_TPT_Version

Codification: FREE_TEXT
Applicability: all rows
Definition: TPT Published Version

#### Flag per profile

| Code | Display name | Flag | Meaning |
|---|---|---|---|
| `SOLVENCY_II` | Solvency II | M | Mandatory — must always be present. |
| `IORP_EIOPA_ECB` | IORP / EIOPA / ECB | — | Profile column not present in this version. |
| `NW_675` | NW 675 | — | Profile column not present in this version. |
| `SST` | SST (FINMA) | — | Profile column not present in this version. |

#### Checks

| Rule ID | Profile(s) | Severity | Triggers when | Failure consequence | Score impact |
|---|---|---|---|---|---|
| `PRESENCE/1000/SOLVENCY_II` | Solvency II | ERROR | Cell is empty for an active row of profile Solvency II | The mandatory cell is missing — file is incomplete for this profile. | MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N |
| `FORMAT/1000` | (all) | ERROR | Populated cell does not match the codification (FREE_TEXT) | Value cannot be parsed/used downstream. | FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches) |


---

