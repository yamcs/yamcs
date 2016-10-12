package org.yamcs;

import static org.junit.Assert.*;

import java.util.concurrent.ExecutionException;

import org.junit.Test;
import org.yamcs.api.MediaType;
import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.api.rest.RestClient;
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
import org.yamcs.utils.TimeEncoding;

import io.netty.handler.codec.http.HttpMethod;



public class PermissionsTest extends AbstractIntegrationTest {

    @Test
    public void testAuthenticationWebServices() throws Exception {
        RestClient restClient1 = getRestClient("baduser", "wrongpassword");
        try {
            restClient1.doRequest("/user", HttpMethod.GET, "").get();
            fail("should have thrown an exception");
        } catch (ExecutionException e) {
            assertTrue(e.getCause().getMessage().contains("Unauthorized"));
        }

        restClient1.close();
    }

    @Test
    public void testPermissionArchive() throws Exception {
        // testuser is allowed to replay integer parameters but no string parameters
        RestClient restClient1 = getRestClient("testuser", "password");
        // Check that integer parameter replay is ok
        generateData("2015-03-02T10:00:00", 3600);
        String resource = "/archive/IntegrationTest/parameters";
        resource += "/REFMDB/SUBSYS1/IntegerPara1_1_6";
        resource += "?start=2015-03-02T10:10:00&stop=2015-03-02T10:10:02&order=asc";

        String response = restClient1.doRequest(resource, HttpMethod.GET, "").get();
        ParameterData pdata = (fromJson(response, SchemaPvalue.ParameterData.MERGE)).build();
        assertNotNull(pdata);
        assertEquals(2, pdata.getParameterCount());
        ParameterValue pv0 = pdata.getParameter(0);
        assertEquals("2015-03-02T10:10:00.000", pv0.getGenerationTimeUTC());

        // Check that string parameter replay is denied
        boolean gotException = false;
        try {
            String stringId = "/REFMDB/SUBSYS1/FixedStringPara1_3_1";
            resource = "/archive/IntegrationTest";
            resource += stringId;
            resource += "?start=2015-03-02T10:10:00&stop=2015-03-02T10:10:02&order=asc";
            response = restClient1.doRequest(resource, HttpMethod.GET, "").get();
            pdata = (fromJson(response, SchemaPvalue.ParameterData.MERGE)).build();
            if(pdata.getParameterCount() == 0) {
                throw new Exception("should get parameters");
            }
        }
        catch(Exception e) {
            gotException = true;
        }
        assertTrue("Permission should be denied for String parameter", gotException);
        restClient1.close();
    }


    @Test
    public void testPermissionSendCommand() throws Exception {
        RestClient restClient1 = getRestClient("testuser", "password");

        // Command INT_ARG_TC is allowed
        WebSocketRequest wsr = new WebSocketRequest("cmdhistory", "subscribe");
        wsClient.sendRequest(wsr);
        IssueCommandRequest cmdreq = getCommand(5, "uint32_arg", "1000");
        String resp = restClient1.doRequest("/processors/IntegrationTest/realtime/commands/REFMDB/SUBSYS1/INT_ARG_TC",
                HttpMethod.POST, toJson(cmdreq, SchemaRest.IssueCommandRequest.WRITE)).get();
        assertTrue(resp.contains("binary"));

        // Command FLOAT_ARG_TC is denied
        cmdreq = getCommand(5, "float_arg", "-15", "double_arg", "0");
        try {
            resp = restClient1.doRequest("/processors/IntegrationTest/realtime/commands/REFMDB/SUBSYS1/FLOAT_ARG_TC",
                    HttpMethod.POST, toJson(cmdreq, SchemaRest.IssueCommandRequest.WRITE)).get();
            fail("should have thrown an exception");
        } catch (ExecutionException e) {
            assertTrue(e.getCause().getMessage().contains("No such command"));
        }

    }

