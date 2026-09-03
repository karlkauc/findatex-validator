package com.findatex.validator.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.findatex.validator.web.dto.UsageStatsDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which thread writes which event kind. Cloud Run throttles the CPU once the
 * response is out, so an event that arrives as a lone request (desktop ingest,
 * page-view beacon) must be written on the request thread, before the response;
 * web-run events keep the fire-and-forget worker because the user's next
 * request gives the instance CPU anyway. No DB or CDI: the insert seam is
 * overridden to record the calling thread.
 */
class UsageStatsServiceDeliveryTest {

    static final class Probe extends UsageStatsService {
        final List<String> insertThreads = new CopyOnWriteArrayList<>();
        final CountDownLatch inserted = new CountDownLatch(1);

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        void insert(UsageRow row) {
            insertThreads.add(Thread.currentThread().getName());
            inserted.countDown();
        }
    }

    private static UsageStatsDto desktopDto() throws Exception {
        return new ObjectMapper().readValue(
                "{\"installId\":\"bb2ce218-04c4-424f-bd59-7695fa76f952\",\"templateId\":\"TPT\"}",
                UsageStatsDto.class);
    }

    @Test
    void desktopIngestIsWrittenOnTheRequestThreadBeforeReturning() throws Exception {
        Probe s = new Probe();
        s.record(desktopDto(), ClientContext.EMPTY);
        // No waiting: the row must already be written when record() returns.
        assertThat(s.insertThreads).containsExactly(Thread.currentThread().getName());
    }

    @Test
    void pageViewIsWrittenOnTheRequestThreadBeforeReturning() {
        Probe s = new Probe();
        s.recordPageView("/", null, null, ClientContext.EMPTY);
        assertThat(s.insertThreads).containsExactly(Thread.currentThread().getName());
    }

    @Test
    void webEventsStayOnTheBackgroundWorker() throws Exception {
        Probe s = new Probe();
        s.recordReportDownload("TPT", "V8.0", ClientContext.EMPTY);
        assertThat(s.inserted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(s.insertThreads).containsExactly("usage-stats-db");
    }
}
