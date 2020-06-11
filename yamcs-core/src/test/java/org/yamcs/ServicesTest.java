package org.yamcs;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;
import org.yamcs.archive.CommandHistoryRecorder;
import org.yamcs.protobuf.ServiceInfo;
import org.yamcs.protobuf.ServiceState;

public class ServicesTest extends AbstractIntegrationTest {

    @Test
    public void testServicesStopStart() throws Exception {
        String serviceClass = CommandHistoryRecorder.class.getName();

        List<ServiceInfo> services = yamcsClient.listServices(yamcsInstance).get();
        assertEquals(9, services.size());

        ServiceInfo servInfo = services.stream()
                .filter(si -> serviceClass.equals(si.getClassName()))
                .findFirst()
                .orElse(null);
        assertEquals(ServiceState.RUNNING, servInfo.getState());

        yamcsClient.stopService(yamcsInstance, servInfo.getName()).get();

        services = yamcsClient.listServices(yamcsInstance).get();
        servInfo = services.stream()
                .filter(si -> serviceClass.equals(si.getClassName()))
                .findFirst()
                .orElse(null);
        assertEquals(ServiceState.TERMINATED, servInfo.getState());

        yamcsClient.startService(yamcsInstance, servInfo.getName()).get();

        services = yamcsClient.listServices(yamcsInstance).get();
        servInfo = services.stream()
                .filter(si -> serviceClass.equals(si.getClassName()))
                .findFirst()
                .orElse(null);
        assertEquals(ServiceState.RUNNING, servInfo.getState());
    }
}
