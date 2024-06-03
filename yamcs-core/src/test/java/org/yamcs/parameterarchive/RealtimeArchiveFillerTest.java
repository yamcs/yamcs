package org.yamcs.parameterarchive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.rocksdb.RocksDBException;
import org.yamcs.ConfigurationException;
import org.yamcs.Processor;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.parameter.BasicParameterValue;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameterarchive.RealtimeArchiveFiller.SegmentQueue;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yaml.snakeyaml.Yaml;

/**
 * Implements unit tests for {@link RealtimeArchiveFiller}.
 */
public class RealtimeArchiveFillerTest {

    /** The size of an interval, in milliseconds. (2^23 seconds) */
    private static final long INTERVAL_SIZE_MILLIS = 8388608000L;

    /** The amount of a backward time jump that will trigger a cache flush. */
    private static final long PAST_JUMP_THRESHOLD_SECS = 86400;
    private static final long PAST_JUMP_THRESHOLD_MILLIS = PAST_JUMP_THRESHOLD_SECS * 1000;

    /** The Yamcs instant at the start of 2021. */
    private static final long YEAR_2021_START_INSTANT = 1609472533000L;

    @Mock
    private ParameterArchive parameterArchive;

    @Mock
    private ParameterIdDb parameterIdDb;

    @Mock
    private ParameterGroupIdDb parameterGroupIdDb;

    @Mock
    private YamcsServer yamcsServer;

    @Mock
    private Processor processor;

    @Mock
    private ParameterRequestManager parameterRequestManager;

    @BeforeEach
    public void setup() throws RocksDBException {
        MockitoAnnotations.openMocks(this);
        when(processor.getParameterRequestManager()).thenReturn(parameterRequestManager);
        when(parameterArchive.getYamcsInstance()).thenReturn("realtime");
        when(parameterArchive.getParameterIdDb()).thenReturn(parameterIdDb);
        when(parameterArchive.getParameterGroupIdDb()).thenReturn(parameterGroupIdDb);
        when(parameterGroupIdDb.getGroup(IntArray.wrap(0))).thenReturn(new ParameterGroupIdDb.ParameterGroup(0, null));

        // TimeEncoding is used when logging messages by RealtimeArchiveFiller.
        TimeEncoding.setUp();
    }

    /**
     * Tests that when there are no parameter values added to the archiver that no segments are written.
     * 
     * @throws InterruptedException
     *             if the archiver is interrupted during shutdown
     * @throws RocksDBException
     *             if there is an error writing to RocksDB
     * @throws IOException
     *             if there is an I/O error writing segments
     */
    @Test
    public void testNoParametersToArchive() throws InterruptedException, RocksDBException, IOException {
        when(yamcsServer.getProcessor(anyString(), anyString())).thenReturn(processor);
        RealtimeArchiveFiller filler = getFiller(1000);
        filler.start();
        filler.shutDown();
        verify(parameterArchive, never()).writeToArchive(any(PGSegment.class));
    }

    /**
     * Tests that when no processor is configured the archiver fails to start.
     */
    @Test
    public void testNoProcessor() {
        assertThrows(ConfigurationException.class, () -> {
            RealtimeArchiveFiller filler = getFiller(1000);
            filler.start();
        });
    }

    /**
     * Tests that all segments are flushed when the archiver is shut down. In this case there is a single value
     * archived, which should be in one segment.
     * 
     * @throws InterruptedException
     *             if the archiver is interrupted during shutdown
     * @throws RocksDBException
     *             if there is an error writing to RocksDB
     * @throws IOException
     *             if there is an I/O error writing segments
     */
    @Test
    public void testFlushOnShutdown() throws InterruptedException, RocksDBException, IOException {
        when(yamcsServer.getProcessor(anyString(), anyString())).thenReturn(processor);
        RealtimeArchiveFiller filler = getFiller(1000);
        filler.start();
        List<ParameterValue> values = getValues(0, 0, "/myproject/value");
        filler.processParameters(values);
        filler.shutDown();

        verify(parameterArchive, times(1)).writeToArchive(any(PGSegment.class));
    }

