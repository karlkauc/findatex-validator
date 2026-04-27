# Roadmap — offene Punkte und mögliche Erweiterungen

Stand: nach Implementierung von XF-16 … XF-25 (Commit `9d2f46c` + folgende). Die wichtigsten Validierungsregeln aus der TPT V7-Spec sind eingebaut. Dieses Dokument hält fest, was als nächstes ansteht und welche Erweiterungen sich anbieten — sortiert nach Priorität.

---

## 1. Restlicher Spec-Inhalt, der noch keine Rule hat

### 1.1 Indikative Conditional-Rule für Feld 135 (Private-Equity-Beta)

| Aspekt | Inhalt |
|---|---|
| **Spec-Zelle** | Row 164, CIC3 + CIC4: `x only if 134 is set to 1` |
| **Flag** | `I` (Indicative) |
| **Vorschlag** | Eintrag in `RuleRegistry.CONDITIONAL_REQUIREMENTS` mit Severity `INFO` |
| **Code-Location** | `src/main/java/com/tpt/validator/validation/RuleRegistry.java`, identisch zu XF-25 |
| **Aufwand** | 5 Minuten + 1 Test |

Skizze:
```java
new ConditionalRequirement("XF-26/PE_BETA",
        "134", FieldPredicate.EqualsAny.of("1"),
        "135", Severity.INFO),
```

### 1.2 Narrative Rule für Feld 95 (Look-through-Identifikation) — **teilweise erledigt**

| Aspekt | Inhalt |
|---|---|
| **Spec-Zelle** | Row 115, alle 16 CIC: `If coming from the lookthrough of an underlying fund` |
| **Flag** | `C` |
| **Status** | Generic ConditionalPresenceRule für Feld 95 wird in `RuleRegistry.ADDITIONALLY_SUPPRESSED_FROM_GENERIC_RULES` unterdrückt — sonst hätte jede Position in einer normalen TPT-Datei eine `COND_PRESENCE/95`-Warning ausgelöst (bei 100 Positionen = 100 False Positives). |
| **Offen** | Ein expliziter UI-Schalter "Look-through-Modus" könnte die Pflicht für Feld 95 reaktivieren, wenn der User die Datei als Look-through markiert. Heuristik (Position-Portfolio-ID weicht von Datei-Portfolio-ID ab) bleibt zu unzuverlässig. |

### 1.3 Erweiterte Closed-List-Werte für Feld 32 (Interest_rate_type)

Spec-Codification: `"Fixed" or "Floating" or "Variable" or "Inflation_linked"`. XF-10 erkennt heute nur `Fixed` und `Floating`/`Variable`. Falls `Inflation_linked` mit eigenen Pflicht-Folgefeldern verknüpft ist, könnte man XF-10 erweitern. Bisher keine Folge-Pflicht in der Spec gefunden.

### 1.4 Cross-Field-Konsistenzen, die in der Spec nur als COMMENT auftauchen

Folgende Hinweise stehen in der Spalte E (COMMENT), nicht als Pflicht:

| Feldpaar | Hinweis | Mögliche XF-Rule |
|---|---|---|
| 13 vs 12-Land | "Economic area can be derived from CIC country code" (CIC erste 2 Zeichen) | XF-27 Konsistenz |
| 53 vs 52-Land | "Issuer economic area can be mapped from issuer country" | XF-28 Konsistenz |
| 87 vs 86-Land | gleicher Mechanismus für Underlying-Issuer | XF-29 |
| 21 vs 4 | Quotation currency = Portfolio currency wenn Position rein in Portfolio-Land | XF-30 (FX-Schwelle) |
| 28 vs 24 / 22 | Marktexposure-Größe vs Marktwert-Größe | XF-31 (Sanity-Check) |

Severity: WARNING (es sind Plausibilitäten, keine harten Pflichten).

---

## 2. Validierung jenseits des per-Datei-Snapshots

### 2.1 SST-Profil aktivieren ✅ (erledigt)

