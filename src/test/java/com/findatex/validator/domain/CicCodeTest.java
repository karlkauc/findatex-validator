package com.findatex.validator.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CicCodeTest {

    @ParameterizedTest
    @CsvSource({
            "FR12, 'Government bonds (CIC 1)', 1",
            "DE22, 'Corporate bonds (CIC 2)',  2",
            "DE31, 'Equity (CIC 3)',           3",
            "XL40, 'Collective investment undertakings (CIC 4)', 4",
            "XV50, 'Structured notes (CIC 5)', 5",
            "XL61, 'Collateralized securities (CIC 6)', 6",
            "XL71, 'Cash & deposits (CIC 7)',  7",
            "XL81, 'Mortgages/Loans (CIC 8)',  8",
            "XL90, 'Property (CIC 9)',         9",
            "XL00, 'Other (CIC 0)',            0",
            "XLA1, 'Futures (CIC A)',          A",
            "XLB1, 'Call options (CIC B)',     B",
            "XLC1, 'Put options (CIC C)',      C",
            "XLD1, 'Swaps (CIC D)',            D",
            "XLE1, 'Forwards (CIC E)',         E",
            "XLF1, 'Credit derivatives (CIC F)', F",
    })
    void categoryNameMapsAllSpecCategories(String raw, String expectedName, String expectedDigit) {
        Optional<CicCode> cic = CicCode.parse(raw);
        assertThat(cic).isPresent();
        assertThat(cic.get().categoryDigit()).isEqualTo(expectedDigit);
        assertThat(cic.get().categoryName()).isEqualTo(expectedName);
    }

    @Test
    void rejectsInvalidStructure() {
        assertThat(CicCode.parse("AB")).isEmpty();          // too short
        assertThat(CicCode.parse("ABCDE")).isEmpty();       // too long
        assertThat(CicCode.parse("12X1")).isEmpty();        // first two chars must be letters
        assertThat(CicCode.parse(null)).isEmpty();
        assertThat(CicCode.parse("")).isEmpty();
    }

    @Test
    void parsesAndUppercases() {
        CicCode c = CicCode.parse("fr12").orElseThrow();
        assertThat(c.raw()).isEqualTo("FR12");
        assertThat(c.countryCode()).isEqualTo("FR");
        assertThat(c.categoryDigit()).isEqualTo("1");
        assertThat(c.subcategory()).isEqualTo("2");
    }
}
