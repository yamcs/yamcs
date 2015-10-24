package org.yamcs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.yamcs.api.ws.WebSocketRequest;
import org.yamcs.protobuf.Archive.DumpArchiveRequest;
import org.yamcs.protobuf.Archive.DumpArchiveResponse;
import org.yamcs.protobuf.Commanding.SendCommandRequest;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Rest.BulkGetParameterValueRequest;
import org.yamcs.protobuf.SchemaArchive;
import org.yamcs.protobuf.SchemaCommanding;
import org.yamcs.protobuf.SchemaPvalue;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.ValueHelper;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.protobuf.Yamcs.ParameterReplayRequest;
import org.yamcs.security.UsernamePasswordToken;
import org.yamcs.utils.HttpClient;
import org.yamcs.utils.TimeEncoding;

import io.netty.handler.codec.http.HttpMethod;


public class PermissionsTest extends AbstractIntegrationTest {
          
    public void testRetrieveDataFromArchive() throws Exception {
        generateData("2016-02-03T10:00:00", 3600);
        NamedObjectId p1_1_6id = NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_6").build();
        NamedObjectId p1_3_1id = NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/FixedStringPara1_3_1").build();

        ParameterReplayRequest prr = ParameterReplayRequest.newBuilder().addNameFilter(p1_1_6id).addNameFilter(p1_3_1id).build();
        DumpArchiveRequest dumpRequest = DumpArchiveRequest.newBuilder().setParameterRequest(prr)
                .setUtcStart("2016-02-03T10:10:00").setUtcStop("2016-02-03T10:10:02").build();
        String response = httpClient.doGetRequest("http://localhost:9190/IntegrationTest/api/archive", toJson(dumpRequest, SchemaArchive.DumpArchiveRequest.WRITE), currentUser);
        DumpArchiveResponse rdar = (fromJson(response, SchemaArchive.DumpArchiveResponse.MERGE)).build();
        List<ParameterData> plist = rdar.getParameterDataList();
        assertNotNull(plist);
        assertEquals(4, plist.size());
        ParameterValue pv0 = plist.get(0).getParameter(0);
        assertEquals("2016-02-03T10:10:00.000", pv0.getGenerationTimeUTC());
        assertEquals("/REFMDB/SUBSYS1/IntegerPara1_1_6", pv0.getId().getName());
        ParameterValue pv3 = plist.get(3).getParameter(0);
        assertEquals("2016-02-03T10:10:01.000", pv3.getGenerationTimeUTC());
        assertEquals("/REFMDB/SUBSYS1/FixedStringPara1_3_1", pv3.getId().getName());
    }


    @Test
    public void testAuthenticationWebServices() throws Exception {
        UsernamePasswordToken wrongUser = new UsernamePasswordToken("baduser", "wrongpassword");
        currentUser = wrongUser;
        boolean gotException = false;
        try {
            testRetrieveDataFromArchive();
        } catch (Exception e) {
            gotException = true;
        }
        assertTrue("replay request should be denied to user", gotException);
    }

    @Test
    public void testPermissionArchive() throws Exception {

        // testuser is allowed to replay integer parameters but no string parameters
        UsernamePasswordToken testuser = new UsernamePasswordToken("testuser", "password");
        currentUser = testuser;

        // Check that integer parameter replay is ok
        generateData("2015-03-02T10:00:00", 3600);
        NamedObjectId p1_1_6id = NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/IntegerPara1_1_6").build();
        ParameterReplayRequest prr = ParameterReplayRequest.newBuilder().addNameFilter(p1_1_6id).build();
        DumpArchiveRequest dumpRequest = DumpArchiveRequest.newBuilder().setParameterRequest(prr)
                .setUtcStart("2015-03-02T10:10:00").setUtcStop("2015-03-02T10:10:02").build();
        String response = httpClient.doGetRequest("http://localhost:9190/IntegrationTest/api/archive", toJson(dumpRequest, SchemaArchive.DumpArchiveRequest.WRITE), currentUser);
        DumpArchiveResponse rdar = (fromJson(response, SchemaArchive.DumpArchiveResponse.MERGE)).build();
        List<ParameterData> plist = rdar.getParameterDataList();
        assertNotNull(plist);
        assertEquals(2, plist.size());
        ParameterValue pv0 = plist.get(0).getParameter(0);
        assertEquals("2015-03-02T10:10:00.000", pv0.getGenerationTimeUTC());
        assertEquals("/REFMDB/SUBSYS1/IntegerPara1_1_6", pv0.getId().getName());

        // Check that string parameter replay is denied
        boolean gotException = false;
        try {
            NamedObjectId p1_3_1id = NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/FixedStringPara1_3_1").build();
            prr = ParameterReplayRequest.newBuilder().addNameFilter(p1_3_1id).build();
            dumpRequest = DumpArchiveRequest.newBuilder().setParameterRequest(prr)
                    .setUtcStart("2015-03-02T10:10:00").setUtcStop("2015-03-02T10:10:02").build();
            response = httpClient.doGetRequest("http://localhost:9190/IntegrationTest/api/archive", toJson(dumpRequest, SchemaArchive.DumpArchiveRequest.WRITE), currentUser);
            rdar = (fromJson(response, SchemaArchive.DumpArchiveResponse.MERGE)).build();
            plist = rdar.getParameterDataList();
            if(plist.size() == 0) {
                throw new Exception("should get parameters");
            }
        }
        catch(Exception e) {
            gotException = true;
        }
        assertTrue("Permission should be denied for String parameter", gotException);
    }


