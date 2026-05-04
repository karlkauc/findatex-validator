package com.findatex.validator.validation.rules.crossfield;

import com.findatex.validator.spec.FieldSpec;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.spec.SpecLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stufe-1-Audit (Verifikation) der drei TPT-Crossfield-Regeln mit hartkodierter
 * CIC-Whitelist (XF-05/CASH, XF-11/MATURITY, XF-13/PIK). Der Test macht den
 * <em>aktuellen</em> Stand der Spec-Anwendbarkeit der Gate-Felder explizit und
 * dient als Regression-Schutz gegen künftige Spec-Updates.
 *
 * <h3>Befunde Stand 2026-05-04 (TPT V7.0)</h3>
 *
 * <ul>
 *   <li><b>XF-11/MATURITY (Feld 39 Maturity date) — BREITER ALS CODE</b>: Die Spec
 *       erklärt Feld 39 für 12 CIC-Klassen anwendbar (1, 2, 5, 6, 7, 8, A, B, C, D,
 *       E, F), für CIC7 nur Sub-Codes 3/4/5. Die hartkodierte Code-Whitelist
 *       {1, 2, 5, 6, 8} verpasst Errors auf CIC7 (Money market), Futures, Options,
 *       Swaps, Forwards und Credit Derivatives. Stufe-2-Kandidat. Siehe
 *       {@code field39_specApplicabilityIsBroaderThanCodeWhitelist}.</li>
 *   <li><b>XF-13/PIK (Feld 146)</b>: Spec deklariert das Feld <em>für alle CICs</em>
 *       anwendbar. Der Code grenzt zusätzlich auf CIC2/CIC8 ein, basierend auf den
 *       PIK-Guidelines (Domain-Wissen, nicht Spec). Keine Spec-Konformitäts-Frage —
 *       die Code-Beschränkung ist intentional enger als die Spec.</li>
 *   <li><b>XF-05/CASH (Felder 9, 24)</b>: Die Regel summiert MarketValue über alle
 *       CIC-7-Zeilen; sie konsultiert kein Gate-Feld, sondern die CIC-Klasse direkt.
 *       Es gibt 10 V7-Felder (32, 33, 38–41, 59, 90–92), die für CIC7 auf Sub-Codes
 *       3/4/5 (Money market) eingeschränkt sind, aber das berührt die Aggregation in
 *       {@code CashPercentageRule} nicht — alle CIC7-Sub-Codes (71–79) zählen
 *       legitim als "Cash & deposits".</li>
 *   <li><b>Inventur</b>: V7 hat 31 Felder mit Sub-Code-Qualifiern. Alle werden in
 *       generischen Regeln (PresenceRule, ConditionalPresenceRule, FormatRule) via
 *       {@code FieldSpec.appliesToCic(cat, sub)} korrekt gehandhabt — nur die drei
 *       hand-geschriebenen Crossfield-Regeln oben sind Risikostellen.</li>
 * </ul>
 */
class HardcodedCicWhitelistsAreSpecConsistentTest {

    private static final SpecCatalog CATALOG = SpecLoader.loadBundled();

    /**
     * XF-11/MATURITY (MaturityAfterReportingRule) filtert hartkodiert auf
     * {1, 2, 5, 6, 8} — die Spec erlaubt Feld 39 jedoch in 12 Klassen mit teils
     * Sub-Code-Restriktion. Dieser Test dokumentiert die Diskrepanz: er hält die
     * Spec-Realität fest (grün), und der Klassendoc-Kommentar oben markiert den
     * Code-Mismatch als Stufe-2-Backlog-Item.
     */
    @Test
    @DisplayName("XF-11: Spec-Anwendbarkeit von Feld 39 ist breiter als Code-Whitelist {1,2,5,6,8}")
    void field39_specApplicabilityIsBroaderThanCodeWhitelist() {
        FieldSpec spec = CATALOG.byNumKey("39").orElseThrow();

        // Spec: 12 Klassen anwendbar.
        assertThat(spec.applicableCic()).containsExactlyInAnyOrder(
                "CIC1", "CIC2", "CIC5", "CIC6", "CIC7", "CIC8",
                "CICA", "CICB", "CICC", "CICD", "CICE", "CICF");

        // CIC7 ist auf Sub-Codes 3/4/5 (Money market) eingeschränkt — alle anderen
        // adressierten Klassen haben plain "x".
        assertThat(spec.applicableSubcategories())
                .containsEntry("CIC7", new TreeSet<>(Set.of("3", "4", "5")))
                .doesNotContainKeys("CIC1", "CIC2", "CIC5", "CIC6", "CIC8",
                                    "CICA", "CICB", "CICC", "CICD", "CICE", "CICF");

        // Code-Whitelist (zur Erinnerung — nicht assertet, da nur in der Regel-Datei steht).
        Set<String> codeWhitelist = Set.of("CIC1", "CIC2", "CIC5", "CIC6", "CIC8");
        Set<String> ignoredByCode = new TreeSet<>(spec.applicableCic());
        ignoredByCode.removeAll(codeWhitelist);
        assertThat(ignoredByCode)
                .as("CIC-Klassen, für die die Spec Feld 39 deklariert, die XF-11 aber ignoriert")
                .containsExactlyInAnyOrder("CIC7", "CICA", "CICB", "CICC", "CICD", "CICE", "CICF");
    }

