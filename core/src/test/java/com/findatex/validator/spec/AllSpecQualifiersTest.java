package com.findatex.validator.spec;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Table-driven assertion that exercises every distinct CIC qualifier string
 * found in the actual TPT V7 spec, paired with the CIC class column it appears
 * in, and asserts the parser produces the curated expected sub-category set.
 *
 * <p>Coverage strategy:
 * <ul>
 *   <li>Every quoted-style qualifier (8 distinct strings).</li>
 *   <li>Every unquoted-style qualifier (22 distinct strings; the dominant
 *       variant in the spec).</li>
 *   <li>Every cross-field-conditional text (must yield an empty whitelist).</li>
 *   <li>Cross-class cases like {@code "x for B1, B4, C1, C4"} which appears
 *       under both CICB and CICC and must produce different whitelists per
 *       column.</li>
 *   <li>Negative inputs: blank, null, plain "x", and bogus CIC names.</li>
 * </ul>
 *
 * <p>If a future spec edition adds a new qualifier, the audit script
 * {@code tools/audit_qualifiers.py} reports it; copy that string into the
 * arguments stream below to restore confidence.
 */
class AllSpecQualifiersTest {

    @ParameterizedTest(name = "{1} ⇐ {0}")
    @MethodSource("allSpecQualifiers")
    void parserExtractsExpectedSubcategoryWhitelist(String text, String cicName, Set<String> expected) {
        assertThat(SpecLoader.parseSubcategoryQualifier(text, cicName))
                .as("text=%s cic=%s", text, cicName)
                .isEqualTo(expected);
    }

