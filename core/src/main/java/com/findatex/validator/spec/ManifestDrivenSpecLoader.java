package com.findatex.validator.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.findatex.validator.template.api.TemplateSpecLoader;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Generic, manifest-driven {@link TemplateSpecLoader}. The on-disk {@link SpecManifest} JSON
 * dictates sheet name, header offsets, profile columns and applicability scope so a new template
 * version is a config-only addition (drop in XLSX + JSON, register a {@code TemplateVersion}).
 *
 * <p>Behaves byte-identically to {@link SpecLoader#loadBundled()} when fed the bundled TPT V7
 * manifest — see {@code SpecLoaderTest} (post sub-step 2.5) for the equivalence check.
 */
public final class ManifestDrivenSpecLoader implements TemplateSpecLoader {

    private static final Logger log = LoggerFactory.getLogger(ManifestDrivenSpecLoader.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SpecManifest manifest;
    private final String xlsxResourcePath;

    public ManifestDrivenSpecLoader(SpecManifest manifest, String xlsxResourcePath) {
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.xlsxResourcePath = Objects.requireNonNull(xlsxResourcePath, "xlsxResourcePath");
    }

    /** Convenience factory: read the manifest JSON from the classpath, then bind the XLSX path. */
    public static ManifestDrivenSpecLoader fromClasspath(String manifestResource, String xlsxResource) {
        Objects.requireNonNull(manifestResource, "manifestResource");
        Objects.requireNonNull(xlsxResource, "xlsxResource");
        try (InputStream in = ManifestDrivenSpecLoader.class.getResourceAsStream(manifestResource)) {
            if (in == null) {
                throw new IllegalStateException("Manifest not found on classpath: " + manifestResource);
            }
            SpecManifest m = MAPPER.readValue(in, SpecManifest.class);
            return new ManifestDrivenSpecLoader(m, xlsxResource);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read manifest " + manifestResource, e);
        }
    }

    public SpecManifest manifest() {
        return manifest;
    }

    /**
     * The catalog of the bundled spec: from the build's {@link CatalogSnapshot} when one
     * matching this workbook and manifest is on the classpath, else parsed from the workbook.
     */
    @Override
    public SpecCatalog load() {
        SpecCatalog snapshot = loadFromSnapshot();
        return snapshot != null ? snapshot : loadFromSpec();
    }

    /** Parses the spec workbook itself — what the snapshot is generated from. */
    public SpecCatalog loadFromSpec() {
        try (InputStream in = ManifestDrivenSpecLoader.class.getResourceAsStream(xlsxResourcePath)) {
            if (in == null) throw new IOException("Spec XLSX not found on classpath: " + xlsxResourcePath);
            try (Workbook wb = new XSSFWorkbook(in)) {
                return loadFromWorkbook(wb);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load spec XLSX " + xlsxResourcePath, e);
        }
    }

    /** {@code null} when there is no snapshot, or it was built from another workbook or manifest. */
    SpecCatalog loadFromSnapshot() {
        try (InputStream in = ManifestDrivenSpecLoader.class.getResourceAsStream(snapshotResourcePath())) {
            if (in == null) return null;
            CatalogSnapshot snapshot = CatalogSnapshot.read(in);
            if (!fingerprint().equals(snapshot.fingerprint())) {
                log.warn("Catalog snapshot {} is stale, parsing the workbook instead", snapshotResourcePath());
                return null;
            }
            return snapshot.toCatalog();
        } catch (IOException | RuntimeException e) {
            log.warn("Catalog snapshot {} unreadable, parsing the workbook instead: {}",
                    snapshotResourcePath(), e.toString());
            return null;
        }
    }

    /** Classpath location of the snapshot, next to the workbook. */
    public String snapshotResourcePath() {
        int dot = xlsxResourcePath.lastIndexOf('.');
        return (dot < 0 ? xlsxResourcePath : xlsxResourcePath.substring(0, dot)) + ".catalog.json";
    }

    /** SHA-256 over the workbook bytes and the manifest: what a snapshot must have been built from. */
    public String fingerprint() {
        try (InputStream in = ManifestDrivenSpecLoader.class.getResourceAsStream(xlsxResourcePath)) {
            if (in == null) throw new IOException("Spec XLSX not found on classpath: " + xlsxResourcePath);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 16];
            for (int n; (n = in.read(buffer)) > 0; ) digest.update(buffer, 0, n);
            digest.update(MAPPER.writeValueAsBytes(manifest));
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to fingerprint spec XLSX " + xlsxResourcePath, e);
        }
    }

    /** Visible for tests so synthetic workbooks can drive the parser without touching the classpath. */
    public SpecCatalog loadFromWorkbook(Workbook wb) {
        Sheet sheet = wb.getSheet(manifest.sheetName());
        if (sheet == null) sheet = wb.getSheetAt(0);

        SpecManifest.Columns cols = manifest.columns();
        List<FieldSpec> fields = new ArrayList<>();
        for (int rIdx = manifest.firstDataRow() - 1; rIdx <= sheet.getLastRowNum(); rIdx++) {
            Row row = sheet.getRow(rIdx);
            if (row == null) continue;
            String num  = SpecLoader.stringValue(row, cols.numData());
            String name = cols.name() != null ? SpecLoader.stringValue(row, cols.name()) : "";
            String path = cols.path() != null ? SpecLoader.stringValue(row, cols.path()) : "";

            // Section/header padding rows: nothing in numData and nothing in the label/path either.
            if (SpecLoader.isBlank(num) && SpecLoader.isBlank(name) && SpecLoader.isBlank(path)) continue;
            // If we only have a number with no companion label and the number itself doesn't look
            // like a field token, treat it as a sub-section heading and skip it.
            if (SpecLoader.isBlank(name) && SpecLoader.isBlank(path) && !SpecLoader.looksLikeFieldLabel(num)) continue;

            String definition = SpecLoader.stringValue(row, cols.definition());
            String comment    = SpecLoader.stringValue(row, cols.comment());
            String codifRaw   = SpecLoader.stringValue(row, cols.codification());
            CodificationDescriptor codif = CodificationParser.parse(codifRaw);

            ApplicabilityScope scope = readApplicabilityScope(row);
            Map<String, Flag> flags = readProfileFlags(row);

            // For TPT (no `name` column declared) the loader falls back to numData inside FieldSpec.
            // For EMT/EET/EPT (`name` declared, no FundsXML path), fundXmlPath stays null and the
            // descriptive label lives on FieldSpec.name(); see the manifest comments.
            FieldSpec spec = new FieldSpec(num.trim(),
                    name.isBlank() ? null : name.trim(),
                    path.isBlank() ? null : path.trim(),
                    definition, comment,
                    codifRaw, codif, flags, scope, rIdx + 1);
            fields.add(spec);
        }
        log.info("Loaded {} {} {} spec fields", fields.size(), manifest.templateId(), manifest.version());
        return new SpecCatalog(fields);
    }

    private ApplicabilityScope readApplicabilityScope(Row row) {
        SpecManifest.ApplicabilityColumns ac = manifest.applicabilityColumns();
        if (ac == null || ac.kind() == null || !"CIC".equalsIgnoreCase(ac.kind())) {
            return EmptyApplicabilityScope.INSTANCE;
        }

        Set<String> applicableCic = new LinkedHashSet<>();
        Map<String, Set<String>> applicableSubs = new HashMap<>();
        List<String> names = ac.names();
        for (int i = 0; i < names.size(); i++) {
            int col = ac.first() + i;
            String cellText = SpecLoader.stringValue(row, col);
            if (SpecLoader.isBlank(cellText)) continue;
            String cicName = names.get(i);
            applicableCic.add(cicName);
            Set<String> subs = SpecLoader.parseSubcategoryQualifier(cellText, cicName);
            if (!subs.isEmpty()) applicableSubs.put(cicName, subs);
        }

        if (applicableCic.isEmpty() && applicableSubs.isEmpty()) {
            return EmptyApplicabilityScope.INSTANCE;
        }
        return new CicApplicabilityScope(applicableCic, applicableSubs);
    }

    private Map<String, Flag> readProfileFlags(Row row) {
        Map<String, Flag> flags = new LinkedHashMap<>();
        for (SpecManifest.ProfileColumn pc : manifest.profileColumns()) {
            Flag flag = switch (pc.kind() == null ? "" : pc.kind().toLowerCase()) {
                case "flag" -> Flag.parse(SpecLoader.stringValue(row, pc.column()));
                case "presencemerge" -> mergeProfileColumns(row, pc.columns());
                default -> Flag.UNKNOWN;
            };
            flags.put(pc.code(), flag);
        }
        return flags;
    }

    /**
     * Merges a list of profile columns to the strictest non-blank flag. The first column is
     * parsed as a regular M/C/O/I/N/A cell; the remainder are treated as presence indicators
     * (any non-blank value contributes Flag.M). Mirrors SpecLoader's IORP/EIOPA/ECB merge.
     */
    private static Flag mergeProfileColumns(Row row, List<Integer> columns) {
        Flag best = Flag.UNKNOWN;
        for (int i = 0; i < columns.size(); i++) {
            int col = columns.get(i);
            String cellText = SpecLoader.stringValue(row, col);
            Flag f = i == 0 ? Flag.parse(cellText)
                    : (SpecLoader.isBlank(cellText) ? Flag.UNKNOWN : Flag.M);
            if (rank(f) > rank(best)) best = f;
        }
        return best;
    }

    private static int rank(Flag f) {
        return switch (f) {
            case M -> 5;
            case C -> 4;
            case O -> 3;
            case I -> 2;
            case NA -> 1;
            case UNKNOWN -> 0;
        };
    }
}
