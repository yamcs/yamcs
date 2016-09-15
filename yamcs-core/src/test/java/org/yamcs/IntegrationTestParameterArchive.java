package org.yamcs;

import static org.junit.Assert.assertEquals;
import io.netty.handler.codec.http.HttpMethod;

import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Ignore;
import org.junit.Test;
import org.yamcs.parameterarchive.ParameterArchive;
import org.yamcs.parameterarchive.ParameterGroupIdDb;
import org.yamcs.parameterarchive.ParameterIdDb;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.TimeSeries;
import org.yamcs.protobuf.Pvalue.TimeSeries.Sample;
import org.yamcs.protobuf.SchemaPvalue;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.utils.TimeEncoding;

public class IntegrationTestParameterArchive extends AbstractIntegrationTest {
    
    private void generateData(String utcStart, int numPackets) {
        long t0 = TimeEncoding.parse(utcStart);
        for (int i=0;i <numPackets; i++) {
            packetGenerator.setGenerationTime(t0+1000*i);
            packetGenerator.generate_PKT1_1();
            packetGenerator.generate_PKT1_3();
        }
    }
    
    
    @Test
    @Ignore
    public void testReplayFillup1() throws Exception {
        Logger.getLogger("org.yamcs").setLevel(Level.INFO);
        Logger.getLogger("org.yamcs.parameterarchive").setLevel(Level.ALL);
        generateData("2015-01-02T10:00:00", 30*24*3600);
        ParameterArchive parameterArchive = YamcsServer.getService(yamcsInstance, ParameterArchive.class);
        Future<?> f = parameterArchive.reprocess(TimeEncoding.parse("2015-01-02T10:00:00"), TimeEncoding.parse("2016-02-03T11:00:00"));
        f.get();
        ParameterIdDb pdb = parameterArchive.getParameterIdDb();
        ParameterGroupIdDb pgdb = parameterArchive.getParameterGroupIdDb();
        pdb.print(System.out);
        pgdb.print(System.out);
        
        parameterArchive.printKeys(System.out);
    }
    
