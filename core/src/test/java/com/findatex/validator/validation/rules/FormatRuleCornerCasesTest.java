package com.findatex.validator.validation.rules;

import com.findatex.validator.domain.RawCell;
import com.findatex.validator.domain.TptFile;
import com.findatex.validator.domain.TptRow;
import com.findatex.validator.spec.CodificationDescriptor;
import com.findatex.validator.spec.CodificationKind;
import com.findatex.validator.spec.FieldSpec;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.tpt.TptProfiles;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Severity;
import com.findatex.validator.validation.ValidationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FormatRuleCornerCasesTest {

    // ----------------------------------------------------------------- NUMERIC

    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "1.5", "-1.5", "1,5", "1e5", "1.5e-5", "0.0001"})
    void numericValidValues(String v) {
        assertNoErrors(numericRule(), v);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.2.3", "EUR", "--1", "1 000"})
    void numericInvalidValues(String v) {
        assertHasError(numericRule(), v, "numeric");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.5", "1", "V", "S", "M", "L", "H", "v", "l"})
    void numericWithLetterAlternativesAcceptsBothNumberAndCodes(String v) {
        // EMT V4.2 NUM 55: "floating decimal or V or S or M or L or H".
        // Numbers pass the numeric parse; the listed letters fall back to closedList check
        // (case-insensitive) without producing a FORMAT finding.
        FormatRule rule = new FormatRule(makeField("55", new CodificationDescriptor(
                CodificationKind.NUMERIC,
                Optional.empty(),
                List.of(
                        new CodificationDescriptor.ClosedListEntry("V", "V"),
                        new CodificationDescriptor.ClosedListEntry("S", "S"),
                        new CodificationDescriptor.ClosedListEntry("M", "M"),
                        new CodificationDescriptor.ClosedListEntry("L", "L"),
                        new CodificationDescriptor.ClosedListEntry("H", "H")),
                "floating decimal  or V or S or M or L or H")));
        assertNoErrors(rule, v);
    }

    // ------------------------------------------------------------------- DATE

    @ParameterizedTest
    @ValueSource(strings = {"2025-01-01", "2025-12-31", "2024-02-29"})
    void dateValidValues(String v) {
        assertNoErrors(dateRule(), v);
    }

    @ParameterizedTest
    @ValueSource(strings = {"31/12/2025", "2025-13-01", "2025-02-30", "Jan 1 2025"})
    void dateInvalidValues(String v) {
        // NB: DateTimeFormatter.ofPattern("yyyy-MM-dd") leniently accepts "2025-1-1" as 2025-01-01,
        // so it is intentionally not in this list.
        assertHasError(dateRule(), v, "ISO 8601");
    }

    // ------------------------------------------------------------- ISO_4217

    @ParameterizedTest
    @ValueSource(strings = {"EUR", "USD", "JPY", "GBP", "CHF", "CNH", "CNT", "GGP", "IMP", "JEP", "TVD"})
    void currencyValidIncludesMarketAdditions(String v) {
        assertNoErrors(currencyRule(), v);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ZZZ", "EU", "EURO", "ABC"})
    void currencyInvalidValues(String v) {
        assertHasError(currencyRule(), v, "currency");
    }

    // -------------------------------------------------------------- ISO_3166

    @ParameterizedTest
    @ValueSource(strings = {"DE", "FR", "US", "GB", "XK", "XL", "XV", "XT", "XA", "EU"})
    void countryValidIncludesAdditions(String v) {
        assertNoErrors(countryRule(), v);
    }

    @ParameterizedTest
    @ValueSource(strings = {"XX", "ZZ", "GERMANY", "D"})
    void countryInvalidValues(String v) {
        assertHasError(countryRule(), v, "country");
    }

    // ------------------------------------------------------------------- NACE

    @ParameterizedTest
    @ValueSource(strings = {"A", "B", "K", "U", "A1", "A12", "A123", "K6411"})
    void naceValidValues(String v) {
        assertNoErrors(naceRule(), v);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Z", "1A", "A12345", "AB12", "A.12", "B12.34"})
    void naceInvalidValues(String v) {
        // Z is outside A..U; 1A starts with a digit; A12345 exceeds 4 digits;
        // AB12 has two leading letters; dots are not allowed by the spec.
        assertHasError(naceRule(), v, "NACE");
    }

    @Test
    void naceEmptyValueIsSkipped() {
        // Format rule never reports for absent values — that's the presence rule's domain.
        assertNoErrors(naceRule(), "");
    }

    // ---------------------------------------------------------------- CIC

    @ParameterizedTest
    @ValueSource(strings = {"XL71", "FR12", "DE31", "XLA0", "XLAZ", "XLF9"})
    void cicValidValues(String v) {
        assertNoErrors(cicRule(), v);
    }

    @ParameterizedTest
    @ValueSource(strings = {"XL7", "XLG0", "XL71X", "1234"})
    void cicInvalidValues(String v) {
        assertHasError(cicRule(), v, "CIC");
    }

    // -------------------------------------------------------------- ALPHA

    @Test
    void alphaWithinLength() {
        assertNoErrors(alphaRule(3), "AB");
        assertNoErrors(alphaRule(3), "EUR");
    }

    @Test
    void alphaTooLong() {
        assertHasError(alphaRule(2), "ABCDE", "Alpha");
    }

    @Test
    void alphaWithDigitsRejected() {
        assertHasError(alphaRule(5), "AB1", "digit");
    }

    @Test
    void alphaWithSpacesAccepted() {
        assertNoErrors(alphaRule(20), "Hello World");
    }

    // -------------------------------------------------------- ALPHANUMERIC

    @Test
    void alphanumWithinLength() {
        assertNoErrors(alphanumRule(10), "ABC123");
    }

    @Test
    void alphanumTooLong() {
        assertHasError(alphanumRule(3), "ABCDE", "Alphanum");
    }

    // ------------------------------------------------------------- CLOSED_LIST

    @Test
    void closedListValidEntry() {
        FormatRule rule = closedListRule(List.of("Bullet", "Sinkable"));
        assertNoErrors(rule, "Bullet");
        assertNoErrors(rule, "bullet");      // case-insensitive match
    }

    @Test
    void closedListUnknownValue() {
        FormatRule rule = closedListRule(List.of("Bullet", "Sinkable"));
        assertHasError(rule, "Crazy", "closed list");
    }

    // -------------------------------------------------------------- FREE_TEXT / UNKNOWN

    @Test
    void freeTextAcceptsAnything() {
        FieldSpec spec = makeField("17", new CodificationDescriptor(
                CodificationKind.FREE_TEXT, Optional.empty(), List.of(), "free"));
        FormatRule rule = new FormatRule(spec);
        assertNoErrors(rule, "anything goes");
        assertNoErrors(rule, "1234.5");
        assertNoErrors(rule, "");
    }

    @Test
    void unknownKindAcceptsAnything() {
        FieldSpec spec = makeField("17", new CodificationDescriptor(
                CodificationKind.UNKNOWN, Optional.empty(), List.of(), ""));
        FormatRule rule = new FormatRule(spec);
        assertNoErrors(rule, "doesn't matter");
    }

    // ------------------------------------------------------------- empty cell

    @Test
    void emptyCellIsNotAFormatError() {
        // Format rule must not double-report a missing value (presence rule's domain).
        FormatRule rule = numericRule();
        assertNoErrors(rule, "");
        assertNoErrors(rule, "   ");
    }

    // ------------------------------------------------------- common helpers

    private static void assertNoErrors(FormatRule rule, String value) {
        List<Finding> findings = rule.evaluate(ctxFor(rule, value));
        assertThat(findings)
                .as("rule %s on value '%s'", rule.id(), value)
                .filteredOn(f -> f.severity() == Severity.ERROR)
                .isEmpty();
    }

    private static void assertHasError(FormatRule rule, String value, String messageContains) {
        List<Finding> findings = rule.evaluate(ctxFor(rule, value));
        assertThat(findings)
                .as("rule %s on value '%s'", rule.id(), value)
                .filteredOn(f -> f.severity() == Severity.ERROR)
                .isNotEmpty()
                .anySatisfy(f -> assertThat(f.message().toLowerCase())
                        .contains(messageContains.toLowerCase()));
    }

    private static ValidationContext ctxFor(FormatRule rule, String value) {
        // Extract the field key from rule.id() = "FORMAT/<numKey>"
        String numKey = rule.id().substring("FORMAT/".length());
        TptRow row = new TptRow(1);
        if (value != null && !value.isEmpty()) {
            row.put(numKey, new RawCell(value, 1, 1));
        }
        TptFile file = new TptFile(Path.of("x.csv"), "csv", List.of(),
                new LinkedHashMap<>(), List.of(), List.of(row));
        return new ValidationContext(file, SpecCatalog_for(numKey, rule), new java.util.HashSet<>(java.util.Arrays.asList(TptProfiles.SOLVENCY_II, TptProfiles.IORP_EIOPA_ECB, TptProfiles.NW_675, TptProfiles.SST)));
    }

    private static SpecCatalog SpecCatalog_for(String numKey, FormatRule rule) {
        return new SpecCatalog(List.of());   // FormatRule doesn't use the catalog at runtime
    }

    private static FormatRule numericRule()  { return new FormatRule(makeField("01",
            new CodificationDescriptor(CodificationKind.NUMERIC, Optional.empty(), List.of(), "n"))); }
    private static FormatRule dateRule()     { return new FormatRule(makeField("02",
            new CodificationDescriptor(CodificationKind.DATE, Optional.empty(), List.of(), "d"))); }
    private static FormatRule currencyRule() { return new FormatRule(makeField("03",
            new CodificationDescriptor(CodificationKind.ISO_4217, Optional.empty(), List.of(), "c"))); }
    private static FormatRule countryRule()  { return new FormatRule(makeField("04",
            new CodificationDescriptor(CodificationKind.ISO_3166_A2, Optional.empty(), List.of(), "co"))); }
    private static FormatRule naceRule()     { return new FormatRule(makeField("05",
            new CodificationDescriptor(CodificationKind.NACE, Optional.empty(), List.of(), "n"))); }
    private static FormatRule cicRule()      { return new FormatRule(makeField("06",
            new CodificationDescriptor(CodificationKind.CIC, Optional.of(4), List.of(), "cic"))); }
    private static FormatRule alphaRule(int max) { return new FormatRule(makeField("07",
            new CodificationDescriptor(CodificationKind.ALPHA, Optional.of(max), List.of(), "a"))); }
    private static FormatRule alphanumRule(int max) { return new FormatRule(makeField("08",
            new CodificationDescriptor(CodificationKind.ALPHANUMERIC, Optional.of(max), List.of(), "an"))); }
    private static FormatRule closedListRule(List<String> codes) {
        List<CodificationDescriptor.ClosedListEntry> entries = codes.stream()
                .map(c -> new CodificationDescriptor.ClosedListEntry(c, c)).toList();
        return new FormatRule(makeField("09", new CodificationDescriptor(
                CodificationKind.CLOSED_LIST, Optional.empty(), entries, "cl")));
    }

    private static FieldSpec makeField(String numKey, CodificationDescriptor codif) {
        Map<ProfileKey, com.findatex.validator.spec.Flag> flags = new java.util.HashMap<>();
        return new FieldSpec(numKey + "_field", "Position / Test", "def", "comment",
                "raw", codif, flags, Set.of(), 1);
    }
}
