package com.tpt.validator.validation.rules.external;

import com.tpt.validator.config.AppSettings;
import com.tpt.validator.external.IssuerNameComparator;
import com.tpt.validator.external.gleif.LeiRecord;
import com.tpt.validator.validation.Finding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Stateless evaluator. Inputs are pre-collected by ExternalValidationService. */
public final class LeiOnlineRule {

    public record LeiHit(String codeNumKey, String typeNumKey, int rowIndex,
                         String lei, String localIssuerName, String localIssuerCountry) {}

    public record Input(String codeNumKey, String typeNumKey,
                        List<LeiHit> hits, Map<String, LeiRecord> records, AppSettings.Lei toggles) {}

    private LeiOnlineRule() {}

    public static List<Finding> evaluate(Input in) {
        List<Finding> out = new ArrayList<>();
        String idBase = "LEI-LIVE/" + in.codeNumKey() + "/" + in.typeNumKey();
        for (LeiHit h : in.hits()) {
            LeiRecord rec = in.records().get(h.lei());
            if (rec == null) {
                out.add(Finding.error(idBase, null, h.codeNumKey(),
                        "GLEIF lookup on field " + h.codeNumKey(),
                        h.rowIndex(), h.lei(),
                        "LEI is not registered in GLEIF"));
                continue;
            }
            if (in.toggles().checkLapsedStatus() && rec.isLapsed()) {
                out.add(Finding.warn("LEI-LIVE-STATUS/" + in.codeNumKey() + "/" + in.typeNumKey(),
                        null, h.codeNumKey(),
                        "GLEIF status check on field " + h.codeNumKey(),
                        h.rowIndex(), h.lei(),
                        "LEI registration is " + rec.registrationStatus()));
            }
            if (in.toggles().checkIssuerName()
                    && !IssuerNameComparator.equivalent(h.localIssuerName(), rec.legalName())) {
                out.add(Finding.warn("LEI-LIVE-NAME/" + in.codeNumKey() + "/" + in.typeNumKey(),
                        null, h.codeNumKey(),
                        "Issuer name vs GLEIF",
                        h.rowIndex(), h.localIssuerName(),
                        "GLEIF legal name is '" + rec.legalName() + "'"));
            }
            if (in.toggles().checkIssuerCountry()
                    && !rec.country().isEmpty()
                    && !rec.country().equalsIgnoreCase(h.localIssuerCountry())) {
                out.add(Finding.warn("LEI-LIVE-COUNTRY/" + in.codeNumKey() + "/" + in.typeNumKey(),
                        null, h.codeNumKey(),
                        "Issuer country vs GLEIF",
                        h.rowIndex(), h.localIssuerCountry(),
                        "GLEIF country is '" + rec.country() + "'"));
            }
        }
        return out;
    }
}
