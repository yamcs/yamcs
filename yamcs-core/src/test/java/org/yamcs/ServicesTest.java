package org.yamcs;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.yamcs.protobuf.ListServicesResponse;
import org.yamcs.protobuf.ServiceInfo;
import org.yamcs.protobuf.ServiceState;

import io.netty.handler.codec.http.HttpMethod;

public class ServicesTest extends AbstractIntegrationTest {

    @Test
    public void testServicesStopStart() throws Exception {
        String serviceClass = "org.yamcs.archive.CommandHistoryRecorder";

        byte[] resp = restClient.doRequest("/services/IntegrationTest", HttpMethod.GET).get();
        ListServicesResponse r = ListServicesResponse.parseFrom(resp);
        assertEquals(9, r.getServicesList().size());

        ServiceInfo servInfo = r.getServicesList().stream()
                .filter(si -> serviceClass.equals(si.getClassName()))
                .findFirst()
                .orElse(null);
        assertEquals(ServiceState.RUNNING, servInfo.getState());

        resp = restClient
                .doRequest("/services/IntegrationTest/" + servInfo.getName() + ":stop", HttpMethod.POST)
                .get();

        resp = restClient.doRequest("/services/IntegrationTest", HttpMethod.GET).get();
        r = ListServicesResponse.parseFrom(resp);
        servInfo = r.getServicesList().stream()
                .filter(si -> serviceClass.equals(si.getClassName()))
                .findFirst()
                .orElse(null);
        assertEquals(ServiceState.TERMINATED, servInfo.getState());

        resp = restClient
                .doRequest("/services/IntegrationTest/" + servInfo.getName() + ":start", HttpMethod.POST)
                .get();

        resp = restClient.doRequest("/services/IntegrationTest", HttpMethod.GET).get();
        r = ListServicesResponse.parseFrom(resp);
        servInfo = r.getServicesList().stream()
                .filter(si -> serviceClass.equals(si.getClassName()))
                .findFirst()
                .orElse(null);
        assertEquals(ServiceState.RUNNING, servInfo.getState());
    }
}
