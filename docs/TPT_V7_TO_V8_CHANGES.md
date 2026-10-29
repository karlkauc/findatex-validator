# TPT V7.0 → V8.0 — Änderungsreport (finale V8, 8. Oktober 2026)

Vergleich der gebündelten Spec-Dateien:

- **V7.0** — `core/src/main/resources/spec/tpt/TPT_V7_20241125.xlsx` (Sheet `TPT V7.0`, Release 2024-11-25)
- **V8.0** — `core/src/main/resources/spec/tpt/TPT_V8_20261008.xlsx` (Sheet `TPT V8.0`, Release 2026-10-08;
  FinDatEx-Datei „TPT V8 post consultation 261008V2.xlsx“, veröffentlicht 2026-10-28)

Quellen: FinDatEx News 93 („FinDatEx publishes version 8 of the Tripartite
Template“), der Technical Annex „TPT V8 – Key changes and implementation
timeline“ (3 Seiten, unter `specs/TPT/v80/`) und ein zellgenauer Diff aller
nummerierten Datenfelder (Pfad, Definition, Codification, Comment, Flag,
CIC-Applicability, Profil-Spalten NW 675 / SST / IORP).

> Die zuvor gebündelte V8 vom 26.05.2026 war der **Konsultationsentwurf**. Die
> finale Fassung unterscheidet sich davon im Sheet-Layout und in rund 30
> Feldern (siehe Abschnitt 7).

## Zeitplan (FinDatEx)

| Produktion | Version |
|------------|---------|
| Dezember 2026 | **V7** |
| Januar / Februar 2027 | V7 oder V8 (parallel) |
| ab März 2027 | **V8** verpflichtend |

Der Dateiname muss die Version tragen (`…_TPTV8_…`), Feld 1000 muss
`V8.0 (official) dated 8 October 2026` enthalten.

## Auf einen Blick

| Kennzahl | V7.0 | V8.0 final |
|----------|------|------------|
| Katalogfelder (Validator) | 152 | 154 (**+2**) |
| Spalten im Sheet | 35 | **26** |
| Primary-Flag-Spalte (M/C/O/I/N/A) | 11 | **6** |
| CIC-Applicability-Spalten | 12–27 | **7–22** |
| Profil-Spalten NW 675 / SST / IORP | 29 / 30 / 31–35 | **24 / 25 / 26** |
| Physisch entfernte Felder | — | **keine** (18 Felder N/A) |
| Neue Felder | — | 150, 151 |
| Umbenannte Felder | — | 62, 148 |

**Fazit:** Inhaltlich eine moderate Revision, strukturell aber ein neues
Sheet-Layout (Hilfsspalten „Reference data / Identification / SCR / QRT /
Control“ und die vier EIOPA-Teilspalten sind weg). Manifest
`tpt-v8-info.json` ist deshalb kein V7-Klon mehr; IORP ist eine einzelne
`flag`-Spalte statt `presenceMerge`.

---

## 1. Neue Felder (2) — konditional, Closed List `0 / 1 / 2`

| Feld | Header | Flag | CIC | Hintergrund |
|------|--------|------|-----|-------------|
| 150 | `150_LTEI_Fund_Eligibility` | C | 3, 4 | Art. 171a/171d DelVO (EU) 2015/35 – Long-Term-Equity-Regime für Fonds ohne Look-Through |
| 151 | `151_Legislative_program_investment` | C | 3 | Art. 173 DelVO (EU) 2015/35 – Aktien unter Legislativprogrammen |

Codification: `0 – Not assessed`, `1 – Eligible`, `2 – Not eligible`. Der
Validator prüft rein mechanisch (Format/Closed List); keine Trigger-Logik.

## 2. Felder „retained but no longer maintained“ (18) — N/A, müssen leer sein

`17b, 29, 31, 35, 36, 44, 54, 58b, 106, 108, 114, 116, 118, 120, 124, 125, 126, 141`

Codification, CIC-Spalten und alle Profil-Flags sind in der Spec geleert;
Comment: „No longer maintained by Findatex. Kept for technical reasons and
deletion in 2028 under consideration.“ Der Technical Annex: *must be left
empty (Null)*.

**Validator:** `NotApplicableRule` → `NOT_APPLICABLE/<num>` als **WARNING**
bei befülltem Feld (nicht im Score). Presence-/Conditional-Regeln entfallen
automatisch über das Manifest. 127/128 (Bond Floor, Option Premium) wurden in
der Konsultation zur Streichung diskutiert, **bleiben aber optional**.

## 3. Issuer- und Gruppen-Identifikation — jetzt Pflicht

| Feld | V7 | V8 final |
|------|----|----------|
| 47 Issuer_identification_code | C | **M**, alle CIC inkl. CIC 0, `Alphanumeric (max 20)` |
| 48 Type_of_identification_code_for_issuer | C, `1 or 9` | **M**, Closed List `1 / 2 / 9` |
| 50 Identification_of_the_group | C | **M**, alle CIC inkl. CIC 0 |
| 51 Type … issuer_group | C, `1 or 9` | **M**, `1 / 2 / 9` |
| 81 Issuer … underlying | C („x for 22“, A1 …) | **M**, CIC 2/A/B/C/D/F pauschal |
| 82 Type … underlying issuer | C | **M**, `1 / 2 / 9`, SST O→C |
| 84 Group … underlying | C („if 85 = 1“) | C, Qualifier entfernt |
| 85 Type … underlying group | C | **M**, `1 / 2 / 9`, SST O→C |

