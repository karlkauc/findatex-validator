# TPT V7 Validator — Requirements

Generated from `src/main/resources/spec/TPT_V7  20241125_updated.xlsx` (FinDatEx Tripartite Template V7.0, dated 2024-11-25).

This document mirrors every datapoint in the spec. It is the input for the rule engine — fields here drive the generated rules in `com.tpt.validator.spec.SpecCatalog`.

## Scope

- **Inputs:** TPT V7 produced files in flat `.xlsx` or `.csv` layout, header row using either NUM_DATA names (e.g. `12_CIC_code_of_the_instrument`) or the FunDataXML path (e.g. `Position / InstrumentCIC`).
- **Active regulatory profiles:**
  - **Solvency II baseline** — column K (`Mandatory / Conditional / Optional / Indicative / N/A`).
  - **IORP / EIOPA / ECB** — combined: column AE (IORP), AF/AG (EIOPA PF.06.02.24 positions/assets), AH (EIOPA PF.06.03.24 look-through), AI (ECB Addon PFE.06.02.30).
  - **NW 675** — column AC.
- **Out of scope:** SST (column AD), FunDataXML structured XML inputs, online ISIN/LEI lookups, PDF export.

## Interpretation of flags

| Flag | Meaning |
|------|---------|
| `M`  | Mandatory — must be present and well-formed. |
| `C`  | Conditional — must be present when CIC and other dependent fields require it. |
| `O`  | Optional — validated only for format/closed-list conformance if present. |
| `I`  | Indicative — informational, format checked, not graded as ERROR if missing. |
| `N/A`| Not applicable for that profile. |

## Cross-field rules (manually authored, derived from spec comments)

| ID | Description | Source |
|---|---|---|
| XF-01 | Field 11 (`CompleteSCRDelivery`) = `Y` ⇒ fields 97..105b mandatory | spec row 20 |
| XF-02 | Field 12 (CIC) governs applicability of every other field per CIC matrix | columns L..AA |
| XF-03 | Quotation/portfolio currency consistency (fields 4 vs 21 vs 24) | rows 33–36 |
| XF-04 | Σ field 26 (PositionWeight) ≈ 1 within tolerance | row 38 |
| XF-05 | Field 9 (CashPercentage) ≈ Σ market value of CIC `xx7x` / TotalNetAssets | rows 18, 22 |
| XF-06 | Field 5 (TotalNetAssets) ≈ field 8 × field 8b (NAV ≈ SharePrice × Shares) within precision tolerance | row 17 |
| XF-07 | Economic area (fields 13/74/87) consistent with country embedded in CIC | rows 23, 91, 104 |
| XF-08 | Field 38 (Coupon frequency) ∈ {0,1,2,4,12,52} | row 50 |
| XF-09 | Field 141 mandatory iff field 140 is filled | row 171 |
| XF-10 | Field 32 = Floating ⇒ fields 34..37 mandatory; Fixed ⇒ field 33 mandatory | rows 44–49 |
| XF-11 | Field 39 (Maturity) ≥ field 7 (Reporting date) for active bonds | rows 14–15, 51 |
| XF-12 | Field 7 (Reporting) ≥ field 6 (Valuation) | rows 14–15 |
| XF-13 | Field 146 (PIK) cases 1..4 — coupon and redemption fields must follow PIK guidelines | PIK guidelines |
| XF-14 | Field 67 (underlying CIC) mandatory iff main CIC ∈ {2, A, B, C, D, F} | row 84 |
| XF-15 | Field 1000 = `V7.0 (official) dated 25 November 2024` | row 179 |

## Scoring

Each profile P contributes the following category scores in `[0, 1]`:
- `mandatoryCompleteness[P] = 1 − missing(M-fields-for-P) / total(M-fields-for-P × applicable rows)`
- `formatConformance = 1 − format-errors / non-empty cells`
- `closedListConformance = 1 − closed-list errors / non-empty closed-list cells`
- `crossFieldConsistency = 1 − cross-field errors / cross-field rules evaluated`
- `profileCompleteness[P]` — combined M+C presence for P
- `overall = 0.4·mandatory + 0.2·format + 0.15·closed-list + 0.15·cross-field + 0.1·profile-completeness`

## Datapoints

(Auto-generated; do not edit by hand — re-run `tools/generate_requirements.py`.)


## Section: Portfolio Characteristics and valuation

### 1_Portfolio_identifying_data

- **FunDataXML path:** `Portfolio / PortfolioID / Code`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** free text / numeric
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: M
    - `IORP`: M
    - `EIOPA_PF.06.02.24_pos`: C0110 - Asset ID Code
    - `EIOPA_PF.06.02.24_ass`: C0110 - Asset ID Code
    - `EIOPA_PF.06.03.24_LT`: C0010 - Collective Investments Undertaking ID Code
- **Definition:** Identification of the fund or share class
- **Codification (verbatim):**
  ```
  Use the following priority:
    - ISO 6166 code of ISIN when available
    - Other recognised codes (e.g.: CUSIP, Bloomberg Ticker, Reuters RIC)
    - Code attributed by the undertaking, when the options above are not available. Code must be unique and kept consistent over time.
  ```
- **Comment:** To show identification of fund or share class
- **Source row in spec:** 9

### 2_Type_of_identification_code_for_the_fund_share_or_portfolio

- **FunDataXML path:** `Portfolio / PortfolioID / CodificationSystem`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** closed list (10 values)
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: M
    - `IORP`: M
    - `EIOPA_PF.06.02.24_pos`: C00120 - Asset ID Code Type
    - `EIOPA_PF.06.02.24_ass`: C00120 - Asset ID Code Type
    - `EIOPA_PF.06.03.24_LT`: C0020 - Collective Investments Undertaking ID Code type
- **Definition:** Codification chosen to identify the share of the CIS
- **Codification (verbatim):**
  ```
  One of the options in the following closed list to be used:
  1 - ISO 6166 for ISIN code
  2 - CUSIP (The Committee on Uniform Securities Identification Procedures number assigned by the CUSIP Service Bureau for U.S. and Canadian companies)
  3 - SEDOL (Stock Exchange Daily Official List for the London Stock Exchange)
  4 – WKN (Wertpapier Kenn-Nummer, the alphanumeric German identification number)
  5 - Bloomberg Ticker (Bloomberg letters code that identify a company's securities)
  6 - BBGID (The Bloomberg Global ID)
  7 - Reuters RIC (Reuters instrument code)
  8 – FIGI (Financial Instrument Global Identifier)
  9 - Other code by members of the Association of National Numbering Agencies
  99 - Code attributed by the undertaking
  ```
- **Comment:** Closed list is taken from QRT Log issued by EIOPA July 2015. Modified to add LEI in 2019  For OTC derivatives cf Mifid II requirements
- **Source row in spec:** 10

### 3_Portfolio_name

- **FunDataXML path:** `Portfolio / PorfolioName`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** alphanumeric (max 255)
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: M
    - `IORP`: M
    - `EIOPA_PF.06.02.24_ass`: C0130 - Item Title
- **Definition:** Name of the Portfolio or name of the CIS
- **Codification (verbatim):**
  ```
  Alphanum (max 255)
  ```
- **Comment:** Portfolio or Fund or Share Class name
- **Source row in spec:** 11

### 4_Portfolio_currency_(B)

- **FunDataXML path:** `Portfolio / PortfolioCurrency`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** ISO 4217 currency
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: M
    - `IORP`: M
    - `EIOPA_PF.06.02.24_ass`: C0220 - Currency
- **Definition:** Valuation currency of the portfolio
- **Codification (verbatim):**
  ```
  Code ISO 4217
  CNH : 2 Chinese yuan (when traded offshore) - Hong Kong
  CNT: Chinese yuan (when traded offshore) -Taiwan
  GGP – Guernsey pound - Guernsey
  IMP: Isle of Man pound also Manx pound -Isle of Man
  JEP: Jersey pound - Jersey
  KID: Kiribati dollar -Kiribati
  NIS – New Israeli Shekel - Israel
  PRB – Transnistrian ruble - Transnistria (The code conflicts with ISO-4217 because PR stands for Puerto Rico. X should have been used for the first letter.)
  TVD – Tuvalu dollar- Tuvalu
  ```
- **Comment:** Share Class currency  if applicable - reported to insurer in currency of one fund or share class (should be consistent with field 3). In case no ISO code exists, please refer to market practices (ex CNH for  Chines Yuam traded offshore)
- **Source row in spec:** 12

### 5_Net_asset_valuation_of_the_portfolio_or_the_share_class_in_portfolio_currency

- **FunDataXML path:** `Portfolio / TotalNetAssets`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: M
    - `IORP`: M
    - `EIOPA_PF.06.02.24_pos`: C0100 - Market Asset Value*  *Must be scalled by the Pension Fund to their respective investment size
    - `EIOPA_PF.06.02.24_ass`: C0100 - Market Asset Value*  *Must be scalled by the Pension Fund to their respective investment size
- **Definition:** Portfolio valuation
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** Per share class - NAV to be reported in same currency as Line 4
- **Source row in spec:** 13

### 6_Valuation_date

- **FunDataXML path:** `Portfolio / ValuationDate`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** date (YYYY-MM-DD, ISO 8601)
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: M
    - `IORP`: M
- **Definition:** Date of valuation (date positions valid for)
- **Codification (verbatim):**
  ```
  YYYY-MM-DD         ISO 8601
  ```
- **Comment:** NAV date
- **Source row in spec:** 14

### 7_Reporting_date

- **FunDataXML path:** `Portfolio / ReportingDate`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** date (YYYY-MM-DD, ISO 8601)
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: M
    - `IORP`: M
- **Definition:** Date of reference for the reporting
- **Codification (verbatim):**
  ```
  YYYY-MM-DD         ISO 8601
  ```
- **Comment:** Date to which data refers ( end of month for example)
- **Source row in spec:** 15

### 8_Share_price

- **FunDataXML path:** `Portfolio / ShareClass / SharePrice`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: M
    - `IORP`: M
    - `EIOPA_PF.06.02.24_ass`: C0370 - Unit price
- **Definition:** Share price of the fund/share class
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** the valuation should be expressed in the currency indicated in data point 4
- **Source row in spec:** 16

### 8b_Total_number_of_shares

- **FunDataXML path:** `Portfolio / ShareClass / TotalNumberOfShares`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: M
    - `IORP`: M
    - `EIOPA_PF.06.02.24_pos`: C0060 - Quantity*  *Must be scalled by the Pension Fund to their respective investment size
    - `EIOPA_PF.06.02.24_ass`: C0060 - Quantity*  *Must be scalled by the Pension Fund to their respective investment size
- **Definition:** Total number of shares (per share class, if applicable)
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** Per share class to enable apportionment of the investment holding by the insurance entity in their proportion ownership.  Attention point: NAV could be different from the Share Price times Number of Shares value because of the precision
- **Source row in spec:** 17

### 9_Cash_ratio

- **FunDataXML path:** `Portfolio / CashPercentage`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
    - `IORP`: O
- **Definition:** Amount of cash of the fund / total net asset value of the fund, in %
- **Codification (verbatim):**
  ```
  number with floating decimal: 1 = 100%
  ```
- **Comment:** Include cash and short term cash equivalents [excludes CIC 74 and other cash equivalents that might be considereed long term]
- **Source row in spec:** 18

### 10_Portfolio_modified_duration

- **FunDataXML path:** `Portfolio / PortfolioModifiedDuration`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
    - `IORP`: O
- **Definition:** Weighted average modified duration of portfolio positions
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** Weighted average modified duration of the Marked to Market instruments in portfolio. This datapoint has the same value for all instruments in the portfolio of the funds. Ideally it should be based on expected modified duration for relevant instruments.
- **Source row in spec:** 19

### 11_Complete_SCR_delivery

