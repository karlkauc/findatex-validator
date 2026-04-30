package com.findatex.validator.spec;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Documents how {@link CodificationParser} classifies the special / outlier
 * codification strings observed in EET V1.1.2 / V1.1.3 (per
 * {@code tools/audit_eet_codifications.py}). Failing this test means the
 * parser's behavior changed for one of these long-tail forms — re-confirm
 * with a SME before adjusting expectations.
 */
class EetCodificationCoverageTest {

    @ParameterizedTest(name = "[{index}] {0} → {1}")
    @CsvSource(delimiter = '|', value = {
            // multi-value forms — slash + semicolon don't trigger the closed-list bullet
            // regex, so these fall through to FREE_TEXT (no enforcement).
            "A / A;D / B;D;F     | FREE_TEXT",
            "R;C / R;C;O;A      | FREE_TEXT",
            "EN;FR;DE           | FREE_TEXT",
            "MM;IQ              | FREE_TEXT",
            "MM;IQ;GE;FR        | FREE_TEXT",
            // V1.1.2 lacks the trailing ";N" of V1.1.3 — both should classify the same.
            "O ; B ; E ; P ; Q ; R ; S ; T ; U ; V ; N | FREE_TEXT",
            "O ; B ; E ; P ; Q ; R ; S ; T ; U ; V     | FREE_TEXT",
            // odd binary
            "Y / Neutral        | FREE_TEXT",
            // whitespace noise on Y / N
            "Y / N              | FREE_TEXT",
            "Y /  N             | FREE_TEXT",
            // standard ones — sanity
            "floating decimal (0.5 = 50%) | NUMERIC",
            "Number with decimal value    | NUMERIC",
            "YYYY-MM-DD         ISO 8601  | DATE",
            "Code ISO 4217                | ISO_4217",
            "1 or 2 or 3                  | FREE_TEXT",
    })
    void codificationKindForOutlierForms(String raw, String expectedKind) {
        CodificationDescriptor d = CodificationParser.parse(raw);
        assertThat(d.kind()).isEqualTo(CodificationKind.valueOf(expectedKind));
    }
}
