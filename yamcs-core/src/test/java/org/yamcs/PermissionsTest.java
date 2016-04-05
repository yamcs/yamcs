package org.yamcs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.yamcs.api.ws.WebSocketRequest;
import org.yamcs.protobuf.*;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Rest.BulkGetParameterValueRequest;
import org.yamcs.protobuf.Rest.BulkSetParameterValueRequest;
import org.yamcs.protobuf.Rest.BulkSetParameterValueRequest.SetParameterValueRequest;
import org.yamcs.protobuf.Rest.IssueCommandRequest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.security.UsernamePasswordToken;
import org.yamcs.utils.HttpClient;
import org.yamcs.utils.TimeEncoding;

import io.netty.handler.codec.http.HttpMethod;

import java.io.IOException;
import java.util.List;


public class PermissionsTest extends AbstractIntegrationTest {
          
    @Test
    public void testAuthenticationWebServices() throws Exception {
        UsernamePasswordToken wrongUser = new UsernamePasswordToken("baduser", "wrongpassword");
        currentUser = wrongUser;
        String response = httpClient.doGetRequest("http://localhost:9190/api/user", null, currentUser);
        assertTrue(response.contains("Unauthorized"));
    }

    @Test
    public void testPermissionArchive() throws Exception {

        // testuser is allowed to replay integer parameters but no string parameters
        UsernamePasswordToken testuser = new UsernamePasswordToken("testuser", "password");
        currentUser = testuser;

        // Check that integer parameter replay is ok
        generateData("2015-03-02T10:00:00", 3600);
        String url = "http://localhost:9190/api/archive/IntegrationTest/parameters";
        url += "/REFMDB/SUBSYS1/IntegerPara1_1_6";
        url += "?start=2015-03-02T10:10:00&stop=2015-03-02T10:10:02&order=asc";
        
        String response = httpClient.doGetRequest(url, null, currentUser);
        ParameterData pdata = (fromJson(response, SchemaPvalue.ParameterData.MERGE)).build();
        assertNotNull(pdata);
        assertEquals(2, pdata.getParameterCount());
        ParameterValue pv0 = pdata.getParameter(0);
        assertEquals("2015-03-02T10:10:00.000", pv0.getGenerationTimeUTC());

        // Check that string parameter replay is denied
        boolean gotException = false;
        try {
            String stringId = "/REFMDB/SUBSYS1/FixedStringPara1_3_1";
            url = "http://localhost:9190/api/archive/IntegrationTest";
            url += stringId;
            url += "?start=2015-03-02T10:10:00&stop=2015-03-02T10:10:02&order=asc";
            response = httpClient.doGetRequest(url, null, currentUser);
            pdata = (fromJson(response, SchemaPvalue.ParameterData.MERGE)).build();
            if(pdata.getParameterCount() == 0) {
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
        IssueCommandRequest cmdreq = getCommand(5, "uint32_arg", "1000");
        String resp = httpClient.doRequest("http://localhost:9190/api/processors/IntegrationTest/realtime/commands/REFMDB/SUBSYS1/INT_ARG_TC",
                HttpMethod.POST, toJson(cmdreq, SchemaRest.IssueCommandRequest.WRITE), currentUser);
        assertTrue(resp.contains("binary"));

        // Command FLOAT_ARG_TC is denied
        cmdreq = getCommand(5, "float_arg", "-15", "double_arg", "0");
        resp = httpClient.doRequest("http://localhost:9190/api/processors/IntegrationTest/realtime/commands/REFMDB/SUBSYS1/FLOAT_ARG_TC",
                HttpMethod.POST, toJson(cmdreq, SchemaRest.IssueCommandRequest.WRITE), currentUser);
        assertTrue("Should get 404 when no permission (shouldn't be able to derive existence)", resp.contains("No such command"));
    }

    @Test
    public void testPermissionGetParameter() throws Exception {
        UsernamePasswordToken testuser = new UsernamePasswordToken("testuser", "password");
        currentUser = testuser;

        // Allowed to subscribe to Integer parameter from cache
        NamedObjectList validSubscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara1_1_6", "/REFMDB/SUBSYS1/IntegerPara1_1_7");
        BulkGetParameterValueRequest req = BulkGetParameterValueRequest.newBuilder().setFromCache(true).addAllId(validSubscrList.getListList()).build();
        String response = httpClient.doRequest("http://localhost:9190/api/processors/IntegrationTest/realtime/parameters/mget", HttpMethod.GET, toJson(req, SchemaRest.BulkGetParameterValueRequest.WRITE), currentUser);
        assertTrue("{}", !response.contains("ForbiddenException"));

        // Denied to subscribe to Float parameter from cache
        validSubscrList = getSubscription("/REFMDB/SUBSYS1/FloatPara1_1_3", "/REFMDB/SUBSYS1/FloatPara1_1_2");
        req = BulkGetParameterValueRequest.newBuilder().setFromCache(true).addAllId(validSubscrList.getListList()).build();
        response = httpClient.doRequest("http://localhost:9190/api/processors/IntegrationTest/realtime/parameters/mget", HttpMethod.GET, toJson(req, SchemaRest.BulkGetParameterValueRequest.WRITE), currentUser);
        assertTrue("Permission should be denied", response.contains("ForbiddenException"));
    }

    @Test
    public void testPermissionSetParameter() throws Exception {
        UsernamePasswordToken testuser = new UsernamePasswordToken("operator", "password");
        currentUser = testuser;

        BulkSetParameterValueRequest.Builder bulkPvals = BulkSetParameterValueRequest.newBuilder();
        bulkPvals.addRequest(SetParameterValueRequest.newBuilder()
                .setId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/LocalPara1"))
                .setValue(ValueHelper.newValue(5)));
        HttpClient httpClient = new HttpClient();
        String response = httpClient.doRequest("http://localhost:9190/api/processors/IntegrationTest/realtime/parameters/mset", HttpMethod.POST, toJson(bulkPvals.build(), SchemaRest.BulkSetParameterValueRequest.WRITE), currentUser);
        assertTrue("Permission should be denied", response.contains("ForbiddenException"));
    }

    @Test
    public void testPermissionUpdateCommandHistory() throws Exception {

        // testUser does not have the permission to update the command history
        // operator has the permission

        UsernamePasswordToken testuser = new UsernamePasswordToken("testuser", "password");
        currentUser = testuser;
        String response = updateCommandHistory();
        assertTrue("Permission should be denied", response.contains("ForbiddenException"));

        UsernamePasswordToken operator = new UsernamePasswordToken("operator", "password");
        currentUser = operator;
        String response2 = updateCommandHistory();
        assertTrue("Permission should be granted", !response2.contains("ForbiddenException"));

    }


    private String updateCommandHistory() throws Exception {
        // insert a value in the command history on dummy command id
        Commanding.CommandId commandId = Commanding.CommandId.newBuilder().setSequenceNumber(0).setOrigin("").setGenerationTime(0).build();
        Rest.UpdateCommandHistoryRequest.Builder updateHistoryRequest = Rest.UpdateCommandHistoryRequest.newBuilder().setCmdId(commandId);
        updateHistoryRequest.addHistoryEntry(Rest.UpdateCommandHistoryRequest.KeyValue.newBuilder().setKey("testKey1").setValue("testValue1"));
        return httpClient.doRequest("http://localhost:9190/api/processors/IntegrationTest/realtime/commandhistory/REFMDB/SUBSYS1/ONE_INT_ARG_TC", HttpMethod.POST, toJson(updateHistoryRequest.build(), SchemaRest.UpdateCommandHistoryRequest.WRITE), currentUser);
    }


    private void generateData(String utcStart, int numPackets) {
        long t0 = TimeEncoding.parse(utcStart);
        for (int i=0;i <numPackets; i++) {
        	packetGenerator.setGenerationTime(t0+1000*i);
            packetGenerator.generate_PKT1_1();
            packetGenerator.generate_PKT1_3();
        }
    }
}
