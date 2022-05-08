package org.yamcs.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.client.ClientException;
import org.yamcs.client.ClientException.ExceptionData;
import org.yamcs.client.Page;
import org.yamcs.client.UnauthorizedException;
import org.yamcs.client.archive.ArchiveClient;
import org.yamcs.client.archive.ArchiveClient.ListOptions;
import org.yamcs.client.processor.ProcessorClient;
import org.yamcs.client.processor.ProcessorClient.GetOptions;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.ValueHelper;

import com.google.protobuf.util.Timestamps;

public class PermissionsTest extends AbstractIntegrationTest {

    private ProcessorClient processorClient;
    private ArchiveClient archiveClient;

    @BeforeAll
    public static void silenceWarnings() {
        // to avoid getting warnings in the test console for invalid permissions
        Logger.getLogger("org.yamcs").setLevel(Level.SEVERE);
    }

    @BeforeEach
    public void prepare() {
        processorClient = yamcsClient.createProcessorClient(yamcsInstance, "realtime");
        archiveClient = yamcsClient.createArchiveClient(yamcsInstance);
    }

    @Test
    public void testAuthenticationWebServices() {
        assertThrows(UnauthorizedException.class, () -> {
            yamcsClient.login("baduser", "wrongpassword".toCharArray());
        });
    }

    @Test
    public void testPermissionArchive() throws Exception {
        // testuser is allowed to replay integer parameters but no string parameters
        yamcsClient.login("testuser", "password".toCharArray());

        // Check that integer parameter replay is ok
        generatePkt13AndPps("2015-03-02T10:00:00Z", 3600);

        Instant start = Instant.parse("2015-03-02T10:10:00Z");
        Instant stop = Instant.parse("2015-03-02T10:10:02Z");
        Page<ParameterValue> page = archiveClient.listValues(
                "/REFMDB/SUBSYS1/IntegerPara1_1_6", start, stop,
                ListOptions.ascending(true)).get();

        List<ParameterValue> values = new ArrayList<>();
        page.iterator().forEachRemaining(values::add);

        assertEquals(2, values.size());
        ParameterValue pv0 = values.get(0);
        assertEquals(Timestamps.parse("2015-03-02T10:10:00.000Z"), pv0.getGenerationTime());

        // Check that string parameter replay is denied
        try {
            page = archiveClient.listValues(
                    "/REFMDB/SUBSYS1/FixedStringPara1_3_1", start, stop).get();
            fail("Should generate an exception");
        } catch (ExecutionException e) {
            ClientException clientException = (ClientException) e.getCause();
            assertTrue(clientException.getMessage().contains("Insufficient"));
        }
    }

    @Test
    public void testPermissionGetParameter() throws Exception {
        yamcsClient.login("testuser", "password".toCharArray());

        // Allowed to get Integer parameter from cache
        processorClient.getValues(Arrays.asList(
                "/REFMDB/SUBSYS1/IntegerPara1_1_6",
                "/REFMDB/SUBSYS1/IntegerPara1_1_7"),
                GetOptions.fromCache(true))
                .get();

        // Denied to get Float parameter from cache
        try {
            processorClient.getValues(Arrays.asList(
                    "/REFMDB/SUBSYS1/FloatPara1_1_3",
                    "/REFMDB/SUBSYS1/FloatPara1_1_2"),
                    GetOptions.fromCache(true))
                    .get();
            fail("should have thrown an exception");
        } catch (ExecutionException e) {
            ExceptionData excData = ((ClientException) e.getCause()).getDetail();
            assertEquals("ForbiddenException", excData.getType());
        }
    }

    @Test
    public void testPermissionSetParameter() throws Exception {
        yamcsClient.login("operator", "password".toCharArray());
        try {
            processorClient.setValue("/REFMDB/SUBSYS1/LocalPara1", ValueHelper.newValue(5)).get();
            fail("should have thrown an exception");
        } catch (ExecutionException e) {
            ClientException e1 = (ClientException) e.getCause();
            ExceptionData excData = e1.getDetail();
            assertEquals("ForbiddenException", excData.getType());
        }
    }

    @Test
    public void testPermissionUpdateCommandHistory() throws Exception {
        // testUser does not have the permission to update the command history
        // operator has the permission

        yamcsClient.login("testuser", "password".toCharArray());
        try {
            processorClient.updateCommand("/REFMDB/SUBSYS1/ONE_INT_ARG_TC", "0-0",
                    "testKey1",
                    Value.newBuilder().setType(Type.STRING).setStringValue("testValue1").build())
                    .get();
            fail("Should have thrown an exception");
        } catch (ExecutionException e) {
            ExceptionData excData = ((ClientException) e.getCause()).getDetail();
            assertEquals("ForbiddenException", excData.getType());
        }

        yamcsClient.login("operator", "password".toCharArray());
        processorClient.updateCommand("/REFMDB/SUBSYS1/ONE_INT_ARG_TC", "0-0",
                "testKey1",
                Value.newBuilder().setType(Type.STRING).setStringValue("testValue1").build())
                .get();
    }
}