- **FunDataXML path:** `Portfolio / CompleteSCRDelivery`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** alpha (1)
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Definition:** Y/N
- **Codification (verbatim):**
  ```
  alpha(1)
  ```
- **Comment:** Y =  have you completed the SCR contributions (97 to 105)
- **Source row in spec:** 20


## Section: Instrument codification

### 12_CIC_code_of_the_instrument

- **FunDataXML path:** `Position / InstrumentCIC`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** alphanumeric (max 4)
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `NW675`: M
    - `SST`: M
    - `IORP`: M
- **Definition:** CIC Code (Complementary Identification Code).
- **Codification (verbatim):**
  ```
  CIC code - Alphanumeric (4)
  ```
- **Comment:** Indicative CIC  This codification (cf. CIC Table) would allow to determine: * the type and the country of the main codification * the S2 type of instrument * the S2 subtype of instrument * can be useful to add the source, but not mandatory Complementary Identification Code used to classify assets, as set out in Annex V:  CIC Table - when classifying  asset using the CIC table, undertakings shall take into consideration the most representative risk to which the asset is exposed to.
- **Source row in spec:** 22

### 13_Economic_zone_of_the_quotation_place

- **FunDataXML path:** `Position / EconomicArea`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** free text / numeric
- **CIC applicability:** CIC3
- **Profile flags:**
    - `SST`: O
- **Definition:** Indication of the economic zone of the quotation place
- **Codification (verbatim):**
  ```
  Integer return corresponding to the following closed list:
  0 = non-listed
  1 = EEA
  2 = OECD exclude EEA
  3 = Rest of the World
  ```
- **Comment:** Data point is option if the CIC in field 12 is provided as the economic zone of quotation can be mapped from the first two positions of the CIC.
- **Source row in spec:** 23

### 14_Identification_code_of_the_instrument

- **FunDataXML path:** `Position / InstrumentCode / Code`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** free text / numeric
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: M
    - `IORP`: M
- **Definition:** Identification code of the financial instrument - including identifier for leg of instrument if required
- **Codification (verbatim):**
  ```
  Code must be unique and kept consistent over time.
  
  Example of unique code /idenifier for each leg:
  123456a and 123456b
  ```
- **Comment:** Closed list is taken from QRT Log issued by EIOPA July 2015  For multiple legs instruments this field shoud contain the Leg identification code, which must  be  different from item 68 the underlying identification code
- **Source row in spec:** 24

### 15_Type_of_identification_code_for_the_instrument

- **FunDataXML path:** `Position / InstrumentCode / CodificationSystem`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** closed list (10 values)
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: M
    - `IORP`: M
- **Definition:** Codification chosen to identify the instrument
- **Codification (verbatim):**
  ```
  One of the options in the following closed list to be used:
  1 - ISO 6166 for ISIN code
  2 - CUSIP (The Committee on Uniform Securities Identification Procedures number assigned by the CUSIP Service Bureau for U.S. and Canadian companies)
  3 - SEDOL (Stock Exchange Daily Official List for the London Stock Exchange)
  4 – WKN (Wertpapier Kenn-Nummer, the alphanumeric German identification number)
  5 - Bloomberg Ticker (Bloomberg letters code that identify a company's securities)
  6 - BBGID (The Bloomberg Global ID)
  7 - Reuters RIC (Reuters instrument code)
  8 – FIGI (Financial Instrument Global Identifier)
  9 - Other code by members of the Association of National Numbering Agencies
  99 - Code attributed by the undertaking
  ```
- **Comment:** Closed list is taken from QRT Log issued by EIOPA July 2015. Modified to add LEI in 2019  For OTC derivatives cf Mifid II requirements
- **Source row in spec:** 25

### 16_Grouping_code_for_multiple_leg_instruments

- **FunDataXML path:** `Position / GroupID`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** alphanumeric (max 255)
- **CIC applicability:** CICA, CICB, CICC, CICD, CICE
- **Profile flags:**
    - `SST`: C
- **Definition:** grouping code for operations on multi leg instruments
- **Codification (verbatim):**
  ```
  Alphanum (max 255)
  
  Example: 123456
  ```
- **Comment:** Common identifier.   For multiple legs instruments, this data point must be filled with the identification code of the instrument, which is the same for each leg.   Cf Mifid II
- **Source row in spec:** 26

### 17_Instrument_name

- **FunDataXML path:** `Position / InstrumentName`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** alphanumeric (max 255)
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: M
    - `IORP`: M
- **Definition:** instrument name
- **Codification (verbatim):**
  ```
  Alphanum (max 255)
  ```
- **Comment:** limited maximum of 255 characters
- **Source row in spec:** 27


## Section: Valuations and exposures

### 17b_Asset_liability

- **FunDataXML path:** `Position / Valuation / AssetOrLiability`
- **Flag (Solvency II baseline):** Not applicable
- **Codification kind:** free text / numeric
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
    - `IORP`: O
- **Definition:** Asset/Liability identification if needed
- **Codification (verbatim):**
  ```
  "A" for asset or "L" for liabilities
  ```
- **Comment:** All exposures should be recorded by signed amount. By exception it is possible to indicate wether a given position shall be considered as an asset or a liabilities from the perspective of the holder of the funds or the portfolio.
- **Source row in spec:** 29

### 18_Quantity

- **FunDataXML path:** `Position / Valuation / Quantity`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** CIC0, CIC2, CIC3, CIC4, CICA, CICB, CICC, CICD
- **Profile flags:**
    - `SST`: C
- **Definition:** Number of instruments on position
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** EIOPA definition (06.02). Number of assets, for relevant assets.   Buy gives +; sale gives -
- **Source row in spec:** 30

### 19_Nominal_amount

- **FunDataXML path:** `Position / Valuation / TotalNominalValueQC`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** CIC0, CIC1, CIC2, CIC5, CIC6, CIC7, CIC8, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `NW675`: C
    - `SST`: C
- **Definition:** Quantity * nominal unit amount
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** EIOPA definition (06.02 and 08.01). Applicable to instruments with CIC-codes 1,2,5,6,72,73,74, 8 and derivatives. Principle amount outstanding measured at par amount, for all assets where this item is relevant, and at nominal amount for CIC = 72, 73, 74, 75 and 79 if applicable.  For derivatives: The amount covered or exposed to the derivative.  For futures and options corresponds to contract size multiplied by the trigger value and by the number of contracts reported in that line. For swaps and forwards it corresponds to the contract amount of the contracts reported in that line. When the trigger value corresponds to a range, the average value of the range shall be used. The notional amount refers to the amount that is being hedged / invested (when not covering risks). If several trades occur, it shall be the net amount at the reporting date.
- **Source row in spec:** 31

### 20_Contract_size_for_derivatives

- **FunDataXML path:** `Position / Valuation / ContractSize`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** CICA, CICB, CICC
- **Profile flags:**
    - `SST`: C
- **Definition:** Contract size
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** Use EIOPA definition (QRT 0801) For Futures & Options: number of underlying assets in the contract (e.g. for equity futures it is the number of equities to be delivered per derivative contract at maturity, for bond futures it is the reference amount underlying each contract).  The way the contract size is defined varies according with the type of instrument.  For futures on equities it is common to find the contract size defined as a function of the number of shares underlying the contract.  For futures on bonds, it is the bond nominal amount underlying the contract.
- **Source row in spec:** 32

### 21_Quotation_currency_(A)

- **FunDataXML path:** `Position / Valuation / QuotationCurrency`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** ISO 4217 currency
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `NW675`: M
    - `SST`: M
    - `IORP`: M
    - `EIOPA_PF.06.03.24_LT`: C0050 - Currency  (input field)
- **Definition:** Currency of quotation for the instrument or denomination
- **Codification (verbatim):**
  ```
  Code ISO 4217
  CNH : 2 Chinese yuan (when traded offshore) - Hong Kong
  CNT: Chinese yuan (when traded offshore) -Taiwan
  GGP – Guernsey pound - Guernsey
  IMP: Isle of Man pound also Manx pound -Isle of Man
  JEP: Jersey pound - Jersey
  KID: Kiribati dollar -Kiribati
  NIS – New Israeli Shekel - Israel
  PRB – Transnistrian ruble - Transnistria (The code conflicts with ISO-4217 because PR stands for Puerto Rico. X should have been used for the first letter.)
  TVD – Tuvalu dollar- Tuvalu
  ```
- **Comment:** Field definition expanded to "Currency of quotation for the instrument or denomination" which makes this field more appropriate and inclusive for derivatives. In case no ISO code exists, please refer to market practices (ex CNH for  Chines Yuam traded offshore)
- **Source row in spec:** 33

### 22_Market_valuation_in_quotation_currency_(A)

- **FunDataXML path:** `Position / Valuation / MarketValueQC`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `NW675`: M
    - `SST`: M
- **Definition:** Market valuation of the position accrued interest included in quotation currency
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** Negative values on derivatives mean the fund should pay in order to offset the existing position - i.e. in case the quote spread is smaller that the coupon rate of the CDS for a long position Market values on listed derivatives instruments or CFDs with daily margin call should be close to zero.  The deposit amounts and the sum of the margin calls since the inception of the positiion are often considered as cash. This amount is signed
- **Source row in spec:** 34

### 23_Clean_market_valuation_in_quotation_currency_(A)

- **FunDataXML path:** `Position / Valuation / CleanValueQC`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: M
- **Definition:** Market valuation of the position accrued interest excluded in quotation currency
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** Duplication of data for equity or any kind of instrument without accrued interest  This amount is signed
- **Source row in spec:** 35

### 24_Market_valuation_in_portfolio_currency_(B)

- **FunDataXML path:** `Position / Valuation / MarketValuePC`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `NW675`: M
    - `SST`: M
    - `IORP`: M
    - `EIOPA_PF.06.03.24_LT`: C0060 - Total amount (input field)
- **Definition:** Market valuation of the position accrued interest included in portfolio currency
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** Negative values on derivatives mean the fund should pay in order to offset the existing position - i.e. in case the quote spread is smaller that the coupon rate of the CDS for a long position Market values on listed derivatives instruments or CFDs with daily margin call should be close to zero.  The deposit amounts and the sum of the margin calls since the inception of the positiion are often considered as cash This amount is signed
- **Source row in spec:** 36

### 25_Clean_market_valuation_in_portfolio_currency_(B)

- **FunDataXML path:** `Position / Valuation / CleanValuePC`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: M
- **Definition:** Market valuation of the position accrued interest excluded in portfolio currency
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** Duplication of data for equity or any kind of instrument without accrued interest  This amount is signed
- **Source row in spec:** 37

### 26_Valuation_weight

- **FunDataXML path:** `Position / Valuation / PositionWeight`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: M
    - `IORP`: M
    - `EIOPA_PF.06.03.24_LT`: C0060 - Total amount (input field)
- **Definition:** Market valuation in portfolio currency / portfolio net asset value in %
- **Codification (verbatim):**
  ```
  number with floating decimal: 1 = 100%
  ```
- **Comment:** 100 % =1 - including cash Required data to calculate the SCR in the case of an open fund.  Per share class This amount is signed
- **Source row in spec:** 38

### 27_Market_exposure_amount_in_quotation_currency_(A)

- **FunDataXML path:** `Position / Valuation / MarketExposureQC`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `NW675`: M
    - `SST`: M
- **Definition:** Market exposure amount different from market valuation for derivatives (valuation of the equivalent position on the underlying asset)
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** For equity future contracts, index futures contracts and options etc. data is calculated depending on characteristics of the contract (quantity, contract size, strike price etc.) and the index value or underlying value.  Example: ESTX 50 Index Future: quantity (79) x contract size (10) x index market value (3.145) = 2.484.550 EUR Exposure.  For options: quantity (79) x contract size (10) * Last valuation price of the underlying (72) *  Sensitivity to underlying asset price (delta) (93).  For the fixed income future contracts this data is equal to the exposure resulting on the cheapest to deliver (analogous to the preceding calculations for equity contracts).  For FRA contracts, FX-Forwards and CDS this data is the notional amount  This amount is signed
- **Source row in spec:** 39

