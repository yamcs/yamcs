package org.yamcs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.client.ClientException;
import org.yamcs.client.ClientException.ExceptionData;
import org.yamcs.client.RestClient;
import org.yamcs.client.UnauthorizedException;
import org.yamcs.client.WebSocketRequest;
import org.yamcs.client.YamcsConnectionProperties;
import org.yamcs.protobuf.BatchGetParameterValuesRequest;
import org.yamcs.protobuf.BatchSetParameterValuesRequest;
import org.yamcs.protobuf.BatchSetParameterValuesRequest.SetParameterValueRequest;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.IssueCommandRequest;
import org.yamcs.protobuf.IssueCommandResponse;
import org.yamcs.protobuf.ParameterSubscriptionRequest;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.UpdateCommandHistoryRequest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.ValueHelper;

import io.netty.handler.codec.http.HttpMethod;

public class PermissionsTest extends AbstractIntegrationTest {

    @BeforeClass
    public static void silenceWarnings() {
        // to avoid getting warnings in the test console for invalid permissions
        Logger.getLogger("org.yamcs").setLevel(Level.SEVERE);
    }

    @Test
    public void testAuthenticationWebServices() throws Exception {
        try {
            getRestClient("baduser", "wrongpassword");
            fail("should have thrown an exception");
        } catch (UnauthorizedException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testPermissionArchive() throws Exception {
        // testuser is allowed to replay integer parameters but no string parameters
        RestClient restClient1 = getRestClient("testuser", "password");
        // Check that integer parameter replay is ok
        generatePkt13AndPps("2015-03-02T10:00:00", 3600);
        String resource = "/archive/IntegrationTest/parameters";
        resource += "/REFMDB/SUBSYS1/IntegerPara1_1_6";
        resource += "?start=2015-03-02T10:10:00&stop=2015-03-02T10:10:02&order=asc";

        byte[] response = restClient1.doRequest(resource, HttpMethod.GET).get();
        ParameterData pdata = ParameterData.parseFrom(response);
        assertNotNull(pdata);
        assertEquals(2, pdata.getParameterCount());
        ParameterValue pv0 = pdata.getParameter(0);
        assertEquals("2015-03-02T10:10:00.000Z", pv0.getGenerationTimeUTC());

        // Check that string parameter replay is denied
        boolean gotException = false;
        try {
            String stringId = "/REFMDB/SUBSYS1/FixedStringPara1_3_1";
            resource = "/archive/IntegrationTest";
            resource += stringId;
            resource += "?start=2015-03-02T10:10:00&stop=2015-03-02T10:10:02&order=asc";
            response = restClient1.doRequest(resource, HttpMethod.GET).get();
            pdata = ParameterData.parseFrom(response);
            if (pdata.getParameterCount() == 0) {
                throw new Exception("should get parameters");
            }
        } catch (Exception e) {
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
        byte[] resp = restClient1.doRequest("/processors/IntegrationTest/realtime/commands/REFMDB/SUBSYS1/INT_ARG_TC",
                HttpMethod.POST, cmdreq).get();
        IssueCommandResponse response = IssueCommandResponse.parseFrom(resp);
        assertTrue(response.hasBinary());

        // Command FLOAT_ARG_TC is denied
        cmdreq = getCommand(5, "float_arg", "-15", "double_arg", "0");
        try {
            resp = restClient1.doRequest("/processors/IntegrationTest/realtime/commands/REFMDB/SUBSYS1/FLOAT_ARG_TC",
                    HttpMethod.POST, cmdreq).get();
            fail("should have thrown an exception");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof ClientException);
        }

    }

    @Test
    public void testPermissionGetParameter() throws Exception {
        RestClient restClient1 = getRestClient("testuser", "password");

        // Allowed to subscribe to Integer parameter from cache
        ParameterSubscriptionRequest validSubscrList = getSubscription("/REFMDB/SUBSYS1/IntegerPara1_1_6",
                "/REFMDB/SUBSYS1/IntegerPara1_1_7");
        BatchGetParameterValuesRequest req = BatchGetParameterValuesRequest.newBuilder().setFromCache(true)
                .addAllId(validSubscrList.getIdList()).build();
        restClient1.doRequest("/processors/IntegrationTest/realtime/parameters:batchGet", HttpMethod.POST, req)
                .get();

        // Denied to subscribe to Float parameter from cache
        validSubscrList = getSubscription("/REFMDB/SUBSYS1/FloatPara1_1_3", "/REFMDB/SUBSYS1/FloatPara1_1_2");
        req = BatchGetParameterValuesRequest.newBuilder().setFromCache(true).addAllId(validSubscrList.getIdList())
                .build();
        try {
            restClient1
                    .doRequest("/processors/IntegrationTest/realtime/parameters:batchGet", HttpMethod.POST, req)
                    .get();
            fail("should have thrown an exception");
        } catch (ExecutionException e) {
            ExceptionData excData = ((ClientException) e.getCause()).getDetail();
            assertEquals("ForbiddenException", excData.getType());
        }
        restClient1.close();
    }

    @Test
    public void testPermissionSetParameter() throws Exception {
        RestClient restClient1 = getRestClient("operator", "password");

        BatchSetParameterValuesRequest.Builder bulkPvals = BatchSetParameterValuesRequest.newBuilder();
        bulkPvals.addRequest(SetParameterValueRequest.newBuilder()
                .setId(NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/LocalPara1"))
                .setValue(ValueHelper.newValue(5)));
        try {
            restClient1.doRequest("/processors/IntegrationTest/realtime/parameters:batchSet", HttpMethod.POST,
                    bulkPvals.build()).get();
            fail("should have thrown an exception");
        } catch (ExecutionException e) {
            ClientException e1 = (ClientException) e.getCause();
            ExceptionData excData = e1.getDetail();
            assertEquals("ForbiddenException", excData.getType());
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
            ExceptionData excData = ((ClientException) e.getCause()).getDetail();
            assertEquals("ForbiddenException", excData.getType());
        }
        try {
            updateCommandHistory(getRestClient("operator", "password"));
        } catch (ExecutionException e) {
            ExceptionData excData = ((ClientException) e.getCause()).getDetail();
            assertEquals("ForbiddenException", excData.getType());
        }

    }

    private byte[] updateCommandHistory(RestClient restClient1) throws Exception {
        // insert a value in the command history on dummy command id
        UpdateCommandHistoryRequest.Builder updateHistoryRequest = UpdateCommandHistoryRequest.newBuilder()
                .setId("0-0");
        updateHistoryRequest.addAttributes(CommandHistoryAttribute.newBuilder()
                .setName("testKey1")
                .setValue(Value.newBuilder().setType(Type.STRING).setStringValue("testValue1")));
        return restClient1
                .doRequest("/processors/IntegrationTest/realtime/commandhistory/REFMDB/SUBSYS1/ONE_INT_ARG_TC",
                        HttpMethod.POST, updateHistoryRequest.build())
                .get();
    }

    private RestClient getRestClient(String username, String password) throws ClientException {
        RestClient restClient1 = null;
        try {
            YamcsConnectionProperties ycp1 = ycp.copy();
            restClient1 = new RestClient(ycp1);
            restClient1.login(username, password.toCharArray());
            restClient1.setAutoclose(false);
            return restClient1;
        } catch (ClientException e) {
            restClient1.close();
            throw e;
        }
    }
}
