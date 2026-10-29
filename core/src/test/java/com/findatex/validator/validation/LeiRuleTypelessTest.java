package com.findatex.validator.validation;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.spec.SpecLoader;
import com.findatex.validator.template.tpt.TptProfiles;
import com.findatex.validator.validation.rules.LeiRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.findatex.validator.validation.TestFileBuilder.values;
import static org.assertj.core.api.Assertions.assertThat;

/** {@link LeiRule} without a type column (TPT V8 fields 115/119/140). */
class LeiRuleTypelessTest {

    private static final SpecCatalog CATALOG = SpecLoader.loadBundled();

    private static ValidationContext ctx(TptFile f) {
        return new ValidationContext(f, CATALOG, Set.of(TptProfiles.SOLVENCY_II));
    }

    @Test
    void idOmitsTheTypeSegment() {
        assertThat(new LeiRule("115").id()).isEqualTo("LEI/115");
        assertThat(new LeiRule("115").hasTypeFlag()).isFalse();
        assertThat(new LeiRule("115", "116").id()).isEqualTo("LEI/115/116");
        assertThat(new LeiRule("115", "116").hasTypeFlag()).isTrue();
    }

    @Test
    void everyNonBlankValueIsChecked() {
        TptFile file = new TestFileBuilder()
                .row(values("115", "529900D6BF99LW9R2E68"))   // valid (SAP)
                .row(values("115", "529900D6BF99LW9R2E69"))   // corrupt check digit
                .row(values("115", ""))                       // blank → presence, not LEI
                .build();

        List<Finding> out = new LeiRule("115").evaluate(ctx(file));

        assertThat(out).hasSize(1);
        assertThat(out.get(0).rowIndex()).isEqualTo(2);
        assertThat(out.get(0).ruleId()).isEqualTo("LEI/115");
    }

    @Test
    void typedVariantStillRequiresTypeOne() {
        TptFile file = new TestFileBuilder()
                .row(values("115", "NOT-A-LEI", "116", "9"))
                .row(values("115", "NOT-A-LEI", "116", "1"))
                .build();
        assertThat(new LeiRule("115", "116").evaluate(ctx(file)))
                .extracting(Finding::rowIndex).containsExactly(2);
    }
}
