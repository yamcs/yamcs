package org.yamcs;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.junit.Before;
import org.junit.Test;
import org.yamcs.parameterarchive.ParameterArchive;
import org.yamcs.parameterarchive.ParameterArchiveV2;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.Ranges;
import org.yamcs.protobuf.Pvalue.Ranges.Range;
import org.yamcs.protobuf.Pvalue.TimeSeries;
import org.yamcs.protobuf.Pvalue.TimeSeries.Sample;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.utils.TimeEncoding;

import io.netty.handler.codec.http.HttpMethod;

public class ParameterArchiveIntegrationTest extends AbstractIntegrationTest {

    @Before
    public void cleanParameterCache() {
        Processor p = Processor.getInstance(yamcsInstance, "realtime");
        p.getParameterCache().clear();
    }
    
    @Test
    public void testRestRetrieval() throws Exception {
        generatePkt13AndPps("2015-01-02T10:00:00", 2 * 3600);

        String resp;
        Value engValue;
        ParameterData pdata;
        org.yamcs.protobuf.Pvalue.ParameterValue pv;
        TimeSeries vals;
        Sample s0;
        
        
        // first two requests before the consolidation, should return data from cache
        resp = restClient.doRequest(
                "/archive/IntegrationTest/parameters/REFMDB/SUBSYS1/FloatPara1_1_2?start=2015-01-02T10:00:00&stop=2015-01-02T11:00:00",
                HttpMethod.GET, "").get();
        pdata = fromJson(resp, ParameterData.newBuilder()).build();
       
        assertEquals(100, pdata.getParameterCount());
        pv = pdata.getParameter(0);
        engValue = pv.getEngValue();
        assertEquals(0.167291805148, engValue.getFloatValue(), 1e-5);
        assertEquals(2850, pv.getExpireMillis());
        

        resp = restClient.doRequest(
                "/archive/IntegrationTest/parameters/REFMDB/SUBSYS1/FloatPara1_1_2/samples?start=2015-01-02T11:40:00&stop=2015-01-02T12:00:00",
                HttpMethod.GET, "").get();
        vals = (fromJson(resp, TimeSeries.newBuilder())).build();
        assertEquals(500, vals.getSampleCount());
        s0 = vals.getSample(0);
        assertEquals(0.167291805148, s0.getMin(), 1e-5);
        assertEquals(0.167291805148, s0.getMax(), 1e-5);
        assertEquals(0.167291805148, s0.getAvg(), 1e-5);

        buildParameterArchive("2015-01-02T10:00:00", "2016-01-02T11:00:00");

        resp = restClient.doRequest(
                "/archive/IntegrationTest/parameters/REFMDB/SUBSYS1/FloatPara1_1_2/samples?start=2015-01-02T10:00:00&stop=2015-01-02T11:00:00",
                HttpMethod.GET, "").get();
        vals = fromJson(resp, TimeSeries.newBuilder()).build();
        assertEquals(500, vals.getSampleCount());
        s0 = vals.getSample(0);
        assertEquals(0.167291805148, s0.getMin(), 1e-5);
        assertEquals(0.167291805148, s0.getMax(), 1e-5);
        assertEquals(0.167291805148, s0.getAvg(), 1e-5);

        
        
        resp = restClient.doRequest(
                "/archive/IntegrationTest/parameters/REFMDB/SUBSYS1/FloatPara1_1_2?start=2015-01-02T10:00:00&stop=2015-01-02T11:00:00",
                HttpMethod.GET, "").get();
        pdata = fromJson(resp, ParameterData.newBuilder()).build();
        assertEquals(100, pdata.getParameterCount());
        pv = pdata.getParameter(0);
        engValue = pv.getEngValue();
        assertEquals(0.167291805148, engValue.getFloatValue(), 1e-5);
        assertEquals(2850, pv.getExpireMillis());
        
        
        resp = restClient.doRequest(
                "/archive/IntegrationTest/parameters/REFMDB/SUBSYS1/FloatPara1_1_2?start=2015-01-02T10:00:00&stop=2015-01-02T11:00:00&limit=10",
                HttpMethod.GET, "").get();
        pdata = fromJson(resp, ParameterData.newBuilder()).build();
        assertEquals(10, pdata.getParameterCount());

        resp = restClient.doRequest(
                "/archive/IntegrationTest/parameters/REFMDB/SUBSYS1/FloatPara1_1_2?start=2015-01-02T10:00:00&stop=2015-01-02T11:00:00&norepeat",
                HttpMethod.GET, "").get();
        pdata = fromJson(resp, ParameterData.newBuilder()).build();

        assertEquals(1, pdata.getParameterCount());
        pv = pdata.getParameter(0);

        assertEquals("2015-01-02T11:00:00.000Z", TimeEncoding.toString(pv.getGenerationTime()));
        assertEquals(0.167291805148, pv.getEngValue().getFloatValue(), 1e-5);
        AcquisitionStatus acqs = pdata.getParameter(0).getAcquisitionStatus();
        assertEquals(AcquisitionStatus.ACQUIRED, acqs);

        // add some realtime data
        generatePkt13AndPps("2015-01-02T12:00:00", 10);

        resp = restClient.doRequest(
                "/archive/IntegrationTest/parameters/REFMDB/SUBSYS1/FloatPara1_1_2?stop=2015-01-03T11:59:00&limit=20",
                HttpMethod.GET, "").get();
        pdata = fromJson(resp, ParameterData.newBuilder()).build();
        assertEquals(20, pdata.getParameterCount());
        long t = TimeEncoding.parse("2015-01-02T12:00:09.000");
        for (int i = 0; i < pdata.getParameterCount(); i++) {
            pv = pdata.getParameter(i);
            assertEquals(TimeEncoding.toString(t), TimeEncoding.toString(pv.getGenerationTime()));
            t -= 1000;
        }

        resp = restClient.doRequest(
                "/archive/IntegrationTest/parameters/REFMDB/SUBSYS1/FloatPara1_1_2?start=2015-01-02T12:00:00&stop=2015-01-03T11:59:00",
                HttpMethod.GET, "").get();
        pdata = fromJson(resp, ParameterData.newBuilder()).build();
        assertEquals(9, pdata.getParameterCount());
        t = TimeEncoding.parse("2015-01-02T12:00:09.000");
        for (int i = 0; i < pdata.getParameterCount(); i++) {
            pv = pdata.getParameter(i);
            assertEquals(TimeEncoding.toString(t), TimeEncoding.toString(pv.getGenerationTime()));
            t -= 1000;
        }

        // request excluding realtime cache
        resp = restClient.doRequest(
                "/archive/IntegrationTest/parameters/REFMDB/SUBSYS1/FloatPara1_1_2?start=2015-01-02T12:00:00&stop=2015-01-03T11:59:00&norealtime",
                HttpMethod.GET, "").get();
        pdata = fromJson(resp, ParameterData.newBuilder()).build();
        assertEquals(0, pdata.getParameterCount());

        // ascending request combining archive with cache

        resp = restClient.doRequest(
                "/archive/IntegrationTest/parameters/REFMDB/SUBSYS1/FloatPara1_1_2?start=2015-01-02T11:59:50&stop=2015-01-03T11:59:00&order=asc",
                HttpMethod.GET, "").get();
        pdata = fromJson(resp, ParameterData.newBuilder()).build();
        assertEquals(20, pdata.getParameterCount());
        t = TimeEncoding.parse("2015-01-02T11:59:50");
        for (int i = 0; i < pdata.getParameterCount(); i++) {
            pv = pdata.getParameter(i);
            assertEquals(TimeEncoding.toString(t), TimeEncoding.toString(pv.getGenerationTime()));
            t += 1000;
        }
    }
    
