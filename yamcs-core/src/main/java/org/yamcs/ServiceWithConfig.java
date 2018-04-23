package org.yamcs;

import com.google.common.util.concurrent.Service;

/**
 * Holder for a service together with its name and config. Services are used at three levels:
 * <ul>
 * <li>Yamcs server global services
 * <li>Yamcs instance specific services
 * <li>Processor specific services
 * </ul>
 * 
 * @author nm
 */
public class ServiceWithConfig {
    final Service service;
    final String serviceClass;
    final String name;
    final Object args;

    public ServiceWithConfig(Service service, String serviceClass, String name, Object args) {
        super();
        this.service = service;
        this.serviceClass = serviceClass;
        this.name = name;
        this.args = args;
    }

    public Service getService() {
        return service;
    }

    public String getServiceClass() {
        return serviceClass;
    }

    public String getName() {
        return name;
    }

    public Object getArgs() {
        return args;
    }
}
