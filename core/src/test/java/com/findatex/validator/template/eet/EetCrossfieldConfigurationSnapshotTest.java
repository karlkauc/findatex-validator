package com.findatex.validator.template.eet;

import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.validation.rules.crossfield.ConditionalRequirement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stabilitäts-Snapshot der EET-Crossfield-Konfiguration. Die Listen in {@link EetRuleSet}
 * (CONDITIONAL_REQUIREMENTS, ART_FIELDS_FORBIDDEN_WHEN_OUT_OF_SCOPE, PAI_BLOCK) sind
 * spec-driven aus dem EET V1.1.3 XLSX abgeleitet — dieser Test pinnt ihren Umfang und
 * prüft, dass jede referenzierte NUM auch tatsächlich im geladenen Catalog existiert.
 *
 * <p>Wenn der Test rot wird, ist entweder
 * <ul>
 *   <li>eine Liste in EetRuleSet vergrößert/verkleinert worden (intendiert? → Counts hier
 *       anpassen), oder</li>
 *   <li>der Spec-Manifest hat eine NUM verschoben, ohne dass die Code-Listen mitziehen
 *       (Defekt-Kandidat).</li>
 * </ul>
 *
 * <p>EMT und EPT haben aktuell keine hand-geschriebenen Crossfield-Listen
 * (TODO(emt-xf)/TODO(ept-xf), beide PENDING SME-Review) — daher ohne Snapshot-Test.
 */
class EetCrossfieldConfigurationSnapshotTest {

    private static final SpecCatalog CATALOG_V113 =
            new EetTemplate().specLoaderFor(EetTemplate.V1_1_3).load();

    @Test
    @DisplayName("CONDITIONAL_REQUIREMENTS: 10 spec-getriebene Einträge mit existierenden Source/Target-NUMs")
    void conditionalRequirementsSnapshot() {
        List<ConditionalRequirement> reqs = EetRuleSet.CONDITIONAL_REQUIREMENTS;
        assertThat(reqs).hasSize(10);

        // IDs sind eindeutig (sonst entstünden duplizierte Findings).
        assertThat(reqs).extracting(ConditionalRequirement::ruleId)
                .doesNotHaveDuplicates();

        // Jede referenzierte NUM existiert im V1.1.3 Catalog.
        for (ConditionalRequirement req : reqs) {
            assertThat(CATALOG_V113.byNumKey(req.sourceFieldNum()))
                    .as("source NUM %s of rule %s must exist in V1.1.3 catalog",
                            req.sourceFieldNum(), req.ruleId())
                    .isPresent();
            assertThat(CATALOG_V113.byNumKey(req.targetFieldNum()))
                    .as("target NUM %s of rule %s must exist in V1.1.3 catalog",
                            req.targetFieldNum(), req.ruleId())
                    .isPresent();
        }

        // Stabile ID-Liste (vor allem als visuelle Inventur-Übersicht beim Lesen).
        assertThat(reqs).extracting(ConditionalRequirement::ruleId)
                .containsExactly(
                        "EET-XF-SFDR-OUT-OF-SCOPE",
                        "EET-XF-ART8-MIN-LT",
                        "EET-XF-ART9-MIN-LT",
                        "EET-XF-ART8-MIN-SI",
                        "EET-XF-ART9-MIN-ENV",
                        "EET-XF-ART9-MIN-SOC",
                        "EET-XF-ART9-PARIS-DECARB-80",
                        "EET-XF-ART9-PARIS-DECARB-81",
                        "EET-XF-COUNTRYLIST-615",
                        "EET-XF-COUNTRYLIST-616");
    }

    @Test
    @DisplayName("Negative-SFDR-Constraint und PAI-Block: alle referenzierten NUMs existieren im Catalog")
    void absenceAndPaiListsMatchCatalog() {
        // ART_FIELDS_FORBIDDEN_WHEN_OUT_OF_SCOPE und PAI_BLOCK sind paketprivat;
        // wir leiten beide aus den ConditionalAbsence/Presence-Rule-IDs ab, die EetRuleSet
        // bei build() registriert. Indirekt — aber der Trigger-Test
        // EetRuleSetTest.paiYesTriggersPaiBlockGating pinnt den Count auf 27 schon.
        // Hier ergänzen wir den Existenz-Check fürs Catalog.

        // Direkter Reflection-loser Pfad: die NUMs kennen wir aus der EetRuleSet-Quelle.
        Set<String> artForbidden = Set.of("30", "31", "40", "41", "42", "43", "44", "45", "46", "47", "48");
        Set<String> paiBlock = Set.of(
                "103", "104",
                "106", "110", "114", "118", "122", "126", "130", "134", "138",
                "142", "146", "150", "154", "158", "162", "166", "170", "174",
                "178", "182", "186", "190", "194", "198", "202");

        assertThat(artForbidden).hasSize(11);
        assertThat(paiBlock).hasSize(27);

        for (String num : artForbidden) {
            assertThat(CATALOG_V113.byNumKey(num))
                    .as("ART_FIELDS_FORBIDDEN_WHEN_OUT_OF_SCOPE: NUM %s", num).isPresent();
        }
        for (String num : paiBlock) {
            assertThat(CATALOG_V113.byNumKey(num))
                    .as("PAI_BLOCK: NUM %s", num).isPresent();
        }
    }
}