### 28_Market_exposure_amount_in_portfolio_currency_(B)

- **FunDataXML path:** `Position / Valuation / MarketExposurePC`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `NW675`: M
    - `SST`: M
- **Definition:** Market exposure amount different from market valuation for derivatives (valuation of the equivalent position on the underlying asset) in the quotation currency of the portfolio
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** This field used for FX exposures, equity exposures, credit and interest rates; using the following rules: * exposure on derivatives are deriving from equivalent exposure on simple underlying instruments without considering type of risk to be evaluated *both Put and CDS should have negative exposures and positive quantities or nominal amounts for long positions, with positive exposure for short positions  *residual maturity should be handled by inf=ormation system that will do SCR calculations and produce QRTs * exposure on cash or equivalent should be egal to the valuation ( exposure for interest rate risks should be obtained by multiplying the amount by the modified duration (field 90) and for credit risk by credit sensitivity (field 91)  * exposure for options or convertible bond instruments should be used by multiplying the exposure by the delta for the relevant risk category. This amount is signed
- **Source row in spec:** 40

### 29_Market_exposure_amount_for_the_3rd_quotation_currency_(C)

- **FunDataXML path:** `Position / Valuation / MarketExposureLeg2`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
- **Definition:** Market exposure amount different from market valuation for derivatives (valuation of the equivalent position on the underlying asset) in the quotation currency of the underlying asset
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** Optional May be used, in some cases, to describe instruments such as FX forwards or FX options. This amount is signed
- **Source row in spec:** 41

### 30_Market_exposure_in_weight

- **FunDataXML path:** `Position / Valuation / MarketExposureWeight`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: M
- **Definition:** Exposure valuation in portfolio currency / total net asset value of the fund, in %
- **Codification (verbatim):**
  ```
  number with floating decimal: 1 = 100%
  ```
- **Comment:** Required data to determine the market exposure arising from the derivatives within the framework of open funds This amount is signed
- **Source row in spec:** 42

### 31_Market_exposure_for_the_3rd_currency_in_weight_over_NAV

- **FunDataXML path:** `Position / Valuation / MarketExposureWeightLeg2`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** CICE
- **Profile flags:**
    - `SST`: C
- **Definition:** Exposure valuation for leg 2 in portfolio currency / total net asset value of the fund, in %
- **Codification (verbatim):**
  ```
  number with floating decimal: 1 = 100%
  ```
- **Comment:** Conditionnal May be used, in some cases, to describe instruments such as FX forwards or FX options.
- **Source row in spec:** 43


## Section: Instrument characteristics & analytics


## Section: Interest rate instruments characteristics

### 32_Interest_rate_type

- **FunDataXML path:** `Position / BondCharacteristics / RateType`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** free text / numeric
- **CIC applicability:** CIC1, CIC2, CIC5, CIC6, CIC7, CIC8, CICD, CICE, CICF
- **Profile flags:**
    - `NW675`: C
    - `SST`: C
- **Definition:** * Fixed - plain vanilla fixed coupon rate * Floating - plain vanilla floating coupon rates (for all interest rates, which refer to a reference interest rate like EONIA or EURIBOR + margin in BP) * Variable - all other variable interest rates like step-up or step-down or fixed-to-float bonds. The variable feature is the (credit) margin or the change between fixed and float. * Infation_linked for inflation linked bonds in order to identify them.
- **Codification (verbatim):**
  ```
  "Fixed" or "Floating" or "Variable" or "Inflation_linked"
  ```
- **Comment:** For step up bonds only ongoing period characteristics are entered.  Floating example : a bond with a coupon rate of EONIA or EURIBOR + xxx bp Variable example : a fixed to float bond with a fixed coupon until 31/12/2040 then a floating coupon of EURIBOR + xxxbp. This bond will be modelled as Variable until 31/12/2040 then Floating until redemption Inflation linked example : a bond with a nominal and a coupon rate embedding an inflation index component
- **Source row in spec:** 46

### 33_Coupon_rate

- **FunDataXML path:** `Position / BondCharacteristics / CouponRate`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** CIC1, CIC2, CIC5, CIC6, CIC7, CIC8, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: C
- **Definition:** Fixed rate: coupon rate as a percentage of nominal amount Floating rate: last fixing rate + margin as a percentage of nominal amount Variable rate: estimation of current rate over the period + margin as a percentage of nominal amount all rates are expressed on an annual basis
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** This field should be filled with the current coupon rate expressed as a percentage of the nominal amount.  It is expressed in a different way from weights (fields 26 and 30 for example).   Example: bond with  fixed 1.5 % coupon to show as "1.5".  A  floater euribor3m + 0.20% to show as "0.26" provided the last fixing was 0.06% for the euribor3m.
- **Source row in spec:** 47

### 34_Interest_rate_reference_identification

- **FunDataXML path:** `Position / BondCharacteristics / VariableRate / IndexID / Code`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** free text / numeric
- **CIC applicability:** CIC1, CIC2, CIC5, CIC6, CIC7, CIC8, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: C
- **Definition:** identification code for interest rate index
- **Codification (verbatim):**
  ```
  Example : EUR006M
  ```
- **Comment:** 34 & 35 fields have been swapped from 20140915 version.  This field should be used to identify the difference between OIS, EONIA, and EURIBOR/LIBOR or other rate index/reference Indices for SCR calculations
- **Source row in spec:** 48

### 35_Identification_type_for_interest_rate_index

- **FunDataXML path:** `Position / BondCharacteristics / VariableRate / IndexID / CodificationSystem`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** free text / numeric
- **CIC applicability:** CIC1, CIC2, CIC5, CIC6, CIC7, CIC8, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: C
- **Definition:** Type of codification used for interest rate index
- **Codification (verbatim):**
  ```
  e.g. "BLOOMBERG" or empty (if internal codification)
  ```
- **Comment:** 34 & 35 fields have been swapped from 20140915 version May use NA or similar code for systems not favouring an empty field
- **Source row in spec:** 49

### 36_Interest_rate_index_name

- **FunDataXML path:** `Position / BondCharacteristics / VariableRate / IndexName`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** free text / numeric
- **CIC applicability:** CIC1, CIC2, CIC5, CIC6, CIC7, CIC8, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: C
- **Definition:** name of interest rate index
- **Codification (verbatim):**
  ```
  Euribor 6month
  ```
- **Source row in spec:** 50

### 37_Interest_rate_margin

- **FunDataXML path:** `Position / BondCharacteristics / VariableRate / Margin`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** CIC1, CIC2, CIC5, CIC6, CIC7, CIC8, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: C
- **Definition:** Facial margin as a percentage of nominal amount on an annual basis
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** Represents the directional numeric adjustment made against the interest rate index. For example in the scenario of an instrument with an interest rate of Euribor 6 month - 0.5% then this field should be populated with -0.5.
- **Source row in spec:** 51

### 38_Coupon_payment_frequency

- **FunDataXML path:** `Position / BondCharacteristics / CouponFrequency`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** free text / numeric
- **CIC applicability:** CIC1, CIC2, CIC5, CIC6, CIC7, CIC8, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: C
- **Definition:** number of coupon payment per year 0 = other than below options: 1= annual 2= biannual 4= quarterly 12= monthly 52= weekly
- **Codification (verbatim):**
  ```
  Frequency ("0" = other than /"1"= Annual  / "2"= biannual / "4"=quarterly / "12"= monthly / "52" = weekly)
  ```
- **Comment:** For OTC derivatives this is the frequency of payment (or receipt) of coupons/interest.
- **Source row in spec:** 52

### 39_Maturity_date

- **FunDataXML path:** `Position / BondCharacteristics / Redemption / MaturityDate`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** date (YYYY-MM-DD, ISO 8601)
- **CIC applicability:** CIC1, CIC2, CIC5, CIC6, CIC7, CIC8, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: C
- **Definition:** Last redemption date
- **Codification (verbatim):**
  ```
  YYYY-MM-DD         ISO 8601
  ```
- **Comment:** Final contractual maturity date for fixed income instrument or derivatives. Potential exercise of prepayment / extension options should not be considered. 9999-12-31 for perpetual bonds.   Contractual expiry date for options.
- **Source row in spec:** 53

### 40_Redemption_type

- **FunDataXML path:** `Position / BondCharacteristics / Redemption /Type`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** free text / numeric
- **CIC applicability:** CIC1, CIC2, CIC5, CIC6, CIC7, CIC8, CICD, CICE
- **Profile flags:**
    - `SST`: C
- **Definition:** Type of redemption payment schedule : bullet, constant annuity…
- **Codification (verbatim):**
  ```
  "Bullet", "Sinkable", "defaulted" empty if non applicable
  ```
- **Comment:** A word of caution: the purpose of this field is for those who wish to feed ALM systems or recalculate prices - if bullet this is achievable; if sinkable, this is not.
- **Source row in spec:** 54

### 41_Redemption_rate

- **FunDataXML path:** `Position / BondCharacteristics / Redemption / Rate`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** CIC1, CIC2, CIC5, CIC6, CIC7, CIC8, CICD, CICE
- **Profile flags:**
    - `SST`: C
- **Definition:** Redemption amount in % of nominal amount
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** If known 1=100%.                                                           Linked to field 19 (Nominal amount).
- **Source row in spec:** 55

### 42_Callable_putable

- **FunDataXML path:** `Position / BondCharacteristics / OptionalCallPut / CallPutType`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** alpha (3)
- **CIC applicability:** CIC1, CIC2, CIC5, CIC6, CIC7, CIC8
- **Profile flags:**
    - `SST`: C
- **Definition:** Cal = Call Put = Put Cap = Cap Flr= Floor empty if none
- **Codification (verbatim):**
  ```
  Alpha(3)( "Cal" = Call / "Put" = Put / "Cap" = Cap / "Flr" = Floor)
  ```
- **Comment:** Enter the characteristics of the shorter maturity option in case of various options. Empty if no options. If the financial instrument has multiple options, the derivative part has to be used.
- **Source row in spec:** 56

### 43_Call_put_date

- **FunDataXML path:** `Position / BondCharacteristics / OptionalCallPut / CallPutDate`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** date (YYYY-MM-DD, ISO 8601)
- **CIC applicability:** CIC1, CIC2, CIC5, CIC6, CIC7, CIC8
- **Profile flags:**
    - `SST`: C
- **Definition:** Next call/put date
- **Codification (verbatim):**
  ```
  YYYY-MM-DD         ISO 8601
  ```
- **Comment:** The first expiry date for options can be captured here - the expiry date of the option element of bonds with embedded optionality.
- **Source row in spec:** 57

### 44_Issuer_bearer_option_exercise

- **FunDataXML path:** `Position / BondCharacteristics / OptionalCallPut / OptionDirection`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** alpha (1)
- **CIC applicability:** CIC1, CIC2, CIC5, CIC6, CIC7, CIC8
- **Profile flags:**
    - `SST`: C
- **Definition:** I : issuer B : bearer O : Both
- **Codification (verbatim):**
  ```
  Alpha(1) ("I "= Issuer / "B" = bearer / "O"= both)
  ```
- **Comment:** If available. For any instrument with a call / put that could be exercised by the issuer or the bearer.
- **Source row in spec:** 58

### 45_Strike_price_for_embedded_(call_put)_options

- **FunDataXML path:** `Position / BondCharacteristics / OptionalCallPut / StrikePrice`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** CIC1, CIC2, CIC5, CIC6, CIC7, CIC8
- **Profile flags:**
    - `SST`: C
- **Definition:** strike price, floor or cap rate for embedded options expressed as a percentage of the nominal amount.
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** Strike price, floor or cap rate for next date in case of multiple options
- **Source row in spec:** 59


## Section: Issuer data

### 46_Issuer_name

- **FunDataXML path:** `Position / CreditRiskData / InstrumentIssuer / Name`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** alphabetic / text
- **CIC applicability:** CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: C
- **Definition:** name of the issuer
- **Codification (verbatim):**
  ```
  Alpha (max 255)
  ```
- **Comment:** For OTC derivatives this data should be the counterpart. For derivative the underlying must be filled in field 80 For bank accounts, it must be the bank name
- **Source row in spec:** 61

### 47_Issuer_identification_code

- **FunDataXML path:** `Position / CreditRiskData / InstrumentIssuer / Code / Code`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** alphanumeric (max 20)
- **CIC applicability:** CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: C
- **Definition:** LEI
- **Codification (verbatim):**
  ```
  Alphanumeric (20)
  ```
- **Comment:** For OTC derivatives this data should be the counterpart. For derivative the underlying must be filled in field 81
- **Source row in spec:** 62

### 48_Type_of_identification_code_for_issuer

- **FunDataXML path:** `Position / CreditRiskData / InstrumentIssuer / Code / CodificationSystem`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** free text / numeric
- **CIC applicability:** CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: C
- **Definition:** C0220   1- LEI 9 - None
- **Codification (verbatim):**
  ```
  1 or 9
  ```
- **Comment:** For OTC derivatives this data should be the counterpart. For derivative the underlying must be filled in field 82
- **Source row in spec:** 63

### 49_Name_of_the_group_of_the_issuer

- **FunDataXML path:** `Position / CreditRiskData / IssuerGroup / Name`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** alphabetic / text
- **CIC applicability:** CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: C
- **Definition:** Name of the highest parent company
- **Codification (verbatim):**
  ```
  Alpha (max 255)
  ```
- **Comment:** For OTC derivatives this data should be the counterpart. For derivative the underlying must be filled in field 83
- **Source row in spec:** 64

### 50_Identification_of_the_group

- **FunDataXML path:** `Position / CreditRiskData / IssuerGroup / Code / Code`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** alphanumeric (max 20)
- **CIC applicability:** CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: C
- **Definition:** LEI
- **Codification (verbatim):**
  ```
  Alphanumeric (20)
  ```
- **Comment:** For OTC derivatives this data should be the counterpart. For derivative the underlying must be filled in field 84
- **Source row in spec:** 65

### 51_Type_of_identification_code_for_issuer_group

- **FunDataXML path:** `Position / CreditRiskData / IssuerGroup / Code / CodificationSystem`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** free text / numeric
- **CIC applicability:** CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: C
- **Definition:** C0260   1- LEI 9 - None
- **Codification (verbatim):**
  ```
  1 or 9
  ```
- **Comment:** For OTC derivatives this data should be the counterpart. For derivative the underlying must be filled in field 85. Only LEI should be used
- **Source row in spec:** 66

### 52_Issuer_country

- **FunDataXML path:** `Position / CreditRiskData / IssuerCountry`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** ISO 3166-1 alpha-2 country
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `NW675`: M
    - `SST`: M
    - `IORP`: M
    - `EIOPA_PF.06.03.24_LT`: C0040 - Country of issue
- **Definition:** Country of the issuer company
- **Codification (verbatim):**
  ```
  Code ISO 3166-1 alpha 2
  ```
- **Comment:** * The localisation of the issuer is assessed by the address of the entity issuing the asset.   * For investment funds, the country is relative to the fund’s manager.       One of the options in the following closed list to be used:     1.  ISO 3166-1 alpha-2 code.      2.  XA: Supranational issuers       3.  EU: European Union Institutions
- **Source row in spec:** 67

### 53_Issuer_economic_area

- **FunDataXML path:** `Position / CreditRiskData / EconomicArea`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** free text / numeric
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
- **Definition:** Economic area of the Issuer 1=EEA / 2=NON EEA / 3=NON OECD
- **Codification (verbatim):**
  ```
  Integer return corresponding to the following closed list:
  1 = EEA
  2 = OECD exclude EEA
  3 = Rest of the World
  ```
- **Comment:** Data point is optional if field 52 is provided as the issuer economic area can be mapped from the issuer country.
- **Source row in spec:** 68

### 54_Economic_sector

- **FunDataXML path:** `Position / CreditRiskData / EconomicSector`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** NACE V2.1
- **CIC applicability:** CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `NW675`: C
    - `SST`: C
- **Definition:** Economic sector
- **Codification (verbatim):**
  ```
  NACE Code (as per EIOPA documentation)
  ```
- **Comment:** Identify the economic sector of issuer based on the 2.0 version of the Statistical classification of economic activities in the European Community (‘NACE’) code V2.0. NACE should be full version for the financial sector i.e. 5 characters without dots. In the other cases, producers of the TPT shall provide the maximum information available (letter as a minimum plus 1, 2, 3 or 4 digits without dots), if meaningful. Please provide V2.1 in datapoint 148 if avalaible, otherwise provide V2.0 in this datapoint.
- **Source row in spec:** 69

### 55_Covered_not_covered

- **FunDataXML path:** `Position / CreditRiskData / Covered`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** alpha (2)
- **CIC applicability:** CIC1, CIC2
- **Profile flags:**
    - `SST`: C
- **Codification (verbatim):**
  ```
  Alpha(2) ("C" = Covered / "NC" = Non Covered)
  ```
- **Comment:** used for mortgage covered bonds and public sector covered bonds (art 22 UCITS directive 85/611/EEC)   - option to be confirmed: to add the guarantor name
- **Source row in spec:** 70

### 56_Securitisation

- **FunDataXML path:** `Position / Securitisation / Securitised`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** alpha (1)
- **CIC applicability:** CIC5, CIC6
- **Profile flags:**
    - `SST`: C
- **Definition:** Securitisation typology
- **Codification (verbatim):**
  ```
  alpha (1)
  "a" refers to the fact that the asset managers have not assessed the eligibility of a treatment of the securitisation positions under Solvency II
  "b" refers to security positions eligible for art 178 (3) and art 178 (5) introduced by the regulation 2018/1221. (Senior STS)
  "c" refers to security positions eligible for art 178 (4) and art 178 (6) introduced by the regulation 2018/1221. (Junior STS)
  "d" refers to resecuritisation positions as per art 178(7) introduced by the regulation 2018/1221. (re-securitisation)
  "e" refers to securitisation positions not covered by any other cases, categories as per Art 178 (8)  and Art 178 (9) introduced by the regulation 2018/1221. (non STS)
  "f" refers to security positions eligible for art 178a (1) & (2) introduced by the regulation 2018/1221. (transitional regime for type 1 securitisations without new underlying exposure since the 01/01/2019)
  "g" refers to security positions eligible for art 178a (3) introduced by the regulation 2018/1221. (transistional regime for some type 1 securitisations on residential mortgages)
  "h"refers to security positions eligible for art 178a (4) introduced by the regulation 2018/1221. (transistional regime for some type 1 securitisations on residential mortgages)
  "i" refers to security positions elligible for art 180 (10) and art 180 (10a) introduced by the regulation 2018/1221. (Securitisation secured by the EIB or the EIF)
  "j" refers to security positions that have been analysed and shall not be considered as "securitisation " under Solvency 2 (No securitisation).
  ```
- **Comment:** Used for synthetic ABS (synthetic asset backed securities, CDO etc.) and other ABS Or Structured Products only. Participant shall not fill this fields for assets other than CIC 5 or CIC 6.  Particpant shall fill "a" or "j" for structured notes or collateralized securities that are not considered as securisations.
- **Source row in spec:** 71

### 57_Explicit_guarantee_by_the_country_of_issue

- **FunDataXML path:** `Position / CreditRiskData / StateGuarantee`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** alpha (1)
- **CIC applicability:** CIC1, CIC2, CIC5, CIC6
- **Profile flags:**
    - `SST`: C
- **Definition:** Y = guaranteed N = without guarantee
- **Codification (verbatim):**
  ```
  Alpha (1) ("Y" = yes "N"= no)
  ```
- **Comment:** Data used to identify the debt guaranteed by a country Yes = 100%, No < 100%  Assets issued or guaranteed by Regional Governments and Local Authorities (RGLA) listed in the Implementing Regulation (EU) 2015/2011, with cic code 13 or 14 are considered guaranteed and have a “Y”. Other RGLA assets, not listed, should have a N.
- **Source row in spec:** 72

### 58_Subordinated_debt

- **FunDataXML path:** `Position / SubordinatedDebt`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** alpha (1)
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
- **Definition:** Subordinated or not ?
- **Codification (verbatim):**
  ```
  Alpha (1) ("Y" = yes "N"= no)
  ```
- **Source row in spec:** 73

### 58b_Nature_of_the_tranche

- **FunDataXML path:** `Position / Securitisation / TrancheLevel`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** alphabetic / text
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
- **Definition:** Tranche level (seniority)
- **Codification (verbatim):**
  ```
  Alpha
  ```
- **Comment:** additional line for the nature of the tranche  free value  alphanumeric
- **Source row in spec:** 74

### 59_Credit_quality_step

- **FunDataXML path:** `Position / CreditRiskData / CreditQualitStep`
- **Flag (Solvency II baseline):** Indicative
- **Codification kind:** free text / numeric
- **CIC applicability:** CIC1, CIC2, CIC5, CIC6, CIC7, CIC8, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `NW675`: I
    - `SST`: I
- **Definition:** Credit quality step as defined by S2 regulation
- **Codification (verbatim):**
  ```
  num (1)
  ```
- **Comment:** See also CEBS Standardised Approach convention.  One of the options in the following closed list shall be used : 0. Credit quality step 0 1. Credit quality step 1 2. Credit quality step 2 3. Credit quality step 3 4. Credit quality step 4 5. Credit quality step 5 6. Credit quality step 6 9. No rating available  Identify the credit quality step attributed to the asset, as defined by article 109a(1) of Directive 2009/138/EC
- **Source row in spec:** 75


## Section: Additional characteristics for derivatives

### 60_Call_Put_Cap_Floor

- **FunDataXML path:** `Position / DerivativeOrConvertible / OptionCharacteristics / CallPutType`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** alpha (3)
- **CIC applicability:** CIC2, CICB, CICC, CICE
- **Profile flags:**
    - `SST`: C
- **Definition:** Cal = Call Put = Put Cap = Cap Flr= Floor empty if none
- **Codification (verbatim):**
  ```
  Alpha(3)( "Cal" = Call / "Put" = Put / "Cap" = Cap / "Flr" = Floor)
  ```
- **Source row in spec:** 77

### 61_Strike_price

- **FunDataXML path:** `Position / DerivativeOrConvertible / OptionCharacteristics / StrikePrice`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** CIC2, CICB, CICC, CICE
- **Profile flags:**
    - `SST`: C
- **Definition:** Strike price expressed as the quotation of the underlying asset
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** Currency of issue  - underlying local currency * Foreign currency options - strike is shown as currency of Leg 1 against Leg 2 * Foreign currency forwards - strike is the forward rate of currency of Leg 1 against currency of Leg 2 * Swaptions - strike of option shown in this field, with Fixed rate of underlying swap is also shown in Coupon 33 Variance swaps - strike will be Volatility Strike Price, defined as square root of variance strike
- **Source row in spec:** 78

### 62_Conversion_factor_(convertibles)_concordance_factor_parity_(options)

- **FunDataXML path:** `Position / DerivativeOrConvertible / OptionCharacteristics / ConversionRatio`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** CIC2
- **Profile flags:**
    - `SST`: C
- **Definition:** Conversion factor for convertible
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** Conversion factor : ratio between the number of shares received and the number of bonds held in case of conversion for a convertible bond. Parity for options is not required in version 7  Concordance factor for bond futures is not required in version 7
- **Source row in spec:** 79

### 63_Effective_date_of_instrument

- **FunDataXML path:** `Position / DerivativeOrConvertible / OptionCharacteristics / Effective Date`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** date (YYYY-MM-DD, ISO 8601)
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
- **Definition:** Effective Date
- **Codification (verbatim):**
  ```
  YYYY-MM-DD         ISO 8601
  ```
- **Comment:** The date on which a derivative (such as an interest rate swap) would start to accrue interest
- **Source row in spec:** 80

### 64_Exercise_type

- **FunDataXML path:** `Position / DerivativeOrConvertible / OptionCharacteristics / OptionStyle`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** alpha (2)
- **CIC applicability:** CICB, CICC
- **Profile flags:**
    - `SST`: C
- **Definition:** AMerican, EUropean, ASiatic, BErmudian
- **Codification (verbatim):**
  ```
  Alpha (2)("AM", "EU", "AS", "BE")
  ```
- **Source row in spec:** 81

### 65_Hedging_rolling

- **FunDataXML path:** `Position / HedgingStrategy`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** alpha (3)
- **CIC applicability:** CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `NW675`: C
    - `SST`: O
- **Definition:** Indication of existing Risk Mitigation program ( Y = used for Risk Mitigation purpose and the position is systematically rolled before maturity, N = used for hedging purpose but no systematic roll before maturity); EPM = Efficient Portfolio Management / not used for hedging purpose .
- **Codification (verbatim):**
  ```
  Alpha (3)  ("Y" ; "N"; "EPM" )
  ```
- **Comment:** In order to be considered as a risk mitigation techniques, the hedging rolling criteria should be valide only for derivatives instruments with more than 1 month initial duration. (from inception to maturity).
- **Source row in spec:** 82


## Section: Derivatives / additional characteristics of the underlying asset

### 67_CIC_of_the_underlying_asset

- **FunDataXML path:** `Position / DerivativeOrConvertible /  UnderlyingInstrument / InstrumentCIC`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** alphanumeric (max 4)
- **CIC applicability:** CIC2, CICA, CICB, CICC, CICD, CICF
- **Profile flags:**
    - `SST`: C
- **Definition:** CIC Code (Complementary Identification Code).
- **Codification (verbatim):**
  ```
  Alphanumeric (4)
  ```
- **Comment:** This codification (CIC Table) would allow determination of : - the type and the country of the main codification - the S2 type of instrument  - the S2 subtype of instrument Complementary Identification Code used to classify assets, as set out in Annex V:  CIC Table - when classifying  asset using the CIC table, undertakings shall take into consideration the most representative risk to which the asset is exposed to.
- **Source row in spec:** 84

### 68_Identification_code_of_the_underlying_asset

- **FunDataXML path:** `Position / DerivativeOrConvertible /  UnderlyingInstrument / InstrumentCode / Code`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** free text / numeric
- **CIC applicability:** CIC2, CICA, CICB, CICC, CICD, CICF
- **Profile flags:**
    - `SST`: C
- **Definition:** identification code of underlying asset
- **Codification (verbatim):**
  ```
  Depends on identification type
  ```
- **Comment:** One of the options in the following closed list can be used:  1. ISO 6166 ISIN when available  2. other "recognised" code otherwise  (CUSIP, Bloomberg ticker, Reuters RIC )  3. Code attributed by the undertaking when the options above are not available.  The code used shall be kept consistent over time and shall not be reused for other products.  - Every asset has own code.
- **Source row in spec:** 85

### 69_Type_of_identification_code_for_the_underlying_asset

- **FunDataXML path:** `Position / UnderlyingInstrument / InstrumentCode / CodificationSystem`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** closed list (10 values)
- **CIC applicability:** CIC2, CICA, CICB, CICC, CICD, CICF
- **Profile flags:**
    - `SST`: C
- **Definition:** name of the codification used for identification of the underlying asset
- **Codification (verbatim):**
  ```
  One of the options in the following closed list to be used:
  1 - ISO 6166 for ISIN code
  2 - CUSIP (The Committee on Uniform Securities Identification Procedures number assigned by the CUSIP Service Bureau for U.S. and Canadian companies)
  3 - SEDOL (Stock Exchange Daily Official List for the London Stock Exchange)
  4 – WKN (Wertpapier Kenn-Nummer, the alphanumeric German identification number)
  5 - Bloomberg Ticker (Bloomberg letters code that identify a company's securities)
  6 - BBGID (The Bloomberg Global ID)
  7 - Reuters RIC (Reuters instrument code)
  8 – FIGI (Financial Instrument Global Identifier)
  9 - Other code by members of the Association of National Numbering Agencies
  99 - Code attributed by the undertaking
  ```
- **Comment:** Closed list is taken from QRT Log issued by EIOPA July 2015. Modified to add LEI in 2019  For OTC derivatives cf Mifid II requirements
- **Source row in spec:** 86

### 70_Name_of_the_underlying_asset

- **FunDataXML path:** `Position / DerivativeOrConvertible /  UnderlyingInstrument / InstrumentName`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** alphabetic / text
- **CIC applicability:** CIC2, CICA, CICB, CICC, CICD, CICF
- **Profile flags:**
    - `SST`: C
- **Definition:** Name
- **Codification (verbatim):**
  ```
  Alpha (max 255)
  ```
- **Source row in spec:** 87

### 71_Quotation_currency_of_the_underlying_asset_(C)

- **FunDataXML path:** `Position / DerivativeOrConvertible /  UnderlyingInstrument / Valuation / Currency`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** ISO 4217 currency
- **CIC applicability:** CIC2, CICA, CICB, CICC, CICD, CICF
- **Profile flags:**
    - `SST`: C
- **Definition:** currency of quotation for the asset
- **Codification (verbatim):**
  ```
  Code ISO 4217
  ```
- **Comment:** This field would be used to determine the forex risk exposure related to the underlying of a convertible. In case no ISO code exists, please refer to market practices (ex CNH for  Chines Yuam traded offshore)
- **Source row in spec:** 88

### 72_Last_valuation_price_of_the_underlying_asset

- **FunDataXML path:** `Position / DerivativeOrConvertible /  UnderlyingInstrument / Valuation / MarketPrice`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** CIC2, CICA, CICB, CICC, CICD, CICF
- **Profile flags:**
    - `SST`: C
- **Definition:** Last valuation price of the underlying asset
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** most recent price of the underlying asset  - optional  - linked to  the question of the  rationale to provide Greeks data  in the file
- **Source row in spec:** 89

### 73_Country_of_quotation_of_the_underlying_asset

- **FunDataXML path:** `Position / DerivativeOrConvertible /  UnderlyingInstrument / Valuation / Country`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** ISO 3166-1 alpha-2 country
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
- **Definition:** Country of quotation of the underlying asset
- **Codification (verbatim):**
  ```
  Code ISO 3166-1 alpha 2
  ```
- **Comment:** This field would be used to determine the action risk exposure of convertible bonds. Same codification to the first 2 characters of the CIC table. - optional
- **Source row in spec:** 90

### 74_Economic_area_of_quotation_of_the_underlying_asset

- **FunDataXML path:** `Position / DerivativeOrConvertible /  UnderlyingInstrument / Valuation / EconomicArea`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** free text / numeric
- **CIC applicability:** CIC2, CICA, CICB, CICC, CICD
- **Profile flags:**
    - `SST`: O
- **Definition:** economic area of quotation 0= non listed, listed 1=EEA / 2=NON EEA / 3=NON OECD
- **Codification (verbatim):**
  ```
  Integer return corresponding to the following closed list:
  0 = non-listed
  1 = EEA
  2 = OECD exclude EEA
  3 = Rest of the World
  ```
- **Comment:** Data point is optional if the CIC in field 67 is provided as the economic zone of quotation can be mapped from the first two positions of the CIC.
- **Source row in spec:** 91

### 75_Coupon_rate_of_the_underlying_asset

- **FunDataXML path:** `Position / DerivativeOrConvertible / UnderlyingInstrument / BondCharacteristics / CouponRate`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
- **Definition:** Fixed rate : coupon rate as a percentage of nominal amount all rates are expressed on an annual basis
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** to be entered if the underlying is an interest rate instrument. it is the same field as field 33 but for the underlying instrument
- **Source row in spec:** 92

### 76_Coupon_payment_frequency_of_the_underlying_asset

- **FunDataXML path:** `Position / DerivativeOrConvertible / UnderlyingInstrument / BondCharacteristics / CouponFrequency`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** free text / numeric
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
- **Definition:** number of coupon payment per year 0 = other than below options: 1= annual 2= biannual 4= quarterly 12= monthly 52= weekly
- **Codification (verbatim):**
  ```
  Frequency ("0" = other than /"1"= Annual  / "2"= biannual / "4"=quarterly / "12"= monthly / "52" = weekly)
  ```
- **Source row in spec:** 93

### 77_Maturity_date_of_the_underlying_asset

- **FunDataXML path:** `Position / DerivativeOrConvertible / UnderlyingInstrument / BondCharacteristics / Redemption / MaturityDate`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** date (YYYY-MM-DD, ISO 8601)
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
- **Definition:** Last redemption date
- **Codification (verbatim):**
  ```
  YYYY-MM-DD         ISO 8601
  ```
- **Comment:** Final maturity date for rate instruments or derivatives
- **Source row in spec:** 94

### 78_Redemption_profile_of_the_underlying_asset

- **FunDataXML path:** `Position / DerivativeOrConvertible / UnderlyingInstrument / BondCharacteristics / Redemption / Type`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** free text / numeric
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
- **Definition:** Type of redemption payment schedule : bullet, constant annuity…
- **Codification (verbatim):**
  ```
  "Bullet", "Sinkable", empty if non applicable
  ```
- **Comment:** This field is for ALM systems or to recalculate prices
- **Source row in spec:** 95

### 79_Redemption_rate_of_the_underlying_asset

- **FunDataXML path:** `Position / DerivativeOrConvertible / UnderlyingInstrument / BondCharacteristics / Redemption / Rate`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
- **Definition:** Redemption amount in % of nominal amount
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** 1=100%
- **Source row in spec:** 96

### 80_Issuer_name_of_the_underlying_asset

- **FunDataXML path:** `Position / DerivativeOrConvertible / UnderlyingInstrument / CreditRiskData / InstrumentIssuer / Name`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** alphabetic / text
- **CIC applicability:** CIC2, CICA, CICB, CICC, CICD, CICF
- **Profile flags:**
    - `SST`: O
- **Definition:** name of the issuer
- **Codification (verbatim):**
  ```
  Alpha (max 255)
  ```
- **Comment:** This is the issuer of the underlying instrument : for a CDS it is the name of the issuer of reference, for a convertible bond it is the issuer of the bond which may be different from the issuer of the convertible bond itself. For an Index put "index"
- **Source row in spec:** 97

### 81_Issuer_identification_code_of_the_underlying_asset

- **FunDataXML path:** `Position / DerivativeOrConvertible / UnderlyingInstrument / CreditRiskData / InstrumentIssuer / Code / Code`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** free text / numeric
- **CIC applicability:** CIC2, CICA, CICB, CICC, CICD, CICF
- **Profile flags:**
    - `SST`: O
- **Definition:** identification code of the issuer
- **Codification (verbatim):**
  ```
  Depend on the nomenclature used
  ```
- **Source row in spec:** 98

### 82_Type_of_issuer_identification_code_of_the_underlying_asset

- **FunDataXML path:** `Position / UnderlyingInstrument / Issuer / InstrumentIssuer / Identification / Code`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** free text / numeric
- **CIC applicability:** CIC2, CICA, CICB, CICC, CICD, CICF
- **Profile flags:**
    - `SST`: O
- **Definition:** C0220   1- LEI 9 - None
- **Codification (verbatim):**
  ```
  1 or 9
  ```
- **Source row in spec:** 99

### 83_Name_of_the_group_of_the_issuer_of_the_underlying_asset

- **FunDataXML path:** `Position / DerivativeOrConvertible / UnderlyingInstrument / CreditRiskData / IssuerGroup / Name`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** alphabetic / text
- **CIC applicability:** CIC2, CICA, CICB, CICC, CICD, CICF
- **Profile flags:**
    - `SST`: O
- **Definition:** Name of the highest parent company
- **Codification (verbatim):**
  ```
  Alpha (max 255)
  ```
- **Comment:** This is the issuer of the underlying instrument : for a CDS it is the name of the issuer of reference, for a convertible bond it is the issuer of the bond which may be different from the issuer of the convertible bond itself. For an Index put "index"
- **Source row in spec:** 100

### 84_Identification_of_the_group_of_the_underlying_asset

- **FunDataXML path:** `Position / DerivativeOrConvertible / UnderlyingInstrument / CreditRiskData / IssuerGroup / Code / Code`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** free text / numeric
- **CIC applicability:** CIC2, CICA, CICB, CICC, CICD, CICF
- **Profile flags:**
    - `SST`: O
- **Definition:** Identification code of the group
- **Codification (verbatim):**
  ```
  Depend on the nomenclature used
  ```
- **Source row in spec:** 101

### 85_Type_of_the_group_identification_code_of_the_underlying_asset

- **FunDataXML path:** `Position / DerivativeOrConvertible / UnderlyingInstrument / CreditRiskData / IssuerGroup / Code / CodificationSystem`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** free text / numeric
- **CIC applicability:** CIC2, CICA, CICB, CICC, CICD, CICF
- **Profile flags:**
    - `SST`: O
- **Definition:** C0260   1- LEI 9 - None
- **Codification (verbatim):**
  ```
  1 or 9
  ```
- **Source row in spec:** 102

### 86_Issuer_country_of_the_underlying_asset

- **FunDataXML path:** `Position / DerivativeOrConvertible / UnderlyingInstrument / CreditRiskData / Country`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** ISO 3166-1 alpha-2 country
- **CIC applicability:** CIC2, CICA, CICB, CICC, CICD, CICF
- **Profile flags:**
    - `SST`: O
- **Definition:** Country of the issuer company
- **Codification (verbatim):**
  ```
  Code ISO 3166-1 alpha 2
  ```
- **Source row in spec:** 103

### 87_Issuer_economic_area_of_the_underlying_asset

- **FunDataXML path:** `Position / DerivativeOrConvertible / UnderlyingInstrument / CreditRiskData / EconomicArea`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** free text / numeric
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
- **Definition:** economic area of the Issuer 1=EEA / 2=NON EEA / 3=NON OECD
- **Codification (verbatim):**
  ```
  Integer return corresponding to the following closed list:
  1 = EEA
  2 = OECD exclude EEA
  3 = Rest of the World
  ```
- **Comment:** Data point is optional if the datapoint 86_Issuer_country_of_the_underlying_asset is provided
- **Source row in spec:** 104

### 88_Explicit_guarantee_by_the_country_of_issue_of_the_underlying_asset

- **FunDataXML path:** `Position / DerivativeOrConvertible / UnderlyingInstrument / CreditRiskData  / StateGuarantee`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** alpha (1)
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
- **Definition:** Y = Guaranteed N = without guarantee
- **Codification (verbatim):**
  ```
  Alpha (1) ("Y" = yes "N"= no)
  ```
- **Comment:** Data used to identify the stocks guaranteed by a country
- **Source row in spec:** 105

### 89_Credit_quality_step_of_the_underlying_asset

- **FunDataXML path:** `Position / DerivativeOrConvertible / UnderlyingInstrument / CreditRiskData  / CreditQualityStep`
- **Flag (Solvency II baseline):** Indicative
- **Codification kind:** free text / numeric
- **CIC applicability:** CIC2, CICA, CICB, CICC, CICF
- **Profile flags:**
    - `SST`: I
- **Definition:** Credit quality step as defined by S2 regulation
- **Codification (verbatim):**
  ```
  num (1)
  ```
- **Comment:** See also CEBS Standardised Approach convention.  One of the options in the following closed list shall be used : 0. Credit quality step 0 1. Credit quality step 1 2. Credit quality step 2 3. Credit quality step 3 4. Credit quality step 4 5. Credit quality step 5 6. Credit quality step 6 9. No rating available  Identify the credit quality step attributed to the asset, as defined by article 109a(1) of Directive 2009/138/EC
- **Source row in spec:** 106


## Section: Analytics

### 90_Modified_duration_to_maturity_date

- **FunDataXML path:** `Position / Analytics / ModifiedDurationToMaturity`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** CIC1, CIC2, CIC5, CIC6, CIC7, CIC8, CICA, CICB, CICC, CICD
- **Profile flags:**
    - `NW675`: C
    - `SST`: C
    - `IORP`: O
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** Modified duration based on maturity date (contractual one), indicated in datapoint 39
- **Source row in spec:** 108

### 91_Modified_duration_to_next_option_exercise_date

- **FunDataXML path:** `Position / Analytics / ModifiedDurationToCall`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** CIC1, CIC2, CIC5, CIC6, CIC7, CIC8, CICB, CICC, CICD
- **Profile flags:**
    - `NW675`: C
    - `SST`: C
    - `IORP`: O
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** Modified duration based on next option exercice indicated in datapoint 43
- **Source row in spec:** 109

### 92_Credit_sensitivity

- **FunDataXML path:** `Position / Analytics / CreditSensitivity`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** CIC1, CIC2, CIC5, CIC6, CIC7, CIC8, CICD, CICF
- **Profile flags:**
    - `SST`: C
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** Modified duration based on maturity date (contractual) indicated in datapoint 39) eventually  used for the SCR spread risk calculation (e.g. based on 176 (1) or (2) DR 2015/35 for bonds and loans)
- **Source row in spec:** 110

