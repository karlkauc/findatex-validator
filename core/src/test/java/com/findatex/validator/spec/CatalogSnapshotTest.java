package com.findatex.validator.spec;

import com.findatex.validator.template.api.TemplateDefinition;
import com.findatex.validator.template.api.TemplateRegistry;
import com.findatex.validator.template.api.TemplateVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The build ships a pre-parsed snapshot of every spec workbook and the loader prefers it.
 * It must be indistinguishable from parsing the workbook.
 */
class CatalogSnapshotTest {

    /** Set by Surefire; an IDE run on classes that never saw the generator has no snapshots. */
    private static final boolean MAVEN_BUILD = System.getProperty("basedir") != null;

    @BeforeAll
    static void registry() {
        TemplateRegistry.init();
    }

    private static List<ManifestDrivenSpecLoader> loaders() {
        List<ManifestDrivenSpecLoader> out = new ArrayList<>();
        for (TemplateDefinition def : TemplateRegistry.all()) {
            for (TemplateVersion v : def.versions()) {
                out.add((ManifestDrivenSpecLoader) def.specLoaderFor(v));
            }
        }
        return out;
    }

    @Test
    void everyBundledSpecHasASnapshotEqualToTheParsedWorkbook() {
        for (ManifestDrivenSpecLoader loader : loaders()) {
            SpecCatalog fromSnapshot = loader.loadFromSnapshot();
            if (fromSnapshot == null) assumeTrue(MAVEN_BUILD, "no snapshot outside a Maven build");
            assertThat(fromSnapshot).as(loader.snapshotResourcePath()).isNotNull();
            assertThat(CatalogSnapshot.of("", fromSnapshot))
                    .as(loader.snapshotResourcePath())
                    .isEqualTo(CatalogSnapshot.of("", loader.loadFromSpec()));
        }
    }

    @Test
    void snapshotSurvivesTheJsonRoundTrip() throws IOException {
        ManifestDrivenSpecLoader loader = loaders().get(0);
        CatalogSnapshot original = CatalogSnapshot.of(loader.fingerprint(), loader.loadFromSpec());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        original.write(out);
        CatalogSnapshot read = CatalogSnapshot.read(new ByteArrayInputStream(out.toByteArray()));
        assertThat(read).isEqualTo(original);
        assertThat(CatalogSnapshot.of(read.fingerprint(), read.toCatalog())).isEqualTo(original);
    }

    @Test
    void fingerprintDependsOnTheManifest() {
        ManifestDrivenSpecLoader tptV8 = loaders().get(0);
        ManifestDrivenSpecLoader other = loaders().get(1);
        assertThat(tptV8.fingerprint()).hasSize(64).isNotEqualTo(other.fingerprint());
    }
}
