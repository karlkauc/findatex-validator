package com.findatex.validator.validation;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.spec.FieldSpec;
import com.findatex.validator.spec.Flag;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.template.tpt.TptProfiles;
import com.findatex.validator.template.tpt.TptTemplate;
import com.findatex.validator.validation.rules.NotApplicableRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.findatex.validator.validation.TestFileBuilder.values;
import static org.assertj.core.api.Assertions.assertThat;

class NotApplicableRuleTest {

    private static final SpecCatalog V8 = new TptTemplate().specLoaderFor(TptTemplate.V8_0).load();

    @Test
    void v8FlagsTheEighteenRetiredFieldsAsNotApplicable() {
        List<String> na = V8.fields().stream()
                .filter(f -> f.flag(TptProfiles.SOLVENCY_II) == Flag.NA)
                .map(FieldSpec::numKey).toList();
        assertThat(na).containsExactly(
                "17b", "29", "31", "35", "36", "44", "54", "58b", "106", "108", "114",
                "116", "118", "120", "124", "125", "126", "141");
    }

    @Test
    void nonBlankValueInNotApplicableFieldIsAWarning() {
        FieldSpec f54 = V8.byNumKey("54").orElseThrow();
        TptFile file = new TestFileBuilder()
                .row(values("12", "FR12", "54", "K"))
                .row(values("12", "DE31", "54", ""))
                .row(values("12", "XL71"))
                .build();

        List<Finding> out = new NotApplicableRule(f54, TptProfiles.SOLVENCY_II)
                .evaluate(new ValidationContext(file, V8, Set.of(TptProfiles.SOLVENCY_II)));

        assertThat(out).hasSize(1);
        assertThat(out.get(0).ruleId()).isEqualTo("NOT_APPLICABLE/54");
        assertThat(out.get(0).severity()).isEqualTo(Severity.WARNING);
        assertThat(out.get(0).rowIndex()).isEqualTo(1);
        assertThat(out.get(0).fieldNum()).isEqualTo("54");
    }

    @Test
    void inactiveProfileProducesNothing() {
        FieldSpec f54 = V8.byNumKey("54").orElseThrow();
        TptFile file = new TestFileBuilder().row(values("54", "K")).build();
        assertThat(new NotApplicableRule(f54, TptProfiles.SOLVENCY_II)
                .evaluate(new ValidationContext(file, V8, Set.of(TptProfiles.SST)))).isEmpty();
    }
}
