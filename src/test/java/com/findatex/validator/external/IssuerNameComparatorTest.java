package com.findatex.validator.external;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class IssuerNameComparatorTest {

    @Test
    void cosmeticDifferencesAreEqual() {
        assertThat(IssuerNameComparator.equivalent("BlackRock Inc", "BlackRock, Inc.")).isTrue();
        assertThat(IssuerNameComparator.equivalent("SAP SE", "Sap se")).isTrue();
        assertThat(IssuerNameComparator.equivalent("Société Générale SA", "Societe Generale")).isTrue();
    }

    @Test
    void realDifferencesAreNotEqual() {
        assertThat(IssuerNameComparator.equivalent("Apple Inc", "Microsoft Corp")).isFalse();
        assertThat(IssuerNameComparator.equivalent("BMW AG", "Volkswagen AG")).isFalse();
    }

    @Test
    void emptyOrNullIsEquivalent() {
        assertThat(IssuerNameComparator.equivalent("", "anything")).isTrue();
        assertThat(IssuerNameComparator.equivalent(null, "anything")).isTrue();
        assertThat(IssuerNameComparator.equivalent("anything", "")).isTrue();
    }
}