### 93_Sensitivity_to_underlying_asset_price_(delta)

- **FunDataXML path:** `Position / Analytics / Delta`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** CIC2, CIC5, CIC6, CICA, CICB, CICC, CICE, CICF
- **Profile flags:**
    - `SST`: C
- **Definition:** Sensitivity to the underlying asset
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** Standard delta definition ( derivative of the option price by the underlying instrument price). For OTC derivatives: Standard delta definition (derivative of option price by the underlying instrument price).  Interest rate DV01 for interest rate swaps and Inflation DV01 for inflation swaps
- **Source row in spec:** 111

### 94_Convexity_gamma_for_derivatives

- **FunDataXML path:** `Position / Analytics / Convexity`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
- **Definition:** Convexity for interest rates instruments; or  gamma for derivatives with optional components
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** Standard convexity or gamma calculation if available The content of this field depends on the type of instrument. For convertible indicate yield convexity.
- **Source row in spec:** 112

### 94b_Vega

- **FunDataXML path:** `Position / Analytics / Vega`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** Derivative of the price of the optional instrument by the volatility, if available
- **Source row in spec:** 113


## Section: Transparency (Control)

### 95_Identification_of_the_original_portfolio_for_positions_embedded_in_a_fund

- **FunDataXML path:** `Position / LookThroughISIN`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** free text / numeric
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: C
- **Definition:** identification code of the investee funds
- **Codification (verbatim):**
  ```
  ISIN or CUSIP or any other identification
  ```
