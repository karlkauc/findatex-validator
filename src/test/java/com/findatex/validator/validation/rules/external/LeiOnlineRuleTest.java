package com.findatex.validator.validation.rules.external;

import com.findatex.validator.config.AppSettings;
import com.findatex.validator.external.gleif.LeiRecord;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LeiOnlineRuleTest {

    private static final AppSettings.Lei ALL_OFF =
            new AppSettings.Lei(true, false, false, false);
    private static final AppSettings.Lei ALL_ON =
            new AppSettings.Lei(true, true, true, true);

    @Test
    void unknownLeiIsError() {
        LeiOnlineRule.Input in = new LeiOnlineRule.Input(
                "47", "48", List.of(new LeiOnlineRule.LeiHit("47", "48", 7,
                        "ZZ900D6BF99LW9R2E68", "Some Issuer", "DE")),
                Map.of(), ALL_OFF);
        List<Finding> out = LeiOnlineRule.evaluate(in);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).ruleId()).isEqualTo("LEI-LIVE/47/48");
        assertThat(out.get(0).severity()).isEqualTo(Severity.ERROR);
    }

    @Test
    void lapsedStatusEmitsWarningWhenToggleOn() {
        LeiRecord lapsed = new LeiRecord("L", "Some Issuer", "DE", "ACTIVE", "LAPSED");
        LeiOnlineRule.Input in = new LeiOnlineRule.Input(
                "47", "48", List.of(new LeiOnlineRule.LeiHit("47", "48", 7, "L", "Some Issuer", "DE")),
                Map.of("L", lapsed), ALL_ON);
        List<Finding> out = LeiOnlineRule.evaluate(in);
        assertThat(out).extracting(Finding::ruleId).contains("LEI-LIVE-STATUS/47/48");
    }

    @Test
    void issuerNameMismatchEmitsWarningWhenToggleOn() {
        LeiRecord ok = new LeiRecord("L", "GLEIF Name", "DE", "ACTIVE", "ISSUED");
        LeiOnlineRule.Input in = new LeiOnlineRule.Input(
                "47", "48", List.of(new LeiOnlineRule.LeiHit("47", "48", 7, "L", "Different Inc", "DE")),
                Map.of("L", ok), ALL_ON);
        List<Finding> out = LeiOnlineRule.evaluate(in);
        assertThat(out).extracting(Finding::ruleId).contains("LEI-LIVE-NAME/47/48");
    }

    @Test
    void issuerCountryMismatchEmitsWarningWhenToggleOn() {
        LeiRecord ok = new LeiRecord("L", "Same Co", "DE", "ACTIVE", "ISSUED");
        LeiOnlineRule.Input in = new LeiOnlineRule.Input(
                "47", "48", List.of(new LeiOnlineRule.LeiHit("47", "48", 7, "L", "Same Co", "FR")),
                Map.of("L", ok), ALL_ON);
        List<Finding> out = LeiOnlineRule.evaluate(in);
        assertThat(out).extracting(Finding::ruleId).contains("LEI-LIVE-COUNTRY/47/48");
    }

    @Test
    void noFindingsWhenAllSubChecksOff() {
        LeiRecord lapsed = new LeiRecord("L", "GLEIF", "DE", "ACTIVE", "LAPSED");
        LeiOnlineRule.Input in = new LeiOnlineRule.Input(
                "47", "48", List.of(new LeiOnlineRule.LeiHit("47", "48", 7, "L", "Local", "FR")),
                Map.of("L", lapsed), ALL_OFF);
        List<Finding> out = LeiOnlineRule.evaluate(in);
        assertThat(out).isEmpty();
    }
}
