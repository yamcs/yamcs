package org.yamcs.http.api;

import org.yamcs.ServiceWithConfig;
import org.yamcs.protobuf.ServiceInfo;
import org.yamcs.protobuf.ServiceState;

public class ServiceHelper {

    public static ServiceInfo toServiceInfo(ServiceWithConfig serviceWithConfig, String instance, String processor) {
        ServiceInfo.Builder serviceb = ServiceInfo.newBuilder()
                .setName(serviceWithConfig.getName())
                .setClassName(serviceWithConfig.getServiceClass())
                .setState(ServiceState.valueOf(serviceWithConfig.getService().state().name()));
        if (instance != null) {
            serviceb.setInstance(instance);
        }
        if (processor != null) {
            serviceb.setProcessor(processor);
        }
        return serviceb.build();
    }
}