- **Comment:** Where the top level fund/share class on this template holds a second level fund there are two possible approaches: 1. the second level fund is reported as a single line holding with no further look-through to its holdings on the same template. 2. the second level fund's holdings are shown on a line-by-line basis on the top level fund template. In scenario 1. this field would not be required. In scenario 2. the second level fund would not appear as a line item having been replaced by its component holdings against which this field should be populated to identify those line-by-line positions of the second level fund. Note that no consolidation of common holdings between the top level fund and the second level fund should be undertaken.
- **Source row in spec:** 115


## Section: Indicative contributions to SCR (Instrument level - optional)

### 97_SCR_mrkt_IR_up_weight_over_NAV

- **FunDataXML path:** `Position / ContributionToSCR / MktIntUp`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** all CIC types
- **Definition:** Capital requirement for interest rate risk for the "up" shock    (Delta between Market value before and market value after stress)
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** optional - percentage of total net asset value of the fund( 100 %=1); algebraic sign: "+": increased capital requirements; "-" decreased capital requirements
- **Source row in spec:** 117

### 98_SCR_mrkt_IR_down_weight_over_NAV

- **FunDataXML path:** `Position / ContributionToSCR / MktintDown`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** all CIC types
- **Definition:** Capital requirement for interest rate risk for the "down" shock  (Delta between Market value before and market value after stress)
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** optional - percentage of total net asset value of the fund( 100 %=1) algebraic sign: "+": increased capital requirements; "-" decreased capital requirements
- **Source row in spec:** 118

