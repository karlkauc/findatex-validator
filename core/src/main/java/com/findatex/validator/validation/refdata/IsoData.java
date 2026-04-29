package com.findatex.validator.validation.refdata;

import java.util.Currency;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class IsoData {

    private static final Set<String> CURRENCIES;
    private static final Set<String> COUNTRIES;

    static {
        Set<String> ccy = new HashSet<>();
        for (Currency c : Currency.getAvailableCurrencies()) {
            ccy.add(c.getCurrencyCode());
        }
        CURRENCIES = Set.copyOf(ccy);

        Set<String> ctry = new HashSet<>();
        for (String c : Locale.getISOCountries()) {
            ctry.add(c);
        }
        // FinDatEx commonly uses "XL" (off-shore international), "XV" (other), "XT" (supranational).
        ctry.add("XL");
        ctry.add("XV");
        ctry.add("XT");
        // TPT V7 issuer-country closed list adds "XA" (supranational issuers) and "EU"
        // (European Union institutions) on top of ISO 3166-1 alpha-2.
        ctry.add("XA");
        ctry.add("EU");
        ctry.add("XK"); // Kosovo
        COUNTRIES = Set.copyOf(ctry);
    }

    private IsoData() {}

    public static boolean isCurrency(String code) {
        return CURRENCIES.contains(code);
    }

    public static boolean isCountry(String code) {
        return COUNTRIES.contains(code);
    }
}