Typ-Codes: `1` LEI · `2` externer Identifier (VAT-ID im EWR, sonst
ISO-3166-α2 + nationale Registernummer, z. B. `US` + EIN) · `9` intern.
**Achtung:** In V7 bedeutete `9` „none“ – Mappingtabellen anpassen. Reihenfolge
laut Annex: LEI vor externem vor internem Identifier.

**115, 119, 140** (Fund issuer, Fund issuer group, Custodian) **müssen LEIs
enthalten**; die Typspalten 116/120/141 sind N/A. 140 bleibt O („LEI of the
custodian … if none is available this item shall not be reported“).

**Validator:** `LeiRule("47","48")` etc. prüfen nur bei Typ `1`;
`LeiRule("115")`, `("119")`, `("140")` prüfen jeden Wert; externe Validierung
(GLEIF) analog über `TptTemplate.EXTERNAL_VALIDATION_V8`. `CustodianPairRule`
(XF-09) und die Konditionalregeln XF-20…XF-24 gelten nur noch für V7.

## 4. Securitisation (Feld 56)

`e` → `e1` (senior non-STS, Art. 178 (8)), `e2` (junior non-STS, Art. 178 (8a)),
`e3` (other, Art. 178 (9)). Übrige Codes a–d, f–j unverändert. Closed List im
Validator automatisch aus der Codification.

## 5. Umbenannte Felder / revidierte Definitionen

| Feld | V7 | V8 |
|------|----|----|
| 62 | `Conversion_factor_(convertibles)_concordance_factor_parity_(options)` | `62_Conversion_factor` — nur Convertibles (CIC 22), nicht mehr für Optionen/Derivate |
| 148 | `Economic_sector_NACE2.1` | `148_Economic_sector_NACE` — „latest NACE version in force“; NW 675 jetzt C |

HeaderMapper matcht auf die Feldnummer, alte Header werden also weiterhin
zugeordnet. Dateien mit V7-Headern laufen daher gegen V8 durch; nur die
Feld-1000-Prüfung (XF-15) und die N/A-Warnungen schlagen an.

## 6. Konditionalregeln – was in V8 noch gilt

| Regel | V7 | V8 | Grund |
|-------|----|----|-------|
| XF-09 Custodian pair 140/141 | ✔ | ✘ | 141 N/A |
| XF-10 Floating → 34, 35, 36, 37 | ✔ | **34, 37** | 35/36 N/A |
| XF-16 29 → 31 | ✔ | ✘ | beide N/A |
| XF-17 34 → 35 / 36 / 37 | ✔ | **nur 37** | 35/36 N/A |
| XF-18 42 = Cal/Put → 43 / 44 | ✔ | **nur 43** | 44 N/A |
| XF-19 42 → 45 | ✔ | ✔ | |
| XF-20/21 48/51 = 1 → 47/50 | ✔ | ✘ | 47/50 sind M → PresenceRule |
| XF-22 85 = 1 → 84 | ✔ | ✘ | Qualifier aus Spec entfernt, 84 C |
| XF-23/24 116/120 = 1 → 115/119 | ✔ | ✘ | 116/120 N/A, 115/119 M |
| XF-25 138 ∈ {1,2,3} → 139 | ✔ | ✔ | |
| Übrige XF-01…XF-15 | ✔ | ✔ | SCR-Felder strukturell unverändert |

## 7. Finale V8 vs. Konsultationsentwurf (26.05.2026)

- Sheet-Layout 35 → 26 Spalten (siehe Tabelle oben); Spalte B heißt jetzt
  „FundsXML XPath“ mit vollständigen Pfaden `/FundsXML4/RegulatoryReportings/IndirectReporting/TripartiteTemplateSolvencyII_V8/…`.
- Nur 2 Felder im Entwurf N/A → 18 in der finalen Fassung.
- 47/48/50/51/81/82/85 im Entwurf noch C → final M; Typ-Code `2` neu.
- 56: `e1/e2/e3` neu; 140: „LEI of the custodian“.
- Tippfehler korrigiert (`Elligibility` → `Eligibility`, `Not Aplicable` …).
- Versionszeile: „Version 8 dated 26/05/19“ → „Version 8.0 dated 08/10/26“;
  Feld 1000 nennt jetzt das offizielle Datum.

## 8. Umsetzung im Validator (Kurzfassung)

- `tpt-v8-info.json` neu (Flag 6, CIC 7–22, NW/SST/IORP 24/25/26).
- `TptTemplate.V8_0` → `TPT_V8_20261008.xlsx`, Label „TPT V8.0 — 2026-10-08“,
  eigenes `EXTERNAL_VALIDATION_V8`.
- `TptRuleSet` versionsabhängig (`isV8OrLater()`, `CONDITIONAL_REQUIREMENTS_V8`,
  `V8_DROPPED_XF_RULES`), typlose `LeiRule`, `InterestRateTypeRule(FLOATING_FIELDS_V8)`,
  neue generische `NotApplicableRule`.
- Fixtures `samples/tpt/v8/` (`tools/build_tpt_v8_samples.py`,
  `TptV8ExampleSamplesTest`). „Try an example“ folgt der gewählten Version:
  V8.0 lädt `samples/tpt/v8/00_showcase.xlsx` (V7-Showcase-Bestand als
  V8-Lieferung plus `FORMAT/150`, `PRESENCE/50`, `NOT_APPLICABLE/54`), V7.0
  weiterhin `samples/tpt/00_showcase.xlsx`. Da V8 die Felder 81/82/85 für
  *jede* CIC-2-Position verpflichtend macht (XML-Pfad und V7 meinen nur
  Wandelanleihen, CIC 22), befüllt das V8-Showcase sie bei Unternehmensanleihen
  mit den eigenen Kennungen des Emittenten.
- Rule-Referenz regeneriert (`docs/rules/tpt-v8-0.md`).
