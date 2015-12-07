package org.yamcs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.yamcs.api.ws.WebSocketRequest;
import org.yamcs.protobuf.Archive.DumpArchiveRequest;
import org.yamcs.protobuf.Archive.DumpArchiveResponse;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Rest.CreateProcessorRequest;
import org.yamcs.protobuf.Rest.EditClientRequest;
import org.yamcs.protobuf.SchemaArchive;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.protobuf.Yamcs.ParameterReplayRequest;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.websocket.ParameterResource;

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
    public void testRetrieveDataFromArchive() throws Exception {
        generateData("2015-01-02T10:00:00", 3600);
        NamedObjectId p1_1_6id = NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_6").build();
        NamedObjectId p1_3_1id = NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/FixedStringPara1_3_1").build();

        ParameterReplayRequest prr = ParameterReplayRequest.newBuilder().addNameFilter(p1_1_6id).addNameFilter(p1_3_1id).build();
        DumpArchiveRequest dumpRequest = DumpArchiveRequest.newBuilder().setParameterRequest(prr)
                .setUtcStart("2015-01-02T10:10:00").setUtcStop("2015-01-02T10:10:02").build();
        String response = httpClient.doGetRequest("http://localhost:9190/api/archive/IntegrationTest", toJson(dumpRequest, SchemaArchive.DumpArchiveRequest.WRITE), currentUser);
        DumpArchiveResponse rdar = (fromJson(response, SchemaArchive.DumpArchiveResponse.MERGE)).build();
        List<ParameterData> plist = rdar.getParameterDataList();
        assertNotNull(plist);
        assertEquals(4, plist.size());
        ParameterValue pv0 = plist.get(0).getParameter(0);
        assertEquals("2015-01-02T10:10:00.000", pv0.getGenerationTimeUTC());
        assertEquals("/REFMDB/SUBSYS1/IntegerPara1_1_6", pv0.getId().getName());
        ParameterValue pv3 = plist.get(3).getParameter(0);
        assertEquals("2015-01-02T10:10:01.000", pv3.getGenerationTimeUTC());
        assertEquals("/REFMDB/SUBSYS1/FixedStringPara1_3_1", pv3.getId().getName());
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

        httpClient.doPostRequest("http://localhost:9190/api/processors/IntegrationTest", toJson(prequest, SchemaRest.CreateProcessorRequest.WRITE), currentUser);

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
        httpClient.doPostRequest("http://localhost:9190/api/clients/" + cinfo.getId(), toJson(pcrequest, SchemaRest.EditClientRequest.WRITE), currentUser);

        cinfo = getClientInfo();
        assertEquals("realtime", cinfo.getProcessorName());
    }

}