### 99_SCR_mrkt_eq_type1_weight_over_NAV

- **FunDataXML path:** `Position / ContributionToSCR / MktEqGlobal`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** all CIC types
- **Definition:** Capital requirement for equity risk - Type 1 *)   (Delta between Market value before and market value after stress)
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** optional - percentage of total net asset value of the fund( 100 %=1) algebraic sign: "+": increased capital requirements; "-" decreased capital requirements In case of private equity funds for which every underlying investment is elligible to type_1_private_equity provisions and no investments represents more than 10 % of the funds valuation, then the asset manager, evenutally doing the calculation may consider every line as equity type 1 even if they are not listed and fill in this data point. If one investment of the portfolio does not repect these rules, then all the investments shall be considered as type 2 equity and this data point shall be blank.
- **Source row in spec:** 119

### 100_SCR_mrkt_eq_type2_weight_over_NAV

- **FunDataXML path:** `Position / ContributionToSCR / MktEqOther`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** all CIC types
- **Definition:** Capital requirement for equity risk - Type 2 *)  (Delta between Market value before and market value after stress)
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** optional - percentage of total net asset value of the fund( 100 %=1) algebraic sign: "+": increased capital requirements; "-" decreased capital requirements This field should also be filled for infrastructure investments since these investments are perfectly correlated with  type 2 equities as per formula described in UE DR 2017/1542 art 168
- **Source row in spec:** 120

### 101_SCR_mrkt_prop_weight_over_NAV

- **FunDataXML path:** `Position / ContributionToSCR / MktProp`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** all CIC types
- **Definition:** Capital requirement for property risk  (Delta between Market value before and market value after stress)
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** optional - percentage of total net asset value of the fund( 100 %=1) algebraic sign: "+": increased capital requirements; "-" decreased capital requirements
- **Source row in spec:** 121

### 102_SCR_mrkt_spread_bonds_weight_over_NAV

- **FunDataXML path:** `Position / ContributionToSCR / MktSpread / Bonds`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** all CIC types
- **Definition:** Capital requirement for spread risk on bonds  (Delta between Market value before and market value after stress)
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** optional - percentage of total net asset value of the fund( 100 %=1) algebraic sign: "+": increased capital requirements; "-" decreased capital requirements
- **Source row in spec:** 122

### 103_SCR_mrkt_spread_structured_weight_over_NAV

- **FunDataXML path:** `Position / ContributionToSCR / MktSpread / Structured`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** all CIC types
- **Definition:** Capital requirement for spread risk on structured products   (Delta between Market value before and market value after stress)
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** optional - percentage of total net asset value of the fund( 100 %=1) algebraic sign: "+": increased capital requirements; "-" decreased capital requirements
- **Source row in spec:** 123

### 104_SCR_mrkt_spread_derivatives_up_weight_over_NAV

- **FunDataXML path:** `Position / ContributionToSCR / MktSpread / DerivativesUp`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** all CIC types
- **Definition:** Capital requirement for spread risk - credit derivatives (upward shock)   (Delta between Market value before and market value after stress)
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** optional - percentage of total net asset value of the fund( 100 %=1) algebraic sign: "+": increased capital requirements; "-" decreased capital requirements
- **Source row in spec:** 124

