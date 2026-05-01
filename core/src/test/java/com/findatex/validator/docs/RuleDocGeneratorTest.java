package com.findatex.validator.docs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden-file regression: re-runs {@link RuleDocGenerator} into a temp directory and diffs the
 * output against the checked-in {@code docs/rules/} files. Fails when rule code changes
 * silently desync the documentation — the failure message points at the regen command.
 *
 * <p>If the test cannot find {@code docs/rules/} on disk (e.g. running from a fresh shallow
 * clone where the docs were never generated), it skips gracefully — the regen step in CI is
 * the canonical enforcement point, not this test.
 */
final class RuleDocGeneratorTest {

    @Test
    void generated_docs_are_in_sync_with_committed_copies(@TempDir Path tmp) throws IOException {
        Path checkedIn = locateDocsRulesDir();
        if (checkedIn == null || !Files.isDirectory(checkedIn)) {
            // Fresh checkout / docs never generated — let the regen profile handle it instead.
            return;
        }

        new RuleDocGenerator().generate(tmp);

        TreeSet<String> actual = listMd(tmp);
        TreeSet<String> expected = listMd(checkedIn);

        assertThat(actual)
                .as("generator output filenames vs. checked-in docs/rules/")
                .isEqualTo(expected);

        List<String> diffs = new ArrayList<>();
        for (String name : actual) {
            String generated = Files.readString(tmp.resolve(name), StandardCharsets.UTF_8);
            String committed = Files.readString(checkedIn.resolve(name), StandardCharsets.UTF_8);
            if (!generated.equals(committed)) {
                diffs.add(name + " (" + diffSummary(committed, generated) + ")");
            }
        }
        assertThat(diffs)
                .as("docs/rules/*.md drifted from RuleDocGenerator output. "
                        + "Regenerate via:  mvn -pl core -Pdocs exec:java")
                .isEmpty();
    }

    /**
     * Looks for {@code docs/rules/} relative to common test working directories. Surefire
     * starts in {@code core/}, so "../docs/rules" hits the right path; running from the repo
     * root would use "docs/rules". Returns {@code null} when neither exists.
     */
    private static Path locateDocsRulesDir() {
        for (String candidate : new String[]{"../docs/rules", "docs/rules"}) {
            Path p = Path.of(candidate);
            if (Files.isDirectory(p)) return p.toAbsolutePath().normalize();
        }
        return null;
    }

    private static TreeSet<String> listMd(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            TreeSet<String> out = new TreeSet<>();
            s.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".md") || n.equals("index.json"))
                    .forEach(out::add);
            return out;
        }
    }

    private static String diffSummary(String committed, String generated) {
        String[] a = committed.split("\n", -1);
        String[] b = generated.split("\n", -1);
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            if (!a[i].equals(b[i])) {
                return "first divergence at line " + (i + 1) + ": committed=\""
                        + truncate(a[i]) + "\" generated=\"" + truncate(b[i]) + "\"";
            }
        }
        return "length differs (" + a.length + " vs " + b.length + " lines)";
    }

    private static String truncate(String s) {
        return s.length() > 80 ? s.substring(0, 77) + "…" : s;
    }
}
