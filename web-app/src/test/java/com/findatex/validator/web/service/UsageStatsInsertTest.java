package com.findatex.validator.web.service;

import com.findatex.validator.stats.UsageEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Column ↔ parameter parity of {@link UsageStatsService#INSERT}: every named
 * column gets exactly one placeholder and one bound value, in order. Runs
 * against a recording JDBC proxy — no DB needed — so a column added to the
 * INSERT without a binding (or vice versa) fails here, not in production.
 */
class UsageStatsInsertTest {

    /** Columns as named in the INSERT, in order. */
    private static List<String> columns() {
        String sql = UsageStatsService.INSERT;
        String list = sql.substring(sql.indexOf('(') + 1, sql.indexOf(')'));
        return Arrays.stream(list.split(",")).map(String::trim).toList();
    }

    /** Executes {@link UsageStatsService#write} on a recording proxy; returns column → bound value. */
    static Map<String, Object> bind(UsageRow row) throws Exception {
        TreeMap<Integer, Object> params = new TreeMap<>();
        boolean[] executed = {false};
        PreparedStatement ps = (PreparedStatement) Proxy.newProxyInstance(
                UsageStatsInsertTest.class.getClassLoader(), new Class<?>[]{PreparedStatement.class},
                (proxy, m, args) -> {
                    String n = m.getName();
                    if (n.equals("setNull")) {
                        params.put((Integer) args[0], null);
                    } else if (n.startsWith("set") && args != null && args.length == 2) {
                        Object v = args[1];
                        params.put((Integer) args[0], v instanceof Array a ? List.of((Object[]) a.getArray()) : v);
                    } else if (n.equals("executeUpdate")) {
                        executed[0] = true;
                        return 1;
                    }
                    return null;
                });
        Connection c = (Connection) Proxy.newProxyInstance(
                UsageStatsInsertTest.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "prepareStatement" -> {
                        assertThat(args[0]).isEqualTo(UsageStatsService.INSERT);
                        yield ps;
                    }
                    case "createArrayOf" -> array((Object[]) args[1]);
                    default -> null;
                });
        UsageStatsService.write(c, row);
        assertThat(executed[0]).isTrue();

        List<String> cols = columns();
        assertThat(params.keySet()).as("one binding per column, 1..n")
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, cols.size()).boxed().toList());
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < cols.size(); i++) out.put(cols.get(i), params.get(i + 1));
        return out;
    }

    private static Array array(Object[] elements) {
        return (Array) Proxy.newProxyInstance(
                UsageStatsInsertTest.class.getClassLoader(), new Class<?>[]{Array.class},
                (proxy, m, args) -> m.getName().equals("getArray") ? elements : null);
    }

    @Test
    void everyColumnHasExactlyOnePlaceholder() {
        String sql = UsageStatsService.INSERT;
        long placeholders = sql.chars().filter(ch -> ch == '?').count();
        assertThat(columns()).doesNotHaveDuplicates();
        assertThat(placeholders).isEqualTo(columns().size());
    }

    @Test
    void severityRuleListsAreBoundNextToRuleIds() throws Exception {
        UsageRow r = new UsageRow();
        r.ruleIds = List.of("A", "B", "C");
        r.errorRuleIds = List.of("A");
        r.warningRuleIds = List.of("B");
        Map<String, Object> bound = bind(r.normalise());
        assertThat(bound.get("rule_ids")).isEqualTo(List.of("A", "B", "C"));
        assertThat(bound.get("error_rule_ids")).isEqualTo(List.of("A"));
        assertThat(bound.get("warning_rule_ids")).isEqualTo(List.of("B"));
    }

    @Test
    void missingSeverityListsAreStoredAsEmptyArraysNotNull() throws Exception {
        // Desktop builds before 2026-10 post no errorRuleIds/warningRuleIds:
        // the columns are NOT NULL, so the insert must bind '{}'.
        UsageRow r = new UsageRow();
        r.errorRuleIds = null;
        r.warningRuleIds = null;
        Map<String, Object> bound = bind(r.normalise());
        assertThat(bound.get("error_rule_ids")).isEqualTo(List.of());
        assertThat(bound.get("warning_rule_ids")).isEqualTo(List.of());
        assertThat(bound.get("event_type")).isEqualTo(UsageEvent.TYPE_VALIDATE);
    }
}
