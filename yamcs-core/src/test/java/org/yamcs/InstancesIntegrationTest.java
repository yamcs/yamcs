package org.yamcs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;
import org.yamcs.protobuf.CreateInstanceRequest;
import org.yamcs.protobuf.ListInstancesResponse;
import org.yamcs.protobuf.YamcsInstance;
import org.yamcs.protobuf.YamcsInstance.InstanceState;

import io.netty.handler.codec.http.HttpMethod;

public class InstancesIntegrationTest extends AbstractIntegrationTest {

    @Test
    public void testStopStart() throws Exception {
        String resp = restClient.doRequest("/instances", HttpMethod.GET, "").get();
        ListInstancesResponse lir = fromJson(resp, ListInstancesResponse.newBuilder()).build();
        assertEquals(1, lir.getInstancesCount());
        YamcsInstance yi = lir.getInstances(0);
        assertEquals("IntegrationTest", yi.getName());
        assertEquals(InstanceState.RUNNING, yi.getState());

        resp = restClient.doRequest("/instances/IntegrationTest:stop", HttpMethod.POST, "").get();
        resp = restClient.doRequest("/instances", HttpMethod.GET, "").get();
        lir = fromJson(resp, ListInstancesResponse.newBuilder()).build();
        assertEquals(1, lir.getInstancesCount());
        yi = lir.getInstances(0);
        assertEquals("IntegrationTest", yi.getName());
        assertEquals(InstanceState.OFFLINE, yi.getState());

        resp = restClient.doRequest("/instances/IntegrationTest:start", HttpMethod.POST, "").get();
        resp = restClient.doRequest("/instances", HttpMethod.GET, "").get();
        lir = fromJson(resp, ListInstancesResponse.newBuilder()).build();
        assertEquals(1, lir.getInstancesCount());
        yi = lir.getInstances(0);
        assertEquals("IntegrationTest", yi.getName());
        assertEquals(InstanceState.RUNNING, yi.getState());
    }

    @Test
    public void testCreateStop() throws Exception {
        CreateInstanceRequest cir = CreateInstanceRequest.newBuilder().setName("inst-test1").setTemplate("templ1")
                .putLabels("label1", "labelValue1").putLabels("label2", "labelValue2").build();

        String resp = restClient.doRequest("/instances", HttpMethod.POST, toJson(cir)).get();
        assertTrue(new File("/tmp/yamcs-IntegrationTest-data/instance-def/yamcs.inst-test1.yaml").exists());
        assertTrue(new File("/tmp/yamcs-IntegrationTest-data/instance-def/yamcs.inst-test1.metadata").exists());
        YamcsInstance yi = fromJson(resp, YamcsInstance.newBuilder()).build();
        assertEquals(InstanceState.RUNNING, yi.getState());

        resp = restClient.doRequest("/instances/inst-test1:stop", HttpMethod.POST, "").get();
        yi = fromJson(resp, YamcsInstance.newBuilder()).build();
        assertEquals(InstanceState.OFFLINE, yi.getState());

        assertFalse(new File("/tmp/yamcs-IntegrationTest-data/instance-def/yamcs.inst-test1.yaml").exists());
        assertTrue(new File("/tmp/yamcs-IntegrationTest-data/instance-def/yamcs.inst-test1.yaml.offline").exists());

        resp = restClient.doRequest("/instances?filter=label:label1%3DlabelValue1", HttpMethod.GET, "").get();
        ListInstancesResponse lir = fromJson(resp, ListInstancesResponse.newBuilder()).build();
        assertEquals(1, lir.getInstancesCount());
        yi = lir.getInstances(0);
        assertEquals("inst-test1", yi.getName());
        assertEquals(InstanceState.OFFLINE, yi.getState());

        resp = restClient
                .doRequest("/instances?filter=label:label1%3DlabelValue1&filter=state%3Drunning", HttpMethod.GET, "")
                .get();
        lir = fromJson(resp, ListInstancesResponse.newBuilder()).build();
        assertEquals(0, lir.getInstancesCount());

        resp = restClient.doRequest("/instances?filter=state!%3Doffline", HttpMethod.GET, "").get();
        lir = fromJson(resp, ListInstancesResponse.newBuilder()).build();
        assertEquals(1, lir.getInstancesCount());
        yi = lir.getInstances(0);
        assertEquals("IntegrationTest", yi.getName());
    }

}