    static Stream<Arguments> allSpecQualifiers() {
        return Stream.of(
                // ============ Quoted style — already worked before the fix ============
                arguments("x for convertible bonds \"22\" or other corporate bonds \"29\" quoted in units",
                        "CIC2", Set.of("2", "9")),
                arguments("x for equity future \"A1\" and for commodity future \"A5\", other \"A9\"",
                        "CICA", Set.of("1", "5", "9")),
                arguments("x for equity options \"B1\", warrants \"B4\", commodities options \"B5\", others \"B9\"",
                        "CICB", Set.of("1", "4", "5", "9")),
                arguments("x for equity options \"C1\", warrants \"C4\", commodities options \"C5\", others \"C9\"",
                        "CICC", Set.of("1", "4", "5", "9")),
                arguments("x for equity legs of Total return swaps \"D4\", Security swaps \"D5\", others \"D9\"",
                        "CICD", Set.of("4", "5", "9")),
                arguments("x for interest rate future \"A2\", currency future \"A3\",  other \"A9\"",
                        "CICA", Set.of("2", "3", "9")),
                arguments("x for bond options \"B2\", currency options \"B3\", swaptions \"B6\", catastrohe and weather risk \"B7\", mortality risk \"B8\", other \"B9\"",
                        "CICB", Set.of("2", "3", "6", "7", "8", "9")),
                arguments("x for bond options \"C2\", currency options \"C3\", swaptions \"C6\", catastrohe and weather risk \"C7\", mortality risk \"C8\", other \"C9\"",
                        "CICC", Set.of("2", "3", "6", "7", "8", "9")),

                // ============ Unquoted style — fixed by the new parser pass ============
                arguments("x for 22",                  "CIC2", Set.of("2")),
                arguments("x for D4, D5",              "CICD", Set.of("4", "5")),
                arguments("x for D1, D3",              "CICD", Set.of("1", "3")),
                arguments("x for F1, F3, F4",          "CICF", Set.of("1", "3", "4")),
                arguments("x for F1, F2",              "CICF", Set.of("1", "2")),
                arguments("x for E1",                  "CICE", Set.of("1")),
                arguments("x for A1",                  "CICA", Set.of("1")),
                arguments("x for B1, B4",              "CICB", Set.of("1", "4")),
                arguments("x for C1, C4,",             "CICC", Set.of("1", "4")),    // trailing comma
                arguments("x for A2",                  "CICA", Set.of("2")),
                arguments("x for B2",                  "CICB", Set.of("2")),
                arguments("x for C2",                  "CICC", Set.of("2")),
                arguments("x for A3",                  "CICA", Set.of("3")),
                arguments("x for B3",                  "CICB", Set.of("3")),
                arguments("x for C3",                  "CICC", Set.of("3")),
                arguments("x\nfor E2",                 "CICE", Set.of("2")),         // newline before "for"
                arguments("x for A3, A5",              "CICA", Set.of("3", "5")),
                arguments("x for 73, 74, 75",          "CIC7", Set.of("3", "4", "5")),
                arguments("x for 73,74,75",            "CIC7", Set.of("3", "4", "5")), // no spaces
                arguments("x for 52, 54",              "CIC5", Set.of("2", "4")),
                arguments("x for 62,64",               "CIC6", Set.of("2", "4")),
                arguments("x for 51, 53, 56",          "CIC5", Set.of("1", "3", "6")),
                arguments("x for 61,63, 66",           "CIC6", Set.of("1", "3", "6")),

                // ============ Mixed-class qualifiers — must filter to the column's class ============
                arguments("x for C1, C4, B1, B4",      "CICB", Set.of("1", "4")),
                arguments("x for C1, C4, B1, B4",      "CICC", Set.of("1", "4")),
                arguments("x for B1, B4, C1, C4",      "CICB", Set.of("1", "4")),
                arguments("x for B1, B4, C1, C4",      "CICC", Set.of("1", "4")),
                arguments("x for B2, C2",              "CICB", Set.of("2")),
                arguments("x for B2, C2",              "CICC", Set.of("2")),

                // ============ Same qualifier under non-matching CIC must yield ∅ ============
                arguments("x for 22",                  "CIC1", Set.of()),
                arguments("x for D4, D5",              "CICA", Set.of()),
                arguments("x for B1, B4",              "CIC0", Set.of()),
                arguments("x for 73, 74, 75",          "CIC2", Set.of()),

                // ============ Cross-field conditional text — must yield ∅ (False-Positive guard) ============
                arguments("x\nif item 34 is not blank",          "CIC2", Set.of()),
                arguments("x\nif item 116 set to \"1\"",         "CIC2", Set.of()),
                arguments("x\nif item 120 set to \"1\"",         "CIC2", Set.of()),
                arguments("x\nif item 48 set to \"1\"",          "CIC2", Set.of()),
                arguments("x\nif item 51 set to \"1\"",          "CIC2", Set.of()),
                arguments("x\nif item 32 set to \"Floating\"",   "CIC2", Set.of()),
                arguments("x\nif item 42 is not blank",          "CIC2", Set.of()),
                arguments("x\nif item 85 set to \"1\"",          "CIC2", Set.of()),
                arguments("x if 138 is \"1\" \"2\" or \"3\"",    "CIC2", Set.of()),
                arguments("x only if 134 is set to 1",           "CIC3", Set.of()),
                arguments("x\nIf item 29 is not blank",          "CICE", Set.of()),
                arguments("if item 42 is Equal to Cal or Put",   "CIC1", Set.of()),
                arguments("If coming from the lookthrough of an underlying fund", "CIC0", Set.of()),
                arguments("x for all legs of all swaps",         "CICD", Set.of()), // prose, no 2-char tokens

                // ============ Trivial / blank / weird ============
                arguments("x",                                   "CIC2", Set.of()),
                arguments("X",                                   "CIC2", Set.of()),
                arguments("",                                    "CIC2", Set.of()),
                arguments(null,                                  "CIC2", Set.of()),
                arguments("x for 22",                            null,    Set.of()),
                arguments("x for 22",                            "BAD",   Set.of()),
                arguments("x for 22",                            "CICX2", Set.of())
        );
    }
}
