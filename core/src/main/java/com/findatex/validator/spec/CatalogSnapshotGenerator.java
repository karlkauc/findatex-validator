package com.findatex.validator.spec;

import com.findatex.validator.template.api.TemplateDefinition;
import com.findatex.validator.template.api.TemplateRegistry;
import com.findatex.validator.template.api.TemplateSpecLoader;
import com.findatex.validator.template.api.TemplateVersion;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Build step (exec-maven-plugin, {@code process-classes}): parses every bundled spec
 * workbook and writes its {@link CatalogSnapshot} into the given classes directory.
 */
public final class CatalogSnapshotGenerator {

    private CatalogSnapshotGenerator() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("usage: CatalogSnapshotGenerator <classes-dir>");
        Path classesDir = Path.of(args[0]);
        TemplateRegistry.init();
        for (TemplateDefinition def : TemplateRegistry.all()) {
            for (TemplateVersion version : def.versions()) {
                TemplateSpecLoader loader = def.specLoaderFor(version);
                if (!(loader instanceof ManifestDrivenSpecLoader manifestLoader)) continue;
                CatalogSnapshot snapshot = CatalogSnapshot.of(
                        manifestLoader.fingerprint(), manifestLoader.loadFromSpec());
                Path target = classesDir.resolve(manifestLoader.snapshotResourcePath().substring(1));
                Files.createDirectories(target.getParent());
                try (OutputStream out = Files.newOutputStream(target)) {
                    snapshot.write(out);
                }
                System.out.println("Catalog snapshot: " + classesDir.relativize(target)
                        + " (" + snapshot.fields().size() + " fields)");
            }
        }
    }
}