Implementiert: SST als viertes Profil neben Solvency II / IORP-EIOPA-ECB / NW 675. Spalte AD der Spec wird vom `SpecLoader` parsed; UI-Checkbox in `MainView.fxml` (default off, opt-in für Schweizer Mandate); Field-Coverage-Tab im Excel-Report bekommt eine SST-Spalte. Tests: SST-Pflichtfelder gegen die echte Spec assertioniert (`SpecLoaderTest#sstColumnIsParsedForKnownFields`), End-zu-End-Run mit `EnumSet.of(Profile.SST)` zeigt nur SST-Findings.

### 2.2 FunDataXML-Eingabe (echtes XML)

Die TPT V7-Distribution erlaubt neben dem flachen xlsx/csv auch ein hierarchisches XML mit Pfaden wie `Portfolio/PortfolioID/Code`. Mein `SpecCatalog.byPath()` ist bereits dafür vorbereitet, aber es gibt noch keinen `XmlLoader`.

| Punkt | Inhalt |
|---|---|
| **Komplexität** | Mittel — XSD nicht im Repo, Schema müsste aus dem Pfad-Baum der Spec rekonstruiert werden |
| **Library** | Java-StAX oder Jackson XML |
| **Mehrwert** | Manche Asset Manager liefern primär XML; xlsx ist oft eine Konvertierung daraus |
| **Plan** | Neuer `XmlLoader` der `Portfolio` einmalig + `Position` als wiederholtes Element parst, Mapping über `SpecCatalog.byPath()` |

### 2.3 PDF-Export des Quality-Reports

| Punkt | Inhalt |
|---|---|
| **Library** | OpenHTMLtoPDF oder iText (LGPL) |
| **Inhalt** | identisch zu den Excel-Tabs: Summary, Scores, Findings, Field Coverage, Per Position |
| **Aufwand** | 4–6 Stunden inkl. Layout-Tuning |

### 2.4 Batch-Validierung mehrerer Files

Ein Lauf über mehrere TPT-Files desselben Fonds (Zeitreihe) oder verschiedener Fonds (Portfolio-Übersicht). Output:

- Aggregierter Excel-Report mit einer Datei pro Tab.
- Zeitreihe der Quality-Scores für einen einzelnen Fonds.
- Vergleichs-Diff zwischen zwei Reportingständen.

UI: zusätzliche Drop-Zone die mehrere Files akzeptiert; Progress-Liste; ein gemeinsamer Score-Trend-Graph.

### 2.5 Historischer Vergleich / Diff

Zwei Reportingstände desselben Fonds nebeneinander:
- Was hat sich an Findings geändert?
- Welche Positionen sind dazugekommen / weggefallen?
- Wie hat sich der Quality-Score entwickelt?

Implementierung als zweites `MainView` mit zwei File-Pickern.

---

## 3. Engine-interne Verbesserungen

### 3.1 Predicate-Erweiterungen für komplexere Bedingungen

Aktuell: `EqualsAny` und `NotBlank`. Mögliche Ergänzungen:

| Predicate | Anwendung |
|---|---|
| `BlankOrEqualsAny` | "Wert ist leer ODER ein bestimmter Code" |
| `Numeric` (mit Range) | "Wert ist eine Zahl in [min, max]" |
| `Compound` (AND/OR) | Kombinierte Bedingungen, falls die Spec sie einführt |
| `RowPredicate` | Bedingung über mehrere Felder (heute nur über ein Source-Feld) |

Refactor: `FieldPredicate` zu `Predicate<TptRow>` umbauen, falls nötig — der bisherige Code bleibt rückwärts-kompatibel über einen Adapter.

### 3.2 XF-09 / XF-10 in das deklarative Schema migrieren

`CustodianPairRule` und `InterestRateTypeRule` sind heute hand-codiert, weil:
- XF-09 ist bidirektional (140 ohne 141 UND 141 ohne 140).
- XF-10 hat zwei Trigger-Werte mit unterschiedlichen Target-Sets.

