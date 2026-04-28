package com.findatex.validator.validation.rules.external;

import com.findatex.validator.config.AppSettings;
import com.findatex.validator.external.openfigi.IsinRecord;
import com.findatex.validator.validation.Finding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class IsinOnlineRule {

    public record IsinHit(String codeNumKey, String typeNumKey, int rowIndex,
                          String isin, String localCurrency, String localCic) {}

    public record Input(String codeNumKey, String typeNumKey,
                        List<IsinHit> hits, Map<String, IsinRecord> records, AppSettings.Isin toggles) {}

    private IsinOnlineRule() {}

    public static List<Finding> evaluate(Input in) {
        List<Finding> out = new ArrayList<>();
        String idBase = "ISIN-LIVE/" + in.codeNumKey() + "/" + in.typeNumKey();
        for (IsinHit h : in.hits()) {
            IsinRecord rec = in.records().get(h.isin());
            if (rec == null) {
                out.add(Finding.error(idBase, null, h.codeNumKey(),
                        "OpenFIGI lookup on field " + h.codeNumKey(),
                        h.rowIndex(), h.isin(),
                        "ISIN is not registered in OpenFIGI"));
                continue;
            }
            if (in.toggles().checkCurrency()
                    && !rec.currency().isEmpty() && !h.localCurrency().isEmpty()
                    && !rec.currency().equalsIgnoreCase(h.localCurrency())) {
                out.add(Finding.warn("ISIN-LIVE-CCY/" + in.codeNumKey() + "/" + in.typeNumKey(),
                        null, h.codeNumKey(),
                        "Quotation currency vs OpenFIGI",
                        h.rowIndex(), h.localCurrency(),
                        "OpenFIGI currency is '" + rec.currency() + "'"));
            }
            // CIC consistency check would compare h.localCic() vs rec.securityType();
            // mapping is non-trivial — defer to a follow-up. The toggle is wired but produces
            // no finding in V1, intentionally documented in the spec section 13.
        }
        return out;
    }
}