    /**
     * Tests that a new value added older than the <code>pastJumpThreshold</code> causes a complete cache flush before
     * adding the value.
     * 
     * @throws InterruptedException
     *             if the executor is interrupted while shutting down
     * @throws IOException
     *             if there is an error writing to the archive
     * @throws RocksDBException
     *             if there is an error writing to the database
     */
    @Test
    public void testFlushBeforeVeryOldValues() throws InterruptedException, RocksDBException, IOException {
        when(yamcsServer.getProcessor(anyString(), anyString())).thenReturn(processor);
        RealtimeArchiveFiller filler = getFiller(1000);
        filler.start();
        List<ParameterValue> values = getValues(YEAR_2021_START_INSTANT + PAST_JUMP_THRESHOLD_MILLIS + 1,
                YEAR_2021_START_INSTANT + PAST_JUMP_THRESHOLD_MILLIS + 1, "/myproject/value");
        filler.processParameters(values);

        values = getValues(YEAR_2021_START_INSTANT, YEAR_2021_START_INSTANT, "/myproject/value");
        filler.processParameters(values);

        // Shut down the executor to make sure the write of the first segment completes.
        filler.executor.shutdown();
        filler.executor.awaitTermination(10, TimeUnit.SECONDS);
        verify(parameterArchive, times(1)).writeToArchive(any(PGSegment.class));

        // And the archiver should now have one segment for the old data.
        assertEquals(1, filler.getSegments(0, 0, false).size());
    }

    /**
     * Tests that values older than the sorting threshold are not added.
     * 
     * @throws InterruptedException
     *             if the executor is interrupted while shutting down
     * @throws IOException
     *             if there is an error writing to the archive
     * @throws RocksDBException
     *             if there is an error writing to the database
     */
    @Test
    public void testIgnoreOldValues() throws InterruptedException, RocksDBException, IOException {
        when(yamcsServer.getProcessor(anyString(), anyString())).thenReturn(processor);
        RealtimeArchiveFiller filler = getFiller(1000);
        filler.start();
        List<ParameterValue> values = getValues(5000, 5000, "/myproject/value");
        filler.processParameters(values);

        // Add a value that is older than the last time minues the sorting threshold.
        values = getValues(3000, 3000, "/myproject/value");
        filler.processParameters(values);

        // Shut down and capture the segment that was written.
        filler.shutDown();
        ArgumentCaptor<PGSegment> segCaptor = ArgumentCaptor.forClass(PGSegment.class);
        verify(parameterArchive).writeToArchive(segCaptor.capture());

        PGSegment seg = segCaptor.getValue();
        assertEquals(5000, seg.getSegmentStart());
        assertEquals(5000, seg.getSegmentEnd());
    }

    /**
     * Tests that when adding a new value, if there is a segment from a prior interval that ends before the new time
     * minus the sorting threshold, that the old segment is archived.
     * 
     * @throws InterruptedException
     *             if the executor is interrupted while shutting down
     * @throws IOException
     *             if there is an error writing to the archive
     * @throws RocksDBException
     *             if there is an error writing to the database
     */
    @Test
    public void testPriorIntervalSegmentIsArchived() throws InterruptedException, RocksDBException, IOException {
        when(yamcsServer.getProcessor(anyString(), anyString())).thenReturn(processor);
        RealtimeArchiveFiller filler = getFiller(1000);
        filler.start();
        List<ParameterValue> values = getValues(INTERVAL_SIZE_MILLIS + 1, INTERVAL_SIZE_MILLIS + 1, "/myproject/value");
        filler.processParameters(values);

        // Add a value that is older than the last time minues the sorting threshold.
        values = getValues(3000, 3000, "/myproject/value");
        filler.processParameters(values);

        // Shut down the executor to make sure the write of the first segment completes.
        filler.executor.shutdown();
        filler.executor.awaitTermination(10, TimeUnit.SECONDS);
        verify(parameterArchive, times(1)).writeToArchive(any(PGSegment.class));

        // And the archiver should now have one segment for the old data.
        assertEquals(1, filler.getSegments(0, 0, false).size());
    }

