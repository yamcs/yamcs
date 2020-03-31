package org.yamcs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import java.util.List;

import org.junit.Test;
import org.yamcs.client.ClientException;
import org.yamcs.client.RestClient;
import org.yamcs.client.UnauthorizedException;
import org.yamcs.client.YamcsConnectionProperties;
import org.yamcs.protobuf.YamcsInstance;

public class RestClientTest extends AbstractIntegrationTest {

    @Test
    public void testGetYamcsInstancesUnauthorized() throws Exception {
        YamcsConnectionProperties ycp = new YamcsConnectionProperties("localhost", 9190);

        RestClient restClient = new RestClient(ycp);
        Throwable e = null;
        try {
            restClient.blockingGetYamcsInstances();
        } catch (ClientException e1) {
            e = e1;
        }
        assertNotNull(e);
        assertSame(UnauthorizedException.class, e.getClass());
    }

    @Test
    public void testGetYamcsInstances() throws Exception {
        YamcsConnectionProperties ycp = new YamcsConnectionProperties("localhost", 9190);
        RestClient restClient = new RestClient(ycp);
        restClient.login(adminUsername, adminPassword);
        List<YamcsInstance> instances = restClient.blockingGetYamcsInstances();

        assertEquals(1, instances.size());
        assertEquals("IntegrationTest", instances.get(0).getName());
    }

    @Test
    public void testGetBulk() throws Exception {
        YamcsConnectionProperties ycp = new YamcsConnectionProperties("localhost", 9190);
        RestClient restClient = new RestClient(ycp);
        restClient.login(adminUsername, adminPassword);
        List<YamcsInstance> instances = restClient.blockingGetYamcsInstances();

        assertEquals(1, instances.size());
        assertEquals("IntegrationTest", instances.get(0).getName());
    }
}
