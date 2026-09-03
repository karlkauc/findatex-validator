package com.findatex.validator.ui;

import com.findatex.validator.validation.Severity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure helpers behind the annotated-source grid — no JavaFX toolkit needed. */
class AnnotatedSourceColumnsTest {

    @Test
    void usesHeaderTextWhenPresent() {
        assertThat(AnnotatedSourceColumns.title(0, "Portfolio identifying data")).isEqualTo("Portfolio identifying data");
        assertThat(AnnotatedSourceColumns.title(5, "  padded  ")).isEqualTo("padded");
    }

    @Test
    void fallsBackToSpreadsheetLetters() {
        assertThat(AnnotatedSourceColumns.title(0, null)).isEqualTo("A");
        assertThat(AnnotatedSourceColumns.title(0, "   ")).isEqualTo("A");
        assertThat(AnnotatedSourceColumns.title(25, "")).isEqualTo("Z");
        assertThat(AnnotatedSourceColumns.title(26, "")).isEqualTo("AA");
        assertThat(AnnotatedSourceColumns.title(27, "")).isEqualTo("AB");
        assertThat(AnnotatedSourceColumns.title(701, "")).isEqualTo("ZZ");
        assertThat(AnnotatedSourceColumns.title(702, "")).isEqualTo("AAA");
    }

    @Test
    void mapsSeverityToStyleClass() {
        assertThat(AnnotatedSourceColumns.styleClassFor(Severity.ERROR)).isEqualTo("source-cell-error");
        assertThat(AnnotatedSourceColumns.styleClassFor(Severity.WARNING)).isEqualTo("source-cell-warn");
        assertThat(AnnotatedSourceColumns.styleClassFor(Severity.INFO)).isEqualTo("source-cell-info");
        assertThat(AnnotatedSourceColumns.styleClassFor(null)).isNull();
    }

    @Test
    void summarisesGridSize() {
        assertThat(AnnotatedSourceColumns.summary(1234, 152, 87))
                .isEqualTo("1234 rows × 152 columns, 87 rows with findings");
        assertThat(AnnotatedSourceColumns.summary(0, 0, 0))
                .isEqualTo("0 rows × 0 columns, 0 rows with findings");
    }
}
