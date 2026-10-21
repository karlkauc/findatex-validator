package com.findatex.validator.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.findatex.validator.stats.UsageEvent;
import com.findatex.validator.web.dto.UsageStatsDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What ends up in a {@link UsageRow} for each entry point — after
 * {@link UsageRow#normalise()}, i.e. exactly what would be inserted. No DB or
 * CDI: the insert seam is overridden to capture the row.
 */
class UsageStatsServiceMappingTest {

    static final class Capture extends UsageStatsService {
        final List<UsageRow> rows = new CopyOnWriteArrayList<>();
        final CountDownLatch inserted = new CountDownLatch(1);

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        void insert(UsageRow row) {
            rows.add(row);
            inserted.countDown();
        }

        UsageRow awaitRow() throws InterruptedException {
            assertThat(inserted.await(5, TimeUnit.SECONDS)).isTrue();
            return rows.get(0);
        }
    }

    private static UsageStatsDto dto(String json) throws Exception {
        return new ObjectMapper().readValue(json, UsageStatsDto.class);
    }

    @Test
    void desktopAppStartAndHeartbeatKeepTheirEventType() throws Exception {
        for (String type : List.of(UsageEvent.TYPE_APP_START, UsageEvent.TYPE_HEARTBEAT)) {
            Capture s = new Capture();
            s.record(dto("{\"eventType\":\"" + type + "\",\"status\":\"ok\","
                    + "\"installId\":\"bb2ce218-04c4-424f-bd59-7695fa76f952\",\"source\":\"desktop\","
                    + "\"appVersion\":\"1.5.0\",\"osName\":\"Windows\",\"javaMajor\":21,"
                    + "\"profiles\":[],\"fileCount\":0,\"ruleIds\":[]}"), ClientContext.EMPTY);
            UsageRow r = s.awaitRow();
            // Before 2026-10 an unknown type was clamped to 'validate' and
            // inflated the run counts — app_start/heartbeat must pass through.
            assertThat(r.eventType).isEqualTo(type);
            assertThat(r.source).isEqualTo("desktop");
            assertThat(r.fileCount).isZero();
            assertThat(r.templateId).isNull();
            assertThat(r.mode).isNull();
        }
    }

    @Test
    void unknownEventTypeIsStillClampedToValidate() throws Exception {
        Capture s = new Capture();
        s.record(dto("{\"eventType\":\"something_new\","
                + "\"installId\":\"bb2ce218-04c4-424f-bd59-7695fa76f952\"}"), ClientContext.EMPTY);
        assertThat(s.awaitRow().eventType).isEqualTo(UsageEvent.TYPE_VALIDATE);
    }

    @Test
    void desktopSeverityRuleListsAreMappedAndOldShapeLeavesThemNull() throws Exception {
        Capture s = new Capture();
        s.record(dto("{\"installId\":\"bb2ce218-04c4-424f-bd59-7695fa76f952\",\"templateId\":\"TPT\","
                + "\"ruleIds\":[\"A\",\"B\",\"C\"],\"errorRuleIds\":[\"A\"],"
                + "\"warningRuleIds\":[\"B\"]}"), ClientContext.EMPTY);
        UsageRow r = s.awaitRow();
        assertThat(r.ruleIds).containsExactly("A", "B", "C");
        assertThat(r.errorRuleIds).containsExactly("A");
        assertThat(r.warningRuleIds).containsExactly("B");
        assertThat(UsageStatsInsertTest.bind(r).get("error_rule_ids")).isEqualTo(List.of("A"));

        // Pre-2026-10 desktop JSON: no split lists → bound as empty arrays.
        Capture old = new Capture();
        old.record(dto("{\"installId\":\"bb2ce218-04c4-424f-bd59-7695fa76f952\",\"ruleIds\":[\"A\"]}"),
                ClientContext.EMPTY);
        UsageRow o = old.awaitRow();
        assertThat(UsageStatsInsertTest.bind(o).get("error_rule_ids")).isEqualTo(List.of());
        assertThat(UsageStatsInsertTest.bind(o).get("warning_rule_ids")).isEqualTo(List.of());
    }

    @Test
    void failedRunCarriesItsFailReasonIntoTheInsert() throws Exception {
        Capture s = new Capture();
        s.recordFailedRun(UsageEvent.STATUS_TEMPLATE_MISMATCH, "header_mismatch:0/57", "TPT", "V7.0",
                null, false, 12, ClientContext.EMPTY);
        UsageRow r = s.awaitRow();
        assertThat(r.failReason).isEqualTo("header_mismatch:0/57");
        assertThat(UsageStatsInsertTest.bind(r).get("fail_reason")).isEqualTo("header_mismatch:0/57");
    }

    @Test
    void desktopFailReasonIsSanitisedCappedAndDroppedForOkRuns() throws Exception {
        String longReason = "IOException/" + "X".repeat(200);
        Capture s = new Capture();
        s.record(dto("{\"installId\":\"bb2ce218-04c4-424f-bd59-7695fa76f952\",\"status\":\"parse_error\","
                + "\"failReason\":\"" + longReason + "\"}"), ClientContext.EMPTY);
        UsageRow r = s.awaitRow();
        assertThat(r.failReason).hasSize(UsageEvent.MAX_FAIL_REASON).startsWith("IOException/XXX");

        Capture msg = new Capture();
        msg.record(dto("{\"installId\":\"bb2ce218-04c4-424f-bd59-7695fa76f952\",\"status\":\"error\","
                + "\"failReason\":\"File C:\\\\secret fund.xlsx\"}"), ClientContext.EMPTY);
        assertThat(msg.awaitRow().failReason).doesNotContain(" ", "\\").matches("[A-Za-z0-9_.:/-]+");

        Capture ok = new Capture();
        ok.record(dto("{\"installId\":\"bb2ce218-04c4-424f-bd59-7695fa76f952\",\"status\":\"ok\","
                + "\"failReason\":\"whatever\"}"), ClientContext.EMPTY);
        UsageRow o = ok.awaitRow();
        assertThat(o.failReason).isNull();
        assertThat(UsageStatsInsertTest.bind(o).get("fail_reason")).isNull();
    }
}