    /**
     * XF-13/PIK (PikRule) nutzt eine Whitelist {2, 8}, die enger ist als die
     * Spec-Anwendbarkeit (alle CICs). Diese Engung kommt aus den PIK-Guidelines,
     * nicht aus der TPT-Spec — daher kein Spec-Defekt, aber eine bewusste
     * Domain-Heuristik die hier explizit dokumentiert wird.
     */
    @Test
    @DisplayName("XF-13: Feld 146 (PIK) ist laut Spec für alle CICs anwendbar; Code-Beschränkung auf CIC2/CIC8 stammt aus PIK-Guidelines")
    void field146_specHasNoCicRestriction_codeAddsBondLoanHeuristic() {
        FieldSpec spec = CATALOG.byNumKey("146").orElseThrow();
        assertThat(spec.applicableCic())
                .as("Leeres applicableCic = appliesAlways (Spec deklariert Feld 146 für jede CIC-Klasse)")
                .isEmpty();
        assertThat(spec.appliesToAllCic()).isTrue();
        assertThat(spec.applicableSubcategories()).isEmpty();
    }

    /**
     * XF-05/CASH (CashPercentageRule) summiert über CIC7-Zeilen. Der Catalog
     * enthält Felder, die innerhalb von CIC7 nur auf Sub-Codes 3/4/5 (Money
     * market) anwendbar sind — diese betreffen aber andere Spec-Felder, nicht
     * die Aggregation in XF-05. Die Regel arbeitet semantisch korrekt auf
     * "alle Cash-Position-Sub-Codes 71–79".
     */
    @Test
    @DisplayName("XF-05: V7-Felder mit CIC7-Sub-Code-Restriktion {3,4,5} betreffen Coupon/Date/Premium-Felder, nicht die Cash-Aggregation")
    void cic7SubcodeRestrictedFields_doNotAffectCashPercentageAggregation() {
        Map<String, Set<String>> cic7Restricted = new TreeMap<>();
        for (FieldSpec spec : CATALOG.fields()) {
            Set<String> sub = spec.applicableSubcategories().getOrDefault("CIC7", Set.of());
            if (!sub.isEmpty()) cic7Restricted.put(spec.numKey(), new TreeSet<>(sub));
        }
        assertThat(cic7Restricted.keySet())
                .as("V7-Felder mit CIC7-Sub-Code-Qualifier (Stand 2026-05-04)")
                .containsExactlyInAnyOrder("32", "33", "38", "39", "40", "41", "59", "90", "91", "92");
        // Alle eingeschränkten Felder bei CIC7: Sub-Codes 3, 4, 5 (Money market).
        cic7Restricted.values().forEach(s -> assertThat(s).containsExactly("3", "4", "5"));

        // Die XF-05-relevanten Aggregations-Felder (9 = CashPercentage, 24 = MarketValue B)
        // tauchen in dieser Liste NICHT auf — sie sind also nicht CIC7-sub-restringiert.
        assertThat(cic7Restricted).doesNotContainKeys("9", "24");
    }

    /**
     * Übergreifende Spec-Inventur — fixiert den aktuellen Stand der Felder mit
     * Sub-Code-Qualifiern, damit künftige Spec-Updates sofort sichtbar werden.
     * Wenn der Inventory wächst, prüfen, ob die zusätzlichen Felder von einer
     * hartkodiert-CIC-filternden Regel berührt werden (aktuell: nur die drei
     * im Klassendoc oben aufgeführten).
     */
    @Test
    @DisplayName("Inventur: 31 V7-Felder haben Sub-Code-Qualifier — Snapshot stabil")
    void v7SubcodeQualifierInventorySnapshot() {
        Map<String, Map<String, Set<String>>> inventory = new TreeMap<>();
        for (FieldSpec spec : CATALOG.fields()) {
            Map<String, Set<String>> subs = spec.applicableSubcategories();
            Map<String, Set<String>> nonEmpty = new LinkedHashMap<>();
            subs.forEach((k, v) -> { if (!v.isEmpty()) nonEmpty.put(k, new TreeSet<>(v)); });
            if (!nonEmpty.isEmpty()) inventory.put(spec.numKey(), nonEmpty);
        }

        // Inventory-Größe als Health-Check.
        assertThat(inventory).hasSize(31);

        // Die drei für die Stufe-2-Diskussion zentralen Felder.
        assertThat(inventory.get("39"))
                .as("Feld 39 (Maturity date) — XF-11-Gate")
                .containsExactly(Map.entry("CIC7", new TreeSet<>(Set.of("3", "4", "5"))));

        // Feld 146 (PIK) hat KEINE Sub-Code-Qualifier in der Spec.
        assertThat(inventory).doesNotContainKey("146");

        // Feld 67 (Underlying CIC) — bereits durch UnderlyingCicRule (Commit d426fdd)
        // korrekt spec-driven.
        assertThat(inventory.get("67"))
                .containsEntry("CIC2", new TreeSet<>(Set.of("2")))
                .containsEntry("CICD", new TreeSet<>(Set.of("4", "5")));

        // Stichproben weiterer betroffener Felder (verifiziert: alle laufen über die
        // generischen PresenceRule/FormatRule und damit über FieldSpec.appliesToCic):
        assertThat(inventory).containsKeys(
                "16", "18", "19",                                // Instrument-/Issuer-Stammdaten
                "32", "33", "38", "40", "41", "59",              // Coupon / Date / Premium / Yield
                "60", "61", "62",                                // Convertible-Felder
                "68", "69", "70", "71", "72", "74",              // Underlying-Stammdaten
                "80", "81", "82", "83", "85", "86", "89",        // Underlying-Issuer-Stammdaten
                "90", "91", "92", "93");                         // Strukturierte/Collateralized Underlyings
    }
}
