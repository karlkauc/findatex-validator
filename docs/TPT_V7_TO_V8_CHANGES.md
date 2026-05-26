# TPT V7.0 → V8.0 — Änderungsreport

Vergleich der gebündelten Spec-Dateien:

- **V7.0** — `core/src/main/resources/spec/tpt/TPT_V7_20241125.xlsx` (Sheet `TPT V7.0`, Release 2024-11-25)
- **V8.0** — `core/src/main/resources/spec/tpt/TPT_V8_20260526.xlsx` (Sheet `TPT V8.0`, Release 2026-05-26)

> Methodik: zellgenauer Diff aller nummerierten Datenfelder (NUM_DATA-Präfix)
> über Pfad, Definition, Codification, Comment, M/C/O-Flag, CIC-Applicability
> sowie die Profil-Spalten NW 675 / SST / IORP.

## Auf einen Blick

| Kennzahl | V7.0 | V8.0 |
|----------|------|------|
| Nummerierte Datenfelder | 145 | 147 (**+2**) |
| Spaltenlayout (Sheet) | 35 Spalten | 35 Spalten (**unverändert**) |
| CIC-Applicability-Spalten | 12–27 | 12–27 (**unverändert**) |
| Profil-Spalten (NW675/SST/IORP) | 29 / 30 / 31–35 | identisch |
| ISIN/LEI-Spalten für externe Validierung | 14/68, 47–141 | **unverändert** (inkl. Custodian-LEI 140/141) |
| Entfernte Felder | — | **keine** |

**Fazit:** V8 ist eine inhaltlich kleine, **strukturell deckungsgleiche**
Revision von V7. Die Validierung läuft manifest-getrieben — V8 nutzt dieselbe
`tpt-v8-info.json` (Kopie von V7 mit geänderten Versions-/Datums-Metadaten) und
dieselbe External-Validation-Config wie V7.

---

## 1. Neue Felder (2)

Beide sind **konditional (`C`)**, regulatorisch motiviert (Solvency II,
RD UE 2015/35) und werden — gemäß Projektregel „never invent regulatory logic" —
**rein mechanisch** geprüft (Vorhandensein/Format/Closed-List), ohne eigene
Trigger-Logik.

### Feld 150 — `150_LTEI_Fund_Elligibility` (`C`)
- **Definition:** Elligibility of the held fund to LTEI dispositions according to regulation
- **Codification (Closed List):** `0 – Not assessed` · `1 – Eligible` · `2 – Not elligible`
- **Hintergrund:** Art. 171d RD UE 2015/35 (revidiert) — bestimmte OGAW mit
  niedrigerem Risikoprofil können für Long-Term-Equity-Investment-Regelungen
  qualifizieren und von einer reduzierten Kapitalanforderung profitieren.

### Feld 151 — `151_Legislative_program_investment` (`C`)
- **Definition:** Elligibility of the held equities to legislative programmes dispositions
- **Codification (Closed List):** `0 – Not assessed` · `1 – Eligible` · `2 – Not elligible`
- **Hintergrund:** Art. 173 RD UE 2015/35 — Aktieninvestments (direkt oder über
  OGAW) unter bestimmten Legislativprogrammen können von einer reduzierten
  Kapitalanforderung profitieren.

---

## 2. Umbenanntes Feld (1)

### Feld 148 — `Economic_sector_NACE2.1` → `Economic_sector_NACE`
- **Codification:** `NACE V2.1 Code` → `Latest version of NACE codification in force`
- **Comment:** Der Satz „if V2.1 is not available, please provide V2.0
  codification in datapoint 54." wurde **entfernt**.
- **Wirkung:** Versions-Pinning auf NACE 2.1 aufgehoben; verweist nun generisch
  auf die jeweils aktuelle NACE-Codification. Manifest-getrieben → automatisch.

---

## 3. Geänderte Codification (Closed-List-relevant)

### Feld 56 — Securitisation-Eligibility unter Solvency II
Der bisherige Einzelcode **`"e"`** wurde in **drei Codes aufgeteilt**:

| V7 | V8 | Bedeutung |
|----|----|-----------|
| `e` (Art. 178(8)/(9)) | `e1` | senior non-STS (Art. 178(8)) |
|    | `e2` | junior non-STS (Art. 178(8a)) |
|    | `e3` | others (Art. 178(9)) |