    @Test
    public void testPermissionSendCommand() throws Exception {
        UsernamePasswordToken testuser = new UsernamePasswordToken("testuser", "password");
        currentUser = testuser;

        // Command INT_ARG_TC is allowed
        WebSocketRequest wsr = new WebSocketRequest("cmdhistory", "subscribe");
        wsClient.sendRequest(wsr);
        SendCommandRequest cmdreq = getCommand("/REFMDB/SUBSYS1/INT_ARG_TC", 5, "uint32_arg", "1000");
        String resp = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/commanding/queue", HttpMethod.POST, toJson(cmdreq, SchemaCommanding.SendCommandRequest.WRITE), currentUser);
        assertEquals("", resp);

        // Command FLOAT_ARG_TC is denied
        cmdreq = getCommand("/REFMDB/SUBSYS1/FLOAT_ARG_TC", 5, "float_arg", "-15", "double_arg", "0");
        resp = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/commanding/queue", HttpMethod.POST, toJson(cmdreq, SchemaCommanding.SendCommandRequest.WRITE), currentUser);
        assertTrue("Should get permission exception message", resp.contains("ForbiddenException"));
    }

    @Test
    public void testPermissionGetParameter() throws Exception {
        UsernamePasswordToken testuser = new UsernamePasswordToken("testuser", "password");
        currentUser = testuser;

        // Allowed to subscribe to Integer parameter from cache
        NamedObjectList validSubscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara1_1_6", "/REFMDB/SUBSYS1/IntegerPara1_1_7");
        BulkGetParameterValueRequest req = BulkGetParameterValueRequest.newBuilder().setFromCache(true).addAllId(validSubscrList.getListList()).build();
        String response = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/parameter/_get", HttpMethod.GET, toJson(req, SchemaRest.BulkGetParameterValueRequest.WRITE), currentUser);
        assertTrue("{}", !response.contains("ForbiddenException"));

        // Denied to subscribe to Float parameter from cache
        validSubscrList = getSubscription("/REFMDB/SUBSYS1/FloatPara1_1_3", "/REFMDB/SUBSYS1/FloatPara1_1_2");
        req = BulkGetParameterValueRequest.newBuilder().setFromCache(true).addAllId(validSubscrList.getListList()).build();
        response = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/parameter/_get", HttpMethod.GET, toJson(req, SchemaRest.BulkGetParameterValueRequest.WRITE), currentUser);
        assertTrue("Permission should be denied", response.contains("ForbiddenException"));
    }

    @Test
    public void testPermissionSetParameter() throws Exception {
        UsernamePasswordToken testuser = new UsernamePasswordToken("operator", "password");
        currentUser = testuser;

        org.yamcs.protobuf.Pvalue.ParameterValue pv1 = ParameterValue.newBuilder()
                .setId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/LocalPara1"))
                .setEngValue(ValueHelper.newValue(5)).build();
        ParameterData pdata = ParameterData.newBuilder().addParameter(pv1).build();
        HttpClient httpClient = new HttpClient();
        String response = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/parameter/_set", HttpMethod.POST, toJson(pdata, SchemaPvalue.ParameterData.WRITE), currentUser);
        assertTrue("Permission should be denied", response.contains("ForbiddenException"));
    }




    private void generateData(String utcStart, int numPackets) {
        long t0 = TimeEncoding.parse(utcStart);
        for (int i=0;i <numPackets; i++) {
        	packetGenerator.setGenerationTime(t0+1000*i);
            packetGenerator.generate_PKT1_1();
            packetGenerator.generate_PKT1_3();
        }
    }


    
    private NamedObjectList getSubscription(String... pfqname) {
        NamedObjectList.Builder b = NamedObjectList.newBuilder();
        for(String p: pfqname) {
            b.addList(NamedObjectId.newBuilder().setName(p).build());
        }
        return b.build();
    }
}
