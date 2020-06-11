package org.yamcs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;

import org.junit.Test;
import org.yamcs.client.InstanceFilter;
import org.yamcs.protobuf.CreateInstanceRequest;
import org.yamcs.protobuf.ListInstancesResponse;
import org.yamcs.protobuf.YamcsInstance;
import org.yamcs.protobuf.YamcsInstance.InstanceState;

public class InstancesIntegrationTest extends AbstractIntegrationTest {

    @Test
    public void testStopStart() throws Exception {
        List<YamcsInstance> instances = yamcsClient.listInstances().get();
        assertEquals(1, instances.size());
        YamcsInstance yi = instances.get(0);
        assertEquals(yamcsInstance, yi.getName());
        assertEquals(InstanceState.RUNNING, yi.getState());

        yamcsClient.stopInstance(yamcsInstance).get();

        instances = yamcsClient.listInstances().get();
        assertEquals(1, instances.size());
        yi = instances.get(0);
        assertEquals(yamcsInstance, yi.getName());
        assertEquals(InstanceState.OFFLINE, yi.getState());

        yamcsClient.startInstance(yamcsInstance).get();

        instances = yamcsClient.listInstances().get();
        assertEquals(1, instances.size());
        yi = instances.get(0);
        assertEquals(yamcsInstance, yi.getName());
        assertEquals(InstanceState.RUNNING, yi.getState());
    }

    @Test
    public void testCreateStop() throws Exception {
        CreateInstanceRequest cir = CreateInstanceRequest.newBuilder()
                .setName("inst-test1")
                .setTemplate("templ1")
                .putLabels("label1", "labelValue1")
                .putLabels("label2", "labelValue2")
                .build();

        YamcsInstance yi = yamcsClient.createInstance(cir).get();
        assertTrue(new File("/tmp/yamcs-IntegrationTest-data/instance-def/yamcs.inst-test1.yaml").exists());
        assertTrue(new File("/tmp/yamcs-IntegrationTest-data/instance-def/yamcs.inst-test1.metadata").exists());
        assertEquals(InstanceState.RUNNING, yi.getState());

        yi = yamcsClient.stopInstance("inst-test1").get();
        assertEquals(InstanceState.OFFLINE, yi.getState());

        assertFalse(new File("/tmp/yamcs-IntegrationTest-data/instance-def/yamcs.inst-test1.yaml").exists());
        assertTrue(new File("/tmp/yamcs-IntegrationTest-data/instance-def/yamcs.inst-test1.yaml.offline").exists());

        InstanceFilter filter = new InstanceFilter();
        filter.addLabel("label1", "labelValue1");
        ListInstancesResponse lir = yamcsClient.listInstances(filter).get();
        assertEquals(1, lir.getInstancesCount());
        yi = lir.getInstances(0);
        assertEquals("inst-test1", yi.getName());
        assertEquals(InstanceState.OFFLINE, yi.getState());

        filter = new InstanceFilter();
        filter.addLabel("label1", "labelValue1");
        filter.addLabel("state", "running");
        lir = yamcsClient.listInstances(filter).get();
        assertEquals(0, lir.getInstancesCount());

        filter = new InstanceFilter();
        filter.excludeState(InstanceState.OFFLINE);
        lir = yamcsClient.listInstances(filter).get();
        assertEquals(1, lir.getInstancesCount());
        yi = lir.getInstances(0);
        assertEquals("IntegrationTest", yi.getName());
    }
}
