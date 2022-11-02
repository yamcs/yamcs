package org.yamcs.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.YConfiguration;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

public class TimeCorrelationServiceTest {
    static String yamcsInstance = "ots-test";
    static String serviceName = "test";
    static String tableName = TimeCorrelationService.TABLE_NAME + serviceName;

    @BeforeAll
    public static void beforeClass() {
        TimeEncoding.setUp();
        EventProducerFactory.setMockup(false);
    }

    private TimeCorrelationService createAndStart(boolean dropTable) throws Exception {
        if (dropTable) {
            YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
            if (ydb.getTable(tableName) != null) {
                ydb.dropTable(tableName);
            }
        }
        TimeCorrelationService ots = new TimeCorrelationService();
        Map<String, Object> conf = new HashMap<>();
        conf.put("numSamples", 2);
        ots.init(yamcsInstance, serviceName, YConfiguration.wrap(conf));
        ots.startAsync().awaitRunning();

        return ots;
    }

    @Test
    public void test1() throws Exception {
        TimeCorrelationService ots = createAndStart(true);
        assertEquals(Instant.INVALID_INSTANT, ots.getTime(0));
        ots.addSample(0, Instant.get(1000, 0));
        assertEquals(Instant.INVALID_INSTANT, ots.getTime(0));
        ots.addSample(1, Instant.get(2000, 0));

        assertEquals(Instant.get(1000, 0), ots.getTime(0));
        assertEquals(Instant.get(2000, 0), ots.getTime(1));
        assertEquals(Instant.get(3000, 0), ots.getTime(2));

        ots.stopAsync().awaitTerminated();
    }

    @Test
    public void testRetrievalFromArchive() throws Exception {
        TimeCorrelationService ots1 = createAndStart(true);
        ots1.addSample(0, Instant.get(10000, 0));
        ots1.addSample(1, Instant.get(11000, 5000));
        assertEquals(Instant.get(13000, 15000), ots1.getTime(3));
        ots1.stopAsync().awaitTerminated();

        TimeCorrelationService ots2 = createAndStart(false);
        assertEquals(Instant.get(13000, 15000), ots2.getTime(3));
    }

    @Test
    public void testReset() throws Exception {
        TimeCorrelationService ots = createAndStart(true);
        ots.addSample(0, Instant.get(1000, 0));
        ots.addSample(1, Instant.get(2000, 0));
        assertEquals(Instant.get(4000, 0), ots.getTime(3));

        ots.reset();
        assertEquals(Instant.INVALID_INSTANT, ots.getTime(0));

        ots.addSample(0, Instant.get(10000, 0));
        ots.addSample(1, Instant.get(11000, 5000));
        assertEquals(Instant.get(13000, 15000), ots.getTime(3));

        ots.stopAsync().awaitTerminated();
    }

    @Test
    public void testExceededAccuracy() throws Exception {
        TimeCorrelationService ots = createAndStart(true);
        ots.addSample(0, Instant.get(1000, 0));
        ots.addSample(1, Instant.get(2000, 0));
        assertEquals(Instant.get(5000, 0), ots.getTime(4));

        ots.addSample(3, Instant.get(4200, 0));// this will cause the coefficients to be recomputed based on the last
                                               // two samples
        assertEquals(Instant.get(5300, 0), ots.getTime(4));

        assertEquals(Instant.get(5000, 0), ots.getHistoricalTime(Instant.get(1000, 0), 4));
    }

    @Test
    public void testExceededValidity() throws Exception {
        TimeCorrelationService ots = createAndStart(true);
        ots.addSample(0, Instant.get(1000, 0));
        ots.addSample(1, Instant.get(2000, 0));
        assertEquals(Instant.get(4000, 0), ots.getTime(3));

        ots.addSample(3, Instant.get(5000, 0));// this will cause the coefficients to be recomputed based on the last
                                               // two samples
        assertEquals(Instant.INVALID_INSTANT, ots.getTime(0));
    }

    @Test
    public void testUnsortedSample() {
        assertThrows(IllegalArgumentException.class, () -> {
            TimeCorrelationService ots = createAndStart(true);
            ots.addSample(10, Instant.get(1000, 0));
            ots.addSample(9, Instant.get(0, 0));
        });
    }
}
