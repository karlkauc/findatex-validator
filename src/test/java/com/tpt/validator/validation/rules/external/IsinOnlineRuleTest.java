package com.tpt.validator.validation.rules.external;

import com.tpt.validator.config.AppSettings;
import com.tpt.validator.external.openfigi.IsinRecord;
import com.tpt.validator.validation.Finding;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IsinOnlineRuleTest {

    private static final AppSettings.Isin ALL_OFF =
            new AppSettings.Isin(true, "", false, false);
    private static final AppSettings.Isin CCY_ON =
            new AppSettings.Isin(true, "", true, false);

    @Test
    void unknownIsinIsError() {
        IsinOnlineRule.Input in = new IsinOnlineRule.Input(
                "14", "15",
                List.of(new IsinOnlineRule.IsinHit("14", "15", 5, "US0378331009", "USD", "")),
                Map.of(), ALL_OFF);
        List<Finding> out = IsinOnlineRule.evaluate(in);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).ruleId()).isEqualTo("ISIN-LIVE/14/15");
    }

    @Test
    void currencyMismatchEmitsWarningWhenToggleOn() {
        IsinRecord rec = new IsinRecord("US0378331005", "BBG", "APPLE INC", "AAPL",
                "US", "Equity", "Common Stock", "USD");
        IsinOnlineRule.Input in = new IsinOnlineRule.Input(
                "14", "15",
                List.of(new IsinOnlineRule.IsinHit("14", "15", 5, "US0378331005", "EUR", "")),
                Map.of("US0378331005", rec), CCY_ON);
        List<Finding> out = IsinOnlineRule.evaluate(in);
        assertThat(out).extracting(Finding::ruleId).contains("ISIN-LIVE-CCY/14/15");
    }
}
