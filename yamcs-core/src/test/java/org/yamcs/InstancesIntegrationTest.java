package org.yamcs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;
import org.yamcs.protobuf.Rest.CreateInstanceRequest;
import org.yamcs.protobuf.Rest.ListInstancesResponse;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance.InstanceState;

import io.netty.handler.codec.http.HttpMethod;

public class InstancesIntegrationTest extends AbstractIntegrationTest {

    @Test
    public void testStopStart() throws Exception {
        String resp = restClient.doRequest("/instances", HttpMethod.GET, "").get();
        ListInstancesResponse lir = fromJson(resp, ListInstancesResponse.newBuilder()).build();
        assertEquals(1, lir.getInstanceCount());
        YamcsInstance yi = lir.getInstance(0);
        assertEquals("IntegrationTest", yi.getName());
        assertEquals(InstanceState.RUNNING, yi.getState());

        resp = restClient.doRequest("/instances/IntegrationTest?state=STOPPED", HttpMethod.POST, "").get();
        resp = restClient.doRequest("/instances", HttpMethod.GET, "").get();
        lir = fromJson(resp, ListInstancesResponse.newBuilder()).build();
        assertEquals(1, lir.getInstanceCount());
        yi = lir.getInstance(0);
        assertEquals("IntegrationTest", yi.getName());
        assertEquals(InstanceState.OFFLINE, yi.getState());

        resp = restClient.doRequest("/instances/IntegrationTest?state=RUNNING", HttpMethod.POST, "").get();
        resp = restClient.doRequest("/instances", HttpMethod.GET, "").get();
        lir = fromJson(resp, ListInstancesResponse.newBuilder()).build();
        assertEquals(1, lir.getInstanceCount());
        yi = lir.getInstance(0);
        assertEquals("IntegrationTest", yi.getName());
        assertEquals(InstanceState.RUNNING, yi.getState());
    }

    @Test
    public void testCreateStop() throws Exception {
        CreateInstanceRequest cir = CreateInstanceRequest.newBuilder().setName("inst-test1").setTemplate("templ1")
                .putLabels("tag1", "tagValue1").putLabels("tag2", "tagValue2").build();

        String resp = restClient.doRequest("/instances", HttpMethod.POST, toJson(cir)).get();
        assertTrue(new File("/tmp/yamcs-IntegrationTest-data/instance-def/yamcs.inst-test1.yaml").exists());
        assertTrue(new File("/tmp/yamcs-IntegrationTest-data/instance-def/yamcs.inst-test1.tags").exists());
        YamcsInstance yi = fromJson(resp, YamcsInstance.newBuilder()).build();
        assertEquals(InstanceState.RUNNING, yi.getState());

        resp = restClient.doRequest("/instances/inst-test1?state=STOPPED", HttpMethod.POST, "").get();
        yi = fromJson(resp, YamcsInstance.newBuilder()).build();
        assertEquals(InstanceState.OFFLINE, yi.getState());

        assertFalse(new File("/tmp/yamcs-IntegrationTest-data/instance-def/yamcs.inst-test1.yaml").exists());
        assertTrue(new File("/tmp/yamcs-IntegrationTest-data/instance-def/yamcs.inst-test1.yaml.offline").exists());

        resp = restClient.doRequest("/instances?filter=label:tag1%3DtagValue1", HttpMethod.GET, "").get();
        ListInstancesResponse lir = fromJson(resp, ListInstancesResponse.newBuilder()).build();
        assertEquals(1, lir.getInstanceCount());
        yi = lir.getInstance(0);
        assertEquals("inst-test1", yi.getName());
        assertEquals(InstanceState.OFFLINE, yi.getState());

        resp = restClient
                .doRequest("/instances?filter=label:tag1%3DtagValue1&filter=state%3Drunning", HttpMethod.GET, "")
                .get();
        lir = fromJson(resp, ListInstancesResponse.newBuilder()).build();
        assertEquals(0, lir.getInstanceCount());

        resp = restClient.doRequest("/instances?filter=state!%3Doffline", HttpMethod.GET, "").get();
        lir = fromJson(resp, ListInstancesResponse.newBuilder()).build();
        assertEquals(1, lir.getInstanceCount());
        yi = lir.getInstance(0);
        assertEquals("IntegrationTest", yi.getName());
    }

}