Comment-Ergänzung in V8: „According to S2 amendment UE tbd. e) categorie has
been divided in 3 categories e1, e2, e3." Daneben rein kosmetische
Whitespace-Bereinigungen in den übrigen Codes (a–d, f–j).

> **Validierungs-Hinweis:** Der Closed-List-Check liest die zulässigen Codes aus
> der Spec — die neuen `e1/e2/e3` werden für V8 automatisch akzeptiert. Falls
> Sample-Fixtures oder Tests den alten Code `"e"` hartkodieren, gilt dieser unter
> V8 als ungültig.

---

## 4. Definition-/Comment-Präzisierungen (keine Validierungswirkung)

Reine Textanpassungen; sie ändern weder Flags, Format noch Closed-Lists.

| Feld | Art | V7 → V8 (Kurzfassung) |
|------|-----|------------------------|
| 1 | Comment | Umformuliert (Identifikation Fonds/Anteilsklasse bei mehreren Klassen) |
| 4 | Comment | Gekürzt (Share-Class-Currency) |
| 46–51 | Comment | „OTC derivatives" → „derivatives … (Central clearing house for listed one)"; Gegenpartei-/Underlying-Hinweise klarer gefasst |
| 51 | Comment | „Only LEI should be used" → „LEI is required" |
| 115 | Definition | „LEI when available, otherwise not reported" → „LEI" |
| 119 | Definition | „LEI of ultimate parent when available, otherwise not reported" → „LEI of ultimate parent" |
| 140 | Codification | „Depend on the nomenclature used" → „LEI of the custodian" |
| 1000 | Codification | Versionshistorie um Zeile „V8 (official) dated XXXXXXXXXX" ergänzt (Platzhalter-Datum im Draft) |

---

## 5. CIC-Applicability-Notation (Felder 47, 50)

In V7 trugen die CIC-Zellen den konditionalen Text
`x\nif item 48 set to "1"` (Feld 47) bzw. `…item 51…` (Feld 50). In V8 steht
nur noch ein schlichtes `x`.

> Der manifest-getriebene Loader wertet eine **nicht-leere** CIC-Zelle als
> „anwendbar". Da beide Versionen nicht-leere Zellen haben, ist der **geparste
> Applicability-Scope identisch** — keine funktionale Auswirkung, nur
> Notations-Bereinigung in der Spec.

---

## 6. Nicht-Feld-Änderungen

- **Intro-/Disclaimer-Prosa** (Zeilen vor der Tabelle) wurde umgeschrieben
  (Solvency-II-Erläuterung statt FinDatEx-Disclaimer). Kein Validierungsbezug.
- Versions-Zelle im Sheet: „Version 8 dated 26/05/19" — vermutlich Draft-Typo;
  wir verwenden bewusst **2026-05-26** als Release-Datum (siehe CHANGELOG).

---

## 7. Auswirkung auf den Validator

| Bereich | Auswirkung |
|---------|-----------|
| Spec-Loader / Manifest | Keine — V8 nutzt dieselbe Manifest-Struktur (config-only) |
| Presence/Format/Closed-List | Felder 150/151 + neue `e1/e2/e3`-Codes (56) automatisch aus der Spec |
| Cross-Field-Regeln (XF-*) | Keine neuen Regeln; 150/151 mechanisch (kein SME-Trigger) |
| External Validation (ISIN/LEI) | Keine — identisches Spaltenlayout, Config geteilt mit V7 |
| `TptVersionRule` (XF-15) | Erwartet bei V8-Ruleset Token „V8"/„8.0" in Feld 1000 |

### Offene Punkte
- Felder **150/151** sind absichtlich **nur mechanisch** geprüft; eine
  regulatorische Trigger-Logik (LTEI- bzw. Legislativprogramm-Bedingungen)
  ist **DEFERRED** und bräuchte SME-Freigabe.
- Feld **1000** trägt in der Draft-Spec ein Platzhalter-Datum
  („XXXXXXXXXX"); bei einer finalen FinDatEx-Veröffentlichung ggf. Release-Datum
  und Versionshistorie nachziehen.
