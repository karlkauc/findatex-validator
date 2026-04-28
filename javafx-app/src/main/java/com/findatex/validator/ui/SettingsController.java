package com.findatex.validator.ui;

import com.findatex.validator.config.AppSettings;
import com.findatex.validator.config.PasswordCipher;
import com.findatex.validator.config.SettingsService;
import com.findatex.validator.external.gleif.GleifClient;
import com.findatex.validator.external.openfigi.OpenFigiClient;
import com.findatex.validator.external.proxy.ProxyConfig;
import com.findatex.validator.external.proxy.ProxyService;
import com.findatex.validator.external.proxy.SystemProxyDetector;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;

import java.net.URI;

public final class SettingsController {

    @FXML private CheckBox leiEnabled, leiLapsed, leiName, leiCountry;
    @FXML private CheckBox isinEnabled, isinCcy, isinCic;
    @FXML private TextField figiKey;
    @FXML private Spinner<Integer> ttlDays;
    @FXML private Button clearCache, testGleif, testFigi;
    @FXML private Label testGleifResult, testFigiResult, diagnosticsLabel;

    @FXML private RadioButton modeSystem, modeManual, modeNone;
    @FXML private TextField pxHost, pxPort, pxUser, pxBypass;
    @FXML private PasswordField pxPass;

    @FXML
    public void initialize() {
        ToggleGroup g = new ToggleGroup();
        modeSystem.setToggleGroup(g);
        modeManual.setToggleGroup(g);
        modeNone.setToggleGroup(g);

        AppSettings s = SettingsService.getInstance().getCurrent();
        AppSettings.External e = s.external();
        leiEnabled.setSelected(e.lei().enabled());
        leiLapsed.setSelected(e.lei().checkLapsedStatus());
        leiName.setSelected(e.lei().checkIssuerName());
        leiCountry.setSelected(e.lei().checkIssuerCountry());
        isinEnabled.setSelected(e.isin().enabled());
        isinCcy.setSelected(e.isin().checkCurrency());
        isinCic.setSelected(e.isin().checkCicConsistency());
        figiKey.setText(e.isin().openFigiApiKey());
        ttlDays.getValueFactory().setValue(e.cache().ttlDays());

        switch (s.proxy().mode()) {
            case SYSTEM -> modeSystem.setSelected(true);
            case MANUAL -> modeManual.setSelected(true);
            case NONE   -> modeNone.setSelected(true);
        }
        pxHost.setText(s.proxy().manual().host());
        pxPort.setText(s.proxy().manual().port() == 0 ? "" : Integer.toString(s.proxy().manual().port()));
        pxUser.setText(s.proxy().manual().user());
        pxPass.setText(PasswordCipher.decrypt(s.proxy().manual().passwordEncrypted()));
        pxBypass.setText(s.proxy().manual().nonProxyHosts());

        diagnosticsLabel.setText(SystemProxyDetector.getCurrentConfig()
                .map(c -> "Detected proxy: " + c)
                .orElse("No system proxy detected"));
    }

    public AppSettings collect() {
        int port = 0;
        try { port = Integer.parseInt(pxPort.getText().trim()); } catch (Exception ignored) {}
        AppSettings.ProxyMode mode = modeManual.isSelected() ? AppSettings.ProxyMode.MANUAL
                : modeNone.isSelected() ? AppSettings.ProxyMode.NONE
                : AppSettings.ProxyMode.SYSTEM;
        AppSettings.ManualProxy mp = new AppSettings.ManualProxy(
                pxHost.getText().trim(), port,
                pxUser.getText().trim(),
                PasswordCipher.encrypt(pxPass.getText()),
                pxBypass.getText().trim());

        AppSettings prev = SettingsService.getInstance().getCurrent();
        return new AppSettings(
                new AppSettings.External(
                        prev.external().enabled(),
                        new AppSettings.Lei(
                                leiEnabled.isSelected(), leiLapsed.isSelected(),
                                leiName.isSelected(), leiCountry.isSelected()),
                        new AppSettings.Isin(
                                isinEnabled.isSelected(), figiKey.getText().trim(),
                                isinCcy.isSelected(), isinCic.isSelected()),
                        new AppSettings.Cache(ttlDays.getValue(), prev.external().cache().directory())),
                new AppSettings.Proxy(mode, mp));
    }

    @FXML
    private void onTestGleif() {
        applyCurrentProxyForTest();
        testGleifResult.setText(quickGet(URI.create(GleifClient.DEFAULT_BASE
                + "/api/v1/lei-records?page%5Bsize%5D=1")));
    }

    @FXML
    private void onTestFigi() {
        applyCurrentProxyForTest();
        testFigiResult.setText(quickGet(URI.create(OpenFigiClient.DEFAULT_BASE
                + "/v3/mapping/values/exchCode")));
    }

    @FXML
    private void onClearCache() {
        java.nio.file.Path dir = java.nio.file.Path.of(System.getProperty("user.home"),
                ".config", "findatex-validator", "cache");
        try {
            java.nio.file.Files.deleteIfExists(dir.resolve("lei-cache.json"));
            java.nio.file.Files.deleteIfExists(dir.resolve("isin-cache.json"));
        } catch (Exception ignored) {}
    }

    private void applyCurrentProxyForTest() {
        AppSettings collected = collect();
        ProxyConfig cfg = ProxyConfig.from(collected.proxy(),
                PasswordCipher.decrypt(collected.proxy().manual().passwordEncrypted()));
        ProxyService.applyMode(cfg);
    }

    private static String quickGet(URI uri) {
        long start = System.currentTimeMillis();
        java.net.HttpURLConnection conn = null;
        try {
            conn = (java.net.HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);
            conn.setInstanceFollowRedirects(true);
            int status = conn.getResponseCode();
            long ms = System.currentTimeMillis() - start;
            return "HTTP " + status + " in " + ms + " ms";
        } catch (Exception e) {
            return "FAILED: " + e.getClass().getSimpleName() + " — " + e.getMessage();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