Mit erweiterten Predicates könnten beide in das deklarative Schema wandern. Reine Cleanup-Arbeit, kein funktionaler Mehrwert.

### 3.3 Auto-Generierung der Conditional-Requirements aus der Spec

Die Patterns `if item X set to "Y"` / `if item X is not blank` / `if X is "1" "2" or "3"` lassen sich aus dem CIC-Qualifier-Text der Spec automatisch parsen. Dann wäre die Liste in `RuleRegistry` nicht mehr manuell, sondern abgeleitet.

| Pro | Contra |
|---|---|
| Spec-Updates ziehen sich automatisch durch | Brüchiger Parser, schlecht debugbar |
| Garantierte Vollständigkeit | Schwer zu erweitern (z. B. neue Severity-Heuristik pro Pattern) |

Empfehlung: erst angehen, wenn die manuelle Liste wirklich groß wird (>50 Einträge). Für jetzt ist die manuelle Liste der bessere Ort.

### 3.4 Quality-Score: gewichten nach Weight (Feld 26)

Heute zählen alle Findings gleich. Sinnvoller: ein Format-Error auf einer Position mit 50 % Gewicht trifft härter als auf einer 0,1 %-Position. Vorschlag:

- `QualityScorer` bekommt Zugriff auf `FindingContext.valuationWeight()` (existiert seit Commit `9d2f46c`).
- Findings tragen einen `weightImpact` (default 1, sonst die Position-Weight).
- Score-Formel: gewichtete Summe statt simpler Anzahl.

### 3.5 Deutsche UI-Lokalisierung

`messages_en.properties` ist heute nur ein Stub. Eine `messages_de.properties` + Locale-Switcher in der UI würde dem deutschsprachigen Asset-Manager-Umfeld entgegenkommen.

---

## 4. UI / UX-Verfeinerung

### 4.1 Clickable Drill-down

Klick auf ein Finding in der Tabelle springt zum Detail (Position-Daten, alle Felder dieser Row, hervorgehobene betroffene Spalten). Heute zeigt die UI nur die Tabellen-Zeilen.

### 4.2 Severity-Heatmap

Pro Position eine kleine Spalte mit Farb-Heatmap (rot/orange/grün), aufgeteilt nach Score-Kategorie. Schneller visueller Eindruck welche Positionen problematisch sind.

### 4.3 Gauge-Charts für Scores

Heute zeigen die Score-Cards nur die Zahl. Echte Tachometer-Visuals (z. B. via JFreeChart) wären lesbarer.

### 4.4 Recent-Files-Liste

Letzte 10 geöffnete Files unter `File → Recent`. Speichern in `~/.config/tpt-validator/recent.json`.

### 4.5 Drag & Drop in der Hauptansicht

Datei in das Hauptfenster ziehen → automatisch laden. Heute nur über den File-Picker.

---

## 5. Externe Validierung

### 5.1 ISIN-Lookup gegen ANNA / Open Figi

Heute prüft `IsinRule` nur die Luhn-Checksumme. Online-Lookup gegen die ANNA-Datenbank oder OpenFIGI bestätigt, dass die ISIN auch existiert (und dass Issuer/Country/Currency konsistent sind).

| Aspekt | Inhalt |
|---|---|
| **API** | OpenFIGI ist kostenlos, ANNA kostet |
| **Caching** | lokales JSON oder SQLite |
| **Privacy** | Lookup leakt ISINs an externe Server — Opt-in nötig |

### 5.2 LEI-Lookup gegen GLEIF

Analog für LEIs. GLEIF stellt ein kostenloses öffentliches API. Lookup würde den Issuer-Namen, Land, Status (active/lapsed) zurückliefern → zusätzliche Konsistenz-Checks gegen Felder 46, 52.

### 5.3 ISO 4217 / 3166 Updates

