package org.yamcs;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.yamcs.archive.CommandHistoryRecorder;
import org.yamcs.protobuf.ListServicesResponse;
import org.yamcs.protobuf.ServiceInfo;
import org.yamcs.protobuf.ServiceState;

public class ServicesTest extends AbstractIntegrationTest {

    @Test
    public void testServicesStopStart() throws Exception {
        String serviceClass = CommandHistoryRecorder.class.getName();

        ListServicesResponse response = yamcsClient.listServices(yamcsInstance).get();
        assertEquals(9, response.getServicesList().size());

        ServiceInfo servInfo = response.getServicesList().stream()
                .filter(si -> serviceClass.equals(si.getClassName()))
                .findFirst()
                .orElse(null);
        assertEquals(ServiceState.RUNNING, servInfo.getState());

        yamcsClient.stopService(yamcsInstance, servInfo.getName()).get();

        response = yamcsClient.listServices(yamcsInstance).get();
        servInfo = response.getServicesList().stream()
                .filter(si -> serviceClass.equals(si.getClassName()))
                .findFirst()
                .orElse(null);
        assertEquals(ServiceState.TERMINATED, servInfo.getState());

        yamcsClient.startService(yamcsInstance, servInfo.getName()).get();

        response = yamcsClient.listServices(yamcsInstance).get();
        servInfo = response.getServicesList().stream()
                .filter(si -> serviceClass.equals(si.getClassName()))
                .findFirst()
                .orElse(null);
        assertEquals(ServiceState.RUNNING, servInfo.getState());
    }
}