### 105_SCR_mrkt_spread_derivatives_down_weight_over_NAV

- **FunDataXML path:** `Position / ContributionToSCR / MktSpread / DerivativesDown`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** all CIC types
- **Definition:** Capital requirement for spread risk - credit derivatives (downward shock)  (Delta between Market value before and market value after stress)
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** optional - percentage of total net asset value of the fund( 100 %=1) algebraic sign: "+": increased capital requirements; "-" decreased capital requirements
- **Source row in spec:** 125

### 105a_SCR_mrkt_FX_up_weight_over_NAV

- **FunDataXML path:** `Position / ContributionToSCR / MktFXUp`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** all CIC types
- **Definition:** Capital requirement for FX (upward shock)   (Delta between Market value before and market value after stress)
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** optional - percentage of total net asset value of the fund( 100 %=1) algebraic sign: "+": increased capital requirements; "-" decreased capital requirements
- **Source row in spec:** 126

### 105b_SCR_mrkt_FX_down_weight_over_NAV

- **FunDataXML path:** `Position / ContributionToSCR / MktFXDown`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** all CIC types
- **Definition:** Capital requirement for FX (downward shock)  (Delta between Market value before and market value after stress)
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** optional - percentage of total net asset value of the fund( 100 %=1) algebraic sign: "+": increased capital requirements; "-" decreased capital requirements
- **Source row in spec:** 127


## Section: Additional information Instrument   - QRTs: S.06.02 (old: Assets D1), S.06.03 (old: Assets D4) - optional

### 106_Asset_pledged_as_collateral

- **FunDataXML path:** `Position / QRTPositionInformation / CollateralisedAsset`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** closed list (5 values)
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
- **Definition:** Indicator used to identify the under-written instruments (Assets D1)
- **Codification (verbatim):**
  ```
  One of the options in the following closed list shall be used for the pledged part of the asset:
  1 – Assets in the balance sheet that are collateral pledged
  2 – Collateral for reinsurance accepted
  3 – Collateral for securities borrowed
  4 – Repos
  9 – Not collateral
  ```
- **Comment:** optional - needed for segregated account Identify assets kept in the undertaking’s balance–sheet that are pledged as collateral. For partially pledged assets two rows for each asset shall be reported, one for the pledged amount and another for the remaining part. This is the field C0100 of the S06.02 QRT template as described in the annex II of the 2015/2450 of 2 December 2015 laying down implementing technical standards with regard to the templates for the submission of information to the supervisory authorities. This field does not concerns collateral received but collateral given.
- **Source row in spec:** 129

### 107_Place_of_deposit

- **FunDataXML path:** `Position / QRTPositionInformation / PlaceOfDeposit`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** free text / numeric
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
- **Definition:** Instruments' place of deposit (S.06.02 - old: Assets D1)
- **Codification (verbatim):**
  ```
  ISO code
  ```
- **Comment:** optional - needed for segregated account (in order to fill QRT S0602 reports)
- **Source row in spec:** 130

### 108_Participation

- **FunDataXML path:** `Position / QRTPositionInformation / Participation`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** free text / numeric
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
- **Definition:** Indicator used to identify the guidelines of participation in accountancy terms
- **Codification (verbatim):**
  ```
  1 Participation / 2 non participation
  ```
- **Comment:** optional - needed for segregated account (in order to fill QRT S0602 reports)
- **Source row in spec:** 131

### 110_Valorisation_method

- **FunDataXML path:** `Position / QRTPositionInformation / ValorisationMethod`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** closed list (6 values)
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
    - `IORP`: O
- **Definition:** valuation method (cf specifications QRT) (S.06.02 - old: Assets D1)
- **Codification (verbatim):**
  ```
  Identify the valuation method used when valuing assets. One of the
  options in the following closed list shall be used:
  1 – quoted market price in active markets for the same assets
  2 – quoted market price in active markets for similar assets
  3 – alternative valuation methods
  4 – adjusted equity methods (applicable for the valuation of
  participations)
  5 – IFRS equity methods (applicable for the valuation of
  participations)
  6 – Market valuation according to Article 9(4) of Delegated
  Regulation 2015/35
  ```
- **Comment:** optional - needed for segregated account (in order to fill QRT S0602 reports)
- **Source row in spec:** 132

### 111_Value_of_acquisition

- **FunDataXML path:** `Position / QRTPositionInformation / AverageBuyPrice`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** free text / numeric
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
- **Definition:** Value of acquisition (S.06.02 - old: Assets D1)
- **Codification (verbatim):**
  ```
  Total acquisition value for assets held, clean value without accrued interest. .Not applicable to CIC categories 7 and 8.
  ```
- **Comment:** optional - needed for segregated account (in order to fill QRT S0602 reports)
- **Source row in spec:** 133

### 112_Credit_rating

- **FunDataXML path:** `Position / QRTPositionInformation / CounterpartyRating / RatingValue`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** —
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
- **Definition:** Rating of the counterparty / issuer (cf specifications QRT) (S.06.02 - old: Assets D1)
- **Comment:** optional - needed for segregated account (in order to fill QRT S0602 reports)
- **Source row in spec:** 134

### 113_Rating_agency

- **FunDataXML path:** `Position / QRTPositionInformation / CounterpartyRating / RatingAgency`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** —
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
- **Definition:** Name of the rating agency (cf specification QRT) (S.06.02 - old: Assets D1)
- **Comment:** optional - needed for segregated account (in order to fill QRT S0602 reports)
- **Source row in spec:** 135

### 114_Issuer_economic_area

- **FunDataXML path:** `Position / QRTPositionInformation / IssuerEconomicArea`
- **Flag (Solvency II baseline):** Not applicable
- **Codification kind:** free text / numeric
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
- **Definition:** economic area of the Issuer 1=EEA / 2=NON EEA / 3=NON OECD
- **Codification (verbatim):**
  ```
  Integer return corresponding to the following closed list:
  1 = EEA
  2 = OECD exclude EEA
  3 = Rest of the World
  ```
- **Comment:** Data point is option if the CIC in field 12 is provided as the economic zone of quotation can be mapped from the first two positions of the CIC.
- **Source row in spec:** 136


## Section: Additional Information Portfolio Characteristics - QRTs: S.06.02 (old: Assets D1), S.06.03 (old: Assets D4)

### 115_Fund_issuer_code

- **FunDataXML path:** `Portfolio / QRTPortfolioInformation / FundIssuer / Code / Code`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** alphabetic / text
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: O
    - `IORP`: M
    - `EIOPA_PF.06.02.24_ass`: C0150 - Issuer code; name of fund manager is required (fund man. vs. fund issuer)
- **Definition:** LEI when available, otherwise not reported
- **Codification (verbatim):**
  ```
  Alphanum
  ```
- **Comment:** S.06.02 (old: Assets D1)
- **Source row in spec:** 138

### 116_Fund_issuer_code_type

- **FunDataXML path:** `Portfolio / QRTPortfolioInformation / FundIssuer / Code / CodificationSystem`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** —
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: O
    - `IORP`: M
    - `EIOPA_PF.06.02.24_ass`: C0160 - Type of issuer code (fund man. vs. fund issuer)
- **Definition:** C0220   1- LEI 9 - None
- **Comment:** S.06.02 (old: Assets D1)
- **Source row in spec:** 139

### 117_Fund_issuer_name

- **FunDataXML path:** `Portfolio / QRTPortfolioInformation / FundIssuer / Name`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** alphabetic / text
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: O
- **Definition:** Name of Issuer of Fund  or Share Class
- **Codification (verbatim):**
  ```
  Alphanum
  ```
- **Comment:** S.06.02 (old: Assets D1)
- **Source row in spec:** 140

### 118_Fund_issuer_sector

- **FunDataXML path:** `Portfolio / QRTPortfolioInformation / FundIssuer / EconomicSector`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** alphabetic / text
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: O
    - `IORP`: M
    - `EIOPA_PF.06.02.24_ass`: C0170 - Issuer Sector  (fund man. vs. fund issuer)
- **Definition:** NACE code of Issuer of Fund  or Share Class
- **Codification (verbatim):**
  ```
  Alphanum
  ```
- **Comment:** S.06.02 (old: Assets D1) Nace codification required by EIOPA is different from the one required by European Regulation (V2.1) starting january 2025. Therefore TPT providers should  indicates the precedent version (NACE code V2),  untill new ITS are published.
- **Source row in spec:** 141

### 119_Fund_issuer_group_code

- **FunDataXML path:** `Portfolio / QRTPortfolioInformation / FundIssuerGroup / Code / Code`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** alphabetic / text
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: O
    - `IORP`: M
    - `EIOPA_PF.06.02.24_ass`: C0190 - Issuer Group Code (fund man. vs. fund issuer)
- **Definition:** LEI of ultimate parent when available, otherwise not reported
- **Codification (verbatim):**
  ```
  Alphanum
  ```
- **Comment:** S.06.02 (old: Assets D1)
- **Source row in spec:** 142

### 120_Fund_issuer_group_code_type

- **FunDataXML path:** `Portfolio / QRTPortfolioInformation / FundIssuerGroup / Code / CodificationSystem`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** —
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: O
    - `IORP`: M
    - `EIOPA_PF.06.02.24_ass`: C0200 - Type of issuer group code (fund man. vs. fund issuer)
- **Definition:** C0260   1- LEI 9 - None
- **Comment:** S.06.02 (old: Assets D1)
- **Source row in spec:** 143

### 121_Fund_issuer_group_name

- **FunDataXML path:** `Portfolio / QRTPortfolioInformation / FundIssuerGroup / Name`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** —
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: O
    - `IORP`: M
    - `EIOPA_PF.06.02.24_ass`: C0180 - Issuer Group (fund man. vs. fund issuer)
- **Definition:** Name of Ultimate parent of issuer of Fund or Share Class
- **Comment:** S.06.02 (old: Assets D1)
- **Source row in spec:** 144

### 122_Fund_issuer_country

- **FunDataXML path:** `Portfolio / QRTPortfolioInformation / FundIssuer / Country`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** ISO 3166-1 alpha-2 country
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: O
    - `IORP`: M
    - `EIOPA_PF.06.02.24_ass`: C0210 - Issuer Country (fund man. vs. fund issuer)
- **Definition:** Country ISO of Issuer of Fund  or Share Class
- **Codification (verbatim):**
  ```
  ISO 3166-1 alpha-2 code
  ```
- **Comment:** S.06.02 (old: Assets D1)
- **Source row in spec:** 145

### 123_Fund_CIC

- **FunDataXML path:** `Portfolio / QRTPortfolioInformation / PortfolioCIC`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** —
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: O
    - `IORP`: M
    - `EIOPA_PF.06.02.24_ass`: C0230 - CIC
    - `ECB_PFE.06.02.30`: EC0172 - Counterparty Sector according to ESA 2010 (input field) to map money market and other funds.
- **Definition:** CIC code - Fund  or Share Class (4 digits)
- **Comment:** S.06.02 (old: Assets D1)  - Remark:  first two digits are expected to be XL ( not country code)
- **Source row in spec:** 146

### 123a_Fund_custodian_country

- **FunDataXML path:** `Portfolio / QRTPortfolioInformation / FundCustodianCountry`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** ISO 3166-1 alpha-2 country
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: O
    - `IORP`: M
    - `EIOPA_PF.06.02.24_pos`: C0040 - Country of custody
- **Definition:** First level of Custody - Fund or seggregated account Custodian
- **Codification (verbatim):**
  ```
  ISO 3166-1 alpha-2 code
  ```
- **Comment:** S.06.02 (old: Assets D1)
- **Source row in spec:** 147

### 124_Duration

