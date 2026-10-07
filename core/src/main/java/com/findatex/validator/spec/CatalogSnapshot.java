package com.findatex.validator.spec;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A parsed {@link SpecCatalog} as JSON. Parsing a spec workbook with POI takes seconds on
 * a cold JVM (the EMT/EET workbooks are several MB); the build therefore parses each one
 * once ({@link CatalogSnapshotGenerator}) and ships the result next to the workbook.
 *
 * @param fingerprint identifies the workbook and manifest the snapshot was parsed from —
 *                    a snapshot with another fingerprint is stale and must be ignored
 */
public record CatalogSnapshot(String fingerprint, List<Field> fields) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Field(String numData,
                        String name,
                        String fundXmlPath,
                        String definition,
                        String comment,
                        String codificationRaw,
                        Codification codification,
                        Map<String, Flag> flags,
                        boolean cicScoped,
                        List<String> applicableCic,
                        Map<String, List<String>> applicableSubcategories,
                        int sourceRow) {
    }

    public record Codification(CodificationKind kind,
                               Integer maxLength,
                               List<CodificationDescriptor.ClosedListEntry> closedList,
                               String rawText) {
    }

    public static CatalogSnapshot of(String fingerprint, SpecCatalog catalog) {
        List<Field> fields = new ArrayList<>(catalog.fields().size());
        for (FieldSpec f : catalog.fields()) {
            CodificationDescriptor c = f.codification();
            Map<String, List<String>> subcategories = new TreeMap<>();
            f.applicableSubcategories().forEach((cic, subs) -> subcategories.put(cic, List.copyOf(new TreeSet<>(subs))));
            fields.add(new Field(f.numData(), f.name(), f.fundXmlPath(), f.definition(), f.comment(),
                    f.codificationRaw(),
                    c == null ? null : new Codification(c.kind(), c.maxLength().orElse(null),
                            c.closedList(), c.rawText()),
                    f.flagsByCode(),
                    f.applicabilityScope() instanceof CicApplicabilityScope,
                    List.copyOf(f.applicableCic()),
                    subcategories,
                    f.sourceRow()));
        }
        return new CatalogSnapshot(fingerprint, fields);
    }

    public SpecCatalog toCatalog() {
        List<FieldSpec> out = new ArrayList<>(fields.size());
        for (Field f : fields) {
            Codification c = f.codification();
            ApplicabilityScope scope = EmptyApplicabilityScope.INSTANCE;
            if (f.cicScoped()) {
                Map<String, Set<String>> subcategories = new LinkedHashMap<>();
                f.applicableSubcategories().forEach((cic, subs) -> subcategories.put(cic, Set.copyOf(subs)));
                scope = new CicApplicabilityScope(new LinkedHashSet<>(f.applicableCic()), subcategories);
            }
            out.add(new FieldSpec(f.numData(), f.name(), f.fundXmlPath(), f.definition(), f.comment(),
                    f.codificationRaw(),
                    c == null ? null : new CodificationDescriptor(c.kind(), Optional.ofNullable(c.maxLength()),
                            c.closedList(), c.rawText()),
                    f.flags(), scope, f.sourceRow()));
        }
        return new SpecCatalog(out);
    }

    public void write(OutputStream out) throws IOException {
        MAPPER.writeValue(out, this);
    }

    public static CatalogSnapshot read(InputStream in) throws IOException {
        return MAPPER.readValue(in, CatalogSnapshot.class);
    }
}