    @Test
    public void testRestRanges() throws Exception {
        generatePkt13AndPps("2018-01-01T10:00:00", 2 * 3600);

        String resp;
        Ranges vals;
        Range r0;

        // first request before the consolidation, should return data from cache

        resp = restClient.doRequest(
                "/archive/IntegrationTest/parameters/REFMDB/SUBSYS1/FloatPara1_1_2/ranges?start=2018-01-01T11:40:00&stop=2018-01-02T12:00:00",
                HttpMethod.GET, "").get();
        vals = (fromJson(resp, Ranges.newBuilder())).build();
        assertEquals(1, vals.getRangeCount());
        r0 = vals.getRange(0);
        assertEquals(1199, r0.getCount());
        assertEquals(0.167291805148, r0.getEngValue().getFloatValue(), 1e-5);
        assertEquals("2018-01-01T11:40:01.000Z", r0.getTimeStart());
        assertEquals("2018-01-01T11:59:59.000Z", r0.getTimeStop());

        buildParameterArchive("2018-01-01T10:00:00", "2018-01-02T11:00:00");
        
        resp = restClient.doRequest(
                "/archive/IntegrationTest/parameters/REFMDB/SUBSYS1/FloatPara1_1_2/ranges?start=2018-01-01T10:00:00&stop=2018-01-02T11:00:00",
                HttpMethod.GET, "").get();
        vals = fromJson(resp, Ranges.newBuilder()).build();
        assertEquals(1, vals.getRangeCount());
        r0 = vals.getRange(0);
        assertEquals(7200, r0.getCount());
        assertEquals(0.167291805148, r0.getEngValue().getFloatValue(), 1e-5);

        
        generatePkt13AndPps("2018-01-01T13:00:00", 3600);
        
        resp = restClient.doRequest(
                "/archive/IntegrationTest/parameters/REFMDB/SUBSYS1/FloatPara1_1_2/ranges?start=2018-01-01T10:00:00&stop=2018-01-02T11:00:00",
                HttpMethod.GET, "").get();
        
        vals = fromJson(resp, Ranges.newBuilder()).build();
        assertEquals(2, vals.getRangeCount());
        r0 = vals.getRange(0);
        assertEquals(7200, r0.getCount());
        
        assertEquals("2018-01-01T10:00:00.000Z", r0.getTimeStart());
        assertEquals("2018-01-01T12:00:01.850Z", r0.getTimeStop()); //last parameter time plus expiration 
        
        Range r1 = vals.getRange(1);
        assertEquals(3600, r1.getCount());
        assertEquals("2018-01-01T13:00:00.000Z", r1.getTimeStart());
        assertEquals("2018-01-01T13:59:59.000Z", r1.getTimeStop());
        
        resp = restClient.doRequest(
                "/archive/IntegrationTest/parameters/REFMDB/SUBSYS1/FloatPara1_1_2/ranges?start=2018-01-01T10:00:00&stop=2018-01-02T11:00:00&minGap=3601001",
                HttpMethod.GET, "").get();
        
        vals = fromJson(resp, Ranges.newBuilder()).build();
        assertEquals(1, vals.getRangeCount());
        r0 = vals.getRange(0);
        assertEquals(7200+3600, r0.getCount());
    }

    private void buildParameterArchive(String start, String stop) throws InterruptedException, ExecutionException {
        ParameterArchive parameterArchiveWrapper = YamcsServer.getService(yamcsInstance, ParameterArchive.class);
        ParameterArchiveV2 parameterArchive = (ParameterArchiveV2) parameterArchiveWrapper.getParchive();
        Future<?> f = parameterArchive.reprocess(TimeEncoding.parse(start),
                TimeEncoding.parse(stop));
        f.get();
    }

    
    
    

}
