package org.yamcs;

import static org.junit.Assert.*;
import io.netty.handler.codec.http.HttpMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.api.rest.BulkRestDataReceiver;
import org.yamcs.api.rest.RestClient;
import org.yamcs.api.ws.WebSocketRequest;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Rest.CreateProcessorRequest;
import org.yamcs.protobuf.Rest.EditClientRequest;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.Yamcs.ArchiveRecord;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.websocket.ParameterResource;

import com.google.protobuf.InvalidProtocolBufferException;


public class IntegrationTestArchive extends AbstractIntegrationTest {
    

    private void generateData(String utcStart, int numPackets) {
        long t0 = TimeEncoding.parse(utcStart);
        for (int i=0;i <numPackets; i++) {
            packetGenerator.setGenerationTime(t0+1000*i);
            packetGenerator.generate_PKT1_1();
            packetGenerator.generate_PKT1_3();
        }
    }

    @Test
    public void testReplay() throws Exception {
        generateData("2015-01-01T10:00:00", 3600);
        ClientInfo cinfo = getClientInfo();
        //create a parameter replay via REST
        CreateProcessorRequest prequest = CreateProcessorRequest.newBuilder()
                .addClientId(cinfo.getId())
                .setName("testReplay")
                .setStart("2015-01-01T10:01:00")
                .setStop("2015-01-01T10:05:00")
                .addPacketname("*")
                .build();

        restClient.doRequest("http://localhost:9190/api/processors/IntegrationTest", HttpMethod.POST, toJson(prequest, SchemaRest.CreateProcessorRequest.WRITE));

        cinfo = getClientInfo();
        assertEquals("testReplay", cinfo.getProcessorName());

        NamedObjectList subscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara1_1_7", "/REFMDB/SUBSYS1/IntegerPara1_1_6");
        WebSocketRequest wsr = new WebSocketRequest("parameter",ParameterResource.WSR_subscribe, subscrList);
        wsClient.sendRequest(wsr);

        ParameterData pdata = wsListener.parameterDataList.poll(2, TimeUnit.SECONDS);
        assertNotNull(pdata);

        assertEquals(2, pdata.getParameterCount());
        ParameterValue p1_1_6 = pdata.getParameter(0);
        assertEquals("2015-01-01T10:01:00.000", p1_1_6.getGenerationTimeUTC());

        pdata = wsListener.parameterDataList.poll(2, TimeUnit.SECONDS);
        assertNotNull(pdata);

        assertEquals(2, pdata.getParameterCount());
        p1_1_6 = pdata.getParameter(0);
        assertEquals("2015-01-01T10:01:01.000", p1_1_6.getGenerationTimeUTC());

        //go back to realtime
        EditClientRequest pcrequest = EditClientRequest.newBuilder().setProcessor("realtime").build();
        restClient.doRequest("http://localhost:9190/api/clients/" + cinfo.getId(), HttpMethod.GET, toJson(pcrequest, SchemaRest.EditClientRequest.WRITE)).get();

        cinfo = getClientInfo();
        assertEquals("realtime", cinfo.getProcessorName());
    }
    
    @Test
    public void testIndex() throws Exception {
        generateData("2015-01-01T10:00:00", 3600);
       
        String response ;
        
        response = restClient.doRequest("http://localhost:9190/api/archive/IntegrationTest/indexes/packets?start=2015-01-01T00:00:00", HttpMethod.GET, "").get();
        List<ArchiveRecord> arlist = allFromJson(response, org.yamcs.protobuf.SchemaYamcs.ArchiveRecord.MERGE);
        assertEquals(4, arlist.size());
        
        response = restClient.doRequest("http://localhost:9190/api/archive/IntegrationTest/indexes/packets?start=2035-01-01T00:00:00", HttpMethod.GET, "").get();
        assertTrue(response.isEmpty());
    }

    
    @Test
    public void testIndexWithRestClient() throws Exception {
        generateData("2015-02-01T10:00:00", 3600);
        List<ArchiveRecord> arlist = new ArrayList<ArchiveRecord>();
        
        CompletableFuture<Void> f = restClient.doBulkGetRequest("/archive/IntegrationTest/indexes/packets?start=2015-02-01T00:00:00", new BulkRestDataReceiver() {
            
            @Override
            public void receiveException(Throwable t) {
                fail(t.getMessage());
            }
            
            @Override
            public void receiveData(byte[] data) throws YamcsApiException {
                try {
                    arlist.add(ArchiveRecord.parseFrom(data));
                } catch (InvalidProtocolBufferException e) {
                    throw new YamcsApiException("Cannot decode ArchiveRecord: "+e.getMessage(), e);
                }
            }
        });
        
        f.get();
        assertEquals(4, arlist.size());
    }

}
