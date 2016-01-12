package org.yamcs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.yamcs.api.ws.WebSocketRequest;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Rest.CreateProcessorRequest;
import org.yamcs.protobuf.Rest.EditClientRequest;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
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