    @Test
    public void testRestRetrieval() throws Exception {
      //  Logger.getLogger("org.yamcs").setLevel(Level.INFO);
        Logger.getLogger("org.yamcs.parameterarchive").setLevel(Level.ALL);
        generateData("2015-01-02T10:00:00", 2*3600);
        
        String resp;
        Value engValue;
        ParameterData pdata;
        org.yamcs.protobuf.Pvalue.ParameterValue pv;
        TimeSeries vals;
        Sample s0;
        
        //first two requests before the consolidation, should return data from cache
        resp = restClient.doRequest("http://localhost:9190/api/archive/IntegrationTest/parameters2/REFMDB/SUBSYS1/FloatPara1_1_2?start=2015-01-02T10:00:00&stop=2015-01-02T11:00:00", HttpMethod.GET, "").get();
        pdata = fromJson(resp, SchemaPvalue.ParameterData.MERGE).build();
        assertEquals(100, pdata.getParameterCount());
        engValue = pdata.getParameter(0).getEngValue();
        assertEquals(0.167291805148, engValue.getFloatValue(), 1e-5);
        
        
        resp = restClient.doRequest("http://localhost:9190/api/archive/IntegrationTest/parameters2/REFMDB/SUBSYS1/FloatPara1_1_2/samples?start=2015-01-02T11:40:00&stop=2015-01-02T12:00:00", HttpMethod.GET, "").get();
        vals = (fromJson(resp, SchemaPvalue.TimeSeries.MERGE)).build();
        assertEquals(500, vals.getSampleCount());
        s0 = vals.getSample(0);
        assertEquals(0.167291805148, s0.getMin(), 1e-5);
        assertEquals(0.167291805148, s0.getMax(), 1e-5);
        assertEquals(0.167291805148, s0.getAvg(), 1e-5);
        
        
        ParameterArchive parameterArchive = YamcsServer.getService(yamcsInstance, ParameterArchive.class);
        Future<?> f = parameterArchive.reprocess(TimeEncoding.parse("2015-01-02T10:00:00"), TimeEncoding.parse("2016-01-02T11:00:00"));
        f.get();
        //parameterArchive.printKeys(System.out);
       
        
        
        resp = restClient.doRequest("http://localhost:9190/api/archive/IntegrationTest/parameters2/REFMDB/SUBSYS1/FloatPara1_1_2/samples?start=2015-01-02T10:00:00&stop=2015-01-02T11:00:00", HttpMethod.GET, "").get();
        vals = (fromJson(resp, SchemaPvalue.TimeSeries.MERGE)).build();
        assertEquals(500, vals.getSampleCount());
        s0 = vals.getSample(0);
        assertEquals(0.167291805148, s0.getMin(), 1e-5);
        assertEquals(0.167291805148, s0.getMax(), 1e-5);
        assertEquals(0.167291805148, s0.getAvg(), 1e-5);
        
      
        resp = restClient.doRequest("http://localhost:9190/api/archive/IntegrationTest/parameters2/REFMDB/SUBSYS1/FloatPara1_1_2?start=2015-01-02T10:00:00&stop=2015-01-02T11:00:00", HttpMethod.GET, "").get();
        pdata = fromJson(resp, SchemaPvalue.ParameterData.MERGE).build();
        assertEquals(100, pdata.getParameterCount());
        engValue = pdata.getParameter(0).getEngValue();
        assertEquals(0.167291805148, engValue.getFloatValue(), 1e-5);
        
        resp = restClient.doRequest("http://localhost:9190/api/archive/IntegrationTest/parameters2/REFMDB/SUBSYS1/FloatPara1_1_2?start=2015-01-02T10:00:00&stop=2015-01-02T11:00:00&limit=10", HttpMethod.GET, "").get();
        pdata = fromJson(resp, SchemaPvalue.ParameterData.MERGE).build();
        assertEquals(10, pdata.getParameterCount());
        
        
        resp = restClient.doRequest("http://localhost:9190/api/archive/IntegrationTest/parameters2/REFMDB/SUBSYS1/FloatPara1_1_2?start=2015-01-02T10:00:00&stop=2015-01-02T11:00:00&norepeat", HttpMethod.GET, "").get();
        pdata = fromJson(resp, SchemaPvalue.ParameterData.MERGE).build();
        
        assertEquals(1, pdata.getParameterCount());
        pv = pdata.getParameter(0);
        
        assertEquals("2015-01-02T11:00:00.000", TimeEncoding.toString(pv.getGenerationTime()));
        assertEquals(0.167291805148, pv.getEngValue().getFloatValue(), 1e-5);
        AcquisitionStatus acqs = pdata.getParameter(0).getAcquisitionStatus();
        assertEquals(AcquisitionStatus.ACQUIRED, acqs);
        
        
        //add some realtime data
        generateData("2015-01-02T12:00:00", 10);
        
        resp = restClient.doRequest("http://localhost:9190/api/archive/IntegrationTest/parameters2/REFMDB/SUBSYS1/FloatPara1_1_2?start=2015-01-02T11:59:00&limit=20", HttpMethod.GET, "").get();
        pdata = fromJson(resp, SchemaPvalue.ParameterData.MERGE).build();
        assertEquals(20, pdata.getParameterCount());
        long t = TimeEncoding.parse("2015-01-02T12:00:09.000");
        for(int i=0; i<pdata.getParameterCount(); i++) {
            pv = pdata.getParameter(i);
            assertEquals(TimeEncoding.toString(t), TimeEncoding.toString(pv.getGenerationTime()));
            t-=1000;
        }

        
        
        resp = restClient.doRequest("http://localhost:9190/api/archive/IntegrationTest/parameters2/REFMDB/SUBSYS1/FloatPara1_1_2?start=2015-01-02T12:00:00", HttpMethod.GET, "").get();
        pdata = fromJson(resp, SchemaPvalue.ParameterData.MERGE).build();
        assertEquals(9, pdata.getParameterCount());
        t = TimeEncoding.parse("2015-01-02T12:00:09.000");
        for(int i=0; i<pdata.getParameterCount(); i++) {
            pv = pdata.getParameter(i);
            assertEquals(TimeEncoding.toString(t), TimeEncoding.toString(pv.getGenerationTime()));
            t-=1000;
        }
        
        //request excluding realtime cache
        resp = restClient.doRequest("http://localhost:9190/api/archive/IntegrationTest/parameters2/REFMDB/SUBSYS1/FloatPara1_1_2?start=2015-01-02T12:00:00&norealtime", HttpMethod.GET, "").get();
        pdata = fromJson(resp, SchemaPvalue.ParameterData.MERGE).build();
        assertEquals(0, pdata.getParameterCount());
        
        
        //ascending request combining archive with cache
        
        resp = restClient.doRequest("http://localhost:9190/api/archive/IntegrationTest/parameters2/REFMDB/SUBSYS1/FloatPara1_1_2?start=2015-01-02T11:59:50&order=asc", HttpMethod.GET, "").get();
        pdata = fromJson(resp, SchemaPvalue.ParameterData.MERGE).build();
        assertEquals(20, pdata.getParameterCount());
        t = TimeEncoding.parse("2015-01-02T11:59:50");
        for(int i=0; i<pdata.getParameterCount(); i++) {
            pv = pdata.getParameter(i);
            assertEquals(TimeEncoding.toString(t), TimeEncoding.toString(pv.getGenerationTime()));
            t+=1000;
        }
    }

}