- **FunDataXML path:** `Portfolio / QRTPortfolioInformation / PortfolioModifiedDuration`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** —
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
    - `IORP`: M
    - `EIOPA_PF.06.02.24_ass`: C0270 - Duration
- **Definition:** For funds or portfolios mainly invested in debt instruments (>50%) - Fund modified Duration (Residual modified duration)
- **Comment:** S.06.02 (old: Assets D1) - Residual modified duration same as datapoint 10 for portfolios mainly invested in debt instruments.
- **Source row in spec:** 148

### 125_Accrued_income_(Security Denominated Currency)

- **FunDataXML path:** `Portfolio / QRTPortfolioInformation /  AccruedIncomeQC ????`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** —
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
    - `IORP`: O
- **Definition:** Amount of accrued income in security denomination currency at report date
- **Comment:** Control value as market values provided both including and excluding accrued income. This is at security level.
- **Source row in spec:** 149

### 126_Accrued_income_(Portfolio Denominated Currency)

- **FunDataXML path:** `Portfolio / QRTPortfolioInformation / AccruedIncomePC`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** —
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
    - `IORP`: M
    - `EIOPA_PF.06.02.24_pos`: C0090 - Accrued interest, tbd (input field to EIOPA template?)
- **Definition:** Amount of accrued income in portfolio denomination currency at report date
- **Comment:** Control value as market values provided both including and excluding accrued income.
- **Source row in spec:** 150


## Section: Specific data for convertible bonds - optional
(pricing of convertible bonds using shock modelling)

### 127_Bond_floor_(convertible_instrument_only)

- **FunDataXML path:** `Position / DerivativeOrConvertible / OptionCharacteristics / Convertible / BondFloor`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
- **Definition:** Lowest value of a convertible bond expressed in quotation currency, at current issuer spread
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** The lowest value that convertible bonds can fall to, given the present value of the remaining future cash flows and principal repayment. The bond floor is the value at which the convertible option becomes worthless because the underlying stock price has fallen substantially below the conversion value
- **Source row in spec:** 152

### 128_Option_premium_(convertible_instrument_only)

- **FunDataXML path:** `Position / DerivativeOrConvertible / OptionCharacteristics / Convertible / OptionPremium`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
- **Definition:** Premium of the embedded option of a convertible bond in quotation currency
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** The amount by which the price of a convertible security exceeds the current market value of the common stock into which it may be converted. A conversion premium is the difference between the price of the convertible and the greater of the conversion or straight-bond value.
- **Source row in spec:** 153


## Section: Specific data in case no yield curve of reference is available 
(investment in currencies with no yield curve of reference published by EIOPA)

### 129_Valuation_yield

- **FunDataXML path:** `Position / BondCharacteristics / ValuationYieldCurve /  Yield`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
- **Definition:** Valuation Yield of the interest rate instrument
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** This data may be used to recalculate yield curve of reference and determine the interest rate shock to be applied. To be discussed
- **Source row in spec:** 155

### 130_Valuation_z_spread

- **FunDataXML path:** `Position / BondCharacteristics / ValuationYieldCurve /  Spread`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** numeric (floating decimal)
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: O
- **Definition:** Issuer spread calculated from Z coupon IRS curve of quotation currency
- **Codification (verbatim):**
  ```
  number with floating decimal
  ```
- **Comment:** This data may be used to recalculate yield curve of reference and determine the interest rate shock to be applied. To be discussed
- **Source row in spec:** 156

### 131_Underlying_asset_category

- **FunDataXML path:** `Position / Instrument/ UAC`
- **Flag (Solvency II baseline):** Mandatory
- **Codification kind:** closed list (9 values)
- **CIC applicability:** CIC0, CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CIC9, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `SST`: O
    - `IORP`: M
    - `EIOPA_PF.06.03.24_LT`: C0030 - Underlying asset category
- **Definition:** SII definition as per QRT S.06.03
- **Codification (verbatim):**
  ```
  One of the options in the following closed list shall be used:
  1 - Government bonds
  2 - Corporate bonds
  3L - Listed equity
  3X - Unlisted equity
  4 - Collective Investment Undertakings
  5 - Structured notes
  6 - Collateralised securities
  7 - Cash and deposits
  8 - Mortgages and loans
  9 - Properties
  0 - Other investments (including receivables)
  A – Futures
  B – Call Options
  C – Put Options
  D – Swaps
  E – Forwards
  F – Credit derivatives
  L - Liabilities
  ```
- **Comment:** please refer to the S06.03 template specification in RD UE 2015/2450
- **Source row in spec:** 157


## Section: Additional Fields decided in September 2016 incorporated in the version V4

### 132_Infrastructure_investment

- **FunDataXML path:** `To be defined with Fundxml`
- **Flag (Solvency II baseline):** Indicative
- **Codification kind:** closed list (6 values)
- **CIC applicability:** CIC1, CIC2, CIC3, CIC5, CIC6, CIC8, CIC9
- **Profile flags:**
    - `NW675`: I
- **Definition:** Type of infrastructure investment according to Type of infrastructure investment according to  COMMISSION DELEGATED REGULATION (EU) 2016/467 of 30 September 2015 amending Commission Delegated Regulation (EU) 2015/35 concerning the calculation of regulatory capital requirements for several categories of assets held by insurance and reinsurance undertakings and COMMISSION DELEGATED REGULATION (EU) 2017/1542 as of 8 June 2017 amending Delegated Regulation (EU) 2015/35 concerning the calculation of regulatory capital requirements for certain categories of assets held by insurance and reinsurance undertakings (infrastructure corporates).
- **Codification (verbatim):**
  ```
  0 - Not assessed
  1 - Debt on eligible Infrastructure project
  2 - Equity on  eligible infrastructure project
  3 - Debt on eligible Infrastructure corporate
  4 - Equity on eligible infrastructure corporate
  5 - Non eligible
  ```
- **Comment:** Data used to calculate reduced SCR for investments on Infrastructure project. The asset manager should conduct the diligence to determine if the instrument is eligible and what is the kind of risk supported by the investor ( equity or debt).   Eligible instruments can be infrastructure projects as well as  infrastructure corporates.  Indicative assessment should not exempt the assurance company from their duties. This field should be filled with "not assessed for other instruments than infrastrcure investments".
- **Source row in spec:** 159


## Section: Additional Information Portfolio Characteristics - QRTs: S.06.02 (old: Assets D1) optional

### 133_custodian_name

- **FunDataXML path:** `To be defined with Fundxml`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** free text / numeric
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `SST`: M
    - `IORP`: M
    - `EIOPA_PF.06.02.24_pos`: C0050 - Custodian, note that in the EIOPA template the LEI code is preferred
- **Definition:** Name of the custodian of the seggregated account
- **Codification (verbatim):**
  ```
  text
  ```
- **Comment:** S.06.02 (old: Assets D1)
- **Source row in spec:** 161


## Section: Additional information  - RD EU 2019/981


## Section: 134_type1_private_equity_portfolio_eligibility


## Section: 135_type1_private_equity_issuer_beta


## Section: Instrument Characteristics
Additional counterparty information for instruments


## Section: 137_Counterparty_sector


## Section: 138_Collateral_eligibility


## Section: 139_Collateral_Market_valuation_in_portfolio_currency


## Section: Additional data point V7

### 140_Custodian_identification_code

- **FunDataXML path:** `Position / DerivativeOrConvertible / UnderlyingInstrument / CreditRiskData / InstrumentIssuer / Code / Code`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** free text / numeric
- **CIC applicability:** all CIC types
- **Definition:** identification code of the custodian
- **Codification (verbatim):**
  ```
  Depend on the nomenclature used
  ```
- **Comment:** Identification of the custodian code using the LEI if available. If none is available this item shall not be reported.
- **Source row in spec:** 170

### 141_Type_of_custodian_identification_code

- **FunDataXML path:** `Position / UnderlyingInstrument / Issuer / InstrumentIssuer / Identification / Code`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** free text / numeric
- **CIC applicability:** all CIC types
- **Definition:** C0220   1-LEI,  9-None or internal
- **Codification (verbatim):**
  ```
  "1" or "9"
  ```
- **Comment:** Identification of the type of code used for the “Code of custodian” item. Mandatory if dp 140_custodian_identification_code is filled
- **Source row in spec:** 171


## Section: 142_Bail-in_Rule

### 143_Maturity_date_expected

- **FunDataXML path:** `Expected redemption date after considering an expected prepayment / extension of the contractual terms based on the options in the instrument`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** date (YYYY-MM-DD, ISO 8601)
- **CIC applicability:** all CIC types
- **Definition:** Expected redemption date after considering an expected prepayment / extension of the contractual terms based on the options in the instrument
- **Codification (verbatim):**
  ```
  YYYY-MM-DD         ISO 8601
  ```
- **Comment:** Expected maturity date for fixed income instrument or derivatives. Potential exercise of prepayment / extension options should be considered. This datapoint is needed by insurance companies in case the legal/contractual maturity date is different from the expected maturity date used sor SCR calculation.
- **Source row in spec:** 173


## Section: 144_Modified_duration_to_maturity_date_expected


## Section: 145_Credit_sensitivity_expected


## Section: 146_PIK

### 147_Infrastructure_investment_additional_QRT

- **FunDataXML path:** `To be defined with Fundxml`
- **Flag (Solvency II baseline):** Optional
- **Codification kind:** closed list (10 values)
- **CIC applicability:** all CIC types
- **Profile flags:**
    - `NW675`: I
- **Definition:** (for mandate or segregated account especially)  Identify if the asset is an infrastructure investment as defined in Article 1(55a) and (55b) of Delegated Regulation (EU) 2015/35. One of the options in the following closed list shall be used:
- **Codification (verbatim):**
  ```
  1 – Not an infrastructure investment;
  2 – Infrastructure non-qualifying: Government Guarantee (Government, Central bank, Regional government or local authority);
  3 – Infrastructure non-qualifying: Government Supported including Public Finance initiative (Government, Central bank, Regional government or local authority);
  4 – Infrastructure non-qualifying: Supranational Guarantee/Supported (ECB, Multilateral development bank, International organisation);
  9 – Infrastructure non-qualifying: Other non-qualifying infrastructure loans or investments, not classified under the above categories;
  12 – Infrastructure qualifying: Government Guarantee (Government, Central bank, Regional government or local authority);
  13 – Infrastructure qualifying: Government Supported including Public Finance initiative (Government, Central bank, Regional government or local authority);
  14 – Infrastructure qualifying: Supranational Guarantee/Supported (ECB, Multilateral development bank, International organisation);
  19 – Infrastructure qualifying: Other qualifying infrastructure investments, not classified in the above categories;
  20 – European Long-Term Investment Fund (ELTIF investing in infrastructure assets and ELTIF investing in other – non infrastructure – assets)
  ```
- **Comment:** Data used for C0330 datapoint of the S0602
- **Source row in spec:** 177

### 148_Economic_sector_NACE2.1

- **FunDataXML path:** `Position / CreditRiskData / EconomicSector`
- **Flag (Solvency II baseline):** Conditional
- **Codification kind:** NACE V2.1
- **CIC applicability:** CIC1, CIC2, CIC3, CIC4, CIC5, CIC6, CIC7, CIC8, CICA, CICB, CICC, CICD, CICE, CICF
- **Profile flags:**
    - `NW675`: C
    - `SST`: C
- **Definition:** Economic sector
- **Codification (verbatim):**
  ```
  NACE V2.1 Code
  ```
- **Comment:** Identify the economic sector of issuer based on the latest version of the Statistical classification of economic activities in the European Community (‘NACE’) code (as published in an EC Regulation). Producers of the TPT shall provide the maximum information available (letter as a minimum plus 1, 2, 3 or 4 digits without dots), if meaningful. if V2.1 is not available, please provide V2.0 codification in datapoint 54.
- **Source row in spec:** 178


## Section: 1000_TPT_Version