    /**
     * Tests that when adding a new value, a prior segment that ends before the current time minus the sorting threshold
     * is archived.
     * 
     * @throws InterruptedException
     *             if the executor is interrupted while shutting down
     * @throws IOException
     *             if there is an error writing to the archive
     * @throws RocksDBException
     *             if there is an error writing to the database
     */
    @Test
    public void testFullIntervalOutsideSortingThresholdIsArchived()
            throws InterruptedException, RocksDBException, IOException {
        when(parameterArchive.getMaxSegmentSize()).thenReturn(2);
        when(yamcsServer.getProcessor(anyString(), anyString())).thenReturn(processor);
        RealtimeArchiveFiller filler = getFiller(1000);
        filler.start();

        // Add two values to fill up a segment.
        List<ParameterValue> values = getValues(0, 0, "/myproject/value");
        filler.processParameters(values);
        values = getValues(1, 1, "/myproject/value");
        filler.processParameters(values);

        // Add a new value after the sorting threshold has elapsed.
        values = getValues(1002, 1002, "/myproject/value");
        filler.processParameters(values);

        // Shut down the executor to make sure the write of the first segment completes.
        filler.executor.shutdown();
        filler.executor.awaitTermination(10, TimeUnit.SECONDS);
        verify(parameterArchive, times(1)).writeToArchive(any(PGSegment.class));

        // And the archiver should now have one segment for the old data.
        assertEquals(1, filler.getSegments(0, 0, false).size());
    }

    /**
     * Tests that when the cache is full, a new value cannot be added, but that all segments are flushed when the
     * archive is shut down.
     * 
     * @throws InterruptedException
     *             if the executor is interrupted while shutting down
     * @throws IOException
     *             if there is an error writing to the archive
     * @throws RocksDBException
     *             if there is an error writing to the database
     */
    @Test
    public void testAddWhenCacheIsFull() throws InterruptedException, RocksDBException, IOException {
        when(parameterArchive.getMaxSegmentSize()).thenReturn(2);
        when(yamcsServer.getProcessor(anyString(), anyString())).thenReturn(processor);
        RealtimeArchiveFiller filler = getFiller(1000);
        filler.start();

        for (int i = 0; i < SegmentQueue.QSIZE - 1; ++i) {
            // Add two values to fill a segment.
            List<ParameterValue> values = getValues(2 * i, 2 * i, "/myproject/value");
            filler.processParameters(values);
            values = getValues(2 * i + 1, 2 * i + 1, "/myproject/value");
            filler.processParameters(values);
        }

        // The queue should now be full. Adding another value should fail.
        assertEquals(SegmentQueue.QSIZE - 1, filler.getSegments(0, 0, false).size());
        List<ParameterValue> values = getValues(2 * SegmentQueue.QSIZE, 2 * SegmentQueue.QSIZE,
                "/myproject/value");
        filler.processParameters(values);
        assertEquals(SegmentQueue.QSIZE - 1, filler.getSegments(0, 0, false).size());

        // Shut down and make sure all segments are flushed.
        filler.shutDown();
        verify(parameterArchive, times(SegmentQueue.QSIZE - 1)).writeToArchive(any(PGSegment.class));
    }

    private RealtimeArchiveFiller getFiller(long sortingThreshold) {
        String configStr = String.format(
                "sortingThreshold: %d\n"
                        + "pastJumpThreshold: %d\n",
                sortingThreshold, PAST_JUMP_THRESHOLD_SECS);
        YConfiguration config = YConfiguration.wrap(new Yaml().load(configStr));
        RealtimeArchiveFiller filler = new RealtimeArchiveFiller(parameterArchive, config);
        filler.setYamcsServer(yamcsServer);
        return filler;
    }

    private List<ParameterValue> getValues(long genTime, long acqTime, String... names) {
        List<ParameterValue> values = new ArrayList<>();
        for (String name : names) {
            ParameterValue value = new ParameterValue(name);
            value.setGenerationTime(genTime);
            value.setAcquisitionTime(acqTime);
            value.setAcquisitionStatus(AcquisitionStatus.ACQUIRED);
            value.setRawSignedInteger(123);
            value.setEngValue(value.getRawValue());
            values.add(value);
        }
        return values;
    }

    List<BasicParameterValue> getParaList(long time) {
        ParameterValue pv = new ParameterValue("test1");
        pv.setEngValue(ValueUtility.getUint64Value(time));
        pv.setGenerationTime(time);
        return Arrays.asList(pv);
    }

}