    @Test
    public void testPermissionGetParameter() throws Exception {
        RestClient restClient1 = getRestClient("testuser", "password");

        // Allowed to subscribe to Integer parameter from cache
        NamedObjectList validSubscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara1_1_6", "/REFMDB/SUBSYS1/IntegerPara1_1_7");
        BulkGetParameterValueRequest req = BulkGetParameterValueRequest.newBuilder().setFromCache(true).addAllId(validSubscrList.getListList()).build();
        restClient1.doRequest("/processors/IntegrationTest/realtime/parameters/mget", HttpMethod.GET, toJson(req, SchemaRest.BulkGetParameterValueRequest.WRITE)).get();
        
        // Denied to subscribe to Float parameter from cache
        validSubscrList = getSubscription("/REFMDB/SUBSYS1/FloatPara1_1_3", "/REFMDB/SUBSYS1/FloatPara1_1_2");
        req = BulkGetParameterValueRequest.newBuilder().setFromCache(true).addAllId(validSubscrList.getListList()).build();
        try {
            restClient1.doRequest("/processors/IntegrationTest/realtime/parameters/mget", HttpMethod.GET, toJson(req, SchemaRest.BulkGetParameterValueRequest.WRITE)).get();
            fail("should have thrown an exception");
        } catch (ExecutionException e) {
            assertTrue(e.getCause().getMessage().contains("ForbiddenException"));
        }
        restClient1.close();
    }

    @Test
    public void testPermissionSetParameter() throws Exception {
        RestClient restClient1 = getRestClient("operator", "password");

        BulkSetParameterValueRequest.Builder bulkPvals = BulkSetParameterValueRequest.newBuilder();
        bulkPvals.addRequest(SetParameterValueRequest.newBuilder()
                .setId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/LocalPara1"))
                .setValue(ValueHelper.newValue(5)));
        try {
            restClient1.doRequest("/processors/IntegrationTest/realtime/parameters/mset", HttpMethod.POST, toJson(bulkPvals.build(), SchemaRest.BulkSetParameterValueRequest.WRITE)).get();
            fail("should have thrown an exception");
        } catch (ExecutionException e) {
            assertTrue(e.getCause().getMessage().contains("ForbiddenException"));
        }
        restClient1.close();
    }

    @Test
    public void testPermissionUpdateCommandHistory() throws Exception {
        // testUser does not have the permission to update the command history
        // operator has the permission
        
        try {
            updateCommandHistory(getRestClient("testuser", "password"));
        } catch (ExecutionException e) {
            assertTrue(e.getCause().getMessage().contains("ForbiddenException"));
        }
        try {
            updateCommandHistory(getRestClient("operator", "password"));
        } catch (ExecutionException e) {
            assertTrue(e.getCause().getMessage().contains("ForbiddenException"));
        }

    }


    private String updateCommandHistory(RestClient restClient1) throws Exception {
        // insert a value in the command history on dummy command id
        Commanding.CommandId commandId = Commanding.CommandId.newBuilder().setSequenceNumber(0).setOrigin("").setGenerationTime(0).build();
        Rest.UpdateCommandHistoryRequest.Builder updateHistoryRequest = Rest.UpdateCommandHistoryRequest.newBuilder().setCmdId(commandId);
        updateHistoryRequest.addHistoryEntry(Rest.UpdateCommandHistoryRequest.KeyValue.newBuilder().setKey("testKey1").setValue("testValue1"));
        return restClient1.doRequest("/processors/IntegrationTest/realtime/commandhistory/REFMDB/SUBSYS1/ONE_INT_ARG_TC", HttpMethod.POST, toJson(updateHistoryRequest.build(), SchemaRest.UpdateCommandHistoryRequest.WRITE)).get();
    }


    private void generateData(String utcStart, int numPackets) {
        long t0 = TimeEncoding.parse(utcStart);
        for (int i=0;i <numPackets; i++) {
            packetGenerator.setGenerationTime(t0+1000*i);
            packetGenerator.generate_PKT1_1();
            packetGenerator.generate_PKT1_3();
        }
    }


    private RestClient getRestClient(String username, String password) {
        YamcsConnectionProperties ycp1 = ycp.clone();

        ycp1.setAuthenticationToken(new UsernamePasswordToken(username, password));
        RestClient restClient1 = new RestClient(ycp1);
        restClient1.setAcceptMediaType(MediaType.JSON);
        restClient1.setSendMediaType(MediaType.JSON);
        restClient1.setAutoclose(false);
        return restClient1;

    }
}