Heute baked-in via `Currency.getAvailableCurrencies()` und `Locale.getISOCountries()`. JDK-Updates ziehen das mit. Bei besonders neuen Codes (z. B. ZWG für Zimbabwe ZiG) kann ein Override-File hilfreich sein.

---

## 6. Testing / Qualitätssicherung

### 6.1 Property-Based Tests mit jqwik

Statt fester Test-Daten generierte Eingaben:
- "Für jeden zufällig generierten CIC-String + zufälligen Sub-Cat-Char gilt: parser-output und appliesToCic-Logik sind konsistent."

Würde vor allem die Sub-Kategorie-Logik (Commit `6acecca`) stresstesten.

### 6.2 Mutation-Testing mit PIT

Pitest prüft, ob die Test-Suite tatsächlich Bug-Mutations erkennt. Bei einer 96 %-Coverage-Engine sollte das Mutation-Score ≥ 80 % ergeben — wenn nicht, sind die Tests zu lax.

### 6.3 Performance-Benchmarks

Für sehr große TPT-Files (>10.000 Positionen) den Ingest und die Validierung profilen. Apache POI's SXSSF-Streaming wird heute nicht genutzt — bei großen Files könnte das relevant werden.

### 6.4 TestFX-basierte UI-Tests

Aktuell ist die UI bewusst nicht getestet (0 % Coverage). TestFX würde zumindest Smoke-Tests ermöglichen ("App startet, Datei laden, Validate, Findings-Tabelle hat ≥ 1 Zeile").

---

## 7. Distribution / Operations

### 7.1 GitHub Actions CI

Bei jedem Push:
- `mvn test` + JaCoCo
- Coverage-Badge in README pflegen
- Bei Tag-Push: `jpackage` Artifacts (Linux .deb, macOS .dmg, Windows .msi) als GitHub-Release-Asset bauen

### 7.2 Docker-Image für Headless-Modus

Falls eine CLI-Variante hinzukommt: ein Docker-Image, das die Engine als Batch-Processor anbietet. Wäre nützlich für CI-Integration bei Asset Managern.

### 7.3 CLI-Variante des Validators

`java -jar tpt-validator-cli.jar input.xlsx --profiles=SOLVENCY_II,IORP_EIOPA_ECB --format=json` für headless-Pipelines. Reused den bestehenden `ValidationEngine` und schreibt JSON statt JavaFX-UI.

---

## 8. Spec-Tracking

### 8.1 Auto-Detect bei Spec-Update

FinDatEx publiziert neue Spec-Versionen (V8 ist denkbar). Bei jedem Spec-Update sollte:
- `tools/audit_qualifiers.py` neue / verschwundene Patterns flaggen.
- `tools/generate_requirements.py` ein Diff der `requirements.md` produzieren.

Workflow: Spec-XLSX austauschen → Tests rot → fehlende Patterns ergänzen → grün.

### 8.2 PIK-Guidelines V8 mitführen

Heutiger Fix bezieht sich auf `PIK guidelines 240913.xlsx`. Bei einer V8 müsste der `PikRule`-Cases-Mechanismus angepasst werden.

---

## Wo anfangen?

Wenn ich aus dieser Liste die **drei** Punkte mit höchstem Verhältnis von Mehrwert zu Aufwand picken müsste, wären es:

1. **1.1 + 1.4** — XF-26 (Beta) + Land-/Economic-Area-Konsistenz-Rules. ~2 Stunden, klare Findings, deckt restliche Spec ab.
2. **5.2 GLEIF-Lookup** — ein bisschen Engineering-Aufwand, aber riesiger Konsistenz-Mehrwert. Cross-checking gegen ein Live-Register fängt echte Daten-Verschlechterungen, die sonst durchrutschen.
3. **2.1 SST-Profil** — Schweizer Mandate sind oft mit dabei; ein voll funktionsfähiges SST würde den Validator für ein zusätzliches Land komplett machen.

Alles andere ist Polish und kann beliebig priorisiert werden.
